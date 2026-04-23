package paol0b.azuredevops.api

import paol0b.azuredevops.model.GitRefUpdateResult
import paol0b.azuredevops.services.AzureDevOpsApiException

/**
 * API interface for Repository operations
 */
interface RepositoryApi {
    /**
     * Retrieves the content of a file at a specific commit from the current repository
     * @param commitId SHA of the commit
     * @param filePath Path of the file (e.g., "/src/main/Program.cs")
     * @return Content of the file as a string
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getFileContent(
        commitId: String,
        filePath: String,
    ): String

    /**
     * Retrieves the content of a file at a specific commit from a specific project/repository
     * @param commitId SHA of the commit
     * @param filePath Path of the file (e.g., "/src/main/Program.cs")
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return Content of the file as a string
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getFileContent(
        commitId: String,
        filePath: String,
        projectName: String?,
        repositoryId: String?,
    ): String

    /**
     * Create a Git ref (branch) in the repository.
     * @param branchName Name of the branch to create (e.g., "refs/heads/feature/new-feature")
     * @param objectId Object ID (commit SHA) to base the branch on
     * @return GitRefUpdateResult with the created branch information
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun createGitRef(
        branchName: String,
        objectId: String,
    ): GitRefUpdateResult

    /**
     * Build a repository web URL
     * @param projectName Project name
     * @param repositoryName Repository name
     * @return Web URL to the repository
     */
    fun buildRepositoryWebUrl(
        projectName: String,
        repositoryName: String,
    ): String

    /**
     * Build a pull request web URL
     * @param projectName Project name
     * @param repositoryName Repository name
     * @param pullRequestId Pull request ID
     * @return Web URL to the pull request
     */
    fun buildPullRequestWebUrl(
        projectName: String,
        repositoryName: String,
        pullRequestId: Int,
    ): String
}
