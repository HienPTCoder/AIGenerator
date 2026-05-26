package com.devmobile.AIGenerator.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import com.devmobile.AIGenerator.data.api.GenerationResult
import com.devmobile.AIGenerator.data.api.GeminiService
import com.devmobile.AIGenerator.data.api.OpenRouterService
import com.devmobile.AIGenerator.data.local.AIPortrait
import com.devmobile.AIGenerator.data.local.AppDatabase
import com.devmobile.AIGenerator.data.local.UserMetrics
import com.devmobile.AIGenerator.data.model.PortraitTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PortraitRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val portraitDao = database.portraitDao()
    private val userMetricsDao = database.userMetricsDao()
    private val geminiService = GeminiService()
    private val openRouterService = OpenRouterService()

    val allPortraits: Flow<List<AIPortrait>> = portraitDao.getAllPortraits()
    val userMetrics: Flow<UserMetrics?> = userMetricsDao.getUserMetrics()

    fun getSelectedModel(): String {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("selected_model", "default") ?: "default"
    }

    fun setSelectedModel(model: String) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_model", model)
            .apply()
    }

    // Initialize metrics if first time launching
    suspend fun checkAndInitializeMetrics() = withContext(Dispatchers.IO) {
        val current = userMetricsDao.getUserMetricsDirect()
        if (current == null) {
            userMetricsDao.insertUserMetrics(UserMetrics(id = 1, coins = 3, isPremium = false, customApiKey = ""))
        } else {
            // Simulated daily coin reset logic: Reset to 3 coins daily if last reset was > 24 hours ago and user is free
            val oneDayInMillis = 24 * 60 * 60 * 1000L
            if (!current.isPremium && System.currentTimeMillis() - current.lastResetTimestamp > oneDayInMillis) {
                userMetricsDao.updateUserMetrics(
                    current.copy(
                        coins = 3,
                        lastResetTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun updateApiKey(key: String) = withContext(Dispatchers.IO) {
        val current = userMetricsDao.getUserMetricsDirect() ?: UserMetrics()
        userMetricsDao.insertUserMetrics(current.copy(customApiKey = key))
    }

    suspend fun togglePremium(isPremium: Boolean) = withContext(Dispatchers.IO) {
        val current = userMetricsDao.getUserMetricsDirect() ?: UserMetrics()
        userMetricsDao.insertUserMetrics(current.copy(isPremium = isPremium))
    }

    suspend fun incrementCoins(amount: Int) = withContext(Dispatchers.IO) {
        val current = userMetricsDao.getUserMetricsDirect() ?: UserMetrics()
        userMetricsDao.insertUserMetrics(current.copy(coins = current.coins + amount))
    }

    suspend fun useCoin(): Boolean = withContext(Dispatchers.IO) {
        val current = userMetricsDao.getUserMetricsDirect() ?: UserMetrics()
        if (current.isPremium) return@withContext true
        if (current.coins > 0) {
            userMetricsDao.insertUserMetrics(current.copy(coins = current.coins - 1))
            return@withContext true
        }
        return@withContext false
    }

    suspend fun deletePortrait(id: Int, filePath: String) = withContext(Dispatchers.IO) {
        portraitDao.deletePortraitById(id)
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("Repository", "Failed to delete file", e)
        }
    }

    /**
     * Centralized pipeline to synthesize the final portrait.
     * Respects user tier, applies a visual watermark if free tier,
     * stores the image to application local storage, and logs in Room DB.
     */
    suspend fun runGenerationPipeline(
        template: PortraitTemplate,
        faceBitmap: Bitmap
    ): GenerationPipelineResult = withContext(Dispatchers.IO) {
        // 1. Double check metrics
        val currentMetrics = userMetricsDao.getUserMetricsDirect() ?: UserMetrics()
        if (!currentMetrics.isPremium && currentMetrics.coins <= 0) {
            return@withContext GenerationPipelineResult.Error("Out of coins. Click 'Earn Daily Coins' or buy Premium for unlimited high-quality generations.")
        }

        val selectedModel = getSelectedModel()
        val apiKey = currentMetrics.customApiKey

        val apiRes = geminiService.generateImage(
            prompt = template.prompt,
            faceBitmap = faceBitmap,
            modelId = selectedModel,
            apiKey = apiKey
        )

        val generatedBitmap = when (apiRes) {
            is GenerationResult.Success -> {
                apiRes.bitmap
            }
            is GenerationResult.LoadingModel -> {
                return@withContext GenerationPipelineResult.ModelLoading(apiRes.estimatedTimeSeconds)
            }
            is GenerationResult.Error -> {
                return@withContext GenerationPipelineResult.Error(apiRes.message)
            }
        }

        // 3. Post-processing: Watermark application (for free tier)
        val shouldWatermark = !currentMetrics.isPremium
        val finalizedBitmap = if (shouldWatermark) {
            applyWatermark(generatedBitmap)
        } else {
            generatedBitmap
        }

        // 4. Save file locally
        val fileName = "portrait_${UUID.randomUUID()}.jpg"
        val storageDir = File(context.filesDir, "generations")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val file = File(storageDir, fileName)
        
        try {
            FileOutputStream(file).use { out ->
                finalizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } catch (e: Exception) {
            Log.e("Repository", "Failed saving final file", e)
            return@withContext GenerationPipelineResult.Error("Failed to cache generated image on disk.")
        }

        // 5. Consume credit
        useCoin()

        // 6. DB Entry
        val modelUsedName = if (selectedModel == "default") template.modelName else selectedModel
        val entity = AIPortrait(
            templateName = template.name,
            filePath = file.absolutePath,
            promptUsed = template.prompt,
            modelUsed = modelUsedName,
            isWatermarked = shouldWatermark
        )
        portraitDao.insertPortrait(entity)

        return@withContext GenerationPipelineResult.Success(entity, finalizedBitmap)
    }

    private fun applyWatermark(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val out = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        // Draw dark translucent banner at bottom for clear readability
        val bannerH = 46f
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC000000")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, height - bannerH, width.toFloat(), height.toFloat(), bannerPaint)

        // Draw crisp overlay text watermark
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9900FFFF") // holographic cyan label
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("⚡ AI PORTRAIT GENERATOR (FREE TIER) ⚡", width / 2f, height - 16f, textPaint)

        return out
    }

}

sealed class GenerationPipelineResult {
    data class Success(val record: AIPortrait, val bitmap: Bitmap) : GenerationPipelineResult()
    data class ModelLoading(val estimatedSeconds: Double) : GenerationPipelineResult()
    data class Error(val message: String) : GenerationPipelineResult()
}
