package dev.bartuzen.qbitcontroller.data.repositories

import dev.bartuzen.qbitcontroller.data.ServerManager
import dev.bartuzen.qbitcontroller.data.repositories.github.GitHubWorkerRepository
import dev.bartuzen.qbitcontroller.model.QBittorrentVersion
import dev.bartuzen.qbitcontroller.model.ServerConfig
import dev.bartuzen.qbitcontroller.model.github.GitHubConfig
import dev.bartuzen.qbitcontroller.network.RequestManager
import dev.bartuzen.qbitcontroller.network.RequestResult

class TorrentListRepository(
    private val requestManager: RequestManager,
    private val serverManager: ServerManager,
    private val gitHubWorkerRepository: GitHubWorkerRepository,
) {
    private fun getEffectiveGitHubConfig(serverConfig: ServerConfig?): GitHubConfig? {
        if (serverConfig == null) return null
        if (serverConfig.gitHubConfig != null && serverConfig.gitHubConfig.token.isNotBlank()) {
            return serverConfig.gitHubConfig
        }
        if (serverConfig.url.contains("github.com")) {
            val token = serverConfig.password ?: ""
            val rawRepo = (serverConfig.name ?: serverConfig.username ?: "").trim()
            val owner = if (rawRepo.contains("/")) rawRepo.substringBefore("/") else (serverConfig.username ?: "")
            val repo = if (rawRepo.contains("/")) rawRepo.substringAfter("/") else rawRepo
            if (token.isNotBlank() && repo.isNotBlank()) {
                return GitHubConfig(token = token, owner = owner, repo = repo)
            }
        }
        return null
    }

    suspend fun getMainData(serverId: Int): RequestResult<dev.bartuzen.qbitcontroller.model.MainData> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        val ghConfig = getEffectiveGitHubConfig(serverConfig)
        if (ghConfig != null) {
            val res = gitHubWorkerRepository.fetchAggregatedMainData(ghConfig)
            return if (res.isSuccess) {
                RequestResult.Success(res.getOrThrow())
            } else {
                RequestResult.Error.RequestError.Unknown(res.exceptionOrNull()?.message ?: "GitHub error")
            }
        }
        return requestManager.request(serverId) { service ->
            service.getMainData()
        }
    }

    suspend fun getPartialMainData(serverId: Int, rid: Int): RequestResult<kotlinx.serialization.json.JsonElement> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        val ghConfig = getEffectiveGitHubConfig(serverConfig)
        if (ghConfig != null) {
            val res = gitHubWorkerRepository.fetchAggregatedMainData(ghConfig)
            return if (res.isSuccess) {
                val data = res.getOrThrow().copy(rid = rid + 1)
                val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
                val elem = json.encodeToJsonElement(dev.bartuzen.qbitcontroller.model.MainDataSerializer, data)
                val jsonMap = (elem as? kotlinx.serialization.json.JsonObject)?.toMutableMap() ?: mutableMapOf()
                jsonMap["full_update"] = kotlinx.serialization.json.JsonPrimitive(true)
                RequestResult.Success(kotlinx.serialization.json.JsonObject(jsonMap))
            } else {
                RequestResult.Error.RequestError.Unknown(res.exceptionOrNull()?.message ?: "GitHub error")
            }
        }
        return requestManager.request(serverId) { service ->
            service.getPartialMainData(rid)
        }
    }

    suspend fun deleteTorrents(serverId: Int, hashes: List<String>, deleteFiles: Boolean): RequestResult<Unit> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        val ghConfig = getEffectiveGitHubConfig(serverConfig)
        if (ghConfig != null) {
            hashes.forEach { hash ->
                val runId = hash.removePrefix("worker_").toLongOrNull()
                    ?: gitHubWorkerRepository.getWorkerByHashOrRunId(hash)?.runId
                if (runId != null) {
                    gitHubWorkerRepository.cancelWorker(ghConfig, runId)
                }
            }
            return RequestResult.Success(Unit)
        }
        return requestManager.request(serverId) { service ->
            service.deleteTorrents(hashes.joinToString("|"), deleteFiles)
        }
    }

    suspend fun pauseTorrents(serverId: Int, hashes: List<String>): RequestResult<String> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        val ghConfig = getEffectiveGitHubConfig(serverConfig)
        if (ghConfig != null) {
            hashes.forEach { hash ->
                val runId = hash.removePrefix("worker_").toLongOrNull()
                    ?: gitHubWorkerRepository.getWorkerByHashOrRunId(hash)?.runId
                if (runId != null) {
                    gitHubWorkerRepository.cancelWorker(ghConfig, runId)
                }
            }
            return RequestResult.Success("Ok.")
        }
        return requestManager.request(serverId) { service ->
            val version = requestManager.getQBittorrentVersion(serverId)
            when {
                version >= QBittorrentVersion(5, 0, 0) -> service.stopTorrents(hashes.joinToString("|"))
                else -> service.pauseTorrents(hashes.joinToString("|"))
            }
        }
    }

    suspend fun resumeTorrents(serverId: Int, hashes: List<String>) = requestManager.request(serverId) { service ->
        val version = requestManager.getQBittorrentVersion(serverId)
        when {
            version >= QBittorrentVersion(5, 0, 0) -> service.startTorrents(hashes.joinToString("|"))
            else -> service.resumeTorrents(hashes.joinToString("|"))
        }
    }

    suspend fun deleteCategory(serverId: Int, category: String) = requestManager.request(serverId) { service ->
        service.deleteCategories(category)
    }

    suspend fun deleteTag(serverId: Int, tag: String) = requestManager.request(serverId) { service ->
        service.deleteTags(tag)
    }

    suspend fun increaseTorrentPriority(serverId: Int, hashes: List<String>) = requestManager.request(serverId) { service ->
        service.increaseTorrentPriority(hashes.joinToString("|"))
    }

    suspend fun decreaseTorrentPriority(serverId: Int, hashes: List<String>) = requestManager.request(serverId) { service ->
        service.decreaseTorrentPriority(hashes.joinToString("|"))
    }

    suspend fun maximizeTorrentPriority(serverId: Int, hashes: List<String>) = requestManager.request(serverId) { service ->
        service.maximizeTorrentPriority(hashes.joinToString("|"))
    }

    suspend fun minimizeTorrentPriority(serverId: Int, hashes: List<String>) = requestManager.request(serverId) { service ->
        service.minimizeTorrentPriority(hashes.joinToString("|"))
    }

    suspend fun createCategory(
        serverId: Int,
        name: String,
        savePath: String,
        downloadPathEnabled: Boolean?,
        downloadPath: String,
    ) = requestManager.request(serverId) { service ->
        service.createCategory(name, savePath, downloadPathEnabled, downloadPath)
    }

    suspend fun setLocation(serverId: Int, hashes: List<String>, location: String) =
        requestManager.request(serverId) { service ->
            service.setLocation(hashes.joinToString("|"), location)
        }

    suspend fun editCategory(
        serverId: Int,
        name: String,
        savePath: String,
        downloadPathEnabled: Boolean?,
        downloadPath: String,
    ) = requestManager.request(serverId) { service ->
        service.editCategory(name, savePath, downloadPathEnabled, downloadPath)
    }

    suspend fun createTags(serverId: Int, names: List<String>) = requestManager.request(serverId) { service ->
        service.createTags(names.joinToString(","))
    }

    suspend fun toggleSpeedLimitsMode(serverId: Int) = requestManager.request(serverId) { service ->
        service.toggleSpeedLimitsMode()
    }

    suspend fun setDownloadSpeedLimit(serverId: Int, limit: Int) = requestManager.request(serverId) { service ->
        service.setDownloadSpeedLimit(limit)
    }

    suspend fun setUploadSpeedLimit(serverId: Int, limit: Int) = requestManager.request(serverId) { service ->
        service.setUploadSpeedLimit(limit)
    }

    suspend fun shutdown(serverId: Int) = requestManager.request(serverId) { service ->
        service.shutdown()
    }

    suspend fun setCategory(serverId: Int, hashes: List<String>, category: String?) =
        requestManager.request(serverId) { service ->
            service.setCategory(hashes.joinToString("|"), category ?: "")
        }
}
