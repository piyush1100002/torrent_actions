package com.torrentactions.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.torrentactions.app.ui.theme.BackgroundDark
import com.torrentactions.app.ui.theme.NeonAmber
import com.torrentactions.app.ui.theme.NeonCyan
import com.torrentactions.app.ui.theme.SurfaceBorder
import com.torrentactions.app.ui.theme.SurfaceDark
import com.torrentactions.app.ui.theme.SurfaceSubtle
import com.torrentactions.app.ui.theme.TextMuted
import com.torrentactions.app.ui.theme.TextPrimary
import com.torrentactions.app.ui.theme.TextSecondary
import com.torrentactions.app.util.MagnetUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetDispatchBottomSheet(
    magnetUrl: String,
    sheetState: SheetState,
    isDispatching: Boolean,
    onDismiss: () -> Unit,
    onDispatch: (magnet: String, imdbId: String?, torrentName: String?) -> Unit
) {
    var rawMagnet by remember(magnetUrl) { mutableStateOf(magnetUrl) }
    var imdbId by remember { mutableStateOf("") }
    var customTorrentName by remember(rawMagnet) {
        mutableStateOf(if (rawMagnet.isNotBlank()) MagnetUtils.extractDisplayName(rawMagnet) else "")
    }

    val torrentTitle = remember(rawMagnet) {
        if (rawMagnet.isNotBlank()) MagnetUtils.extractDisplayName(rawMagnet) else ""
    }
    val infoHash = remember(rawMagnet) {
        if (rawMagnet.isNotBlank()) MagnetUtils.extractInfoHash(rawMagnet) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Trigger Torrent Download",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            // Top editable torrent title field
            OutlinedTextField(
                value = customTorrentName,
                onValueChange = { customTorrentName = it },
                label = { Text("Torrent Name") },
                placeholder = { Text("e.g. Inception 1080p") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedLabelColor = NeonCyan,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Preview Box if magnet is present
            if (rawMagnet.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSubtle)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = torrentTitle.ifBlank { "Torrent Download" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (infoHash != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "BTIH: $infoHash",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Magnet Input Field (if user opened manually)
            OutlinedTextField(
                value = rawMagnet,
                onValueChange = { rawMagnet = it },
                label = { Text("Magnet Link or .torrent URL") },
                placeholder = { Text("magnet:?xt=urn:btih:...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedLabelColor = NeonCyan,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Optional IMDb ID Field
            OutlinedTextField(
                value = imdbId,
                onValueChange = { imdbId = it.trim() },
                label = { Text("Optional IMDb ID (e.g. tt1375666)") },
                placeholder = { Text("tt1375666") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = NeonAmber
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonAmber,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedLabelColor = NeonAmber,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                text = "💡 Providing an IMDb ID skips the automatic title lookup and categorizes the torrent directly in Hugging Face / Postgres.",
                fontSize = 11.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action Button
            Button(
                onClick = {
                    if (rawMagnet.isNotBlank()) {
                        val cleanedImdb = MagnetUtils.cleanImdbId(imdbId)
                        val resolvedName = customTorrentName.trim().ifBlank { null }
                        onDispatch(rawMagnet.trim(), cleanedImdb, resolvedName)
                    }
                },
                enabled = rawMagnet.isNotBlank() && !isDispatching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = BackgroundDark,
                    disabledContainerColor = SurfaceSubtle,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isDispatching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = BackgroundDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Triggering Action...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start in GitHub Actions",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
