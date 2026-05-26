package com.devmobile.AIGenerator.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.devmobile.AIGenerator.data.local.AIPortrait
import com.devmobile.AIGenerator.data.local.UserMetrics
import com.devmobile.AIGenerator.data.model.PortraitTemplate
import com.devmobile.AIGenerator.data.model.TemplateProvider
import com.devmobile.AIGenerator.ui.viewmodel.GenerationUiState
import com.devmobile.AIGenerator.ui.viewmodel.PortraitViewModel
import java.io.File

/**
 * Main application UI controller implementing the standard Single-view/Multi-screen
 * layout using state-driven Compose routing for maximum stability.
 */
@Composable
fun AppNavigationUI(viewModel: PortraitViewModel) {
    val selectedTemplate by viewModel.selectedTemplate.collectAsState()
    val showSettings = remember { mutableStateOf(false) }
    val showHistory = remember { mutableStateOf(false) }

    val metrics by viewModel.userMetrics.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Core view transitions
        AnimatedContent(
            targetState = selectedTemplate,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            label = "screen_transition"
        ) { template ->
            if (template == null) {
                HomeScreen(
                    viewModel = viewModel,
                    metrics = metrics,
                    onOpenSettings = { showSettings.value = true },
                    onOpenHistory = { showHistory.value = true }
                )
            } else {
                TemplateDetailScreen(
                    viewModel = viewModel,
                    template = template,
                    metrics = metrics,
                    onBack = { viewModel.selectTemplate(null) }
                )
            }
        }

        // Overlay dialogs
        if (showSettings.value) {
            SettingsDialog(
                metrics = metrics ?: UserMetrics(),
                onDismiss = { showSettings.value = false },
                onSaveApiKey = { viewModel.updateApiKey(it) },
                onTogglePremium = { viewModel.togglePremium(it) }
            )
        }

        if (showHistory.value) {
            HistorySheet(
                viewModel = viewModel,
                onDismiss = { showHistory.value = false }
            )
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: PortraitViewModel,
    metrics: UserMetrics?,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val context = LocalContext.current

    // Background accent glow drawing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2A9D4EDD), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.1f),
                        radius = size.width * 0.8f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2200F0FF), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.8f),
                        radius = size.width * 0.9f
                    )
                )
            }
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header panel with premium button and metrics
            Spacer(modifier = Modifier.height(12.dp))
            HeaderBlock(
                metrics = metrics,
                onEarnCoins = {
                    viewModel.earnCoins()
                    Toast.makeText(context, "Coins earned! +2 Generations available.", Toast.LENGTH_SHORT).show()
                },
                onOpenSettings = onOpenSettings,
                onOpenHistory = onOpenHistory
            )

            // Dynamic Promotion card
            Spacer(modifier = Modifier.height(16.dp))
            PromotionBanner(metrics = metrics, onUnlockPremium = { viewModel.togglePremium(true) })

            // Categories horizontal selection list
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Styling Templates",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            val finalCategories = listOf("All") + TemplateProvider.categories
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(finalCategories) { cat ->
                    val isActive = selectedCategory == cat
                    CategoryChip(
                        name = cat,
                        isActive = isActive,
                        onClick = { viewModel.selectCategory(cat) }
                    )
                }
            }

            // Central Templates Grid
            Spacer(modifier = Modifier.height(16.dp))
            val filteredTemplates = remember(selectedCategory) {
                if (selectedCategory == "All") TemplateProvider.templates
                else TemplateProvider.templates.filter { it.category == selectedCategory }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (filteredTemplates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No templates in this category yet.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(filteredTemplates) { template ->
                        TemplateCard(
                            template = template,
                            isPremiumUser = metrics?.isPremium ?: false,
                            onClick = { viewModel.selectTemplate(template) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderBlock(
    metrics: UserMetrics?,
    onEarnCoins: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "AI PORTRAIT",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("app_logo_title")
            )
            Text(
                text = "Avatar & Portrait Generator",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coins counter pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x30AC5DFF))
                    .border(1.dp, Color(0x60AC5DFF), RoundedCornerShape(20.dp))
                    .clickable { onEarnCoins() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = "Credits icon",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (metrics?.isPremium == true) "∞ GLORY" else "${metrics?.coins ?: 3} Coin",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // View History Button
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1B1638))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "Open Generations History",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Settings Button (Token configuration)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1B1638))
                    .size(36.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Token Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PromotionBanner(metrics: UserMetrics?, onUnlockPremium: () -> Unit) {
    if (metrics?.isPremium == true) {
        // User is Premium banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1B1638), Color(0xFFAC5DFF))
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Premium star",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Elite Creator Unlocked",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        "Unlimited generations, premium styles, and clean HD original images.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    } else {
        // Premium promotion banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF8000FF), Color(0xFF00E5FF))
                    )
                )
                .clickable { onUnlockPremium() }
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Premium Crown",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Get VIP Unlimited Generation",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            "Remove watermarks, priority speed & HD quality.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = "Arrow right",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryChip(name: String, isActive: Boolean, onClick: () -> Unit) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF130E26)
    val borderColor = if (isActive) Color.White.copy(alpha = 0.2f) else Color(0x30AC5DFF)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            color = if (isActive) Color.White else Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TemplateCard(
    template: PortraitTemplate,
    isPremiumUser: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("template_${template.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left stylized category thumb placeholder using deep gradients
            val thumbGrad = remember(template.category) {
                val colors = when (template.category) {
                    "Anime" -> Pair(Color(0xFFAC5DFF), Color(0xFFFF007A))
                    "Business" -> Pair(Color(0xFF2C3E50), Color(0xFF3498DB))
                    "Luxury" -> Pair(Color(0xFFFFD700), Color(0xFF1E1E1E))
                    "Wedding" -> Pair(Color(0xFFFFB6C1), Color(0xFFFFD2E8))
                    "Fitness" -> Pair(Color(0xFFFF5F6D), Color(0xFFFFC371))
                    "Fantasy" -> Pair(Color(0xFF10B981), Color(0xFF3B82F6))
                    "Cyberpunk" -> Pair(Color(0xFFFF007A), Color(0xFF00F0FF))
                    else -> Pair(Color(0xFF7928CA), Color(0xFFAC5DFF))
                }
                Brush.linearGradient(listOf(colors.first, colors.second))
            }

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(thumbGrad),
                contentAlignment = Alignment.Center
            ) {
                val modelIcon = when (template.category) {
                    "Anime" -> Icons.Default.Palette
                    "Business" -> Icons.Default.BusinessCenter
                    "Luxury" -> Icons.Default.WorkspacePremium
                    "Wedding" -> Icons.Default.Favorite
                    "Fitness" -> Icons.Default.FitnessCenter
                    "Fantasy" -> Icons.Default.AutoAwesome
                    "Cyberpunk" -> Icons.Default.Bolt
                    else -> Icons.Default.Image
                }
                Icon(
                    imageVector = modelIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Category Badge
                    Text(
                        text = template.category.uppercase(),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Premium tag inside active templates
                    if (template.isPremiumOnly) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x30FFD700))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "VIP STYLE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = template.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = template.description,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TemplateDetailScreen(
    viewModel: PortraitViewModel,
    template: PortraitTemplate,
    metrics: UserMetrics?,
    onBack: () -> Unit
) {
    val faceBitmap by viewModel.selectedFaceBitmap.collectAsState()
    val uiState by viewModel.generationUiState.collectAsState()
    val context = LocalContext.current

    // Launcher activities
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri).use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    viewModel.setFaceBitmap(bmp)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading image from gallery.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            viewModel.setFaceBitmap(bmp)
        }
    }

    // Dynamic color banner to blend background uniquely per selected category
    val primaryColor = remember(template.category) {
        when (template.category) {
            "Anime" -> Color(0xFFAC5DFF)
            "Business" -> Color(0xFF3498DB)
            "Luxury" -> Color(0xFFFFD700)
            "Wedding" -> Color(0xFFFFD2E8)
            "Fitness" -> Color(0xFFFF5F6D)
            "Fantasy" -> Color(0xFF10B981)
            "Cyberpunk" -> Color(0xFF00F0FF)
            else -> Color(0xFF9D4EDD)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Back toolbar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.setFaceBitmap(null)
                        viewModel.resetGenerationState()
                        onBack()
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF120E2C))
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Studio Canvas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Studio Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .background(Color(0xFF120E2C)),
                contentAlignment = Alignment.Center
            ) {
                if (faceBitmap != null) {
                    Image(
                        bitmap = faceBitmap!!.asImageBitmap(),
                        contentDescription = "Uploaded Selfie",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Cyberpunk active scan overlays when analyzing
                    if (uiState is GenerationUiState.Validating || uiState is GenerationUiState.Generating) {
                        ScanLinesOverlay()
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Upload Face Image First",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Selfies should be clear, front-facing, with single faces and no filters or muffs for outstanding result quality.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Choose Image block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1638)),
                    modifier = Modifier.weight(1f).testTag("gallery_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Gallery", fontSize = 12.sp)
                }

                Button(
                    onClick = { cameraLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1638)),
                    modifier = Modifier.weight(1f).testTag("camera_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take Quick Selfie", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Specs Card
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF120E2C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Synthesis specifications:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Prompt: ${template.prompt}",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Style Strength: ${template.styleStrength} • Guidance Scale: ${template.guidanceScale}",
                        fontSize = 10.sp,
                        color = primaryColor
                    )
                }
            }

            // Bottom CTA generator
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { viewModel.startPortraitGeneration() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_portrait_button")
                ,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                enabled = uiState !is GenerationUiState.Generating && uiState !is GenerationUiState.Validating
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GENERATE AI PORTRAIT", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }

            // Dynamic Pipeline results overlay display
            Spacer(modifier = Modifier.height(16.dp))
            when (val state = uiState) {
                is GenerationUiState.Validating -> {
                    StatusRow(title = "Validating face photo graph...", isSpinning = true)
                }
                is GenerationUiState.Generating -> {
                    StatusRow(title = "Synthesizing AI pixels...", isSpinning = true)
                }
                is GenerationUiState.ModelWarmingUp -> {
                    StatusRow(title = "Gemini is warming up: ${state.estimatedSeconds}s remaining...", isSpinning = true)
                }
                is GenerationUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x35FF3B30)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = "Error", tint = Color.Red)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(state.message, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                is GenerationUiState.Success -> {
                    Dialog(onDismissRequest = { viewModel.resetGenerationState() }) {
                        SuccessDialogContent(
                            result = state.result,
                            bitmap = state.bitmap,
                            onClose = { viewModel.resetGenerationState() }
                        )
                    }
                }
                else -> {}
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun StatusRow(title: String, isSpinning: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141029)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSpinning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(title, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun SuccessDialogContent(result: AIPortrait, bitmap: Bitmap, onClose: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF120E2C)),
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AI Masterpiece Ready!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calculated output with watermark if free
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Success portrait",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Style: ${result.templateName}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )

            Text(
                "Model: ${result.modelUsed}",
                color = Color.Gray,
                fontSize = 10.sp
            )

            if (result.isWatermarked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Unlock Elite VIP status to capture unwatermarked clean HD originals.",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss Studio", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsDialog(
    metrics: UserMetrics,
    onDismiss: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onTogglePremium: (Boolean) -> Unit
) {
    var keyText by remember { mutableStateOf(metrics.customApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var premiumToggle by remember { mutableStateOf(metrics.isPremium) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141029)),
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AI Studio Hub",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.testTag("settings_title")
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Gemini API Key Configuration
                Text(
                    "Gemini API Key",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    placeholder = {
                        Text(
                            "AIzaSy...",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (keyText.isNotEmpty()) {
                                IconButton(onClick = { keyText = "" }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Xóa",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { keyVisible = !keyVisible }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (keyVisible) "Ẩn key" else "Hiện key",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF3A3060),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = Color(0xFF090518),
                        unfocusedContainerColor = Color(0xFF090518)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("api_key_field")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Lấy miễn phí tại aistudio.google.com → \"Get API key\"",
                    fontSize = 10.sp,
                    color = Color(0xFF8A80C0),
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Premium Configuration simulator
                Text(
                    "Mô Phỏng Trạng Thái VIP Premium:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mở khóa Premium VIP", color = Color.White, fontSize = 12.sp)
                        Text("Không giới hạn lượt tạo, không watermark", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = premiumToggle,
                        onCheckedChange = {
                            premiumToggle = it
                            onTogglePremium(it)
                        },
                        modifier = Modifier.testTag("premium_switch")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onSaveApiKey(keyText)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_settings_button")
                ) {
                    Text("Lưu Cấu Hình (Save)")
                }
            }
        }
    }
}

@Composable
fun HistorySheet(
    viewModel: PortraitViewModel,
    onDismiss: () -> Unit
) {
    val portraits by viewModel.allPortraits.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141029)),
            modifier = Modifier.fillMaxSize(0.9f).padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "History Workspace",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (portraits.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Cached, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No generations yet. Create ones inside canvas!", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(portraits) { item ->
                            HistoryRow(portrait = item, onDelete = {
                                viewModel.deleteHistoryItem(item.id, item.filePath)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRow(portrait: AIPortrait, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1A3E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded rendering from cached local file path
            val file = remember(portrait.filePath) { File(portrait.filePath) }
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(54.dp).background(Color.Gray).clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.BrokenImage, contentDescription = null, tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    portrait.templateName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Text(
                    portrait.modelUsed.take(24),
                    color = Color.LightGray.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
                Text(
                    if (portrait.isWatermarked) "Free Tier" else "Premium Tier",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 9.sp
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete from history", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun ScanLinesOverlay() {
    val infiniteTransition = rememberInfiniteTransition()
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sweepHeight = maxHeight
        val scanOffset = sweepHeight * scanY

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .offset(y = scanOffset)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFF00E5FF), Color.Transparent)
                    )
                )
        )
        // Shimmer glass overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(scanY)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x1F00E5FF))
                    )
                )
        )
    }
}
