package com.torrentactions.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = try {
            EncryptedSharedPreferences.create(
                context,
                "torrent_actions_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("torrent_actions_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    var gitHubToken: String
        get() = prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_TOKEN, value.trim()).apply()

    var repoOwner: String
        get() = prefs.getString(KEY_REPO_OWNER, "piyushpradhan00001") ?: "piyushpradhan00001"
        set(value) = prefs.edit().putString(KEY_REPO_OWNER, value.trim()).apply()

    var repoName: String
        get() = prefs.getString(KEY_REPO_NAME, "torrent_actions") ?: "torrent_actions"
        set(value) = prefs.edit().putString(KEY_REPO_NAME, value.trim()).apply()

    var workflowFile: String
        get() = prefs.getString(KEY_WORKFLOW_FILE, "torrent_download.yml") ?: "torrent_download.yml"
        set(value) = prefs.edit().putString(KEY_WORKFLOW_FILE, value.trim()).apply()

    var targetBranch: String
        get() = prefs.getString(KEY_TARGET_BRANCH, "main") ?: "main"
        set(value) = prefs.edit().putString(KEY_TARGET_BRANCH, value.trim()).apply()

    var pollIntervalSeconds: Int
        get() = prefs.getInt(KEY_POLL_INTERVAL, 2)
        set(value) = prefs.edit().putInt(KEY_POLL_INTERVAL, value.coerceIn(1, 30)).apply()

    fun isConfigured(): Boolean {
        return gitHubToken.isNotBlank() && repoOwner.isNotBlank() && repoName.isNotBlank()
    }

    companion object {
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_REPO_OWNER = "repo_owner"
        private const val KEY_REPO_NAME = "repo_name"
        private const val KEY_WORKFLOW_FILE = "workflow_file"
        private const val KEY_TARGET_BRANCH = "target_branch"
        private const val KEY_POLL_INTERVAL = "poll_interval"
    }
}
