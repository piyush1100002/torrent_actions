package com.torrentactions.app

import android.app.Application
import com.torrentactions.app.data.api.GitHubApiClient
import com.torrentactions.app.data.local.SecurePreferences
import com.torrentactions.app.data.repository.TorrentRepository

class TorrentApplication : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var apiClient: GitHubApiClient
        private set

    lateinit var repository: TorrentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        securePreferences = SecurePreferences(this)
        apiClient = GitHubApiClient()
        repository = TorrentRepository(apiClient, securePreferences)
    }
}
