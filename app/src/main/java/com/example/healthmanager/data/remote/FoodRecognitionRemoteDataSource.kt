package com.example.healthmanager.data.remote

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class FoodRecognitionRemoteDataSource(
    private val client: OkHttpClient,
    private val apiKey: String
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

    fun recognizeFoodJson(
        imageBase64: String,
        imageDataUrl: String,
        promptText: String
    ): JSONObject {
        val candidates = listOf(
            FoodModelConfig(
                model = "glm-4v-flash",
                enableThinking = false,
                useRawBase64Image = false
            ),
            FoodModelConfig(
                model = "glm-4v-plus",
                enableThinking = false,
                useRawBase64Image = false
            )
        )

        var lastError: Exception? = null

        for (candidate in candidates) {
            try {
                return recognizeFoodJsonOnce(
                    imagePayload = if (candidate.useRawBase64Image) imageBase64 else imageDataUrl,
                    promptText = promptText,
                    config = candidate
                )
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: IllegalStateException("All food recognition models failed")
    }

    private fun recognizeFoodJsonOnce(
        imagePayload: String,
        promptText: String,
        config: FoodModelConfig
    ): JSONObject {
        val jsonPayload = JSONObject().apply {
            put("model", config.model)
            put("temperature", 0.1)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", promptText)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", imagePayload)
                            })
                        })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("https://open.bigmodel.cn/api/paas/v4/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException("Food recognition request failed: HTTP ${response.code} $body")
            }

            val root = JSONObject(body)
            if (!root.has("choices")) {
                throw IllegalStateException("Food recognition response missing choices: $body")
            }

            val message = root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")

            val content = extractMessageContent(message)
            val cleaned = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonStart = cleaned.indexOf("{")
            val jsonEnd = cleaned.lastIndexOf("}")

            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                throw IllegalStateException("Food recognition response did not contain valid JSON: $cleaned")
            }

            return JSONObject(cleaned.substring(jsonStart, jsonEnd + 1))
        }
    }

    private fun extractMessageContent(message: JSONObject): String {
        return when (val contentValue = message.opt("content")) {
            is String -> contentValue
            is JSONArray -> buildString {
                for (index in 0 until contentValue.length()) {
                    when (val item = contentValue.opt(index)) {
                        is JSONObject -> append(item.optString("text", item.toString()))
                        null -> Unit
                        else -> append(item.toString())
                    }
                }
            }
            null -> ""
            else -> contentValue.toString()
        }
    }

    private data class FoodModelConfig(
        val model: String,
        val enableThinking: Boolean,
        val useRawBase64Image: Boolean
    )
}
