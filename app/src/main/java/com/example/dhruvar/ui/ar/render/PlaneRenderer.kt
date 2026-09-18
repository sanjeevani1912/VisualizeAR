package com.example.dhruvar.ui.ar.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders detected horizontal ground planes in OpenGL ES 2.0.
 *
 * Displays:
 * 1. A semi-transparent tactical mesh surface (alpha ~ 0.25) so the user clearly
 *    sees the detected ground plane without occluding the real world.
 * 2. A crisp, high-visibility tactical boundary line indicating plane boundaries.
 */
class PlaneRenderer {

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var uMvpMatrixHandle: Int = 0
    private var uColorHandle: Int = 0

    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Reusable vertex buffer for dynamic plane polygon vertices
    private var vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(1024 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

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
        planes: Collection<Plane>,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        val trackingPlanes = planes.filter {
            it.trackingState == TrackingState.TRACKING &&
                (it.type == Plane.Type.HORIZONTAL_UPWARD_FACING || it.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING)
        }

        if (trackingPlanes.isEmpty()) return

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)

        for (plane in trackingPlanes) {
            val polygon = plane.polygon ?: continue
            val pointCount = polygon.remaining() / 2
            if (pointCount < 3) continue

            plane.centerPose.toMatrix(modelMatrix, 0)
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

            GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)

            // Prepare 3D vertices: (x, 0.0, z) from the 2D polygon (x, z)
            val requiredCapacity = (pointCount + 1) * 3
            if (vertexBuffer.capacity() < requiredCapacity) {
                vertexBuffer = ByteBuffer.allocateDirect(requiredCapacity * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            }
            vertexBuffer.clear()

            // Center vertex for triangle fan
            vertexBuffer.put(0.0f)
            vertexBuffer.put(0.0f)
            vertexBuffer.put(0.0f)

            polygon.rewind()
            while (polygon.hasRemaining()) {
                val x = polygon.get()
                val z = polygon.get()
                vertexBuffer.put(x)
                vertexBuffer.put(0.0f)
                vertexBuffer.put(z)
            }
            // Close the fan with the first point
            polygon.rewind()
            vertexBuffer.put(polygon.get())
            vertexBuffer.put(0.0f)
            vertexBuffer.put(polygon.get())

            vertexBuffer.position(0)

            GLES20.glEnableVertexAttribArray(aPositionHandle)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            // 1. Draw subtle tactical plane surface (semi-transparent Precision Blue)
            GLES20.glUniform4f(uColorHandle, 0.0f, 0.478f, 0.855f, 0.22f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, pointCount + 2)

            // 2. Draw crisp boundary line loop (brighter border)
            GLES20.glLineWidth(3.0f)
            GLES20.glUniform4f(uColorHandle, 0.15f, 0.65f, 1.0f, 0.85f)
            // Skip center vertex for line loop: points 1 to pointCount
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 1, pointCount)

            GLES20.glDisableVertexAttribArray(aPositionHandle)
        }

        GLES20.glDepthMask(true)
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
