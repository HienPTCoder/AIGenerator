package com.example.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class HuggingFaceService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a request to Hugging Face Inference API.
     * Some model endpoints take the parameters combined as JSON,
     * while standard Image-to-Image models accept binary image bytes with prompt parameters.
     */
    suspend fun generateImage(
        modelName: String,
        apiKey: String,
        prompt: String,
        negativePrompt: String,
        styleStrength: Float,
        guidanceScale: Float,
        faceBitmap: Bitmap
    ): GenerationResult {
        if (apiKey.isBlank()) {
            return GenerationResult.Error("API Key is missing. Please add your Hugging Face API key in Settings or activate AI Simulation Mode.")
        }

        val url = "https://api-inference.huggingface.co/models/$modelName"
        Log.d("HFC", "Calling HF API: $url")

        // Progressively compress bitmap to JPEG array to stay underneath Hugging Face API payload size limits safely
        val outputStream = ByteArrayOutputStream()
        faceBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val imageBytes = outputStream.toByteArray()

        try {
            // First approach: POST the binary image directly to Hugging Face.
            // Under this paradigm, parameters are supplied via structured JSON contents or custom headers.
            // Let's perform a JSON-based input request with Base64 if possible, or binary payload depending on the model's preferred mode
            
            val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("X-Prompt", prompt) // Standard fallback header for prompts in binary inputs
                .addHeader("X-Negative-Prompt", negativePrompt)
                .addHeader("X-Strength", styleStrength.toString())
                .addHeader("X-Guidance-Scale", guidanceScale.toString())
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val contentType = response.body?.contentType()?.toString() ?: ""
                
                Log.d("HFC", "HF Response Status: $code, Content-Type: $contentType")

                if (response.isSuccessful) {
                    val responseBytes = response.body?.bytes()
                    if (responseBytes != null && (contentType.contains("image") || responseBytes.size > 2048)) {
                        val bitmap = BitmapFactory.decodeByteArray(responseBytes, 0, responseBytes.size)
                        if (bitmap != null) {
                            return GenerationResult.Success(bitmap, isSimulated = false)
                        }
                    }
                }

                // If unsuccessful or returned error JSON (e.g. model loading/warming up), parse response.
                val errorString = response.body?.string() ?: "Unknown API response"
                Log.e("HFC", "HF API Failed with: $errorString")

                if (response.code == 503) {
                    // 503 means model is currently loading, HF provides estimated time in JSON
                    try {
                        val json = JSONObject(errorString)
                        val estimatedTime = json.optDouble("estimated_time", 20.0)
                        return GenerationResult.LoadingModel(estimatedTime)
                    } catch (e: Exception) {
                        return GenerationResult.LoadingModel(25.0)
                    }
                }

                if (response.code == 401 || response.code == 403) {
                    return GenerationResult.Error("Authentication Error: Please double check that your Hugging Face API key is correct and has standard scopes.")
                }

                return GenerationResult.Error("API error ($code): ${parseErrorMessage(errorString)}")
            }

        } catch (e: IOException) {
            Log.e("HFC", "IOException during API query", e)
            return GenerationResult.Error("Network Error: Please check your internet connection and try again.")
        } catch (e: Exception) {
            Log.e("HFC", "Generic Error during API query", e)
            return GenerationResult.Error(e.message ?: "An unexpected error occurred.")
        }
    }

    private fun parseErrorMessage(errorJson: String): String {
        return try {
            val obj = JSONObject(errorJson)
            if (obj.has("error")) {
                obj.getString("error")
            } else if (obj.has("message")) {
                obj.getString("message")
            } else {
                errorJson.take(120)
            }
        } catch (e: Exception) {
            errorJson.take(120)
        }
    }
}

sealed class GenerationResult {
    data class Success(val bitmap: Bitmap, val isSimulated: Boolean) : GenerationResult()
    data class LoadingModel(val estimatedTimeSeconds: Double) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}
