package com.pdfmerger.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.theme.BrewAmber
import com.pdfmerger.app.ui.theme.BrewTerracotta

@Composable
fun StagingBottomBar(
    itemCount: Int,
    isSortAscending: Boolean,
    onAddClick: () -> Unit,
    onSortClick: () -> Unit,
    onSaveClick: () -> Unit,
    onResetClick: () -> Unit,
    onMergeClick: () -> Unit,
    isMerging: Boolean
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
                BarIconButton(icon = Icons.Outlined.Add, onClick = onAddClick, contentDesc = "Add PDF")
                BarIconButton(
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    onClick = onSortClick,
                    enabled = itemCount > 1,
                    contentDesc = "Sort"
                )
                BarIconButton(
                    icon = Icons.Outlined.SaveAlt,
                    onClick = onSaveClick,
                    enabled = itemCount > 0,
                    contentDesc = "Save"
                )
                BarIconButton(
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = onResetClick,
                    enabled = itemCount > 0,
                    tint = MaterialTheme.colorScheme.error,
                    contentDesc = "Clear"
                )
            }

            Button(
                onClick = onMergeClick,
                enabled = itemCount > 1 && !isMerging,
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .height(44.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(BrewAmber, BrewTerracotta)),
                        shape = RoundedCornerShape(14.dp),
                        alpha = if (itemCount > 1 && !isMerging) 1f else 0.4f
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Merge", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun BarIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDesc: String
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (enabled) tint else tint.copy(alpha = 0.25f),
            modifier = Modifier.size(22.dp)
        )
    }
}
