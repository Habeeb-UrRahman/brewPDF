package com.pdfmerger.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.BrewScaffold

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    BrewScaffold(
        title = "Settings",
        subtitle = "Preferences and information",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingsItem(
                icon = Icons.Outlined.DarkMode,
                title = "App Theme",
                subtitle = "Follows system setting automatically"
            )

            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            SettingsItem(
                icon = Icons.Outlined.Security,
                title = "Privacy & Offline",
                subtitle = "100% offline processing. Your files never leave your device."
            )

            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "brewPDF v2.0",
                subtitle = "Built with ❤️ by Brew Creative Studio"
            )

            Text(
                "Connect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            SettingsItem(
                icon = Icons.Outlined.Info, // Placeholder for Website icon
                title = "Website",
                subtitle = "https://www.habeeburrahman.in",
                onClick = { uriHandler.openUri("https://www.habeeburrahman.in") }
            )

            SettingsItem(
                icon = Icons.Outlined.Info, // Placeholder for YouTube icon
                title = "YouTube",
                subtitle = "https://www.youtube.com/@techbrewtv",
                onClick = { uriHandler.openUri("https://www.youtube.com/@techbrewtv") }
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
