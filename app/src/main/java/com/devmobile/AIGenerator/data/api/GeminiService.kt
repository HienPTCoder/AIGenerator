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

    suspend fun generateImage(
        prompt: String,
        faceBitmap: Bitmap
    ): GenerationResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Phân tích khuôn mặt bằng gemini-2.5-flash (Google AI, free)
            Log.d("GeminiService", "Step 1: Analyzing face with gemini-2.5-flash...")
            val faceDescription = analyzeFace(faceBitmap)
                ?: return@withContext GenerationResult.Error("Không thể nhận diện khuôn mặt. Hãy dùng ảnh chân dung rõ ràng hơn.")

            Log.d("GeminiService", "Face description: $faceDescription")

            // Step 2: Tạo ảnh bằng Imagen 3 với prompt kết hợp (Vertex AI)
            val combinedPrompt = """
                Ultra high quality portrait photo.
                Person: $faceDescription.
                Style: $prompt.
                Cinematic lighting, sharp focus, 8k, photorealistic.
            """.trimIndent()

            Log.d("GeminiService", "Step 2: Generating image with Imagen 3...")
            val bitmap = generateWithImagen(combinedPrompt)
                ?: return@withContext GenerationResult.Error("Không nhận được ảnh. Vui lòng thử lại.")

            return@withContext GenerationResult.Success(bitmap, isSimulated = false)

        } catch (e: Exception) {
            Log.e("GeminiService", "Generation failed", e)
            return@withContext GenerationResult.Error(buildUserMessage(e.localizedMessage ?: "Lỗi kết nối."))
        }
    }

    private suspend fun analyzeFace(bitmap: Bitmap): String? {
        val resized = scaleDownBitmap(bitmap, 512)
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-2.5-flash")

        val response = model.generateContent(
            content {
                image(resized)
                text(
                    "Describe this person's appearance for AI portrait generation. " +
                    "Return only concise English keywords separated by commas. " +
                    "Focus on: gender, approximate age, face shape, hair style, hair color, " +
                    "eye shape, skin tone, glasses, expression. No explanations."
                )
            }
        )
        return response.text?.trim()?.takeIf { it.isNotBlank() }
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
