package azuredevops.api.impl

import azuredevops.api.PullRequestApi
import azuredevops.http.HttpClient
import azuredevops.model.AzureDevOpsConfig
import azuredevops.model.Identity
import azuredevops.model.PullRequest
import azuredevops.model.PullRequestChange
import azuredevops.model.PullRequestListResponse
import azuredevops.model.PullRequestResponse
import azuredevops.model.ReviewerRequest
import azuredevops.model.ReviewerVote
import azuredevops.services.AzureDevOpsApiException
import azuredevops.services.AzureDevOpsConfigService
import com.intellij.openapi.project.Project

/**
 * Default implementation of PullRequestApi using HttpClient.
 *
 * This class handles all Pull Request related API operations:
 * - Get PRs (single, list, organization-wide)
 * - Create PR
 * - Update PR (complete, abandon, set auto-complete, draft status)
 * - Get PR changes and commits
 * - Vote on PR
 * - Get policy evaluations
 */
class DefaultPullRequestApi(
    private val project: Project,
    private val httpClient: HttpClient,
) : PullRequestApi {
    companion object {
        private const val API_VERSION = "7.0"
    }

    override fun getPullRequests(
        status: String,
        top: Int,
    ): List<PullRequest> {
        val config = requireValidConfig()
        val statusParam = if (status == "all") "all" else status
        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/pullrequests?searchCriteria.status=$statusParam&\$top=$top&api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val listResponse =
                com.google.gson
                    .Gson()
                    .fromJson(response, PullRequestListResponse::class.java)
            listResponse.value
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving Pull Requests: ${e.message}", e)
        }
    }

    override fun getAllOrganizationPullRequests(
        status: String,
        top: Int,
    ): List<PullRequest> {
        val config = requireValidConfig()
        val statusParam = if (status == "all") "all" else status
        val url =
            "${httpClient.getApiBaseUrl()}/_apis/git/pullrequests?searchCriteria.status=$statusParam&\$top=$top&api-version=$API_VERSION"

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val listResponse =
                com.google.gson
                    .Gson()
                    .fromJson(response, PullRequestListResponse::class.java)
            listResponse.value
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving organization Pull Requests: ${e.message}", e)
        }
    }

    override fun getPullRequest(pullRequestId: Int): PullRequest =
        getPullRequest(pullRequestId, null, null)

    override fun getPullRequest(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): PullRequest {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/pullrequests/$pullRequestId?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PullRequest::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving Pull Request: ${e.message}", e)
        }
    }

    override fun findActivePullRequest(
        sourceBranch: String,
        targetBranch: String,
    ): PullRequest? {
        val prs = getPullRequests("active", 100)
        return prs.find { pr ->
            pr.sourceRefName == sourceBranch && pr.targetRefName == targetBranch
        }
    }

    override fun findPullRequestForBranch(branchName: String): PullRequest? {
        val prs = getPullRequests("active", 100)
        return prs.find { pr -> pr.targetRefName == branchName }
    }

    override fun createPullRequest(
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String,
        requiredReviewers: List<Identity>,
        optionalReviewers: List<Identity>,
        isDraft: Boolean,
    ): PullRequestResponse {
        val config = requireValidConfig()

        val reviewers = mutableListOf<ReviewerRequest>()
        requiredReviewers.forEach { identity ->
            identity.id?.let { id ->
                reviewers.add(ReviewerRequest(id = id, isRequired = true))
            }
        }
        optionalReviewers.forEach { identity ->
            identity.id?.let { id ->
                reviewers.add(ReviewerRequest(id = id, isRequired = false))
            }
        }

        val requestBody =
            mapOf(
                "sourceRefName" to sourceBranch,
                "targetRefName" to targetBranch,
                "title" to title,
                "description" to description,
                "reviewers" to reviewers,
                "isDraft" to isDraft,
            )

        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/pullrequests?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executePost(url, requestBody, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PullRequestResponse::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while creating Pull Request: ${e.message}", e)
        }
    }

    override fun getPullRequestChanges(pullRequestId: Int): List<PullRequestChange> =
        getPullRequestChanges(pullRequestId, null, null)

    override fun getPullRequestChanges(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<PullRequestChange> {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/pullrequests/$pullRequestId/changes?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val changesResponse =
                com.google.gson
                    .Gson()
                    .fromJson(response, azuredevops.model.PullRequestChanges::class.java)
            changesResponse.changeEntries ?: emptyList()
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving Pull Request changes: ${e.message}", e)
        }
    }

    override fun getPullRequestCommits(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<String> {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val url =
            httpClient.buildApiUrl(
                effectiveProject,
                effectiveRepo,
                "/pullrequests/$pullRequestId/commits?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val commits =
                com.google.gson
                    .Gson()
                    .fromJson(response, Array<azuredevops.model.GitCommitRef>::class.java)
            commits.mapNotNull { it.commitId }
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving Pull Request commits: ${e.message}", e)
        }
    }

    override fun completePullRequest(
        pullRequest: PullRequest,
        commitMessage: String,
        completionOptions: Map<String, Any>,
    ): PullRequest {
        val config = requireValidConfig()
        val updateBody =
            mapOf(
                "status" to "completed",
                "lastMergeSourceCommit" to mapOf("commitId" to pullRequest.lastMergeSourceCommit?.commitId),
                "completionOptions" to completionOptions + mapOf("mergeCommitMessage" to commitMessage),
            )

        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/pullrequests/${pullRequest.pullRequestId}?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executePatch(url, updateBody, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PullRequest::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while completing Pull Request: ${e.message}", e)
        }
    }

    override fun setAutoComplete(
        pullRequest: PullRequest,
        autoCompleteSetBy: String,
    ): PullRequest {
        val config = requireValidConfig()
        val updateBody =
            mapOf(
                "autoCompleteSetBy" to mapOf("id" to autoCompleteSetBy),
            )

        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/pullrequests/${pullRequest.pullRequestId}?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executePatch(url, updateBody, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PullRequest::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while setting auto-complete: ${e.message}", e)
        }
    }

    override fun abandonPullRequest(pullRequest: PullRequest): PullRequest {
        val config = requireValidConfig()
        val updateBody =
            mapOf(
                "status" to "abandoned",
            )

        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/pullrequests/${pullRequest.pullRequestId}?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executePatch(url, updateBody, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PullRequest::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while abandoning Pull Request: ${e.message}", e)
        }
    }

    override fun updatePullRequestDraftStatus(
        pullRequest: PullRequest,
        isDraft: Boolean,
    ): PullRequest {
        val config = requireValidConfig()
        val updateBody =
            mapOf(
                "isDraft" to isDraft,
            )

        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/pullrequests/${pullRequest.pullRequestId}?api-version=$API_VERSION",
            )

        return try {
            val response = httpClient.executePatch(url, updateBody, config.personalAccessToken)
            com.google.gson
                .Gson()
                .fromJson(response, PullRequest::class.java)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while updating draft status: ${e.message}", e)
        }
    }

    override fun voteOnPullRequest(
        pullRequest: PullRequest,
        vote: Int,
    ): ReviewerVote {
        val config = requireValidConfig()
        val userId = "me" // getCurrentUserId() - TODO: implement
        val voteBody =
            mapOf(
                "vote" to vote,
            )

        val url =
            httpClient.buildApiUrl(
                config.project,
                config.repository,
                "/pullrequests/${pullRequest.pullRequestId}/reviewers/$userId?api-version=$API_VERSION",
            )

        return try {
            httpClient.executePut(url, voteBody, config.personalAccessToken)
            ReviewerVote.fromVoteValue(vote)
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while voting on Pull Request: ${e.message}", e)
        }
    }

    override fun getPolicyEvaluations(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<azuredevops.model.PolicyEvaluation> {
        val config = requireValidConfig()
        val effectiveProject = projectName ?: config.project
        val effectiveRepo = repositoryId ?: config.repository
        val baseUrl = httpClient.getApiBaseUrl()

        val artifactId =
            "vstfs:///CodeReview/CodeReviewId/${config.project ?: effectiveProject}/$pullRequestId"
        val encodedArtifactId = httpClient.encodePathSegment(artifactId)
        val url =
            "$baseUrl/$effectiveProject/_apis/policy/evaluations?artifactId=$encodedArtifactId&api-version=7.0-preview"

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val listResponse =
                com.google.gson
                    .Gson()
                    .fromJson(response, azuredevops.model.PolicyEvaluationListResponse::class.java)
            listResponse.value?.filter { it.configuration?.isEnabled == true } ?: emptyList()
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error while retrieving policy evaluations: ${e.message}", e)
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
