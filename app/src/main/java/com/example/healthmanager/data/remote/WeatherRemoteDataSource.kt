package com.example.healthmanager.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class WeatherRemoteDataSource(
    private val client: OkHttpClient,
    private val apiKey: String
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

    fun fetchWeatherJson(queryCity: String): JSONObject {
        val encodedCity = URLEncoder.encode(queryCity, "UTF-8")
        val url = "https://uapis.cn/api/v1/misc/weather" +
                "?city=$encodedCity" +
                "&key=$apiKey" +
                "&forecast=true" +
                "&hourly=true" +
                "&indices=true" +
                "&extended=true"

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException("Weather request failed: HTTP ${response.code} $body")
            }

            if (body.startsWith("<!DOCTYPE") || body.startsWith("<html")) {
                throw IllegalStateException("Weather API returned HTML instead of JSON")
            }

            return JSONObject(body)
        }
    }
}
