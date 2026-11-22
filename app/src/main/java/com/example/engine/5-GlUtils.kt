package com.example.engine

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

private const val TAG = "CosmosGL"

class GlProgram(val handle: Int) {
    private val uniformCache = HashMap<String, Int>()

    fun use() {
        GLES30.glUseProgram(handle)
    }

    fun loc(name: String): Int =
        uniformCache.getOrPut(name) { GLES30.glGetUniformLocation(handle, name) }

    fun setFloat(name: String, v: Float) {
        val l = loc(name); if (l >= 0) GLES30.glUniform1f(l, v)
    }

    fun setInt(name: String, v: Int) {
        val l = loc(name); if (l >= 0) GLES30.glUniform1i(l, v)
    }

    fun setVec2(name: String, x: Float, y: Float) {
        val l = loc(name); if (l >= 0) GLES30.glUniform2f(l, x, y)
    }

    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        val l = loc(name); if (l >= 0) GLES30.glUniform3f(l, x, y, z)
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val l = loc(name); if (l >= 0) GLES30.glUniform4f(l, x, y, z, w)
    }

    fun setMat4(name: String, m: FloatArray) {
        val l = loc(name); if (l >= 0) GLES30.glUniformMatrix4fv(l, 1, false, m, 0)
    }

    fun setVec3Array(name: String, data: FloatArray, count: Int) {
        val l = loc(name); if (l >= 0) GLES30.glUniform3fv(l, count, data, 0)
    }

    fun setVec4Array(name: String, data: FloatArray, count: Int) {
        val l = loc(name); if (l >= 0) GLES30.glUniform4fv(l, count, data, 0)
    }
}

object GlUtils {
    fun compileProgram(vsSource: String, fsSource: String): GlProgram {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES30.glGetProgramInfoLog(prog)}")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return GlProgram(prog)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val typeStr = if (type == GLES30.GL_VERTEX_SHADER) "VS" else "FS"
            Log.e(TAG, "$typeStr compile error: ${GLES30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    fun createFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }

    fun createIntBuffer(data: IntArray): IntBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply { put(data); position(0) }

    fun createWhiteTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        val buf = ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)).apply { position(0) }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return tex
    }

    fun createTextureFromBitmap(bitmap: Bitmap, clampS: Boolean = false): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            if (clampS) GLES30.GL_CLAMP_TO_EDGE else GLES30.GL_REPEAT
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        // Sharper textures at glancing angles when the GPU supports anisotropic filtering
        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        if (extensions.contains("GL_EXT_texture_filter_anisotropic")) {
            val maxAniso = FloatArray(1)
            GLES30.glGetFloatv(0x84FF, maxAniso, 0) // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, 0x84FE, minOf(maxAniso[0], 4f)) // GL_TEXTURE_MAX_ANISOTROPY_EXT
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return tex
    }
}
