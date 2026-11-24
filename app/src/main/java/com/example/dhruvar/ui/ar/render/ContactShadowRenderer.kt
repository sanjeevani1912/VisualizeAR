package com.example.dhruvar.ui.ar.render

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Soft dark "blob" drawn flat on the ground under an asset so it looks planted, not floating.
 * Same simple shader style as [OriginMarkerRenderer]; blended, depth write off. Draw after assets.
 */
class ContactShadowRenderer {

    private var program = 0
    private var aPositionHandle = 0
    private var uMvpMatrixHandle = 0
    private var uAlphaHandle = 0

    private val modelMatrix = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val fan: FloatBuffer

    init {
        // Unit disc in the XZ plane: centre + rim, as a triangle fan.
        val coords = FloatArray((SEGMENTS + 2) * 3)
        for (i in 0..SEGMENTS) {
            val a = 2.0 * Math.PI * i / SEGMENTS
            coords[(i + 1) * 3 + 0] = cos(a).toFloat()
            coords[(i + 1) * 3 + 2] = sin(a).toFloat()
        }
        fan = ByteBuffer.allocateDirect(coords.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(coords); position(0) }
    }

    fun createOnGlThread() {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        uMvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MvpMatrix")
        uAlphaHandle = GLES20.glGetUniformLocation(program, "u_Alpha")
    }

    /** Draws one shadow centred at world ([x], [groundY], [z]) with the given [radius] in metres. */
    fun draw(
        x: Float,
        groundY: Float,
        z: Float,
        radius: Float,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, groundY + LIFT_M, z)
        Matrix.scaleM(modelMatrix, 0, radius, 1f, radius)
        Matrix.multiplyMM(viewModel, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewModel, 0)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(uAlphaHandle, ALPHA)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, fan)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, SEGMENTS + 2)
        GLES20.glDisableVertexAttribArray(aPositionHandle)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun loadShader(type: Int, code: String): Int =
        GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }

    private companion object {
        const val SEGMENTS = 28
        const val LIFT_M = 0.01f
        const val ALPHA = 0.45f

        const val VERTEX_SHADER_CODE = """
            uniform mat4 u_MvpMatrix;
            attribute vec3 a_Position;
            varying vec2 v_Disc;
            void main() {
                v_Disc = a_Position.xz;
                gl_Position = u_MvpMatrix * vec4(a_Position, 1.0);
            }
        """

        const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            uniform float u_Alpha;
            varying vec2 v_Disc;
            void main() {
                float d = length(v_Disc);
                float fade = 1.0 - smoothstep(0.25, 1.0, d);
                gl_FragColor = vec4(0.0, 0.0, 0.0, u_Alpha * fade);
            }
        """
    }
}
