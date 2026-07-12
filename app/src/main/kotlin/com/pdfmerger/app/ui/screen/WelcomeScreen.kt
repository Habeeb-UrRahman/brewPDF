package com.pdfmerger.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfmerger.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

// ── Onboarding page data ──

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val description: String,
    val accentStart: Color,
    val accentEnd: Color,
    val illustration: @Composable (animProgress: Float) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()

    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Welcome to\nbrewPDF",
                subtitle = "Your Private PDF Toolkit",
                description = "100% offline · Open Source · No data collection\nEverything runs entirely on your device.",
                accentStart = BrewAmber,
                accentEnd = BrewTerracotta,
                illustration = { progress -> WelcomeIllustration(progress) }
            ),
            OnboardingPage(
                title = "Merge & Stage",
                subtitle = "Your PDF Assembly Line",
                description = "Stage multiple PDFs, reorder them with drag & drop, preview pages, then merge — all without internet.",
                accentStart = BrewAmber,
                accentEnd = Color(0xFFE27B58),
                illustration = { progress -> MergeIllustration(progress) }
            ),
            OnboardingPage(
                title = "100 MB Limit",
                subtitle = "Persistent Staging Area",
                description = "Your staged PDFs persist between sessions — up to 100 MB. Close the app, come back, and they're still there, ready to merge.",
                accentStart = BrewTerracotta,
                accentEnd = BrewSage,
                illustration = { progress -> StorageIllustration(progress) }
            ),
            OnboardingPage(
                title = "15+ Tools",
                subtitle = "Everything You Need",
                description = "Extract pages · Split · OCR · Encrypt · Scan\nConvert images · Add page numbers · Night mode reader — and much more.",
                accentStart = BrewSage,
                accentEnd = Color(0xFF4A8BA8),
                illustration = { progress -> ToolsIllustration(progress) }
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1

    // Pulsing entrance animation
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
    val globalPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Animated background orbs ──
        val currentPage = pages[pagerState.currentPage]
        val animatedAccentStart by animateColorAsState(
            targetValue = currentPage.accentStart,
            animationSpec = tween(600),
            label = "bg_start"
        )
        val animatedAccentEnd by animateColorAsState(
            targetValue = currentPage.accentEnd,
            animationSpec = tween(600),
            label = "bg_end"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-right orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccentStart.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.12f),
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = Offset(w * 0.85f, h * 0.12f)
            )

            // Bottom-left orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccentEnd.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.85f),
                    radius = w * 0.6f
                ),
                radius = w * 0.6f,
                center = Offset(w * 0.15f, h * 0.85f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Skip button ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(onClick = onFinished) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Pager ──
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Illustration area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        page.illustration(globalPulse)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Title
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            lineHeight = 38.sp
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle with accent
                    Text(
                        text = page.subtitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                        color = page.accentStart
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description
                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        lineHeight = 22.sp
                    )
                }
            }

            // ── Page indicators + Next/Get Started button ──
            Column(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Dot indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, page ->
                        val isActive = pagerState.currentPage == index
                        val dotWidth by animateDpAsState(
                            targetValue = if (isActive) 28.dp else 8.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "dot_$index"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isActive) page.accentStart else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            animationSpec = tween(300),
                            label = "dot_color_$index"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                // Action button
                val buttonGradient = Brush.linearGradient(
                    colors = listOf(animatedAccentStart, animatedAccentEnd)
                )
                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinished()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(buttonGradient, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isLastPage) "Get Started" else "Next",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Illustration Composables ──

@Composable
private fun WelcomeIllustration(animProgress: Float) {
    val breathe = sin(animProgress * 2 * Math.PI).toFloat()

    Box(contentAlignment = Alignment.Center) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size((160 + breathe * 8).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrewAmber.copy(alpha = 0.15f + breathe * 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Inner circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrewAmber, BrewTerracotta)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Shield / lock icon
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        // Floating trust badges
        val badges = listOf(
            Triple(Icons.Outlined.WifiOff, "Offline", Offset(-100f, -60f)),
            Triple(Icons.Outlined.Lock, "Private", Offset(100f, -40f)),
            Triple(Icons.Outlined.MoneyOff, "Free", Offset(80f, 80f))
        )

        badges.forEachIndexed { i, (icon, label, baseOffset) ->
            val yBob = sin((animProgress + i * 0.33f) * 2 * Math.PI).toFloat() * 6f
            Box(
                modifier = Modifier
                    .offset(
                        x = baseOffset.x.dp,
                        y = (baseOffset.y + yBob).dp
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = BrewAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeIllustration(animProgress: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Animated PDF pages merging together
        val pageColors = listOf(
            BrewAmber.copy(alpha = 0.9f),
            BrewTerracotta.copy(alpha = 0.9f),
            Color(0xFFE27B58).copy(alpha = 0.9f)
        )

        pageColors.forEachIndexed { i, color ->
            val phase = (animProgress + i * 0.33f) % 1f
            // Pages converge from spread to stacked
            val spreadX = (i - 1) * 55f
            val convergeAmount = (sin(phase * 2 * Math.PI).toFloat() + 1f) / 2f
            val currentX = spreadX * (1f - convergeAmount * 0.6f)
            val currentRotation = (i - 1) * 8f * (1f - convergeAmount * 0.7f)

            Box(
                modifier = Modifier
                    .offset(x = currentX.dp, y = (i * 4f).dp)
                    .rotate(currentRotation)
                    .size(90.dp, 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(5) { lineIdx ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (lineIdx == 4) 0.5f else 0.85f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }

        // Merge icon at center
        Box(
            modifier = Modifier
                .offset(y = 70.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrewAmber, BrewTerracotta)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Layers,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun StorageIllustration(animProgress: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(200.dp, 160.dp)
        ) {
            val w = size.width
            val h = size.height

            // Storage container (rounded rect)
            drawRoundRect(
                color = BrewTerracotta.copy(alpha = 0.12f),
                topLeft = Offset(0f, h * 0.15f),
                size = Size(w, h * 0.7f),
                cornerRadius = CornerRadius(32f, 32f)
            )

            // Storage bar outline
            drawRoundRect(
                color = BrewTerracotta.copy(alpha = 0.3f),
                topLeft = Offset(w * 0.12f, h * 0.55f),
                size = Size(w * 0.76f, h * 0.12f),
                cornerRadius = CornerRadius(16f, 16f)
            )

            // Animated fill (shows 100MB limit at ~70% fill)
            val fillWidth = w * 0.76f * (0.3f + sin(animProgress * 2 * Math.PI).toFloat() * 0.15f + 0.35f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(BrewAmber, BrewTerracotta)
                ),
                topLeft = Offset(w * 0.12f, h * 0.55f),
                size = Size(fillWidth, h * 0.12f),
                cornerRadius = CornerRadius(16f, 16f)
            )
        }

        // "100 MB" badge
        val badgeBob = sin(animProgress * 2 * Math.PI).toFloat() * 4f
        Box(
            modifier = Modifier
                .offset(y = (-50 + badgeBob).dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrewTerracotta, BrewSage)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                "100 MB",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        // Floating labels
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = 55.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "Persists between sessions",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolsIllustration(animProgress: Float) {
    val toolItems = listOf(
        Pair(Icons.Outlined.ContentCut, ToolExtract),
        Pair(Icons.Outlined.Lock, ToolEncrypt),
        Pair(Icons.Outlined.Image, ToolImagesToPdf),
        Pair(Icons.Outlined.FindInPage, ToolOcr),
        Pair(Icons.Outlined.DocumentScanner, ToolScanDocument),
        Pair(Icons.Outlined.Edit, BrewAmber),
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Orbital tool icons
        toolItems.forEachIndexed { i, (icon, color) ->
            val angle = (animProgress * 360f + i * (360f / toolItems.size)) % 360f
            val radians = Math.toRadians(angle.toDouble())
            val radius = 90f
            val x = (kotlin.math.cos(radians) * radius).toFloat()
            val y = (sin(radians) * radius * 0.6f).toFloat()

            // Simulate depth with scale
            val depthScale = 0.7f + (sin(radians).toFloat() + 1f) * 0.15f
            val depthAlpha = 0.5f + (sin(radians).toFloat() + 1f) * 0.25f

            Box(
                modifier = Modifier
                    .offset(x = x.dp, y = y.dp)
                    .scale(depthScale)
                    .alpha(depthAlpha)
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Center badge
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrewSage, Color(0xFF4A8BA8))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "15+",
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 20.sp
            )
        }
    }
}

