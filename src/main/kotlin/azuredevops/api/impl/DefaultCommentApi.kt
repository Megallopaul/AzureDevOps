package azuredevops.api.impl

import azuredevops.api.CommentApi
import azuredevops.http.HttpClient
import azuredevops.model.AzureDevOpsConfig
import azuredevops.model.Comment
import azuredevops.model.CommentThread
import azuredevops.model.CommentThreadListResponse
import azuredevops.model.CreateCommentRequest
import azuredevops.model.CreateThreadRequest
import azuredevops.model.ThreadContext
import azuredevops.model.ThreadStatus
import azuredevops.model.UpdateThreadStatusRequest
import azuredevops.services.AzureDevOpsApiException
import azuredevops.services.AzureDevOpsConfigService
import com.intellij.openapi.project.Project

/**
 * Default implementation of CommentApi using HttpClient.
 *
 * This class handles all Pull Request comment related API operations:
 * - Get comment threads
 * - Add comment to thread
 * - Update thread status
 * - Create new thread
 */
class DefaultCommentApi(
    private val project: Project,
    private val httpClient: HttpClient,
) : CommentApi {
    companion object {
        private const val API_VERSION = "7.0"
    }

    override fun getCommentThreads(pullRequestId: Int): List<CommentThread> =
        getCommentThreads(pullRequestId, null, null)

    override fun getCommentThreads(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<CommentThread> {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/pullRequests/$pullRequestId/threads?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val listResponse =
                com.google.gson
                    .Gson()
                    .fromJson(response, CommentThreadListResponse::class.java)
            listResponse.value.filter { it.isDeleted != true }
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving comments: ${e.message}", e)
        }
    }

    override fun addCommentToThread(
        pullRequestId: Int,
        threadId: Int,
        content: String,
        projectName: String?,
        repositoryId: String?,
    ): Comment {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val request = CreateCommentRequest(content = content)
        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/pullRequests/$pullRequestId/threads/$threadId/comments?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executePost(url, request, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, Comment::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while adding comment: ${e.message}", e)
        }
    }

    override fun updateThreadStatus(
        pullRequestId: Int,
        threadId: Int,
        status: ThreadStatus,
        projectName: String?,
        repositoryId: String?,
    ) {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val request = UpdateThreadStatusRequest(status)
        val jsonBody =
            com.google.gson
                .Gson()
                .toJson(request)

        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/pullRequests/$pullRequestId/threads/$threadId?api-version=$API_VERSION",
            )

        try {
            httpClient.executePatch(
                url,
                com.google.gson.JsonParser
                    .parseString(jsonBody),
                config.personalAccessToken,
            )
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while updating thread status: ${e.message}", e)
        }
    }

    override fun createThread(
        pullRequestId: Int,
        content: String,
        filePath: String?,
        lineNumber: Int?,
        projectName: String?,
        repositoryId: String?,
    ): CommentThread {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository

        // Use Map-based approach like the original API client
        val threadContextMap = mutableMapOf<String, Any>()
        if (filePath != null) {
            threadContextMap["filePath"] = if (filePath.startsWith("/")) filePath else "/$filePath"
        }

        if (lineNumber != null) {
            val position = mapOf("line" to lineNumber, "offset" to 1)
            threadContextMap["rightFileStart"] = position
            threadContextMap["rightFileEnd"] = position
        }

        val request =
            mapOf(
                "comments" to listOf(mapOf("content" to content, "commentType" to 1)),
                "threadContext" to threadContextMap,
                "status" to 1,
            )

        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/pullRequests/$pullRequestId/threads?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executePost(url, request, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, CommentThread::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while creating thread: ${e.message}", e)
        }
    }

    // region Helper methods

    private fun requireValidConfig(): azuredevops.model.AzureDevOpsConfig {
        val configService = azuredevops.services.AzureDevOpsConfigService.getInstance(project)
        val config = configService.getConfig()
        if (!config.isValid()) {
            throw AzureDevOpsApiException(
                """Authentication required. Please login:
1. Go to File → Settings → Tools → Azure DevOps Accounts
2. Click 'Add' button to add your account
3. Complete the authentication in your browser

The plugin will automatically use your authenticated account for this repository.""",
            )
        }
        return config
    }

    // endregion
}
