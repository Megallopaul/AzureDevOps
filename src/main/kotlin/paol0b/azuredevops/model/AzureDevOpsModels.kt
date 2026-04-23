package paol0b.azuredevops.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the Azure DevOps account configuration
 */
@ConsistentCopyVisibility
data class AzureDevOpsConfig private constructor(
    val organization: String = "",
    val project: String = "",
    val repository: String = "",
    val personalAccessToken: String = "",
) {
    fun isValid(): Boolean =
        organization.isNotBlank() &&
            project.isNotBlank() &&
            repository.isNotBlank() &&
            personalAccessToken.isNotBlank()

    companion object {
        fun create(
            organization: String,
            project: String,
            repository: String,
            personalAccessToken: String,
        ): AzureDevOpsConfig =
            AzureDevOpsConfig(
                organization = organization,
                project = project,
                repository = repository,
                personalAccessToken = personalAccessToken,
            )
    }
}

/**
 * Request to create a Pull Request
 */
data class CreatePullRequestRequest(
    val sourceRefName: String,
    val targetRefName: String,
    val title: String,
    val description: String = "",
    val reviewers: List<ReviewerRequest>? = null,
    val isDraft: Boolean = false,
)

/**
 * Reviewer to add to the PR during creation
 */
data class ReviewerRequest(
    val id: String,
    @SerializedName("isRequired")
    val isRequired: Boolean = false,
)

/**
 * Response from Pull Request creation
 */
data class PullRequestResponse(
    @SerializedName("pullRequestId")
    val pullRequestId: Int,
    val title: String,
    val description: String?,
    val sourceRefName: String,
    val targetRefName: String,
    val status: String,
    @SerializedName("createdBy")
    val createdBy: CreatedBy?,
    @SerializedName("creationDate")
    val creationDate: String?,
    @SerializedName("url")
    val url: String?,
)

/**
 * Complete Pull Request with all details
 */
data class PullRequest(
    @SerializedName("pullRequestId")
    val pullRequestId: Int,
    val title: String,
    val description: String?,
    val sourceRefName: String,
    val targetRefName: String,
    val status: PullRequestStatus,
    @SerializedName("createdBy")
    val createdBy: User?,
    @SerializedName("creationDate")
    val creationDate: String?,
    @SerializedName("closedDate")
    val closedDate: String?,
    @SerializedName("mergeStatus")
    val mergeStatus: String?,
    @SerializedName("isDraft")
    val isDraft: Boolean?,
    @SerializedName("reviewers")
    val reviewers: List<Reviewer>?,
    @SerializedName("labels")
    val labels: List<Label>?,
    @SerializedName("url")
    val url: String?,
    @SerializedName("repository")
    val repository: Repository?,
    @SerializedName("lastMergeSourceCommit")
    val lastMergeSourceCommit: CommitRef?,
    @SerializedName("lastMergeTargetCommit")
    val lastMergeTargetCommit: CommitRef?,
    @SerializedName("autoCompleteSetBy")
    val autoCompleteSetBy: User?,
) {
    fun getWebUrl(): String = url ?: ""

    fun getSourceBranchName(): String = sourceRefName.removePrefix("refs/heads/")

    fun getTargetBranchName(): String = targetRefName.removePrefix("refs/heads/")

    fun isActive(): Boolean = status == PullRequestStatus.Active

    fun isMerged(): Boolean = status == PullRequestStatus.Completed

    fun isAbandoned(): Boolean = status == PullRequestStatus.Abandoned

    /**
     * Check if the PR has merge conflicts
     */
    fun hasConflicts(): Boolean = mergeStatus == "conflicts"

    /**
     * Check if the PR is ready to complete (all checks passed, policies met, approvals received)
     * This matches when Azure DevOps shows the "Complete" button
     */
    fun isReadyToComplete(): Boolean {
        // Must be active
        if (!isActive()) return false

        // Must not have conflicts
        if (mergeStatus == "conflicts" || mergeStatus == "failure") return false

        // Must not be rejected by policy
        if (mergeStatus == "rejectedByPolicy") return false

        // Check if there are any required reviewers who haven't approved
        val requiredReviewers = reviewers?.filter { it.isRequired == true } ?: emptyList()
        if (requiredReviewers.isNotEmpty()) {
            val hasAllRequiredApprovals =
                requiredReviewers.all { reviewer ->
                    reviewer.vote == 10 // 10 = approved in Azure DevOps
                }
            if (!hasAllRequiredApprovals) return false
        }

        // Check if there are any rejections (vote -10 or -5)
        val hasRejections = reviewers?.any { it.vote == -10 || it.vote == -5 } ?: false
        if (hasRejections) return false

        // If merge status is succeeded and no policy violations, it's ready
        return mergeStatus == "succeeded"
    }

    /**
     * Check if auto-complete is already set
     */
    fun hasAutoComplete(): Boolean = autoCompleteSetBy != null

    /**
     * Check if the current user is the creator of the PR
     * @param currentUserId The ID of the current authenticated user
     */
    fun isCreatedByUser(currentUserId: String?): Boolean = currentUserId != null && createdBy?.id == currentUserId
}

/**
 * Pull Request status
 * Sealed class for better type safety and exhaustiveness checking
 */
sealed class PullRequestStatus(
    @SerializedName("value")
    val value: String,
) {
    abstract fun getDisplayName(): String

    object NotSet : PullRequestStatus("notSet") {
        override fun getDisplayName() = "Not Set"
    }

    object Active : PullRequestStatus("active") {
        override fun getDisplayName() = "Active"
    }

    object Abandoned : PullRequestStatus("abandoned") {
        override fun getDisplayName() = "Abandoned"
    }

    object Completed : PullRequestStatus("completed") {
        override fun getDisplayName() = "Completed"
    }

    companion object {
        fun fromValue(value: String?): PullRequestStatus =
            when (value) {
                "notSet" -> NotSet
                "active" -> Active
                "abandoned" -> Abandoned
                "completed" -> Completed
                else -> NotSet
            }
    }
}

/**
 * Reviewer of a PR
 */
data class Reviewer(
    val id: String?,
    val displayName: String?,
    val uniqueName: String?,
    @SerializedName("imageUrl")
    val imageUrl: String?,
    val vote: Int?,
    @SerializedName("isRequired")
    val isRequired: Boolean?,
) {
    fun getVoteStatus(): ReviewerVote = ReviewerVote.fromVoteValue(vote)
}

enum class ReviewerVote {
    Approved,
    ApprovedWithSuggestions,
    NoVote,
    WaitingForAuthor,
    Rejected,
    ;

    fun getDisplayName(): String =
        when (this) {
            Approved -> "✓ Approved"
            ApprovedWithSuggestions -> "✓ Approved with suggestions"
            NoVote -> "○ No vote"
            WaitingForAuthor -> "⚠ Waiting for author"
            Rejected -> "✗ Rejected"
        }

    companion object {
        fun fromVoteValue(vote: Int?): ReviewerVote =
            when (vote) {
                10 -> Approved
                5 -> ApprovedWithSuggestions
                0 -> NoVote
                -5 -> WaitingForAuthor
                -10 -> Rejected
                else -> NoVote
            }
    }
}

/**
 * Label/Tag of a PR
 */
data class Label(
    val id: String?,
    val name: String?,
    val active: Boolean?,
)

/**
 * Repository info
 */
data class Repository(
    val id: String?,
    val name: String?,
    @SerializedName("project")
    val project: Project?,
    @SerializedName("remoteUrl")
    val remoteUrl: String?,
)

/**
 * Project info
 */
data class Project(
    val id: String?,
    val name: String?,
)

/**
 * User/CreatedBy info
 */
data class User(
    val id: String?,
    val displayName: String?,
    val uniqueName: String?,
    @SerializedName("imageUrl")
    val imageUrl: String?,
)

data class CreatedBy(
    val displayName: String?,
    val uniqueName: String?,
)

/**
 * Response for PR list
 */
data class PullRequestListResponse(
    val value: List<PullRequest>,
    val count: Int?,
)

/**
 * Represents a Git branch
 */
data class GitBranch(
    val name: String,
    val displayName: String,
) {
    companion object {
        fun fromRefName(refName: String): GitBranch {
            val displayName = refName.removePrefix("refs/heads/")
            return GitBranch(refName, displayName)
        }
    }
}

/**
 * Error response from Azure DevOps API
 */
data class AzureDevOpsError(
    val message: String?,
    @SerializedName("typeKey")
    val typeKey: String?,
    @SerializedName("errorCode")
    val errorCode: Int?,
)

data class AzureDevOpsErrorResponse(
    @SerializedName("\$id")
    val id: String?,
    val innerException: String?,
    val message: String?,
    @SerializedName("typeName")
    val typeName: String?,
    @SerializedName("typeKey")
    val typeKey: String?,
    @SerializedName("errorCode")
    val errorCode: Int?,
    @SerializedName("eventId")
    val eventId: Int?,
)

/**
 * Thread of comments in a PR
 */
data class CommentThread(
    val id: Int?,
    @SerializedName("pullRequestThreadContext")
    val pullRequestThreadContext: ThreadContext?,
    val comments: List<Comment>?,
    val status: ThreadStatus?,
    @SerializedName("threadContext")
    val threadContext: ThreadContext?,
    @SerializedName("isDeleted")
    val isDeleted: Boolean?,
) {
    /**
     * Gets the file path, searching in pullRequestThreadContext first, then threadContext
     */
    fun getFilePath(): String? = pullRequestThreadContext?.filePath ?: threadContext?.filePath

    /**
     * Gets the start line, searching in pullRequestThreadContext first, then threadContext
     */
    fun getRightFileStart(): Int? = pullRequestThreadContext?.rightFileStart?.line ?: threadContext?.rightFileStart?.line

    /**
     * Gets the end line, searching in pullRequestThreadContext first, then threadContext
     */
    fun getRightFileEnd(): Int? = pullRequestThreadContext?.rightFileEnd?.line ?: threadContext?.rightFileEnd?.line

    fun isActive(): Boolean = status == ThreadStatus.Active || status == ThreadStatus.Pending

    fun isResolved(): Boolean = !isActive()

    /**
     * Checks if this is a system-generated thread (e.g., from Azure DevOps automation)
     * System threads typically have commentType = "system" or no human author
     */
    fun isSystemGenerated(): Boolean {
        val firstComment = comments?.firstOrNull() ?: return false
        return firstComment.commentType == "system" || firstComment.author == null
    }
}

/**
 * Thread context (position in file)
 */
data class ThreadContext(
    @SerializedName("filePath")
    val filePath: String?,
    @SerializedName("rightFileStart")
    val rightFileStart: LineInfo?,
    @SerializedName("rightFileEnd")
    val rightFileEnd: LineInfo?,
    @SerializedName("leftFileStart")
    val leftFileStart: LineInfo?,
    @SerializedName("leftFileEnd")
    val leftFileEnd: LineInfo?,
)

data class LineInfo(
    val line: Int?,
    val offset: Int?,
)

/**
 * Thread status
 * Sealed class for better type safety and exhaustiveness checking
 */
sealed class ThreadStatus(
    @SerializedName("value")
    val value: String,
    val apiValue: Int,
) {
    abstract fun getDisplayName(): String

    object Unknown : ThreadStatus("unknown", 0) {
        override fun getDisplayName() = "Unknown"
    }

    object Active : ThreadStatus("active", 1) {
        override fun getDisplayName() = "Active"
    }

    object Fixed : ThreadStatus("fixed", 2) {
        override fun getDisplayName() = "Fixed"
    }

    object WontFix : ThreadStatus("wontFix", 3) {
        override fun getDisplayName() = "Won't Fix"
    }

    object Closed : ThreadStatus("closed", 4) {
        override fun getDisplayName() = "Closed"
    }

    object ByDesign : ThreadStatus("byDesign", 5) {
        override fun getDisplayName() = "By Design"
    }

    object Pending : ThreadStatus("pending", 6) {
        override fun getDisplayName() = "Pending"
    }

    companion object {
        fun fromValue(value: String?): ThreadStatus =
            when (value) {
                "unknown" -> Unknown
                "active" -> Active
                "fixed" -> Fixed
                "wontFix" -> WontFix
                "closed" -> Closed
                "byDesign" -> ByDesign
                "pending" -> Pending
                else -> Unknown
            }

        fun fromApiValue(value: Int): ThreadStatus =
            when (value) {
                0 -> Unknown
                1 -> Active
                2 -> Fixed
                3 -> WontFix
                4 -> Closed
                5 -> ByDesign
                6 -> Pending
                else -> Unknown
            }

        /**
         * Returns all possible status values
         */
        fun allValues(): List<ThreadStatus> = listOf(Unknown, Active, Fixed, WontFix, Closed, ByDesign, Pending)
    }

    /**
     * Converts the status to the numeric value required by the Azure DevOps API
     * Used in PATCH requests as the status field
     */
    fun toApiValue(): Int = apiValue
}

/**
 * Single comment
 */
data class Comment(
    val id: Int?,
    val content: String?,
    val author: User?,
    @SerializedName("publishedDate")
    val publishedDate: String?,
    @SerializedName("lastUpdatedDate")
    val lastUpdatedDate: String?,
    @SerializedName("lastContentUpdatedDate")
    val lastContentUpdatedDate: String?,
    @SerializedName("commentType")
    val commentType: String?,
    @SerializedName("isDeleted")
    val isDeleted: Boolean?,
)

/**
 * Request to create a comment
 */
data class CreateCommentRequest(
    val content: String,
    @SerializedName("parentCommentId")
    val parentCommentId: Int? = null,
    @SerializedName("commentType")
    val commentType: String = "text",
)

/**
 * Request to update the status of a thread
 * Azure DevOps API requires status and comments fields
 * Comments can be an empty array
 */
data class UpdateThreadStatusRequest(
    @SerializedName("status")
    val status: Int,
) {
    constructor(status: ThreadStatus) : this(status.toApiValue())
}

/**
 * Response for thread list
 */
data class CommentThreadListResponse(
    val value: List<CommentThread>,
    val count: Int?,
)

/**
 * Reference to a commit
 */
data class CommitRef(
    @SerializedName("commitId")
    val commitId: String?,
    @SerializedName("url")
    val url: String?,
)

/**
 * Identity (user/group) from Azure DevOps for reviewer search
 */
data class Identity(
    val id: String?,
    val displayName: String?,
    @SerializedName("uniqueName")
    val uniqueName: String?,
    @SerializedName("imageUrl")
    val imageUrl: String?,
    val descriptor: String?,
)

/**
 * Response from identity search
 */
data class IdentitySearchResponse(
    val value: List<Identity>?,
    val count: Int?,
)

/**
 * Pull Request iteration info
 */
data class PullRequestIteration(
    @SerializedName("id")
    val id: Int?,
)

/**
 * Response for PR iterations list
 */
data class PullRequestIterationListResponse(
    val value: List<PullRequestIteration>?,
    val count: Int?,
)
