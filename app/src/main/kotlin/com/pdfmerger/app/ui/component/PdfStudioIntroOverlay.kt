package com.pdfmerger.app.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfmerger.app.ui.theme.BrewAmber
import com.pdfmerger.app.ui.theme.BrewTerracotta

private data class GuideStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val accent: Color,
    val features: List<Pair<ImageVector, String>> = emptyList()
)

private val guideSteps = listOf(
    GuideStep(
        icon = Icons.Outlined.AutoAwesome,
        title = "Welcome to\nPDF Studio",
        body = "Your all-in-one PDF workspace.\nProcess multiple files with stacked tools in a single tap.",
        accent = BrewAmber
    ),
    GuideStep(
        icon = Icons.Outlined.Add,
        title = "Add & Organize",
        body = "Import PDFs, reorder freely, and manage multiple sessions for different projects.",
        accent = Color(0xFF5C6BC0),
        features = listOf(
            Icons.Outlined.FileOpen to "Import from any app",
            Icons.Outlined.SwapVert to "Drag to reorder",
            Icons.Outlined.Folder to "Multiple sessions"
        )
    ),
    GuideStep(
        icon = Icons.Outlined.AutoAwesomeMosaic,
        title = "Stack Your Tools",
        body = "Toggle any combination of actions to build your processing pipeline.",
        accent = Color(0xFF26A69A),
        features = listOf(
            Icons.Outlined.FontDownload to "Watermark & Stamp",
            Icons.Outlined.FormatListNumbered to "Page Numbers",
            Icons.Outlined.Compress to "Compress",
            Icons.Outlined.Lock to "Encrypt / Lock",
            Icons.Outlined.PhotoLibrary to "Convert to Images",
            Icons.Outlined.Layers to "Merge"
        )
    ),
    GuideStep(
        icon = Icons.Outlined.Tune,
        title = "Fine-Tune Settings",
        body = "Each tool opens its own configuration panel. Customize opacity, fonts, passwords, formats — everything.",
        accent = Color(0xFFFF7043),
        features = listOf(
            Icons.Outlined.Opacity to "Watermark opacity & color",
            Icons.Outlined.TextFormat to "Custom page number format",
            Icons.Outlined.Key to "Set encryption password"
        )
    ),
    GuideStep(
        icon = Icons.Outlined.PlayCircleOutline,
        title = "Execute & Done",
        body = "Hit Execute, preview the result, name your file, and save. That's it — all in one go!",
        accent = BrewTerracotta,
        features = listOf(
            Icons.Outlined.Visibility to "Preview before saving",
            Icons.Outlined.DriveFileRenameOutline to "Name your output",
            Icons.Outlined.Share to "Share instantly"
        )
    )
)

@Composable
fun PdfStudioIntroOverlay(onDismiss: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = guideSteps[currentStep]
    val isLast = currentStep == guideSteps.lastIndex

    // Icon pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    // Solid fullscreen surface
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle gradient backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                step.accent.copy(alpha = 0.06f),
                                step.accent.copy(alpha = 0.14f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top bar: step counter + skip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentStep + 1} of ${guideSteps.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Main content area
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut(tween(200)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "step_content"
                ) { stepIndex ->
                    val s = guideSteps[stepIndex]

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon with glow ring
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(s.accent.copy(alpha = 0.1f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(s.accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = s.icon,
                                    contentDescription = null,
                                    tint = s.accent,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .scale(iconScale)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = s.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        // Feature list (if this step has features)
                        if (s.features.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(28.dp))

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 16.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    s.features.forEach { (icon, label) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(s.accent.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = s.accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom: dots + button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Pill-shaped indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        guideSteps.forEachIndexed { index, _ ->
                            val isActive = index == currentStep
                            Box(
                                modifier = Modifier
                                    .animateContentSize(tween(300))
                                    .size(
                                        width = if (isActive) 28.dp else 8.dp,
                                        height = 8.dp
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isActive) step.accent
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    )
                            )
                        }
                    }

                    // Action button
                    Button(
                        onClick = {
                            if (isLast) onDismiss()
                            else currentStep++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = step.accent
                        )
                    ) {
                        Text(
                            text = if (isLast) "Get Started" else "Next",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
