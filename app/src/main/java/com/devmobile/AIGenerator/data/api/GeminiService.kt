package com.devmobile.AIGenerator.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateImage(
        prompt: String,
        faceBitmap: Bitmap,
        apiKey: String
    ): GenerationResult = withContext(Dispatchers.IO) {
        try {
            Log.d("GeminiService", "Generating image with prompt: $prompt")
            val bitmap = callImageGeneration(prompt, apiKey)
                ?: return@withContext GenerationResult.Error("Không nhận được ảnh từ server. Vui lòng thử lại.")
            return@withContext GenerationResult.Success(bitmap, isSimulated = false)
        } catch (e: Exception) {
            Log.e("GeminiService", "Image generation failed", e)
            return@withContext GenerationResult.Error(buildUserMessage(e.localizedMessage ?: "Lỗi kết nối."))
        }
    }

    private fun callImageGeneration(prompt: String, apiKey: String): Bitmap? {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("IMAGE")
                    put("TEXT")
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/gemini-3.1-flash-image-preview:generateContent?key=$apiKey")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = response.body?.string() ?: ""
                Log.e("GeminiService", "HTTP ${response.code}: $error")
                throw Exception("HTTP ${response.code}: $error")
            }
            val body = response.body?.string() ?: return null
            val parts = JSONObject(body)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("inline_data")) {
                    val data = part.getJSONObject("inline_data").getString("data")
                    val bytes = Base64.decode(data, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            return null
        }
    }

    private fun buildUserMessage(msg: String): String = when {
        msg.contains("401") || msg.contains("API key", ignoreCase = true) ->
            "API Key không hợp lệ. Kiểm tra lại trong Settings."
        msg.contains("403") ->
            "Không có quyền truy cập. Bật Gemini API tại Google AI Studio."
        msg.contains("429") || msg.contains("quota", ignoreCase = true) ->
            "Đã vượt giới hạn miễn phí (429). Thử lại sau."
        else -> "Lỗi: $msg"
    }
}

sealed class GenerationResult {
    data class Success(val bitmap: Bitmap, val isSimulated: Boolean) : GenerationResult()
    data class LoadingModel(val estimatedTimeSeconds: Double) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}
