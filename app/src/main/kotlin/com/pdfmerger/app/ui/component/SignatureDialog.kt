package com.pdfmerger.app.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.io.FileOutputStream

@Composable
fun SignatureDialog(
    onDismiss: () -> Unit,
    onSave: (File) -> Unit,
    cacheDir: File
) {
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(400.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Draw Signature", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = Path().apply {
                                        moveTo(offset.x, offset.y)
                                    }
                                },
                                onDrag = { change, _ ->
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    // Trigger recomposition by creating a new path reference if needed, 
                                    // but we can just update a state variable
                                    currentPath = Path().apply { addPath(currentPath!!) }
                                },
                                onDragEnd = {
                                    currentPath?.let { paths = paths + it }
                                    currentPath = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        paths.forEach { path ->
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 5f)
                            )
                        }
                        currentPath?.let { path ->
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 5f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { paths = emptyList() }) {
                        Text("Clear")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        // Save paths to a bitmap
                        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.Transparent.toArgb())
                        val paint = Paint().apply {
                            color = android.graphics.Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = 10f
                            isAntiAlias = true
                        }
                        paths.forEach { composePath ->
                            val androidPath = composePath.asAndroidPath()
                            canvas.drawPath(androidPath, paint)
                        }
                        
                        val outFile = File(cacheDir, "signature_${System.currentTimeMillis()}.png")
                        FileOutputStream(outFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                        onSave(outFile)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
