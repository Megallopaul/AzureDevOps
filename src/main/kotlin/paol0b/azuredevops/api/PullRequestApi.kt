package paol0b.azuredevops.api

import paol0b.azuredevops.model.Identity
import paol0b.azuredevops.model.PolicyEvaluation
import paol0b.azuredevops.model.PullRequest
import paol0b.azuredevops.model.PullRequestChange
import paol0b.azuredevops.model.PullRequestResponse
import paol0b.azuredevops.model.ReviewerVote
import paol0b.azuredevops.services.AzureDevOpsApiException

/**
 * API interface for Pull Request operations
 */
interface PullRequestApi {
    /**
     * Retrieves Pull Requests from the current repository
     * @param status Filter by status (e.g., "active", "completed", "abandoned", "all")
     * @param top Maximum number of PRs to retrieve
     * @return List of Pull Requests
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getPullRequests(
        status: String = "active",
        top: Int = 100,
    ): List<PullRequest>

    /**
     * Retrieves Pull Requests from all projects in the organization
     * @param status Filter by status (e.g., "active", "completed", "abandoned", "all")
     * @param top Maximum number of PRs to retrieve
     * @return List of Pull Requests from all organization projects
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getAllOrganizationPullRequests(
        status: String = "active",
        top: Int = 100,
    ): List<PullRequest>

    /**
     * Retrieves a single Pull Request with all details from the current repository
     * @param pullRequestId PR ID
     * @return Complete Pull Request
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getPullRequest(pullRequestId: Int): PullRequest

    /**
     * Retrieves a single Pull Request with all details from a specific project/repository
     * @param pullRequestId PR ID
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return Complete Pull Request
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getPullRequest(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): PullRequest

    /**
     * Searches for an active Pull Request between two specific branches
     * @param sourceBranch Source branch name (e.g., "feature/xyz")
     * @param targetBranch Target branch name (e.g., "main")
     * @return PullRequest if found, null otherwise
     */
    fun findActivePullRequest(
        sourceBranch: String,
        targetBranch: String,
    ): PullRequest?

    /**
     * Finds a Pull Request by its target branch name
     * @param branchName Target branch name to search for
     * @return PullRequest if found, null otherwise
     */
    fun findPullRequestForBranch(branchName: String): PullRequest?

    /**
     * Creates a Pull Request on Azure DevOps
     * @param sourceBranch Source branch (e.g., "refs/heads/feature/xyz")
     * @param targetBranch Target branch (e.g., "refs/heads/main")
     * @param title PR title
     * @param description PR description (optional)
     * @param requiredReviewers List of required reviewers
     * @param optionalReviewers List of optional reviewers
     * @param isDraft Whether the PR should be created as a draft
     * @return PullRequestResponse if successful
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun createPullRequest(
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String = "",
        requiredReviewers: List<Identity> = emptyList(),
        optionalReviewers: List<Identity> = emptyList(),
        isDraft: Boolean = false,
    ): PullRequestResponse

    /**
     * Retrieves the changes (file modifications) in a Pull Request
     * @param pullRequestId PR ID
     * @return List of PullRequestChange
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getPullRequestChanges(pullRequestId: Int): List<PullRequestChange>

    /**
     * Retrieves the changes (file modifications) in a Pull Request from a specific project/repository
     * @param pullRequestId PR ID
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return List of PullRequestChange
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getPullRequestChanges(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<PullRequestChange>

    /**
     * Retrieves the commits in a Pull Request
     * @param pullRequestId PR ID
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return List of commit IDs
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getPullRequestCommits(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<String>

    /**
     * Completes (merges) a Pull Request
     * @param pullRequest The PR to complete
     * @param commitMessage Commit message for the merge
     * @param completionOptions Completion options (e.g., squash, delete source branch)
     * @return Updated Pull Request
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun completePullRequest(
        pullRequest: PullRequest,
        commitMessage: String,
        completionOptions: Map<String, Any>,
    ): PullRequest

    /**
     * Sets auto-complete for a Pull Request
     * @param pullRequest The PR to update
     * @param autoCompleteSetBy User ID who set auto-complete
     * @return Updated Pull Request
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun setAutoComplete(
        pullRequest: PullRequest,
        autoCompleteSetBy: String,
    ): PullRequest

    /**
     * Abandons (closes without merging) a Pull Request
     * @param pullRequest The PR to abandon
     * @return Updated Pull Request
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun abandonPullRequest(pullRequest: PullRequest): PullRequest

    /**
     * Updates the draft status of a Pull Request
     * @param pullRequest The PR to update
     * @param isDraft New draft status
     * @return Updated Pull Request
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun updatePullRequestDraftStatus(
        pullRequest: PullRequest,
        isDraft: Boolean,
    ): PullRequest

    /**
     * Casts a vote on a Pull Request
     * @param pullRequest The PR to vote on
     * @param vote Vote value (e.g., 10 for approve, -10 for reject)
     * @return Vote enum value
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun voteOnPullRequest(
        pullRequest: PullRequest,
        vote: Int,
    ): ReviewerVote

    /**
     * Retrieves policy evaluations for a Pull Request
     * @param pullRequestId PR ID
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return List of policy evaluation results
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getPolicyEvaluations(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<PolicyEvaluation>
}
