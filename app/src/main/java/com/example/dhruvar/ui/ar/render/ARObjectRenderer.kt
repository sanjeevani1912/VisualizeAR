package com.example.dhruvar.ui.ar.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.google.ar.core.Pose

/**
 * OpenGL ES 2.0 renderer that visualizes 3D tactical assets in AR world space.
 * Applies directional diffuse lighting and highlights selected objects.
 */
class ARObjectRenderer {

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var aNormalHandle: Int = 0
    private var uMvpMatrixHandle: Int = 0
    private var uModelMatrixHandle: Int = 0
    private var uColorHandle: Int = 0
    private var uLightDirHandle: Int = 0
    private var uIsSelectedHandle: Int = 0

    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Directional sunlight coming from above-front-right
    private val lightDir = floatArrayOf(0.4f, 0.8f, 0.45f)

    fun createOnGlThread() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

        program = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
        }

        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        aNormalHandle = GLES20.glGetAttribLocation(program, "a_Normal")
        uMvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MvpMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelMatrix")
        uColorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        uLightDirHandle = GLES20.glGetUniformLocation(program, "u_LightDir")
        uIsSelectedHandle = GLES20.glGetUniformLocation(program, "u_IsSelected")
    }

    fun draw(
        objects: List<LayoutObject>,
        originPose: Pose,
        selectedObjectId: String?,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        if (objects.isEmpty()) return

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)

        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(uLightDirHandle, 1, lightDir, 0)

        for (obj in objects) {
            val mesh = AssetModelRegistry.getMesh(obj.assetType)
            val isSelected = (obj.id == selectedObjectId)

            // 1. Transform 2D planning coordinates (x, z, rotation) into ARCore 3D World Pose
            val objectPose = ARCoordinateTransformer.transformPlanningToWorldPose(
                originPose = originPose,
                planXMeters = obj.x,
                planZMeters = obj.z,
                planRotationDegrees = obj.rotationDegrees
            )

            objectPose.toMatrix(modelMatrix, 0)

            // 2. Apply physical dimension scaling if custom dimensions differ from prototype specification
            val defaultSpec = obj.assetType.defaultSpecification
            val scaleX = (obj.widthMeters / defaultSpec.widthMeters).coerceIn(0.2f, 5.0f)
            val scaleY = (obj.heightMeters / defaultSpec.heightMeters).coerceIn(0.2f, 5.0f)
            val scaleZ = (obj.lengthMeters / defaultSpec.lengthMeters).coerceIn(0.2f, 5.0f)
            Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, scaleZ)

            // 3. Compute MVP transformation
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

            GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)

            // 4. Pass mesh color & selection flag
            GLES20.glUniform4fv(uColorHandle, 1, mesh.primaryColor, 0)
            GLES20.glUniform1f(uIsSelectedHandle, if (isSelected) 1.0f else 0.0f)

            // 5. Bind vertex buffers & draw solid geometry
            GLES20.glEnableVertexAttribArray(aPositionHandle)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer)

            GLES20.glEnableVertexAttribArray(aNormalHandle)
            GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.normalBuffer)

            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                mesh.indexCount,
                GLES20.GL_UNSIGNED_SHORT,
                mesh.indexBuffer
            )

            // 6. If selected, draw a distinctive luminous wireframe accent around the asset
            if (isSelected) {
                GLES20.glLineWidth(3.5f)
                GLES20.glUniform4f(uColorHandle, 0.98f, 0.58f, 0.12f, 1.0f) // Anchor Orange highlight
                GLES20.glDrawElements(
                    GLES20.GL_LINES,
                    mesh.indexCount,
                    GLES20.GL_UNSIGNED_SHORT,
                    mesh.indexBuffer
                )
            }

            GLES20.glDisableVertexAttribArray(aPositionHandle)
            GLES20.glDisableVertexAttribArray(aNormalHandle)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    companion object {
        private const val VERTEX_SHADER_CODE = """
            uniform mat4 u_MvpMatrix;
            uniform mat4 u_ModelMatrix;
            attribute vec3 a_Position;
            attribute vec3 a_Normal;
            varying vec3 v_Normal;
            void main() {
                v_Normal = normalize((u_ModelMatrix * vec4(a_Normal, 0.0)).xyz);
                gl_Position = u_MvpMatrix * vec4(a_Position, 1.0);
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            varying vec3 v_Normal;
            uniform vec4 u_Color;
            uniform vec3 u_LightDir;
            uniform float u_IsSelected;
            void main() {
                float diffuse = max(dot(v_Normal, normalize(u_LightDir)), 0.0);
                float ambient = 0.40;
                float light = ambient + (diffuse * 0.60);
                vec3 rgb = u_Color.rgb * light;
                if (u_IsSelected > 0.5) {
                    rgb = mix(rgb, vec3(0.96, 0.60, 0.12), 0.35);
                }
                gl_FragColor = vec4(rgb, u_Color.a);
            }
        """
    }
}
