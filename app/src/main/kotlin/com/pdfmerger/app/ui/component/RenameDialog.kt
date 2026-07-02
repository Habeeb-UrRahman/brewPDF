package com.pdfmerger.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.theme.BrewAmber

/**
 * Dialog to rename a file before saving.
 * Shows a text field pre-filled with the suggested name (without extension).
 * Returns the final name including the original extension.
 */
@Composable
fun RenameDialog(
    suggestedName: String,
    extension: String = ".pdf",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Strip extension for editing
    val nameWithoutExtension = suggestedName.removeSuffix(extension)
    var editedName by remember { mutableStateOf(nameWithoutExtension) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = BrewAmber,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "Save As",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose a name for the output file:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = {
                        Text(
                            extension,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrewAmber,
                        focusedLabelColor = BrewAmber
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = editedName.trim().ifEmpty { nameWithoutExtension }
                    onConfirm("$finalName$extension")
                },
                enabled = editedName.trim().isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrewAmber)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
