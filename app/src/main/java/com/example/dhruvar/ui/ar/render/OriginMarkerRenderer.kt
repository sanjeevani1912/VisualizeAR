package com.example.dhruvar.ui.ar.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Anchor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * OpenGL ES 2.0 renderer that visualizes the calibrated AR Planning Origin on the physical ground.
 *
 * Displays:
 * 1. Concentric tactical datum rings in AnchorOrange.
 * 2. Crosshair coordinate orientation axes (+X lateral, +Z depth).
 * 3. Solid center datum point.
 */
class OriginMarkerRenderer {

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var uMvpMatrixHandle: Int = 0
    private var uColorHandle: Int = 0

    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val outerRingBuffer: FloatBuffer
    private val innerRingBuffer: FloatBuffer
    private val crosshairBuffer: FloatBuffer

    private val ringSegments = 40

    init {
        // Outer ring (radius 0.35m)
        outerRingBuffer = createCircleBuffer(radius = 0.35f, segments = ringSegments)
        // Inner ring (radius 0.15m)
        innerRingBuffer = createCircleBuffer(radius = 0.15f, segments = ringSegments)

        // Crosshair lines along +X and +Z axes (0.5m length, 5mm vertical bias)
        val crosshairCoords = floatArrayOf(
            -0.5f, 0.005f, 0.0f,   0.5f, 0.005f, 0.0f, // X axis
            0.0f, 0.005f, -0.5f,   0.0f, 0.005f, 0.5f  // Z axis
        )
        crosshairBuffer = ByteBuffer.allocateDirect(crosshairCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(crosshairCoords)
                position(0)
            }
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
        anchor: Anchor,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        anchor.pose.toMatrix(modelMatrix, 0)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Tactical Anchor Orange: rgba(0.96, 0.45, 0.05, 0.90)
        GLES20.glUniform4f(uColorHandle, 0.96f, 0.45f, 0.05f, 0.90f)
        GLES20.glLineWidth(4.0f)

        // 1. Draw outer ring
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, outerRingBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, ringSegments)

        // 2. Draw inner ring
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, innerRingBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, ringSegments)

        // 3. Draw crosshair axes
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, crosshairBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun createCircleBuffer(radius: Float, segments: Int): FloatBuffer {
        val coords = FloatArray(segments * 3)
        for (i in 0 until segments) {
            val angle = 2.0 * Math.PI * i / segments
            coords[i * 3 + 0] = (radius * cos(angle)).toFloat()
            coords[i * 3 + 1] = 0.005f // Slight 5mm vertical bias to prevent z-fighting with ground plane
            coords[i * 3 + 2] = (radius * sin(angle)).toFloat()
        }
        return ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(coords)
                position(0)
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
