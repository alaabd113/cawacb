package com.example.data

import com.example.domain.CityLocation
import com.example.domain.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchLiveWeather(city: CityLocation): WeatherInfo = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,cloud_cover,weather_code",
            city.lat,
            city.lon
        )
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext fallbackWeather(city)
                val body = response.body?.string() ?: return@withContext fallbackWeather(city)
                parseOpenMeteoJson(body)
            }
        } catch (_: Exception) {
            fallbackWeather(city)
        }
    }

    internal fun parseOpenMeteoJson(json: String): WeatherInfo {
        val root = JSONObject(json)
        val current = root.optJSONObject("current") ?: return WeatherInfo()
        val temp = current.optDouble("temperature_2m", 22.0)
        val cloud = current.optInt("cloud_cover", 25).coerceIn(0, 100)
        val code = current.optInt("weather_code", 1)
        return WeatherInfo(
            cloudCoverPercent = cloud,
            temperatureC = temp,
            descriptionAr = "${wmoCodeToArabic(code)} · ${String.format(Locale.US, "%.0f°C", temp)}",
            isLive = true
        )
    }

    private fun fallbackWeather(city: CityLocation): WeatherInfo {
        val approxTemp = (28.0 - kotlin.math.abs(city.lat - 22.0) * 0.45).coerceIn(8.0, 34.0)
        return WeatherInfo(
            cloudCoverPercent = 24,
            temperatureC = approxTemp,
            descriptionAr = "سماء صافية نسبياً · ${String.format(Locale.US, "%.0f°C", approxTemp)}",
            isLive = false
        )
    }

    private fun wmoCodeToArabic(code: Int): String = when (code) {
        0 -> "سماء صافية تماماً"
        1, 2 -> "غائم جزئياً"
        3 -> "سماء ملبدة بالغيوم"
        45, 48 -> "ضباب خفيف"
        51, 53, 55, 61, 63, 65 -> "أمطار متفرقة"
        71, 73, 75 -> "تساقط ثلوج"
        80, 81, 82 -> "زخات مطرية"
        95, 96, 99 -> "عاصفة رعدية"
        else -> "أجواء مستقرة"
    }
}
