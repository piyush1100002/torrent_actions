package dev.bartuzen.qbitcontroller.data.repositories.torrent

import dev.bartuzen.qbitcontroller.data.ServerManager
import dev.bartuzen.qbitcontroller.data.repositories.github.GitHubWorkerRepository
import dev.bartuzen.qbitcontroller.model.PieceState
import dev.bartuzen.qbitcontroller.model.QBittorrentVersion
import dev.bartuzen.qbitcontroller.model.Torrent
import dev.bartuzen.qbitcontroller.model.TorrentProperties
import dev.bartuzen.qbitcontroller.network.RequestManager
import dev.bartuzen.qbitcontroller.network.RequestResult
import io.ktor.utils.io.ByteReadChannel
import kotlin.time.Clock

class TorrentOverviewRepository(
    private val requestManager: RequestManager,
    private val serverManager: ServerManager,
    private val gitHubWorkerRepository: GitHubWorkerRepository,
) {
    suspend fun getTorrent(serverId: Int, hash: String): RequestResult<List<Torrent>> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer && serverConfig.gitHubConfig != null) {
            val mainDataRes = gitHubWorkerRepository.fetchAggregatedMainData(serverConfig.gitHubConfig)
            if (mainDataRes.isSuccess) {
                val torrent = mainDataRes.getOrThrow().torrents.find { it.hash == hash }
                return RequestResult.Success(if (torrent != null) listOf(torrent) else emptyList())
            }
        }
        return requestManager.request(serverId) { service ->
            service.getTorrentList(hash)
        }
    }

    suspend fun getProperties(serverId: Int, hash: String): RequestResult<TorrentProperties> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer) {
            val worker = gitHubWorkerRepository.getWorkerByHashOrRunId(hash)
            val properties = TorrentProperties(
                pieceSize = 1048576L,
                piecesCount = 100,
                piecesHave = worker?.progressPercent ?: 0,
                totalSize = 1000000L,
                additionDate = Clock.System.now(),
                completionDate = null,
                creationDate = null,
                createdBy = "GitHub Actions",
                savePath = "/downloads",
                comment = "Workflow Run #${worker?.runId ?: ""}",
                nextReannounce = 0L,
                connections = worker?.peerCount ?: 0,
                connectionsLimit = 100,
                seeds = 0,
                seedsTotal = 0,
                peers = worker?.peerCount ?: 0,
                peersTotal = worker?.peerCount ?: 0,
                wasted = 0L
            )
            return RequestResult.Success(properties)
        }
        return requestManager.request(serverId) { service ->
            service.getTorrentProperties(hash)
        }
    }

    suspend fun getPieces(serverId: Int, hash: String): RequestResult<List<PieceState>> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer) {
            return RequestResult.Success(emptyList())
        }
        return requestManager.request(serverId) { service ->
            service.getTorrentPieces(hash)
        }
    }

    suspend fun deleteTorrent(serverId: Int, hash: String, deleteFiles: Boolean): RequestResult<Unit> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer && serverConfig.gitHubConfig != null) {
            val runId = hash.removePrefix("worker_").toLongOrNull()
                ?: gitHubWorkerRepository.getWorkerByHashOrRunId(hash)?.runId
            if (runId != null) {
                gitHubWorkerRepository.cancelWorker(serverConfig.gitHubConfig, runId)
            }
            return RequestResult.Success(Unit)
        }
        return requestManager.request(serverId) { service ->
            service.deleteTorrents(hash, deleteFiles)
        }
    }

    suspend fun pauseTorrent(serverId: Int, hash: String): RequestResult<String> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer && serverConfig.gitHubConfig != null) {
            val runId = hash.removePrefix("worker_").toLongOrNull()
                ?: gitHubWorkerRepository.getWorkerByHashOrRunId(hash)?.runId
            if (runId != null) {
                gitHubWorkerRepository.cancelWorker(serverConfig.gitHubConfig, runId)
            }
            return RequestResult.Success("Ok.")
        }
        return requestManager.request(serverId) { service ->
            val version = requestManager.getQBittorrentVersion(serverId)
            when {
                version >= QBittorrentVersion(5, 0, 0) -> service.stopTorrents(hash)
                else -> service.pauseTorrents(hash)
            }
        }
    }

    suspend fun resumeTorrent(serverId: Int, hash: String) = requestManager.request(serverId) { service ->
        val version = requestManager.getQBittorrentVersion(serverId)
        when {
            version >= QBittorrentVersion(5, 0, 0) -> service.startTorrents(hash)
            else -> service.resumeTorrents(hash)
        }
    }

    suspend fun toggleSequentialDownload(serverId: Int, hash: String) = requestManager.request(serverId) { service ->
        service.toggleSequentialDownload(hash)
    }

    suspend fun togglePrioritizeFirstLastPiecesDownload(serverId: Int, hash: String) =
        requestManager.request(serverId) { service ->
            service.togglePrioritizeFirstLastPiecesDownload(hash)
        }

    suspend fun setAutomaticTorrentManagement(serverId: Int, hash: String, enable: Boolean) =
        requestManager.request(serverId) { service ->
            service.setAutomaticTorrentManagement(hash, enable)
        }

    suspend fun setDownloadSpeedLimit(serverId: Int, hash: String, limit: Int) =
        requestManager.request(serverId) { service ->
            service.setDownloadSpeedLimit(hash, limit)
        }

    suspend fun setUploadSpeedLimit(serverId: Int, hash: String, limit: Int) = requestManager.request(serverId) { service ->
        service.setUploadSpeedLimit(hash, limit)
    }

    suspend fun setForceStart(serverId: Int, hash: String, value: Boolean) = requestManager.request(serverId) { service ->
        service.setForceStart(hash, value)
    }

    suspend fun setSuperSeeding(serverId: Int, hash: String, value: Boolean) = requestManager.request(serverId) { service ->
        service.setSuperSeeding(hash, value)
    }

    suspend fun recheckTorrent(serverId: Int, hash: String) = requestManager.request(serverId) { service ->
        service.recheckTorrents(hash)
    }

    suspend fun reannounceTorrent(serverId: Int, hash: String) = requestManager.request(serverId) { service ->
        service.reannounceTorrents(hash)
    }

    suspend fun renameTorrent(serverId: Int, hash: String, name: String): RequestResult<Unit> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer) {
            val worker = gitHubWorkerRepository.getWorkerByHashOrRunId(hash)
            if (worker != null) {
                gitHubWorkerRepository.renameTorrent(worker, name)
                return RequestResult.Success(Unit)
            }
        }
        return requestManager.request(serverId) { service ->
            service.renameTorrent(hash, name)
        }
    }

    suspend fun setLocation(serverId: Int, hash: String, location: String) = requestManager.request(serverId) { service ->
        service.setLocation(hash, location)
    }

    suspend fun setDownloadPath(serverId: Int, hash: String, path: String) = requestManager.request(serverId) { service ->
        service.setDownloadPath(hash, path)
    }

    suspend fun getCategories(serverId: Int) = requestManager.request(serverId) { service ->
        service.getCategories()
    }

    suspend fun getTags(serverId: Int) = requestManager.request(serverId) { service ->
        service.getTags()
    }

    suspend fun setCategory(serverId: Int, hash: String, category: String?) = requestManager.request(serverId) { service ->
        service.setCategory(hash, category ?: "")
    }

    suspend fun addTags(serverId: Int, hash: String, tags: List<String>) = requestManager.request(serverId) { service ->
        service.addTags(hash, tags.joinToString(","))
    }

    suspend fun removeTags(serverId: Int, hash: String, tags: List<String>) = requestManager.request(serverId) { service ->
        service.removeTags(hash, tags.joinToString(","))
    }

    suspend fun setShareLimit(
        serverId: Int,
        hash: String,
        ratioLimit: Double,
        seedingTimeLimit: Int,
        inactiveSeedingTimeLimit: Int,
    ) = requestManager.request(serverId) { service ->
        service.setShareLimit(hash, ratioLimit, seedingTimeLimit, inactiveSeedingTimeLimit)
    }

    suspend fun exportTorrent(serverId: Int, hash: String, block: suspend (ByteReadChannel) -> Unit) =
        requestManager.request(serverId) { service ->
            service.exportTorrent(hash, block)
        }
}
