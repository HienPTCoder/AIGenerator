package com.example.data.repository

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
import com.example.data.api.GenerationResult
import com.example.data.api.HuggingFaceService
import com.example.data.local.AIPortrait
import com.example.data.local.AppDatabase
import com.example.data.local.UserMetrics
import com.example.data.model.PortraitTemplate
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
    private val huggingFaceService = HuggingFaceService()

    val allPortraits: Flow<List<AIPortrait>> = portraitDao.getAllPortraits()
    val userMetrics: Flow<UserMetrics?> = userMetricsDao.getUserMetrics()

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
        faceBitmap: Bitmap,
        useLocalSimulation: Boolean
    ): GenerationPipelineResult = withContext(Dispatchers.IO) {
        // 1. Double check metrics
        val currentMetrics = userMetricsDao.getUserMetricsDirect() ?: UserMetrics()
        if (!currentMetrics.isPremium && currentMetrics.coins <= 0) {
            return@withContext GenerationPipelineResult.Error("Out of coins. Click 'Earn Daily Coins' or buy Premium for unlimited high-quality generations.")
        }

        // 2. Base API key extraction or simulate
        val apiKey = currentMetrics.customApiKey.trim()

        val generatedBitmap: Bitmap
        val simulated: Boolean

        if (useLocalSimulation || apiKey.isEmpty()) {
            // Local high-fidelity AI simulation rendering
            generatedBitmap = synthesizeLocalSimulation(template, faceBitmap)
            simulated = true
        } else {
            // Remote Hugging Face Inference Call
            val apiRes = huggingFaceService.generateImage(
                modelName = template.modelName,
                apiKey = apiKey,
                prompt = template.prompt,
                negativePrompt = template.negativePrompt,
                styleStrength = template.styleStrength,
                guidanceScale = template.guidanceScale,
                faceBitmap = faceBitmap
            )

            when (apiRes) {
                is GenerationResult.Success -> {
                    generatedBitmap = apiRes.bitmap
                    simulated = false
                }
                is GenerationResult.LoadingModel -> {
                    return@withContext GenerationPipelineResult.ModelLoading(apiRes.estimatedTimeSeconds)
                }
                is GenerationResult.Error -> {
                    return@withContext GenerationPipelineResult.Error(apiRes.message)
                }
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
        val entity = AIPortrait(
            templateName = template.name,
            filePath = file.absolutePath,
            promptUsed = template.prompt,
            modelUsed = if (simulated) "On-Device Simulation Engine v2.0" else template.modelName,
            isWatermarked = shouldWatermark
        )
        portraitDao.insertPortrait(entity)

        return@withContext GenerationPipelineResult.Success(entity, finalizedBitmap)
    }

    /**
     * Combines background gradient colors with high-contrast filter processing
     * and rounded framing to render gorgeous stylized studio-tier portraits locally.
     */
    private fun synthesizeLocalSimulation(template: PortraitTemplate, faceBitmap: Bitmap): Bitmap {
        val width = 768
        val height = 1024
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Segment colors based on category
        val (gradColor1, gradColor2, gradColor3, filterId) = when (template.category) {
            "Anime" -> Quadruplet(Color.parseColor("#120C24"), Color.parseColor("#4B1E8A"), Color.parseColor("#8E2DE2"), 1)
            "Business" -> Quadruplet(Color.parseColor("#1F2937"), Color.parseColor("#111827"), Color.parseColor("#4B5563"), 2)
            "Luxury" -> Quadruplet(Color.parseColor("#0F0C20"), Color.parseColor("#1B1A17"), Color.parseColor("#D4AF37"), 3)
            "Wedding" -> Quadruplet(Color.parseColor("#FFEFFF"), Color.parseColor("#FFD2E8"), Color.parseColor("#FFF3F3"), 4)
            "Fitness" -> Quadruplet(Color.parseColor("#0D0B16"), Color.parseColor("#16102C"), Color.parseColor("#D53F8C"), 5)
            "Fantasy" -> Quadruplet(Color.parseColor("#090E21"), Color.parseColor("#1E3A8A"), Color.parseColor("#10B981"), 6)
            "Cyberpunk" -> Quadruplet(Color.parseColor("#0E071D"), Color.parseColor("#7928CA"), Color.parseColor("#FF007A"), 7)
            else -> Quadruplet(Color.parseColor("#0B0C10"), Color.parseColor("#1F2833"), Color.parseColor("#66FCF1"), 8)
        }

        // 1. Draw Radial-Linear Gradient Background
        val radialShader = RadialGradient(
            width / 2f, height / 2f, width * 0.8f,
            intArrayOf(gradColor2, gradColor1),
            floatArrayOf(0.1f, 1.0f), Shader.TileMode.CLAMP
        )
        paint.shader = radialShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Light rays/highlights for depth
        val highlightsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.TRANSPARENT, gradColor3, Color.TRANSPARENT),
                floatArrayOf(0.2f, 0.5f, 0.8f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), highlightsPaint)

        // 2. Prepare & Stylize Face Bitmap
        // Scale and crop face inside a beautiful stylized oval or center frame
        val faceMargin = 120
        val faceSize = width - (faceMargin * 2)
        val faceLeft = faceMargin.toFloat()
        val faceTop = 220f
        val faceRect = RectF(faceLeft, faceTop, faceLeft + faceSize, faceTop + (faceSize * 1.2f))

        val styledFace = getStyledFace(faceBitmap, filterId, faceSize, (faceSize * 1.2f).toInt())

        // Draw soft back-glow for the face
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                faceRect.centerX(), faceRect.centerY(), faceSize * 0.8f,
                gradColor3, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawCircle(faceRect.centerX(), faceRect.centerY(), faceSize * 0.7f, glowPaint)

        // Put the styled face on top
        canvas.drawBitmap(styledFace, null, faceRect, null)

        // 3. Draw Neon Glowing Border Frame
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gradColor3
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawRoundRect(faceRect, 48f, 48f, borderPaint)

        // Draw custom lighting flares inside canvas (glassmorphic particles)
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 40
        }
        canvas.drawCircle(200f, 150f, 8f, particlePaint)
        canvas.drawCircle(600f, 180f, 14f, particlePaint)
        canvas.drawCircle(140f, 750f, 10f, particlePaint)
        canvas.drawCircle(650f, 800f, 6f, particlePaint)

        // 4. Artistic Bottom Ribbon
        val ribbonRect = RectF(40f, height - 180f, width - 40f, height - 40f)
        val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9050510") // very dark purple semi-transparent
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(ribbonRect, 28f, 28f, ribbonPaint)

        // Thin glow on ribbon border
        ribbonPaint.apply {
            color = gradColor3
            style = Paint.Style.STROKE
            strokeWidth = 3f
            alpha = 150
        }
        canvas.drawRoundRect(ribbonRect, 28f, 28f, ribbonPaint)

        // Title and descriptor text on ribbon
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(template.name.uppercase(), width / 2f, height - 120f, textPaint)

        textPaint.apply {
            color = Color.parseColor("#99FFFFFF")
            textSize = 22f
            isFakeBoldText = false
        }
        canvas.drawText("AI Synthesis • Output: ${template.category} Premium", width / 2f, height - 74f, textPaint)

        return outBitmap
    }

    private fun getStyledFace(src: Bitmap, filterId: Int, destW: Int, destH: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, destW, destH, true)
        val styled = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(styled)
        
        // Base rounded mask to cut face into elegant card shape
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rectF = RectF(0f, 0f, destW.toFloat(), destH.toFloat())
        canvas.drawRoundRect(rectF, 42f, 42f, maskPaint)

        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        // Color styling filters
        val cm = ColorMatrix()
        when (filterId) {
            1 -> { // Anime: Saturated, slightly dreamy pink/cyan highlight
                cm.setSaturation(1.6f)
            }
            2 -> { // Business: Warm tone, refined corporate contrasts
                cm.setSaturation(0.9f)
            }
            3 -> { // Luxury: Sophisticated gold-sepia undertone with premium contrast
                val sepiaMat = ColorMatrix().apply {
                    set(floatArrayOf(
                        0.95f, 0.05f, 0f, 0f, 15f,
                        0.05f, 0.90f, 0f, 0f, 10f,
                        0f, 0.05f, 0.80f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                cm.postConcat(sepiaMat)
            }
            4 -> { // Wedding: Bright, airy soft pastel glow
                cm.setSaturation(1.1f)
                val brightnessMat = ColorMatrix().apply {
                    set(floatArrayOf(
                        1f, 0f, 0f, 0f, 20f,
                        0f, 1f, 0f, 0f, 15f,
                        0f, 0f, 1f, 0f, 15f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                cm.postConcat(brightnessMat)
            }
            5 -> { // Fitness: Metallic dark and contrasty with rich glow
                cm.setSaturation(1.2f)
            }
            6 -> { // Fantasy: Cool elven bluish tones
                val coolMat = ColorMatrix().apply {
                    set(floatArrayOf(
                        0.8f, 0f, 0.1f, 0f, 0f,
                        0f, 0.9f, 0.1f, 0f, 10f,
                        0f, 0f, 1.2f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                cm.postConcat(coolMat)
            }
            7 -> { // Cyberpunk: Extreme high contrast & pink-magenta pop
                cm.setSaturation(1.7f)
                val cyberMat = ColorMatrix().apply {
                    set(floatArrayOf(
                        1.2f, 0f, 0f, 0f, 30f,
                        0f, 0.8f, 0f, 0f, -20f,
                        0f, 0f, 1.4f, 0f, 50f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                cm.postConcat(cyberMat)
            }
            else -> {
                cm.setSaturation(1.0f)
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        
        // Draw the scaled bitmap applying color filters and rounded mask
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        return styled
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

    // Helper holder
    data class Quadruplet<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

sealed class GenerationPipelineResult {
    data class Success(val record: AIPortrait, val bitmap: Bitmap) : GenerationPipelineResult()
    data class ModelLoading(val estimatedSeconds: Double) : GenerationPipelineResult()
    data class Error(val message: String) : GenerationPipelineResult()
}
