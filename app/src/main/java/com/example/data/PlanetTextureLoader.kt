package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class PlanetTexKey(val urls: List<String>) {
    EARTH_DAY(listOf("https://cdn.jsdelivr.net/npm/three-globe@2.25.1/example/img/earth-blue-marble.jpg")),
    EARTH_NIGHT(listOf("https://cdn.jsdelivr.net/npm/three-globe@2.25.1/example/img/earth-night.jpg")),
    EARTH_WATER(listOf("https://cdn.jsdelivr.net/npm/three-globe@2.25.1/example/img/earth-water.png")),
    EARTH_CLOUD(listOf(
        "https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/earthcloudmaptrans.jpg",
        "https://raw.githubusercontent.com/jeromeetienne/threex.planets/master/images/earthcloudmaptrans.jpg"
    )),
    MOON_MAP(listOf(
        "https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/moonmap1k.jpg",
        "https://raw.githubusercontent.com/jeromeetienne/threex.planets/master/images/moonmap1k.jpg"
    )),
    MERCURY_MAP(listOf("https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/mercurymap.jpg")),
    MARS_MAP(listOf(
        "https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/marsmap1k.jpg",
        "https://raw.githubusercontent.com/jeromeetienne/threex.planets/master/images/marsmap1k.jpg"
    )),
    JUPITER_MAP(listOf(
        "https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/jupitermap.jpg",
        "https://raw.githubusercontent.com/jeromeetienne/threex.planets/master/images/jupitermap.jpg"
    )),
    SATURN_MAP(listOf(
        "https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/saturnmap.jpg",
        "https://raw.githubusercontent.com/jeromeetienne/threex.planets/master/images/saturnmap.jpg"
    )),
    URANUS_MAP(listOf("https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/uranusmap.jpg")),
    NEPTUNE_MAP(listOf("https://cdn.jsdelivr.net/gh/jeromeetienne/threex.planets@master/images/neptunemap.jpg"))
}

class PlanetTextureLoader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun loadTextureBitmap(key: PlanetTexKey): Bitmap? = withContext(Dispatchers.IO) {
        for (url in key.urls) {
            try {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) continue
                    val bytes = resp.body?.bytes() ?: continue
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bmp != null) return@withContext bmp
                }
            } catch (_: Exception) {
                // Fallback to next mirror or pure 8K procedural GLSL surface
            }
        }
        null
    }
}
