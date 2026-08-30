package com.torrentactions.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.torrentactions.app.ui.components.MagnetDispatchBottomSheet
import com.torrentactions.app.ui.components.SettingsDialog
import com.torrentactions.app.ui.components.TorrentFilesBottomSheet
import com.torrentactions.app.ui.components.WorkerCard
import com.torrentactions.app.ui.theme.BackgroundDark
import com.torrentactions.app.ui.theme.NeonAmber
import com.torrentactions.app.ui.theme.NeonCyan
import com.torrentactions.app.ui.theme.NeonEmerald
import com.torrentactions.app.ui.theme.NeonRose
import com.torrentactions.app.ui.theme.SurfaceBorder
import com.torrentactions.app.ui.theme.SurfaceCard
import com.torrentactions.app.ui.theme.SurfaceDark
import com.torrentactions.app.ui.theme.SurfaceSubtle
import com.torrentactions.app.ui.theme.TextMuted
import com.torrentactions.app.ui.theme.TextPrimary
import com.torrentactions.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: TorrentViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showManualDispatch by remember { mutableStateOf(false) }

    val dispatchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle Toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    // Handle Error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Torrent Actions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshWorkers() },
                        enabled = !uiState.isRefreshing && uiState.isConfigured
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showManualDispatch = true },
                containerColor = NeonCyan,
                contentColor = BackgroundDark,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = {
                    Text(
                        text = "New Download",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Not Configured Warning Card
                if (!uiState.isConfigured) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, NeonAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = NeonAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "GitHub Token Setup Required",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonAmber
                                    )
                                }
                                Text(
                                    text = "Configure your GitHub token or log in with Device Code to monitor and trigger torrent downloads.",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = { showSettings = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonAmber,
                                        contentColor = BackgroundDark
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Open Settings", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2. Summary Stats Card
                if (uiState.isConfigured) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Active Count
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${uiState.activeWorkers.size}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.activeWorkers.isNotEmpty()) NeonCyan else TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Active Workers",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(1.dp)
                                        .background(SurfaceBorder)
                                )

                                // Completed Count
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val completedCount = uiState.historyWorkers.count { it.stage.name == "COMPLETED" }
                                    Text(
                                        text = "$completedCount",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonEmerald,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Completed",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(1.dp)
                                        .background(SurfaceBorder)
                                )

                                // Polling Status
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(NeonEmerald, CircleShape)
                                        )
                                        Text(
                                            text = "LIVE",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonEmerald,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "Sync ~1.2s",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Active Workers Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Active Workers (${uiState.activeWorkers.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // 4. Active Workers Cards or Empty State
                if (uiState.activeWorkers.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, SurfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            color = SurfaceCard.copy(alpha = 0.4f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "No active torrent downloads",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Tap '+ New Download' or open a magnet link in any app.",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.activeWorkers,
                        key = { it.runId }
                    ) { worker ->
                        WorkerCard(
                            worker = worker,
                            onCancelClick = { viewModel.cancelWorker(it) },
                            onViewFilesClick = { viewModel.openFilesForWorker(it) },
                            onRenameClick = { viewModel.setEditingWorkerForTitle(it) }
                        )
                    }
                }

                // 5. Recent History Section (Collapsible)
                if (uiState.historyWorkers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Recent History (${uiState.historyWorkers.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.toggleHistoryVisibility() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (uiState.showHistory) "Hide" else "Show",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (uiState.showHistory) {
                        items(
                            items = uiState.historyWorkers,
                            key = { it.runId }
                        ) { worker ->
                            WorkerCard(
                                worker = worker,
                                onCancelClick = { /* Finished */ },
                                onViewFilesClick = { /* Finished */ },
                                onRenameClick = { viewModel.setEditingWorkerForTitle(it) }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Initial Loading Overlay
            if (uiState.isInitialLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                        Text(
                            text = "Connecting to GitHub Actions...",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    // Modal 1: Settings Dialog
    if (showSettings) {
        SettingsDialog(
            initialToken = uiState.gitHubToken,
            initialOwner = uiState.repoOwner,
            initialRepo = uiState.repoName,
            initialWorkflow = uiState.workflowFile,
            onDismiss = { showSettings = false },
            onSave = { token, owner, repo, workflow ->
                viewModel.saveSettings(token, owner, repo, workflow)
            },
            onTestConnection = { token, owner, repo ->
                viewModel.testConnection(token, owner, repo)
            }
        )
    }

    // Modal 2: Magnet Dispatch Sheet
    val activeMagnet = uiState.incomingMagnet ?: if (showManualDispatch) "" else null
    if (activeMagnet != null) {
        MagnetDispatchBottomSheet(
            magnetUrl = activeMagnet,
            sheetState = dispatchSheetState,
            isDispatching = uiState.isDispatching,
            onDismiss = {
                viewModel.setIncomingMagnet(null)
                showManualDispatch = false
            },
            onDispatch = { magnet, imdbId, torrentName ->
                viewModel.dispatchWorkflow(magnet, imdbId, torrentName)
                showManualDispatch = false
            }
        )
    }

    // Modal 3: Torrent Files & Content Bottom Sheet
    if (uiState.selectedWorkerForFiles != null) {
        TorrentFilesBottomSheet(
            worker = uiState.selectedWorkerForFiles,
            files = uiState.workerFiles,
            isLoading = uiState.isLoadingFiles,
            sheetState = filesSheetState,
            onDismiss = { viewModel.closeFilesDialog() },
            onRefreshFiles = { viewModel.refreshFiles() },
            onRenameTorrent = { newName ->
                uiState.selectedWorkerForFiles?.let { viewModel.renameTorrent(it, newName) }
            },
            onRenameFile = { oldPath, newPath ->
                uiState.selectedWorkerForFiles?.let { viewModel.renameFile(it, oldPath, newPath) }
            }
        )
    }

    // Modal 4: Quick Rename Torrent Title Dialog
    if (uiState.editingWorkerForTitle != null) {
        val targetWorker = uiState.editingWorkerForTitle!!
        var editTitle by remember(targetWorker.torrentName) { mutableStateOf(targetWorker.torrentName) }

        AlertDialog(
            onDismissRequest = { viewModel.setEditingWorkerForTitle(null) },
            containerColor = SurfaceCard,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = NeonAmber)
                    Text("Rename Torrent", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Update the display name for this torrent download:", fontSize = 12.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Torrent Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonAmber,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTitle.isNotBlank()) {
                            viewModel.renameTorrent(targetWorker, editTitle.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = BackgroundDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.setEditingWorkerForTitle(null) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
