package com.example.dhruvar.ui.ar.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders a 3D tactical distance measurement line in AR world space
 * connecting the Planning Origin anchor to the selected object's base.
 */
class ARDistanceLineRenderer {

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var uMvpMatrixHandle: Int = 0
    private var uColorHandle: Int = 0

    private val mvpMatrix = FloatArray(16)
    private val lineBuffer: FloatBuffer

    init {
        lineBuffer = ByteBuffer.allocateDirect(2 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    fun createOnGlThread() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

        program = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
        }

        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        uMvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MvpMatrix")
        uColorHandle = GLES20.glGetUniformLocation(program, "u_Color")
    }

    fun draw(
        originPose: Pose,
        objectPose: Pose,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        // MVP matrix directly from view * projection (vertices are in world coordinates)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val oTrans = originPose.translation
        val tTrans = objectPose.translation

        val lineCoords = floatArrayOf(
            oTrans[0], oTrans[1] + 0.05f, oTrans[2],
            tTrans[0], tTrans[1] + 0.05f, tTrans[2]
        )

        lineBuffer.clear()
        lineBuffer.put(lineCoords)
        lineBuffer.position(0)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)
        // High-visibility Tactical Anchor Orange line: rgba(0.98, 0.58, 0.12, 0.95)
        GLES20.glUniform4f(uColorHandle, 0.98f, 0.58f, 0.12f, 0.95f)

        GLES20.glLineWidth(5.0f)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, lineBuffer)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
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
            attribute vec3 a_Position;
            void main() {
                gl_Position = u_MvpMatrix * vec4(a_Position, 1.0);
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """
    }
}
