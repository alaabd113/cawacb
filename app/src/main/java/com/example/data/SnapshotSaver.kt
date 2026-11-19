package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import java.io.File
import java.io.FileOutputStream

object SnapshotSaver {
    fun captureAndSave(
        context: Context,
        surfaceView: GLSurfaceView,
        onResult: (Boolean) -> Unit
    ) {
        val width = surfaceView.width
        val height = surfaceView.height
        if (width <= 0 || height <= 0) {
            onResult(false)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            val saved = saveBitmapToGallery(context, bitmap)
                            bitmap.recycle()
                            onResult(saved)
                        } else {
                            bitmap.recycle()
                            onResult(false)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (_: Exception) {
                bitmap.recycle()
                onResult(false)
            }
        } else {
            onResult(false)
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        val filename = "COSMOS_IX_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CosmosIX")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
