package com.example.dhruvar.ui.ar.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.google.ar.core.Pose

/**
 * OpenGL ES 2.0 renderer that visualizes 3D tactical assets in AR world space.
 * Applies directional diffuse lighting, color-texture sampling and highlights selected objects.
 */
class ARObjectRenderer {

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var aNormalHandle: Int = 0
    private var aTexCoordHandle: Int = 0
    private var uMvpMatrixHandle: Int = 0
    private var uModelMatrixHandle: Int = 0
    private var uColorHandle: Int = 0
    private var uLightDirHandle: Int = 0
    private var uIsSelectedHandle: Int = 0
    private var uTextureHandle: Int = 0
    private var uHasTextureHandle: Int = 0

    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Directional sunlight coming from above-front-right
    private val lightDir = floatArrayOf(0.4f, 0.8f, 0.45f)

    fun createOnGlThread() {
        // A new GL context invalidates every texture id held by previously loaded meshes.
        AssetModelRegistry.reset()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

        program = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
        }

        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        aNormalHandle = GLES20.glGetAttribLocation(program, "a_Normal")
        aTexCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        uMvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MvpMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelMatrix")
        uColorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        uLightDirHandle = GLES20.glGetUniformLocation(program, "u_LightDir")
        uIsSelectedHandle = GLES20.glGetUniformLocation(program, "u_IsSelected")
        uTextureHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        uHasTextureHandle = GLES20.glGetUniformLocation(program, "u_HasTexture")
    }

    fun draw(
        objects: List<LayoutObject>,
        originPose: Pose,
        selectedObjectId: String?,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        visualizationScale: Float = 1.0f,
        assetSizeFactor: Float = 1.0f,
        poseOverride: ((LayoutObject) -> Pose?)? = null
    ) {
        if (objects.isEmpty()) return

        val scale = visualizationScale.coerceAtLeast(0.001f)
        val sizeBoost = assetSizeFactor.coerceAtLeast(0.1f)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)

        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(uLightDirHandle, 1, lightDir, 0)
        GLES20.glUniform1i(uTextureHandle, 0)

        for (obj in objects) {
            val mesh = AssetModelRegistry.getMesh(obj.assetType)
            val isSelected = (obj.id == selectedObjectId)

            // 1. Transform 2D planning coordinates (x, z, rotation) into ARCore 3D World Pose
            val objectPose = poseOverride?.invoke(obj) ?: ARCoordinateTransformer.transformPlanningToWorldPose(
                originPose = originPose,
                planXMeters = obj.x,
                planZMeters = obj.z,
                planRotationDegrees = obj.rotationDegrees,
                visualizationScale = scale
            )

            objectPose.toMatrix(modelMatrix, 0)

            // 2. Apply physical dimension scaling; miniature mode also shrinks meshes about origin
            val defaultSpec = obj.assetType.defaultSpecification
            val scaleX = (obj.widthMeters / defaultSpec.widthMeters).coerceIn(0.2f, 8.0f) * scale * sizeBoost
            val scaleY = (obj.heightMeters / defaultSpec.heightMeters).coerceIn(0.2f, 8.0f) * scale * sizeBoost
            val scaleZ = (obj.lengthMeters / defaultSpec.lengthMeters).coerceIn(0.2f, 8.0f) * scale * sizeBoost
            Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, scaleZ)

            // 3. Compute MVP transformation
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

            GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
            GLES20.glUniform1f(uIsSelectedHandle, if (isSelected) 1.0f else 0.0f)

            // 4. Draw each part with its own color / texture
            for (part in mesh.parts) {
                drawPart(part)
            }
        }
    }

    private fun drawPart(part: MeshPart) {
        GLES20.glUniform4fv(uColorHandle, 1, part.color, 0)

        val textureId = part.texture?.let { ensureTexture(it) } ?: 0
        val textured = textureId != 0 && part.uvBuffer != null
        GLES20.glUniform1f(uHasTextureHandle, if (textured) 1.0f else 0.0f)
        if (textured) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glEnableVertexAttribArray(aTexCoordHandle)
            GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, part.uvBuffer)
        }

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, part.vertexBuffer)

        GLES20.glEnableVertexAttribArray(aNormalHandle)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 0, part.normalBuffer)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, part.indexCount, GLES20.GL_UNSIGNED_SHORT, part.indexBuffer)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)
        if (textured) GLES20.glDisableVertexAttribArray(aTexCoordHandle)
    }

    /** Uploads the texture on first use. Returns 0 if it could not be decoded. */
    private fun ensureTexture(source: TextureSource): Int {
        if (source.glId != 0) return source.glId
        val data = source.imageData ?: return 0
        source.imageData = null // decode once; a failure falls back to the plain color

        val bitmap = decodeForGpu(data) ?: return 0
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bitmap.recycle()
        source.glId = ids[0]
        return ids[0]
    }

    /**
     * Decodes at no more than [MAX_TEXTURE_SIZE] and rounds each side down to a power of two,
     * which ES 2.0 needs for mipmaps and repeat wrapping.
     */
    private fun decodeForGpu(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_TEXTURE_SIZE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val w = Integer.highestOneBit(decoded.width)
        val h = Integer.highestOneBit(decoded.height)
        if (w == decoded.width && h == decoded.height) return decoded
        return Bitmap.createScaledBitmap(decoded, w, h, true).also { if (it !== decoded) decoded.recycle() }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    companion object {
        private const val MAX_TEXTURE_SIZE = 1024

        private const val VERTEX_SHADER_CODE = """
            uniform mat4 u_MvpMatrix;
            uniform mat4 u_ModelMatrix;
            attribute vec3 a_Position;
            attribute vec3 a_Normal;
            attribute vec2 a_TexCoord;
            varying vec3 v_Normal;
            varying vec2 v_TexCoord;
            void main() {
                v_Normal = normalize((u_ModelMatrix * vec4(a_Normal, 0.0)).xyz);
                v_TexCoord = a_TexCoord;
                gl_Position = u_MvpMatrix * vec4(a_Position, 1.0);
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            varying vec3 v_Normal;
            varying vec2 v_TexCoord;
            uniform vec4 u_Color;
            uniform vec3 u_LightDir;
            uniform float u_IsSelected;
            uniform sampler2D u_Texture;
            uniform float u_HasTexture;
            void main() {
                vec4 base = u_Color;
                if (u_HasTexture > 0.5) {
                    base = base * texture2D(u_Texture, v_TexCoord);
                }
                if (base.a < 0.1) {
                    discard;
                }
                float diffuse = max(dot(v_Normal, normalize(u_LightDir)), 0.0);
                float ambient = 0.40;
                float light = ambient + (diffuse * 0.60);
                vec3 rgb = base.rgb * light;
                if (u_IsSelected > 0.5) {
                    rgb = mix(rgb, vec3(0.96, 0.60, 0.12), 0.40);
                }
                gl_FragColor = vec4(rgb, 1.0);
            }
        """
    }
}
