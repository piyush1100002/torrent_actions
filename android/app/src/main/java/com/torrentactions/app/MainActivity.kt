package com.torrentactions.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.torrentactions.app.ui.screens.MainDashboardScreen
import com.torrentactions.app.ui.screens.TorrentViewModel
import com.torrentactions.app.ui.theme.TorrentActionsTheme
import com.torrentactions.app.util.MagnetUtils

class MainActivity : ComponentActivity() {

    private val viewModel: TorrentViewModel by viewModels {
        val app = application as TorrentApplication
        TorrentViewModel.Factory(app.repository, app.securePreferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            TorrentActionsTheme {
                MainDashboardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.startPolling()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPolling()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data: Uri? = intent.data
                if (data != null) {
                    val rawUri = data.toString()
                    val magnet = MagnetUtils.extractMagnetFromText(rawUri) ?: rawUri
                    if (MagnetUtils.isMagnetOrTorrent(magnet)) {
                        viewModel.setIncomingMagnet(magnet)
                    }
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val magnet = MagnetUtils.extractMagnetFromText(sharedText)
                    if (magnet != null) {
                        viewModel.setIncomingMagnet(magnet)
                    }
                }
            }
        }
    }
}
