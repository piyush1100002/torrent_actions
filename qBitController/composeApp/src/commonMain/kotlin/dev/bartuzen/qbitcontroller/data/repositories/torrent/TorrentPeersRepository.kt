package dev.bartuzen.qbitcontroller.data.repositories.torrent

import dev.bartuzen.qbitcontroller.data.ServerManager
import dev.bartuzen.qbitcontroller.model.TorrentPeer
import dev.bartuzen.qbitcontroller.model.TorrentPeers
import dev.bartuzen.qbitcontroller.network.RequestManager
import dev.bartuzen.qbitcontroller.network.RequestResult

class TorrentPeersRepository(
    private val requestManager: RequestManager,
    private val serverManager: ServerManager,
) {
    suspend fun getPeers(serverId: Int, hash: String): RequestResult<TorrentPeers> {
        val serverConfig = serverManager.getServerOrNull(serverId)
        if (serverConfig != null && serverConfig.isGitHubActionsServer) {
            return RequestResult.Success(TorrentPeers(emptyMap()))
        }
        return requestManager.request(serverId) { service ->
            service.getPeers(hash)
        }
    }

    suspend fun addPeers(serverId: Int, hash: String, peers: List<String>) = requestManager.request(serverId) { service ->
        service.addPeers(hash, peers.joinToString("|"))
    }

    suspend fun banPeers(serverId: Int, peers: List<String>) = requestManager.request(serverId) { service ->
        service.banPeers(peers.joinToString("|"))
    }
}
