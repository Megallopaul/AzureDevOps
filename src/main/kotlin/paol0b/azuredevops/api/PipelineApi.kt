package paol0b.azuredevops.api

import paol0b.azuredevops.model.BuildDefinition
import paol0b.azuredevops.model.BuildTimeline
import paol0b.azuredevops.model.PipelineBuild
import paol0b.azuredevops.services.AzureDevOpsApiException

/**
 * API interface for Pipeline/Build operations
 */
interface PipelineApi {
    /**
     * Retrieves builds with optional filters
     * @param definitionId Optional build definition ID filter
     * @param requestedFor Optional user who requested the build
     * @param branchName Optional branch name filter
     * @param statusFilter Optional status filter (e.g., "inProgress", "completed", "cancelled")
     * @param resultFilter Optional result filter (e.g., "succeeded", "failed", "partiallySucceeded")
     * @param top Maximum number of builds to retrieve
     * @return List of PipelineBuild
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getBuilds(
        definitionId: Int? = null,
        requestedFor: String? = null,
        branchName: String? = null,
        statusFilter: String? = null,
        resultFilter: String? = null,
        top: Int = 50,
    ): List<PipelineBuild>

    /**
     * Retrieves a single build by ID
     * @param buildId Build ID
     * @return PipelineBuild
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getBuild(buildId: Int): PipelineBuild

    /**
     * Retrieves the build timeline (stages and jobs) for a build
     * @param buildId Build ID
     * @return BuildTimeline
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getBuildTimeline(buildId: Int): BuildTimeline

    /**
     * Retrieves the log text for a specific build step
     * @param buildId Build ID
     * @param logId Log ID (typically 0 for the main log)
     * @param startLine Optional starting line number (default: 0)
     * @param endLine Optional ending line number (default: null for all remaining lines)
     * @return Log content as a string
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getBuildLogText(
        buildId: Int,
        logId: Int,
        startLine: Int = 0,
        endLine: Int? = null,
    ): String

    /**
     * Retrieves the log text for a specific build step starting from a specific line
     * @param buildId Build ID
     * @param logId Log ID
     * @param startLine Starting line number
     * @return Log content as a string
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getBuildLogTextFromLine(
        buildId: Int,
        logId: Int,
        startLine: Int,
    ): String

    /**
     * Retrieves all build definitions for the current repository
     * @return List of BuildDefinition
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getBuildDefinitions(): List<BuildDefinition>

    /**
     * Queues a new build
     * @param definitionId Build definition ID
     * @param branchName Branch to build (optional, uses default if null)
     * @param parameters Optional build parameters
     * @return Queued PipelineBuild
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun queueBuild(
        definitionId: Int,
        branchName: String? = null,
        parameters: Map<String, String> = emptyMap(),
    ): PipelineBuild
}
