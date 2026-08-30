package com.torrentactions.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.torrentactions.app.data.parser.WorkerStage
import com.torrentactions.app.data.repository.TorrentWorker
import com.torrentactions.app.ui.theme.NeonAmber
import com.torrentactions.app.ui.theme.NeonCyan
import com.torrentactions.app.ui.theme.NeonEmerald
import com.torrentactions.app.ui.theme.NeonRose
import com.torrentactions.app.ui.theme.SurfaceBorder
import com.torrentactions.app.ui.theme.SurfaceCard
import com.torrentactions.app.ui.theme.SurfaceSubtle
import com.torrentactions.app.ui.theme.TextMuted
import com.torrentactions.app.ui.theme.TextPrimary
import com.torrentactions.app.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkerCard(
    worker: TorrentWorker,
    onCancelClick: (Long) -> Unit,
    onViewFilesClick: (TorrentWorker) -> Unit,
    onRenameClick: (TorrentWorker) -> Unit,
    modifier: Modifier = Modifier
) {
    val stageColor = when (worker.stage) {
        WorkerStage.DOWNLOADING -> NeonCyan
        WorkerStage.UPLOADING_HF -> NeonEmerald
        WorkerStage.COMPLETED -> NeonEmerald
        WorkerStage.FAILED -> NeonRose
        WorkerStage.CANCELLED -> TextMuted
        WorkerStage.QUEUED, WorkerStage.SETTING_UP -> NeonAmber
    }

    val animatedProgress by animateFloatAsState(
        targetValue = worker.progressPercent / 100f,
        animationSpec = tween(durationMillis = 400),
        label = "progress"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (worker.isActive) stageColor.copy(alpha = 0.4f) else SurfaceBorder, RoundedCornerShape(16.dp)),
        color = SurfaceCard
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Stage Tag & Run ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(stageColor, CircleShape)
                    )
                    Text(
                        text = when (worker.stage) {
                            WorkerStage.QUEUED -> "QUEUED"
                            WorkerStage.SETTING_UP -> "INITIALIZING"
                            WorkerStage.DOWNLOADING -> "DOWNLOADING TORRENT"
                            WorkerStage.UPLOADING_HF -> "PUSHING TO HUGGING FACE"
                            WorkerStage.COMPLETED -> "COMPLETED"
                            WorkerStage.FAILED -> "FAILED"
                            WorkerStage.CANCELLED -> "CANCELLED"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = stageColor,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "#${worker.runId}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }

            // Title with Quick Rename Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = worker.torrentName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!worker.imdbId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceSubtle)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "IMDb: ",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = worker.imdbId,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NeonAmber
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onRenameClick(worker) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename Title",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Progress Bar & Percentage
            if (worker.isActive || worker.stage == WorkerStage.COMPLETED) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (worker.stage == WorkerStage.UPLOADING_HF) "HF Push in Progress" else "Download Progress",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${worker.progressPercent}%",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = stageColor
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = stageColor,
                        trackColor = SurfaceSubtle,
                    )
                }
            }

            // Metrics Grid (Speed, ETA, Downloaded/Total Size, Peers)
            if (worker.isActive) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (worker.downloadSpeed != "--") {
                        MetricChip(
                            icon = Icons.Default.Speed,
                            label = "Speed",
                            value = worker.downloadSpeed,
                            accentColor = NeonCyan
                        )
                    }

                    if (worker.eta != "--") {
                        MetricChip(
                            icon = Icons.Default.HourglassEmpty,
                            label = "ETA",
                            value = worker.eta,
                            accentColor = NeonAmber
                        )
                    }

                    if (worker.downloadedSize != "--" && worker.totalSize != "--") {
                        MetricChip(
                            icon = Icons.Default.Storage,
                            label = "Size",
                            value = "${worker.downloadedSize} / ${worker.totalSize}",
                            accentColor = TextSecondary
                        )
                    }

                    if (worker.peerCount > 0) {
                        MetricChip(
                            icon = Icons.Default.Group,
                            label = "Peers",
                            value = "${worker.peerCount}",
                            accentColor = NeonEmerald
                        )
                    }
                }
            }

            // Footer Actions: "Files" button & "Cancel Worker"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (worker.isActive) {
                    TextButton(
                        onClick = { onViewFilesClick(worker) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Files",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { onCancelClick(worker.runId) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonRose
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRose.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = NeonRose
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cancel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
