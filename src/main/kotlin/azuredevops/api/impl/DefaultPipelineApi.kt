package azuredevops.api.impl

import azuredevops.api.PipelineApi
import azuredevops.http.HttpClient
import azuredevops.model.AzureDevOpsConfig
import azuredevops.model.BuildDefinition
import azuredevops.model.BuildDefinitionListResponse
import azuredevops.model.BuildListResponse
import azuredevops.model.BuildTimeline
import azuredevops.model.PipelineBuild
import azuredevops.services.AzureDevOpsApiException
import azuredevops.services.AzureDevOpsConfigService
import com.intellij.openapi.project.Project

/**
 * Default implementation of PipelineApi using HttpClient.
 */
class DefaultPipelineApi(
    private val project: Project,
    private val httpClient: HttpClient,
) : PipelineApi {
    companion object {
        private const val API_VERSION = "7.0"
    }

    override fun getBuilds(
        definitionId: Int?,
        requestedFor: String?,
        branchName: String?,
        statusFilter: String?,
        resultFilter: String?,
        top: Int,
    ): List<PipelineBuild> {
        val config = requireValidConfig()

        val params = mutableListOf("\$top=$top", "api-version=$API_VERSION")
        definitionId?.let { params.add("definitions=$it") }
        requestedFor?.let { params.add("requestedFor=${httpClient.encodePathSegment(it)}") }
        branchName?.let {
            val ref = if (it.startsWith("refs/")) it else "refs/heads/$it"
            params.add("branchName=$ref")
        }
        statusFilter?.let { if (it != "all") params.add("statusFilter=$it") }
        resultFilter?.let { if (it != "all") params.add("resultFilter=$it") }

        val url =
            httpClient.buildBuildApiUrl(config.project, "/builds?${params.joinToString("&")}")

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val listResponse =
                com.google.gson
                    .Gson()
                    .fromJson(response, BuildListResponse::class.java)
            listResponse.value
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving builds: ${e.message}", e)
        }
    }

    override fun getBuild(buildId: Int): PipelineBuild {
        val config = requireValidConfig()
        val url =
            httpClient.buildBuildApiUrl(config.project, "/builds/$buildId?api-version=$API_VERSION")

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PipelineBuild::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving build: ${e.message}", e)
        }
    }

    override fun getBuildTimeline(buildId: Int): BuildTimeline {
        val config = requireValidConfig()
        val url =
            httpClient.buildBuildApiUrl(
                config.project,
                "/builds/$buildId/timeline?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, BuildTimeline::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving build timeline: ${e.message}", e)
        }
    }

    override fun getBuildLogText(
        buildId: Int,
        logId: Int,
        startLine: Int,
        endLine: Int?,
    ): String {
        val config = requireValidConfig()
        var url =
            httpClient.buildBuildApiUrl(
                config.project,
                "/builds/$buildId/logs/$logId?api-version=$API_VERSION",
            )

        val params = mutableListOf<String>()
        if (startLine > 0) params.add("startLine=$startLine")
        endLine?.let { params.add("endLine=$it") }

        if (params.isNotEmpty()) {
            url += "&${params.joinToString("&")}"
        }

        return try {
            httpClient.executeGet(url, config.personalAccessToken)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving build log: ${e.message}", e)
        }
    }

    override fun getBuildLogTextFromLine(
        buildId: Int,
        logId: Int,
        startLine: Int,
    ): String = getBuildLogText(buildId, logId, startLine, null)

    override fun getBuildDefinitions(): List<BuildDefinition> {
        val config = requireValidConfig()
        val url =
            httpClient.buildBuildApiUrl(
                config.project,
                "/definitions?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val listResponse =
                com.google.gson
                    .Gson()
                    .fromJson(response, BuildDefinitionListResponse::class.java)
            listResponse.value ?: emptyList()
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving build definitions: ${e.message}", e)
        }
    }

    override fun queueBuild(
        definitionId: Int,
        branchName: String?,
        parameters: Map<String, String>,
    ): PipelineBuild {
        val config = requireValidConfig()
        val url =
            httpClient.buildBuildApiUrl(
                config.project,
                "/builds?api-version=$API_VERSION",
            )

        val requestBody = mutableMapOf<String, Any>("definition" to mapOf("id" to definitionId))

        branchName?.let {
            val sourceBranch = if (it.startsWith("refs/heads/")) it else "refs/heads/$it"
            requestBody["sourceBranch"] = sourceBranch
        }

        if (parameters.isNotEmpty()) {
            requestBody["parameters"] =
                com.google.gson
                    .Gson()
                    .toJson(parameters)
        }

        return try {
            val response = httpClient.executePost(url, requestBody, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PipelineBuild::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while queuing build: ${e.message}", e)
        }
    }

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
