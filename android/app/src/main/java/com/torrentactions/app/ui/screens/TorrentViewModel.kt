package com.torrentactions.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.torrentactions.app.data.api.QBTorrentFile
import com.torrentactions.app.data.local.SecurePreferences
import com.torrentactions.app.data.repository.TorrentRepository
import com.torrentactions.app.data.repository.TorrentWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "TorrentViewModel"

data class DashboardUiState(
    val activeWorkers: List<TorrentWorker> = emptyList(),
    val historyWorkers: List<TorrentWorker> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDispatching: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val isConfigured: Boolean = false,
    val incomingMagnet: String? = null,
    val selectedWorkerForFiles: TorrentWorker? = null,
    val workerFiles: List<QBTorrentFile> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val editingWorkerForTitle: TorrentWorker? = null,
    val repoOwner: String = "",
    val repoName: String = "",
    val workflowFile: String = "",
    val gitHubToken: String = "",
    val showHistory: Boolean = false
)

class TorrentViewModel(
    private val repository: TorrentRepository,
    private val prefs: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            isConfigured = prefs.isConfigured(),
            repoOwner = prefs.repoOwner,
            repoName = prefs.repoName,
            workflowFile = prefs.workflowFile,
            gitHubToken = prefs.gitHubToken
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        loadSettings()
        if (prefs.isConfigured()) {
            refreshWorkers(isInitial = true)
        }
    }

    fun loadSettings() {
        _uiState.update {
            it.copy(
                isConfigured = prefs.isConfigured(),
                repoOwner = prefs.repoOwner,
                repoName = prefs.repoName,
                workflowFile = prefs.workflowFile,
                gitHubToken = prefs.gitHubToken
            )
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (prefs.isConfigured()) {
                    fetchAllWorkersInternal(isBackground = true)
                }
                // High frequency: 1200ms when active workers exist, otherwise 3500ms
                val activeCount = _uiState.value.activeWorkers.size
                val interval = if (activeCount > 0) 1200L else 3500L
                delay(interval)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshWorkers(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (isInitial) {
                _uiState.update { it.copy(isInitialLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            }
            fetchAllWorkersInternal(isBackground = false)
            _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false) }
        }
    }

    private suspend fun fetchAllWorkersInternal(isBackground: Boolean) {
        val result = repository.fetchAllWorkers()
        if (result.isSuccess) {
            val all = result.getOrDefault(emptyList())
            val active = all.filter { it.isActive }
            val history = all.filter { !it.isActive }

            _uiState.update { current ->
                val updatedSelected = if (current.selectedWorkerForFiles != null) {
                    all.find { it.runId == current.selectedWorkerForFiles.runId } ?: current.selectedWorkerForFiles
                } else null

                current.copy(
                    activeWorkers = active,
                    historyWorkers = history,
                    selectedWorkerForFiles = updatedSelected,
                    errorMessage = null
                )
            }
        } else if (!isBackground) {
            val error = result.exceptionOrNull()?.localizedMessage ?: "Failed to fetch workers"
            _uiState.update { it.copy(errorMessage = error) }
        }
    }

    fun dispatchWorkflow(magnet: String, imdbId: String?, torrentName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDispatching = true, errorMessage = null) }
            val result = repository.dispatchTorrent(magnet, imdbId, torrentName)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isDispatching = false,
                        incomingMagnet = null,
                        toastMessage = "✅ Torrent download queued in GitHub Actions!"
                    )
                }
                delay(1000)
                refreshWorkers()
            } else {
                val error = result.exceptionOrNull()?.localizedMessage ?: "Failed to trigger workflow"
                _uiState.update {
                    it.copy(
                        isDispatching = false,
                        errorMessage = error
                    )
                }
            }
        }
    }

    fun cancelWorker(runId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(toastMessage = "Requesting cancellation for #$runId...") }
            val result = repository.cancelWorker(runId)
            if (result.isSuccess) {
                _uiState.update { it.copy(toastMessage = "Worker #$runId cancelled successfully") }
                delay(800)
                refreshWorkers()
            } else {
                val error = result.exceptionOrNull()?.localizedMessage ?: "Failed to cancel worker"
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    fun openFilesForWorker(worker: TorrentWorker) {
        _uiState.update { it.copy(selectedWorkerForFiles = worker, isLoadingFiles = true, workerFiles = emptyList()) }
        loadFilesForWorker(worker)
    }

    fun closeFilesDialog() {
        _uiState.update { it.copy(selectedWorkerForFiles = null, workerFiles = emptyList(), isLoadingFiles = false) }
    }

    fun refreshFiles() {
        val worker = _uiState.value.selectedWorkerForFiles ?: return
        loadFilesForWorker(worker)
    }

    private fun loadFilesForWorker(worker: TorrentWorker) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true) }
            val result = repository.getTorrentFiles(worker)
            if (result.isSuccess) {
                _uiState.update { it.copy(workerFiles = result.getOrDefault(emptyList()), isLoadingFiles = false) }
            } else {
                _uiState.update { it.copy(isLoadingFiles = false) }
            }
        }
    }

    fun renameTorrent(worker: TorrentWorker, newName: String) {
        viewModelScope.launch {
            val result = repository.renameTorrent(worker, newName)
            if (result.isSuccess) {
                _uiState.update { current ->
                    val updatedActive = current.activeWorkers.map {
                        if (it.runId == worker.runId) it.copy(torrentName = newName) else it
                    }
                    val updatedHistory = current.historyWorkers.map {
                        if (it.runId == worker.runId) it.copy(torrentName = newName) else it
                    }
                    val updatedSelected = if (current.selectedWorkerForFiles?.runId == worker.runId) {
                        current.selectedWorkerForFiles.copy(torrentName = newName)
                    } else current.selectedWorkerForFiles

                    current.copy(
                        activeWorkers = updatedActive,
                        historyWorkers = updatedHistory,
                        selectedWorkerForFiles = updatedSelected,
                        editingWorkerForTitle = null,
                        toastMessage = "Torrent title renamed to '$newName'"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun renameFile(worker: TorrentWorker, oldPath: String, newPath: String) {
        viewModelScope.launch {
            val result = repository.renameFile(worker, oldPath, newPath)
            if (result.isSuccess) {
                _uiState.update { it.copy(toastMessage = "File renamed to '$newPath'") }
                loadFilesForWorker(worker)
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Rename failed") }
            }
        }
    }

    fun setEditingWorkerForTitle(worker: TorrentWorker?) {
        _uiState.update { it.copy(editingWorkerForTitle = worker) }
    }

    fun saveSettings(token: String, owner: String, repo: String, workflow: String) {
        prefs.gitHubToken = token
        prefs.repoOwner = owner
        prefs.repoName = repo
        prefs.workflowFile = workflow
        loadSettings()
        refreshWorkers(isInitial = true)
    }

    suspend fun testConnection(token: String, owner: String, repo: String): Result<String> {
        return repository.validateConnection(token, owner, repo)
    }

    fun setIncomingMagnet(magnet: String?) {
        _uiState.update { it.copy(incomingMagnet = magnet) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun toggleHistoryVisibility() {
        _uiState.update { it.copy(showHistory = !it.showHistory) }
    }

    class Factory(
        private val repository: TorrentRepository,
        private val prefs: SecurePreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TorrentViewModel(repository, prefs) as T
        }
    }
}
