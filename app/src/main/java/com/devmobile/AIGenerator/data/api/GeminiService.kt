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
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

class GeminiService {

    /**
     * Pipeline to generate an image using Firebase AI:
     * 1. Multi-modal face analysis using gemini-2.5-flash via Google AI backend.
     * 2. Direct high-fidelity image synthesis using Imagen 3 via Google AI backend.
     * 
     * All calls are fully secured via Firebase Console backend credentials and Firebase App Check,
     * removing the need for a client-side API Key entirely!
     */
    suspend fun generateImage(
        prompt: String,
        faceBitmap: Bitmap
    ): GenerationResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Multimodal analysis of user's face selfie using Firebase SDK with Google AI Backend
            Log.d("GeminiService", "Initiating face analysis via Firebase AI (Google AI Backend)...")
            
            val aiInstance = Firebase.ai(backend = GenerativeBackend.googleAI())
            val resizedBitmap = scaleDownBitmap(faceBitmap, 512)
            
            // Use gemini-2.5-flash as requested by the user
            val generativeModel = aiInstance.generativeModel(modelName = "gemini-2.5-flash")
            
            // Call generateContent by using the content DSL builder
            val response = generativeModel.generateContent(
                content {
                    image(resizedBitmap)
                    text("Describe the person in this photo in detail, focusing on: gender, approximate age, hair style, hair color, eye shape, face shape, glasses, and any other notable facial features or expression. Output only a list of descriptive comma-separated keywords/phrases in English, suitable to be used as prompts for generating an image. Do not include introductory text.")
                }
            )
            
            val faceDescription = response.text?.trim()
            if (faceDescription.isNullOrBlank()) {
                return@withContext GenerationResult.Error("Firebase AI: Không thể nhận diện các đặc điểm khuôn mặt trong ảnh chân dung của bạn.")
            }
            Log.d("GeminiService", "Face Analysis completed: $faceDescription")

            // Step 2: Combine descriptive features with original prompt style template
            val combinedPrompt = "Create a gorgeous high-quality stylized portrait. Subject details: $faceDescription. Artistic style guidelines: $prompt. Photorealistic studio lighting, ultra-detailed professional portrait."
            Log.d("GeminiService", "Combined Prompt for Imagen 3: $combinedPrompt")

            // Step 3: Run Image synthesis using Imagen 3 via Firebase SDK
            Log.d("GeminiService", "Synthesizing image via Firebase Imagen 3...")
            
            val vertexAIInstance = Firebase.ai(backend = GenerativeBackend.vertexAI())
            val imagenModel = vertexAIInstance.imagenModel(
                modelName = "imagen-3.0-generate-002",
                generationConfig = ImagenGenerationConfig(
                    numberOfImages = 1,
                    aspectRatio = ImagenAspectRatio.SQUARE_1x1
                )
            )
            
            val imageResponse = imagenModel.generateImages(combinedPrompt)
            val inlineImage = imageResponse.images.firstOrNull() as? ImagenInlineImage
            val generatedBitmap = inlineImage?.let {
                BitmapFactory.decodeByteArray(it.data, 0, it.data.size)
            }

            if (generatedBitmap != null) {
                return@withContext GenerationResult.Success(generatedBitmap, isSimulated = false)
            } else {
                return@withContext GenerationResult.Error("Firebase AI Error: Không thể trích xuất dữ liệu ảnh từ máy chủ Firebase Imagen.")
            }

        } catch (e: Exception) {
            Log.e("GeminiService", "Firebase AI general execution failed", e)
            val msg = e.localizedMessage ?: "Đã xảy ra lỗi kết nối."
            val userHelpfulMsg = when {
                msg.contains("API key not valid", ignoreCase = true) -> "Firebase API Key không hợp lệ. Vui lòng xác thực lại tệp google-services.json của bạn."
                msg.contains("quota", ignoreCase = true) || msg.contains("429") -> "Giới hạn cuộc gọi (429): Bạn đã vượt quá giới hạn cuộc gọi Firebase AI."
                else -> "Lỗi Firebase AI: $msg. Đảm bảo rằng bạn đã kích hoạt Firebase AI Logic trong Firebase Console và thiết lập tệp google-services.json hợp lệ."
            }
            return@withContext GenerationResult.Error(userHelpfulMsg)
        }
    }

    private fun scaleDownBitmap(src: Bitmap, maxDimension: Int): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= maxDimension && height <= maxDimension) return src
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return src.scale(newWidth, newHeight)
    }
}

sealed class GenerationResult {
    data class Success(val bitmap: Bitmap, val isSimulated: Boolean) : GenerationResult()
    data class LoadingModel(val estimatedTimeSeconds: Double) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}
