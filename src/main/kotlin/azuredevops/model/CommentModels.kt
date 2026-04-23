package azuredevops.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a position in a comment (line and offset)
 */
data class CommentPosition(
    @SerializedName("line")
    val line: Int?,
    @SerializedName("offset")
    val offset: Int?,
)

/**
 * Request to create a comment thread
 */
data class CreateThreadRequest(
    @SerializedName("comments")
    val comments: List<CreateCommentRequest>,
    @SerializedName("threadContext")
    val threadContext: ThreadContext? = null,
    @SerializedName("status")
    val status: Int = 1, // Active
)
