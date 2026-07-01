package com.pdfmerger.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ToolBottomBar(
    leftIcon: ImageVector? = null,
    onLeftClick: (() -> Unit)? = null,
    leftContentDesc: String? = null,
    showClearButton: Boolean = false,
    onClearClick: (() -> Unit)? = null,
    actionText: String,
    actionIcon: ImageVector? = null,
    isActionEnabled: Boolean,
    isProcessing: Boolean,
    actionColor: Color,
    onActionClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val buttonBackgroundColor by animateColorAsState(
        targetValue = if (isActionEnabled || isProcessing) actionColor else MaterialTheme.colorScheme.surfaceVariant,
        label = "btnColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                spotColor = actionColor.copy(alpha = 0.2f)
            )
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (leftIcon != null && onLeftClick != null) {
                    FilledIconButton(
                        onClick = onLeftClick,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = leftIcon,
                            contentDescription = leftContentDesc,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                if (showClearButton && onClearClick != null) {
                    FilledIconButton(
                        onClick = onClearClick,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onActionClick,
                enabled = isActionEnabled && !isProcessing,
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                modifier = Modifier.height(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            brush = if (isProcessing) Brush.horizontalGradient(
                                colors = listOf(buttonBackgroundColor.copy(alpha = alpha), buttonBackgroundColor)
                            ) else Brush.horizontalGradient(
                                colors = listOf(buttonBackgroundColor, buttonBackgroundColor)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (actionIcon != null && !isProcessing) {
                            Icon(actionIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        } else if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        }
                        val displayText = if (isProcessing) "Processing…" else actionText
                        val textColor = if (isActionEnabled || isProcessing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = displayText, 
                            color = textColor, 
                            fontWeight = FontWeight.Black, 
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
