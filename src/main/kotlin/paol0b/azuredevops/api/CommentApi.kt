package paol0b.azuredevops.api

import paol0b.azuredevops.model.Comment
import paol0b.azuredevops.model.CommentThread
import paol0b.azuredevops.model.ThreadStatus
import paol0b.azuredevops.services.AzureDevOpsApiException

/**
 * API interface for Pull Request comment operations
 */
interface CommentApi {
    /**
     * Retrieves all comment threads for a Pull Request from the current repository
     * Automatically filters out deleted threads (isDeleted == true)
     * @param pullRequestId PR ID
     * @return List of comment threads (never null, may be empty)
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getCommentThreads(pullRequestId: Int): List<CommentThread>

    /**
     * Retrieves all comment threads for a Pull Request from a specific project/repository
     * Automatically filters out deleted threads (isDeleted == true)
     * @param pullRequestId PR ID
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return List of comment threads (never null, may be empty)
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getCommentThreads(
        pullRequestId: Int,
        projectName: String?,
        repositoryId: String?,
    ): List<CommentThread>

    /**
     * Adds a comment to an existing thread
     * @param pullRequestId PR ID
     * @param threadId Thread ID
     * @param content Comment content
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return The created comment
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun addCommentToThread(
        pullRequestId: Int,
        threadId: Int,
        content: String,
        projectName: String? = null,
        repositoryId: String? = null,
    ): Comment

    /**
     * Updates the status of a comment thread (e.g., resolves or reopens)
     * @param pullRequestId PR ID
     * @param threadId Thread ID
     * @param status New status (e.g., ThreadStatus.Fixed, ThreadStatus.Active)
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun updateThreadStatus(
        pullRequestId: Int,
        threadId: Int,
        status: ThreadStatus,
        projectName: String? = null,
        repositoryId: String? = null,
    )

    /**
     * Creates a new comment thread on a Pull Request
     * @param pullRequestId PR ID
     * @param content The initial comment content
     * @param filePath Path to the file (optional for general comments)
     * @param lineNumber Line number where the comment should be placed (optional)
     * @param projectName Project name (can be null to use current project)
     * @param repositoryId Repository ID or name (can be null to use current repository)
     * @return The created comment thread
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun createThread(
        pullRequestId: Int,
        content: String,
        filePath: String? = null,
        lineNumber: Int? = null,
        projectName: String? = null,
        repositoryId: String? = null,
    ): CommentThread
}
