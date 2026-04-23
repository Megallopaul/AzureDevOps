package azuredevops.api.impl

import azuredevops.api.RepositoryApi
import azuredevops.http.HttpClient
import azuredevops.model.AzureDevOpsConfig
import azuredevops.model.GitRefUpdate
import azuredevops.model.GitRefUpdateResponse
import azuredevops.model.GitRefUpdateResult
import azuredevops.services.AzureDevOpsApiException
import azuredevops.services.AzureDevOpsConfigService
import com.intellij.openapi.project.Project

/**
 * Default implementation of RepositoryApi using HttpClient.
 */
class DefaultRepositoryApi(
    private val project: Project,
    private val httpClient: HttpClient,
) : RepositoryApi {
    companion object {
        private const val API_VERSION = "7.0"
    }

    override fun getFileContent(
        commitId: String,
        filePath: String,
    ): String = getFileContent(commitId, filePath, null, null)

    override fun getFileContent(
        commitId: String,
        filePath: String,
        projectName: String?,
        repositoryId: String?,
    ): String {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val encodedPath = httpClient.encodePathSegment(filePath)

        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/items?path=$encodedPath&versionDescriptor.version=$commitId&versionDescriptor.versionType=commit&includeContent=true",
            ) + "&api-version=$API_VERSION"

        val response = httpClient.executeGet(url, config.personalAccessToken)

        return try {
            val jsonObject =
                com.google.gson
                    .Gson()
                    .fromJson(response, com.google.gson.JsonObject::class.java)
            jsonObject.get("content")?.asString ?: ""
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving file content: ${e.message}", e)
        }
    }

    override fun createGitRef(
        branchName: String,
        objectId: String,
    ): GitRefUpdateResult {
        val config = requireValidConfig()
        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/refs?api-version=$API_VERSION",
            )

        val refUpdates =
            listOf(
                GitRefUpdate(
                    name = "refs/heads/$branchName",
                    oldObjectId = "0000000000000000000000000000000000000000",
                    newObjectId = objectId,
                ),
            )

        return try {
            val response = httpClient.executePost(url, refUpdates, config.personalAccessToken)
            val result =
                com.google.gson
                    .Gson()
                    .fromJson(response, GitRefUpdateResponse::class.java)
            result.value?.firstOrNull()
                ?: throw AzureDevOpsApiException("No result returned from ref creation")
        } catch (e: AzureDevOpsApiException) {
            throw e
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error creating branch '$branchName': ${e.message}", e)
        }
    }

    override fun buildRepositoryWebUrl(
        projectName: String,
        repositoryName: String,
    ): String {
        val baseUrl = httpClient.getApiBaseUrl()
        val encodedProject = httpClient.encodePathSegment(projectName)
        val encodedRepo = httpClient.encodePathSegment(repositoryName)
        return "$baseUrl/$encodedProject/_git/$encodedRepo"
    }

    override fun buildPullRequestWebUrl(
        projectName: String,
        repositoryName: String,
        pullRequestId: Int,
    ): String = "${buildRepositoryWebUrl(projectName, repositoryName)}/pullrequest/$pullRequestId"

    // region Helper methods

    private fun requireValidConfig(): azuredevops.model.AzureDevOpsConfig {
        val configService = azuredevops.services.AzureDevOpsConfigService.getInstance(project)
        val config = configService.getConfig()
        if (!config.isValid()) {
            throw AzureDevOpsApiException("Authentication required. Please login.")
        }
        return config
    }

    // endregion
}
