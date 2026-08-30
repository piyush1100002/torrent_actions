package com.torrentactions.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList()
)

@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String? = null,
    val status: String? = null, // queued, in_progress, completed
    val conclusion: String? = null, // success, failure, cancelled, timed_out
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class WorkflowJobsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<WorkflowJob> = emptyList()
)

@Serializable
data class WorkflowJob(
    val id: Long,
    @SerialName("run_id") val runId: Long,
    val name: String? = null,
    val status: String? = null, // queued, in_progress, completed
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val steps: List<WorkflowJobStep> = emptyList()
)

@Serializable
data class WorkflowJobStep(
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    val number: Int = 0
)

@Serializable
data class DispatchPayload(
    val ref: String = "main",
    val inputs: Map<String, String> = emptyMap()
)

@Serializable
data class GitHubUserResponse(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String = "https://github.com/login/device",
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5
)

@Serializable
data class DeviceAccessTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null
)

