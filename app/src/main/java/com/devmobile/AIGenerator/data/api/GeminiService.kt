package com.devmobile.AIGenerator.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.scale
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService {

    private val openRouterService = OpenRouterService()

    suspend fun generateImage(
        prompt: String,
        faceBitmap: Bitmap,
        modelId: String = "default",
        apiKey: String = ""
    ): GenerationResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Phân tích khuôn mặt bằng gemini-2.5-flash (Google AI hoặc OpenRouter)
            val faceDescription: String = if (modelId == "default" || modelId.isBlank()) {
                Log.d("GeminiService", "Step 1: Bypassing face analysis for default Gemini model.")
                ""
            } else {
                if (apiKey.isBlank()) {
                    return@withContext GenerationResult.Error("Vui lòng cấu hình OpenRouter API Key trong Cài đặt (AI Studio Hub) trước khi sử dụng model này.")
                }
                Log.d("GeminiService", "Step 1: Analyzing face with OpenRouter gemini-2.5-flash...")
                val faceResult = openRouterService.analyzeFace(faceBitmap, apiKey)
                when (faceResult) {
                    is FaceAnalysisResult.Success -> faceResult.description
                    is FaceAnalysisResult.Error -> {
                        return@withContext GenerationResult.Error("Lỗi phân tích khuôn mặt từ OpenRouter: ${faceResult.message}")
                    }
                }
            }
            Log.d("GeminiService", "Face description: $faceDescription")

            // Step 2: Tạo ảnh bằng Imagen 3 hoặc OpenRouter
            val combinedPrompt = if (faceDescription.isBlank()) {
                prompt
            } else {
                """
                    Ultra high quality portrait photo.
                    Person: $faceDescription.
                    Style: $prompt.
                    Cinematic lighting, sharp focus, 8k, photorealistic.
                """.trimIndent()
            }

            val bitmap = if (modelId == "default" || modelId.isBlank()) {
                Log.d("GeminiService", "Step 2: Generating image with Imagen 3...")
                generateWithImagen(combinedPrompt)
                    ?: return@withContext GenerationResult.Error("Không nhận được ảnh từ Google AI. Vui lòng thử lại.")
            } else {
                Log.d("GeminiService", "Step 2: Generating image with OpenRouter model: $modelId...")
                val openRouterRes = openRouterService.generateImage(modelId, combinedPrompt, apiKey)
                when (openRouterRes) {
                    is GenerationResult.Success -> openRouterRes.bitmap
                    is GenerationResult.Error -> return@withContext GenerationResult.Error(openRouterRes.message)
                    else -> return@withContext GenerationResult.Error("Phản hồi không xác định từ OpenRouter.")
                }
            }

            return@withContext GenerationResult.Success(bitmap, isSimulated = false)

        } catch (e: Exception) {
            Log.e("GeminiService", "Generation failed", e)
            return@withContext GenerationResult.Error(buildUserMessage(e.localizedMessage ?: "Lỗi kết nối."))
        }
    }
    

    private suspend fun generateWithImagen(prompt: String): Bitmap? {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-3.1-flash-image-preview",
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE, ResponseModality.TEXT)
                }
            )

        val response = model.generateContent(prompt)

        val imagePart = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.filterIsInstance<InlineDataPart>()
            ?.firstOrNull()

        return imagePart?.let {
            BitmapFactory.decodeByteArray(it.inlineData, 0, it.inlineData.size)
        }
    }

    private fun scaleDownBitmap(src: Bitmap, maxDimension: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDimension && h <= maxDimension) return src
        val ratio = w.toFloat() / h.toFloat()
        val (newW, newH) = if (w > h) maxDimension to (maxDimension / ratio).toInt()
                           else (maxDimension * ratio).toInt() to maxDimension
        return src.scale(newW, newH)
    }

    private fun buildUserMessage(msg: String): String = when {
        msg.contains("quota", ignoreCase = true) || msg.contains("429") ->
            "Đã vượt quota. Kiểm tra Firebase Console."
        msg.contains("billing", ignoreCase = true) ->
            "Vertex AI yêu cầu billing. Bật billing tại GCP Console."
        msg.contains("403") || msg.contains("permission", ignoreCase = true) ->
            "Lỗi quyền truy cập. Kiểm tra cấu hình Firebase."
        msg.contains("UNAUTHENTICATED") || msg.contains("401") ->
            "Lỗi xác thực Firebase. Kiểm tra google-services.json."
        else -> "Lỗi: $msg"
    }
}

sealed class GenerationResult {
    data class Success(val bitmap: Bitmap, val isSimulated: Boolean) : GenerationResult()
    data class LoadingModel(val estimatedTimeSeconds: Double) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}
