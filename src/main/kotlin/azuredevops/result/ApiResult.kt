package azuredevops.result

/**
 * Unified error handling for Azure DevOps API operations.
 *
 * This sealed class represents all possible error states when calling the API.
 * Using a sealed class enables exhaustive pattern matching with `when` expressions.
 *
 * Example usage:
 * ```kotlin
 * when (val result = api.getPullRequests()) {
 *     is ApiResult.Success -> display(result.data)
 *     is ApiResult.Error -> when (result.error) {
 *         is ApiError.Unauthorized -> showLoginScreen()
 *         is ApiError.NotFound -> showMessage("Resource not found")
 *         is ApiError.NetworkFailure -> showRetryButton()
 *         is ApiError.ServerError -> showErrorMessage(error.message)
 *     }
 * }
 * ```
 */
sealed class ApiError {
    /**
     * Authentication or authorization failed.
     * HTTP 401 Unauthorized or 403 Forbidden.
     * User needs to re-authenticate or check permissions.
     */
    object Unauthorized : ApiError()

    /**
     * Resource not found.
     * HTTP 404 Not Found.
     * The requested PR, work item, or repository doesn't exist.
     */
    object NotFound : ApiError()

    /**
     * Bad request due to invalid parameters.
     * HTTP 400 Bad Request.
     * Client-side error in request format or parameters.
     */
    data class BadRequest(
        val message: String,
    ) : ApiError()

    /**
     * Network failure - couldn't reach the server.
     * Includes timeouts, DNS failures, no internet connection.
     */
    data class NetworkFailure(
        val cause: Throwable,
    ) : ApiError()

    /**
     * Server-side error.
     * HTTP 5xx status codes.
     * Azure DevOps service is temporarily unavailable.
     */
    data class ServerError(
        val code: Int,
        val message: String,
    ) : ApiError()

    /**
     * Rate limit exceeded.
     * HTTP 429 Too Many Requests.
     * Client should retry after the specified delay.
     */
    data class RateLimitExceeded(
        val retryAfterSeconds: Long? = null,
    ) : ApiError()

    /**
     * Conflict - request conflicts with current state.
     * HTTP 409 Conflict.
     * E.g., trying to create a branch that already exists.
     */
    data class Conflict(
        val message: String,
    ) : ApiError()

    /**
     * Unknown or unhandled error.
     * Fallback for any error that doesn't fit other categories.
     */
    data class Unknown(
        val message: String,
        val cause: Throwable? = null,
    ) : ApiError()

    /**
     * Converts an HTTP status code to the appropriate ApiError.
     */
    companion object {
        fun fromHttpStatusCode(
            statusCode: Int,
            message: String,
            cause: Throwable? = null,
        ): ApiError =
            when (statusCode) {
                401, 403 -> Unauthorized
                404 -> NotFound
                400 -> BadRequest(message)
                409 -> Conflict(message)
                429 -> RateLimitExceeded()
                in 500..599 -> ServerError(statusCode, message)
                else -> Unknown(message, cause)
            }
    }
}

/**
 * Result of an API operation.
 * Either contains the successful data or an error.
 *
 * @param T The type of data returned on success
 */
sealed class ApiResult<T> {
    /**
     * Successful API call with data.
     */
    data class Success<T>(
        val data: T,
    ) : ApiResult<T>()

    /**
     * Failed API call with error details.
     */
    data class Error(
        val error: ApiError,
    ) : ApiResult<Nothing>()

    /**
     * Returns true if this is a success result.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Returns true if this is an error result.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Returns the data if success, null if error.
     */
    fun getOrNull(): T? =
        when (this) {
            is Success -> data
            is Error -> null
        }

    /**
     * Returns the data if success, throws exception if error.
     */
    fun getOrThrow(): T =
        when (this) {
            is Success -> data
            is Error -> throw ApiException(error)
        }

    /**
     * Returns the data if success, or the result of [onFailure] if error.
     */
    inline fun getOrElse(onFailure: (ApiError) -> T): T =
        when (this) {
            is Success -> data
            is Error -> onFailure(error)
        }

    /**
     * Returns a new ApiResult with the transformed data if success,
     * or the same error if error.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <R> map(transform: (T) -> R): ApiResult<R> =
        when (this) {
            is Success -> Success(transform(data))
            is Error -> this as ApiResult<R>
        }

    /**
     * Executes [action] if this is a success, returns this result.
     * Useful for side effects.
     */
    inline fun onEach(action: (T) -> Unit): ApiResult<T> =
        when (this) {
            is Success -> {
                action(data)
                this
            }
            is Error -> this
        }

    /**
     * Executes [action] if this is an error, returns this result.
     * Useful for logging or error handling side effects.
     */
    inline fun onError(action: (ApiError) -> Unit): ApiResult<T> =
        when (this) {
            is Success -> this
            is Error -> {
                action(error)
                this
            }
        }

    /**
     * Converts ApiResult<T> to ApiResult<R> using [transform] for success,
     * or returns error using [errorTransform] for errors.
     */
    inline fun <R> fold(
        transform: (T) -> R,
        errorTransform: (ApiError) -> R,
    ): R =
        when (this) {
            is Success -> transform(data)
            is Error -> errorTransform(error)
        }
}

/**
 * Exception wrapper for ApiError.
 * Used when exception-based error handling is required (e.g., interop with Java).
 */
class ApiException(
    val apiError: ApiError,
) : Exception(
        when (apiError) {
            is ApiError.Unauthorized -> "Authentication failed"
            is ApiError.NotFound -> "Resource not found"
            is ApiError.BadRequest -> "Bad request: ${apiError.message}"
            is ApiError.NetworkFailure -> "Network error: ${apiError.cause?.message}"
            is ApiError.ServerError -> "Server error (${apiError.code}): ${apiError.message}"
            is ApiError.RateLimitExceeded -> "Rate limit exceeded"
            is ApiError.Conflict -> "Conflict: ${apiError.message}"
            is ApiError.Unknown -> "Unknown error: ${apiError.message}"
        },
        if (apiError is ApiError.NetworkFailure) apiError.cause else null,
    )

/**
 * Type alias for Kotlin's built-in Result type with ApiError.
 * This provides a familiar API while maintaining compatibility with Kotlin idioms.
 */
typealias ApiResultAlias<T> = kotlin.Result<T>

/**
 * Extension function to convert kotlin.Result to ApiResult.
 * Useful when migrating from exception-based to result-based error handling.
 */
@Suppress("UNCHECKED_CAST")
fun <T> Result<T>.toApiResult(): ApiResult<T> =
    fold(
        onSuccess = { ApiResult.Success(it) },
        onFailure = { exception ->
            val apiError =
                when (exception) {
                    is ApiException -> exception.apiError
                    is java.net.UnknownHostException,
                    is java.net.ConnectException,
                    is java.net.SocketTimeoutException,
                    -> ApiError.NetworkFailure(exception)
                    else -> ApiError.Unknown(exception.message ?: "Unknown error", exception)
                }
            ApiResult.Error(apiError) as ApiResult<T>
        },
    )

/**
 * Extension function to convert ApiResult to kotlin.Result.
 * Useful for interop with Kotlin coroutines and Flow.
 */
fun <T> ApiResult<T>.toResult(): Result<T> =
    when (this) {
        is ApiResult.Success -> Result.success(data)
        is ApiResult.Error -> Result.failure(ApiException(error))
    }
