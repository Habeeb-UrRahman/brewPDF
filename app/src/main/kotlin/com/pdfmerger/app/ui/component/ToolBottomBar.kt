package com.pdfmerger.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (leftIcon != null && onLeftClick != null) {
                    IconButton(
                        onClick = onLeftClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = leftIcon,
                            contentDescription = leftContentDesc,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                if (showClearButton && onClearClick != null) {
                    IconButton(
                        onClick = onClearClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onActionClick,
                enabled = isActionEnabled && !isProcessing,
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .height(44.dp)
                    .background(
                        color = if (isActionEnabled && !isProcessing) actionColor else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) {
                    if (actionIcon != null && !isProcessing) {
                        Icon(actionIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    } else if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    val displayText = if (isProcessing) "Processing…" else actionText
                    Text(
                        text = displayText, 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
