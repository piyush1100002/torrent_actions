package dev.bartuzen.qbitcontroller.data.repositories.torrent

import dev.bartuzen.qbitcontroller.data.ServerManager
import dev.bartuzen.qbitcontroller.data.repositories.github.GitHubWorkerRepository
import dev.bartuzen.qbitcontroller.model.TorrentFile
import dev.bartuzen.qbitcontroller.model.TorrentFilePriority
import dev.bartuzen.qbitcontroller.network.RequestManager
import dev.bartuzen.qbitcontroller.network.RequestResult

class TorrentFilesRepository(
    private val requestManager: RequestManager,
    private val serverManager: ServerManager,
    private val gitHubWorkerRepository: GitHubWorkerRepository,
) {
    suspend fun getFiles(serverId: Int, hash: String): RequestResult<List<TorrentFile>> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer) {
            val worker = gitHubWorkerRepository.getWorkerByHashOrRunId(hash)
            if (worker != null) {
                val res = gitHubWorkerRepository.getTorrentFiles(worker)
                if (res.isSuccess) {
                    val files = res.getOrThrow().map { qbFile ->
                        TorrentFile(
                            index = qbFile.index,
                            name = qbFile.name,
                            size = qbFile.size,
                            progress = qbFile.progress.toDouble(),
                            priority = TorrentFilePriority.entries.find { it.id == qbFile.priority } ?: TorrentFilePriority.NORMAL
                        )
                    }
                    return RequestResult.Success(files)
                } else {
                    return RequestResult.Error.RequestError.Unknown(res.exceptionOrNull()?.message ?: "Direct connection not available")
                }
            }
        }
        return requestManager.request(serverId) { service ->
            service.getFiles(hash)
        }
    }

    suspend fun setFilePriority(serverId: Int, hash: String, ids: List<Int>, priority: TorrentFilePriority) =
        requestManager.request(serverId) { service ->
            service.setFilePriority(hash, ids.joinToString("|"), priority.id)
        }

    suspend fun renameFile(serverId: Int, hash: String, file: String, newName: String): RequestResult<Unit> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer) {
            val worker = gitHubWorkerRepository.getWorkerByHashOrRunId(hash)
            if (worker != null) {
                val res = gitHubWorkerRepository.renameFile(worker, file, newName)
                if (res.isSuccess) return RequestResult.Success(Unit)
            }
        }
        return requestManager.request(serverId) { service ->
            service.renameFile(hash, file, newName)
        }
    }

    suspend fun renameFolder(serverId: Int, hash: String, folder: String, newName: String) =
        requestManager.request(serverId) { service ->
            service.renameFolder(hash, folder, newName)
        }
}
