package com.devmobile.AIGenerator.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagenAspectRatio
import com.google.firebase.ai.type.ImagenGenerationConfig
import com.google.firebase.ai.type.ImagenInlineImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService {

    suspend fun generateImage(
        prompt: String,
        faceBitmap: Bitmap,
        apiKey: String
    ): GenerationResult = withContext(Dispatchers.IO) {
        try {
            Log.d("GeminiService", "Generating via Firebase Vertex AI + imagen-3.0-generate-002...")

            val imagenModel = Firebase.ai(backend = GenerativeBackend.vertexAI())
                .imagenModel(
                    modelName = "imagen-3.0-generate-002",
                    generationConfig = ImagenGenerationConfig(
                        numberOfImages = 1,
                        aspectRatio = ImagenAspectRatio.SQUARE_1x1
                    )
                )

            val response = imagenModel.generateImages(prompt)

            val inlineImage = response.images.firstOrNull() as? ImagenInlineImage
            val bitmap = inlineImage?.let {
                BitmapFactory.decodeByteArray(it.data, 0, it.data.size)
            }

            if (bitmap != null) {
                return@withContext GenerationResult.Success(bitmap, isSimulated = false)
            } else {
                return@withContext GenerationResult.Error("Không nhận được ảnh. Vui lòng thử lại.")
            }

        } catch (e: Exception) {
            Log.e("GeminiService", "Imagen 3 generation failed", e)
            return@withContext GenerationResult.Error(buildUserMessage(e.localizedMessage ?: "Lỗi kết nối."))
        }
    }

    private fun buildUserMessage(msg: String): String = when {
        msg.contains("quota", ignoreCase = true) || msg.contains("429") ->
            "Đã vượt quota. Kiểm tra Firebase Console."
        msg.contains("billing", ignoreCase = true) ->
            "Vertex AI yêu cầu billing. Bật billing tại GCP Console."
        msg.contains("403") || msg.contains("permission", ignoreCase = true) ->
            "Lỗi quyền truy cập. Bật Vertex AI API trong GCP project."
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
