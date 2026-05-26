package com.devmobile.AIGenerator.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenRouterService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateImage(
        modelId: String,
        prompt: String,
        apiKey: String
    ): GenerationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GenerationResult.Error("OpenRouter API Key is empty. Please configure it in Settings.")
        }

        try {
            Log.d("OpenRouterService", "Starting image generation with model: $modelId")
            val escapedPrompt = escapeJsonString(prompt)
            val jsonPayload = """
                {
                  "model": "$modelId",
                  "messages": [
                    {
                      "role": "user",
                      "content": $escapedPrompt
                    }
                  ],
                  "modalities": ["image"],
                  "max_tokens": 1000
                }
            """.trimIndent()

            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/devmobile/AIGenerator")
                .addHeader("X-Title", "AI Portrait Generator")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                Log.d("OpenRouterService", "Response received. Code: ${response.code}")

                if (!response.isSuccessful || bodyString == null) {
                    val errorMsg = try {
                        bodyString?.let {
                            val errObj = JSONObject(it)
                            if (errObj.has("error")) {
                                errObj.getJSONObject("error").optString("message", "Error code ${response.code}")
                            } else {
                                "Error code ${response.code}"
                              }
                        } ?: "Empty response body"
                    } catch (e: Exception) {
                        "Error code ${response.code}: ${response.message}"
                    }
                    return@withContext GenerationResult.Error("OpenRouter API Error: $errorMsg")
                }

                val jsonObj = JSONObject(bodyString)
                
                // Check for top-level error object from OpenRouter
                if (jsonObj.has("error")) {
                    val errorObj = jsonObj.getJSONObject("error")
                    val errMsg = errorObj.optString("message", "Unknown OpenRouter error")
                    return@withContext GenerationResult.Error("OpenRouter Error: $errMsg")
                }

                val choices = jsonObj.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext GenerationResult.Error("Không có kết quả sinh ảnh từ OpenRouter.")
                }

                val messageObj = choices.getJSONObject(0).optJSONObject("message")
                    ?: return@withContext GenerationResult.Error("Phản hồi trống từ OpenRouter.")

                var base64Url = ""

                // 1. Try to check message.images
                if (messageObj.has("images")) {
                    val imagesArray = messageObj.getJSONArray("images")
                    if (imagesArray.length() > 0) {
                        val firstImage = imagesArray.get(0)
                        base64Url = when (firstImage) {
                            is JSONObject -> {
                                if (firstImage.has("image_url")) {
                                    firstImage.getJSONObject("image_url").optString("url", "")
                                } else {
                                    firstImage.optString("url", "")
                                }
                            }
                            is String -> firstImage
                            else -> ""
                        }
                    }
                }

                // 2. If not found, check message.content
                if (base64Url.isBlank() && messageObj.has("content")) {
                    val contentStr = messageObj.optString("content", "")
                    if (contentStr.startsWith("data:image/")) {
                        base64Url = contentStr
                    }
                }

                if (base64Url.isBlank()) {
                    return@withContext GenerationResult.Error("Không tìm thấy dữ liệu ảnh Base64 trong phản hồi của OpenRouter.")
                }

                val base64Data = if (base64Url.contains(",")) {
                    base64Url.split(",")[1]
                } else {
                    base64Url
                }.trim()

                return@withContext try {
                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    if (bitmap != null) {
                        GenerationResult.Success(bitmap, isSimulated = false)
                    } else {
                        GenerationResult.Error("Không thể giải mã dữ liệu ảnh thành Bitmap.")
                    }
                } catch (e: Exception) {
                    Log.e("OpenRouterService", "Failed to decode Base64", e)
                    GenerationResult.Error("Lỗi giải mã ảnh chân dung: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e("OpenRouterService", "API call failed", e)
            return@withContext GenerationResult.Error("Lỗi kết nối OpenRouter: ${e.localizedMessage}")
        }
    }

    suspend fun analyzeFace(
        bitmap: Bitmap,
        apiKey: String
    ): FaceAnalysisResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext FaceAnalysisResult.Error("API Key trống. Vui lòng cấu hình trong phần Cài đặt.")
        }

        try {
            Log.d("OpenRouterService", "Starting face analysis with OpenRouter...")
            val resized = scaleDownBitmap(bitmap, 512)
            
            // Base64-encode the image
            val byteStream = java.io.ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 90, byteStream)
            val base64Data = android.util.Base64.encodeToString(byteStream.toByteArray(), android.util.Base64.NO_WRAP)
            
            val escapedPrompt = "Describe this person's appearance for AI portrait generation. Return only concise English keywords separated by commas. Focus on: gender, approximate age, face shape, hair style, hair color, eye shape, skin tone, glasses, expression. No explanations."

            val jsonPayload = """
                {
                  "model": "google/gemini-2.5-flash",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {
                          "type": "text",
                          "text": "$escapedPrompt"
                        },
                        {
                          "type": "image_url",
                          "image_url": {
                            "url": "data:image/jpeg;base64,$base64Data"
                          }
                        }
                      ]
                    }
                  ],
                  "max_tokens": 250
                }
            """.trimIndent()

            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/devmobile/AIGenerator")
                .addHeader("X-Title", "AI Portrait Generator")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                Log.d("OpenRouterService", "Face analysis response code: ${response.code}")

                if (!response.isSuccessful || bodyString == null) {
                    val errorMsg = try {
                        bodyString?.let {
                            val errObj = JSONObject(it)
                            if (errObj.has("error")) {
                                errObj.getJSONObject("error").optString("message", "Error code ${response.code}")
                            } else {
                                "Error code ${response.code}"
                            }
                        } ?: "Empty response body"
                    } catch (e: Exception) {
                        "Error code ${response.code}: ${response.message}"
                    }
                    return@withContext FaceAnalysisResult.Error(errorMsg)
                }

                val jsonObj = JSONObject(bodyString)
                if (jsonObj.has("error")) {
                    val errorObj = jsonObj.getJSONObject("error")
                    val errMsg = errorObj.optString("message", "Unknown OpenRouter error")
                    return@withContext FaceAnalysisResult.Error(errMsg)
                }

                val choices = jsonObj.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext FaceAnalysisResult.Error("Không có kết quả lựa chọn phân tích từ OpenRouter.")
                }

                val messageObj = choices.getJSONObject(0).optJSONObject("message")
                    ?: return@withContext FaceAnalysisResult.Error("Phản hồi trống từ OpenRouter.")

                val content = messageObj.optString("content", "")
                if (content.isBlank()) {
                    return@withContext FaceAnalysisResult.Error("Nội dung phân tích trống từ OpenRouter.")
                }

                return@withContext FaceAnalysisResult.Success(content.trim())
            }
        } catch (e: Exception) {
            Log.e("OpenRouterService", "Face analysis threw exception", e)
            return@withContext FaceAnalysisResult.Error("Lỗi kết nối OpenRouter: ${e.localizedMessage}")
        }
    }

    private fun scaleDownBitmap(src: Bitmap, maxDimension: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDimension && h <= maxDimension) return src
        val ratio = w.toFloat() / h.toFloat()
        val (newW, newH) = if (w > h) maxDimension to (maxDimension / ratio).toInt()
                           else (maxDimension * ratio).toInt() to maxDimension
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    private fun escapeJsonString(str: String): String {
        return "\"" + str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }
}

sealed class FaceAnalysisResult {
    data class Success(val description: String) : FaceAnalysisResult()
    data class Error(val message: String) : FaceAnalysisResult()
}
