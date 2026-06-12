package com.pdfmerger.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.theme.*

// ── Data ──

enum class Tool {
    Merge, Compress, Extract, ImagesToPdf, Encrypt, PageEditor, PdfToImages, Unlock, Watermark, PageNumbers, Redact, Settings
}

data class ToolItem(
    val tool: Tool,
    val icon: ImageVector,
    val name: String,
    val description: String,
    val accent: Color
)

data class ToolCategory(
    val title: String,
    val tools: List<ToolItem>
)

val toolCategories = listOf(
    ToolCategory("Organize", listOf(
        ToolItem(Tool.Extract, Icons.Outlined.ContentCut, "Extract Pages", "Pull specific pages out", ToolExtract),
        ToolItem(Tool.PageEditor, Icons.Outlined.ViewComfy, "Page Editor", "Reorder, rotate & delete", ToolPageEditor),
        ToolItem(Tool.PageNumbers, Icons.Outlined.FormatListNumbered, "Page Numbers", "Add numbers to pages", ToolPageNumbers),
    )),
    ToolCategory("Convert", listOf(
        ToolItem(Tool.ImagesToPdf, Icons.Outlined.Image, "Images → PDF", "Photos into a document", ToolImagesToPdf),
        ToolItem(Tool.PdfToImages, Icons.Outlined.PhotoLibrary, "PDF → Images", "Export pages as photos", ToolPdfToImages),
    )),
    ToolCategory("Protect & Optimize", listOf(
        ToolItem(Tool.Encrypt, Icons.Outlined.Lock, "Lock PDF", "Password protect a file", ToolEncrypt),
        ToolItem(Tool.Unlock, Icons.Outlined.LockOpen, "Unlock PDF", "Remove passwords", ToolUnlock),
        ToolItem(Tool.Watermark, Icons.Outlined.FontDownload, "Watermark", "Coming soon with brewPDF v3", ToolWatermark),
        ToolItem(Tool.Redact, Icons.Outlined.FormatStrikethrough, "Redact", "Coming soon with brewPDF v3", ToolRedact),
        ToolItem(Tool.Compress, Icons.Outlined.Compress, "Compress", "Coming soon with brewPDF v3", ToolCompress),
    )),
)

// ── Home Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onToolSelected: (Tool) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── App Header ──
            item {
                AppHeader(onSettingsClick = { onToolSelected(Tool.Settings) })
            }

            // ── Trust Strip ──
            item {
                TrustStrip()
            }

            // ── Merge Hero Card ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                MergeHeroCard(onClick = { onToolSelected(Tool.Merge) })
            }

            // ── Tool Categories ──
            toolCategories.forEachIndexed { catIndex, category ->
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                itemsIndexed(category.tools) { toolIndex, toolItem ->
                    val totalDelay = (catIndex * category.tools.size + toolIndex) * 50
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(totalDelay.toLong())
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) { it / 4 }
                    ) {
                        ToolRow(
                            toolItem = toolItem,
                            onClick = {
                                if (toolItem.tool == Tool.Watermark || toolItem.tool == Tool.Redact || toolItem.tool == Tool.Compress) {
                                    android.widget.Toast.makeText(context, "Coming Soon with brewPDF v3", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    onToolSelected(toolItem.tool)
                                }
                            }
                        )
                    }
                }
            }

            // ── Footer ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "brewPDF v2.0 · Brew Creative Studio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

// ── Sub-components ──

@Composable
private fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "brewPDF",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your private PDF toolkit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrustStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrustItem(icon = Icons.Outlined.Lock, text = "Private")
        TrustDot()
        TrustItem(icon = Icons.Outlined.WifiOff, text = "Offline")
        TrustDot()
        TrustItem(icon = Icons.Outlined.MoneyOff, text = "Free")
    }
}

@Composable
private fun TrustItem(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TrustDot() {
    Text(
        text = "  ·  ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun MergeHeroCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = BrewAmber.copy(alpha = 0.15f),
                spotColor = BrewTerracotta.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrewAmber, BrewTerracotta)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Merge & Stage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Combine multiple PDFs into one. Stage files, reorder, preview, and merge — all offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Stacked pages visual hint
                Box(contentAlignment = Alignment.Center) {
                    // Back page
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = 4.dp)
                            .size(48.dp, 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    // Front page
                    Box(
                        modifier = Modifier
                            .size(48.dp, 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        // Page lines
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (it == 3) 0.6f else 1f)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color.White.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    toolItem: ToolItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon pill
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(toolItem.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = toolItem.icon,
                    contentDescription = toolItem.name,
                    tint = toolItem.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toolItem.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = toolItem.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
