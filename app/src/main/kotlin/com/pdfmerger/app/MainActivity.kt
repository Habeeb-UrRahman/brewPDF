package com.pdfmerger.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.pdfmerger.app.viewmodel.MergeViewModel

/**
 * Single-activity entry point for the PDF Merger app.
 *
 * Handles:
 * - Normal app launch (MAIN action)
 * - Single PDF share (ACTION_SEND + EXTRA_STREAM)
 * - Multiple PDF share (ACTION_SEND_MULTIPLE + EXTRA_STREAM)
 * - New shares while already running (onNewIntent via singleTask)
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MergeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[MergeViewModel::class.java]

        // Handle initial intent (app launched via share)
        handleIncomingIntent(intent)

        setContent {
            PdfMergerApp(viewModel = viewModel)
        }
    }

    /**
     * Called when a new share arrives while the app is already running
     * (because launchMode="singleTask" in manifest).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Extracts PDF URIs from SEND / SEND_MULTIPLE intents and adds them
     * to the ViewModel staging list.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val uris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                uri?.let { uris.add(it) }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val extras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                        ?.filterIsInstance<Uri>()
                        ?.let { ArrayList(it) }
                }
                extras?.let { uris.addAll(it) }
            }
        }

        if (uris.isNotEmpty()) {
            // Take persistent read permission where possible
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }
            viewModel.sharedUris.value = uris
        }
    }
}
