package azuredevops.http

import azuredevops.services.AzureDevOpsApiException
import azuredevops.services.AzureDevOpsConfigService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * HTTP client for executing raw HTTP requests to Azure DevOps API.
 * Handles authentication, request execution, and error handling.
 *
 * This class wraps OkHttp and provides Azure DevOps-specific functionality.
 */
class HttpClient(
    private val project: Project,
) {
    private val logger = Logger.getInstance(HttpClient::class.java)
    private val okHttpClient = OkHttpClient()
    private val gson = com.google.gson.Gson()

    companion object {
        private const val API_VERSION = "7.0"
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    }

    /**
     * Executes a GET request and returns the response body as a string.
     *
     * @param url The full URL to request
     * @param token The authentication token (PAT or OAuth)
     * @return Response body as string
     * @throws AzureDevOpsApiException if the request fails
     */
    @Throws(AzureDevOpsApiException::class)
    fun executeGet(url: String, token: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .addHeader("Authorization", createAuthHeader(token))
                .addHeader("Accept", "application/json")
                .build()

        return executeRequest(request)
    }

    /**
     * Executes a POST request with a JSON body.
     *
     * @param url The full URL to request
     * @param body The body object to serialize to JSON
     * @param token The authentication token
     * @return Response body as string
     * @throws AzureDevOpsApiException if the request fails
     */
    @Throws(AzureDevOpsApiException::class)
    fun executePost(url: String, body: Any, token: String): String {
        val jsonBody = gson.toJson(body)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

        val request =
            Request
                .Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", createAuthHeader(token))
                .addHeader("Accept", "application/json")
                .build()

        return executeRequest(request)
    }

    /**
     * Executes a PUT request with a JSON body.
     *
     * @param url The full URL to request
     * @param body The body object to serialize to JSON
     * @param token The authentication token
     * @return Response body as string
     * @throws AzureDevOpsApiException if the request fails
     */
    @Throws(AzureDevOpsApiException::class)
    fun executePut(url: String, body: Any, token: String): String {
        val jsonBody = gson.toJson(body)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

        val request =
            Request
                .Builder()
                .url(url)
                .put(requestBody)
                .addHeader("Authorization", createAuthHeader(token))
                .addHeader("Accept", "application/json")
                .build()

        return executeRequest(request)
    }

    /**
     * Executes a PATCH request with a JSON body.
     *
     * @param url The full URL to request
     * @param body The body object to serialize to JSON
     * @param token The authentication token
     * @return Response body as string
     * @throws AzureDevOpsApiException if the request fails
     */
    @Throws(AzureDevOpsApiException::class)
    fun executePatch(url: String, body: Any, token: String): String {
        val jsonBody = gson.toJson(body)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

        val request =
            Request
                .Builder()
                .url(url)
                .patch(requestBody)
                .addHeader("Authorization", createAuthHeader(token))
                .addHeader("Accept", "application/json")
                .build()

        return executeRequest(request)
    }

    /**
     * Executes a DELETE request.
     *
     * @param url The full URL to request
     * @param token The authentication token
     * @return Response body as string
     * @throws AzureDevOpsApiException if the request fails
     */
    @Throws(AzureDevOpsApiException::class)
    fun executeDelete(url: String, token: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", createAuthHeader(token))
                .addHeader("Accept", "application/json")
                .build()

        return executeRequest(request)
    }

    /**
     * Executes a generic HTTP request.
     *
     * @param request The OkHttp request to execute
     * @return Response body as string
     * @throws AzureDevOpsApiException if the request fails
     */
    @Throws(AzureDevOpsApiException::class)
    private fun executeRequest(request: Request): String =
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()

                if (response.isSuccessful) {
                    responseBody
                } else {
                    throw handleErrorResponse(response.code, responseBody, request.url.toString())
                }
            }
        } catch (e: IOException) {
            throw AzureDevOpsApiException("Network error: ${e.message}", e)
        } catch (e: AzureDevOpsApiException) {
            throw e
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Request failed: ${e.message}", e)
        }

    /**
     * Handles HTTP error responses.
     *
     * @param statusCode The HTTP status code
     * @param errorBody The error response body
     * @param url The request URL for logging
     * @return AzureDevOpsApiException with appropriate message
     */
    private fun handleErrorResponse(
        statusCode: Int,
        errorBody: String,
        url: String,
    ): AzureDevOpsApiException {
        logger.warn("Azure DevOps API error - Status: $statusCode, URL: $url, Body: $errorBody")

        val errorMessage =
            try {
                val error = gson.fromJson(errorBody, azuredevops.model.AzureDevOpsErrorResponse::class.java)
                error?.message ?: "Unknown error"
            } catch (e: Exception) {
                logger.warn("Failed to parse error response", e)
                errorBody.ifEmpty { "HTTP Error $statusCode" }
            }

        return when (statusCode) {
            401, 403 -> AzureDevOpsApiException("Authentication failed: $errorMessage")
            404 -> AzureDevOpsApiException("Resource not found: $errorMessage")
            400 -> AzureDevOpsApiException("Bad request: $errorMessage")
            500 -> AzureDevOpsApiException("Server error: $errorMessage")
            else -> AzureDevOpsApiException("API error ($statusCode): $errorMessage")
        }
    }

    /**
     * Creates an authorization header value from a token.
     * Handles both PAT (Basic auth) and OAuth (Bearer token) formats.
     *
     * @param token The authentication token
     * @return Authorization header value
     */
    fun createAuthHeader(token: String): String {
        if (isJwtToken(token)) {
            return "Bearer $token"
        } else {
            val credentials = ":$token"
            val encodedCredentials =
                Base64.getEncoder().encodeToString(
                    credentials.toByteArray(StandardCharsets.UTF_8),
                )
            return "Basic $encodedCredentials"
        }
    }

    /**
     * Detects whether the token is a JWT (OAuth) token.
     * JWT format: header.payload.signature — all three parts are non-empty
     * and the header is a base64-encoded JSON object starting with "eyJ".
     */
    private fun isJwtToken(token: String): Boolean {
        if (!token.startsWith("eyJ")) return false
        val parts = token.split(".")
        return parts.size == 3 && parts.all { it.isNotEmpty() }
    }

    /**
     * Builds a URL-encoded path segment.
     *
     * @param value The path segment to encode
     * @return URL-encoded path segment
     */
    fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    /**
     * Gets the API base URL from configuration.
     *
     * @return API base URL
     */
    fun getApiBaseUrl(): String {
        val configService = AzureDevOpsConfigService.getInstance(project)
        return configService.getApiBaseUrl()
    }

    /**
     * Gets the current organization name from configuration.
     *
     * @return Organization name
     */
    fun getOrganization(): String {
        val configService = AzureDevOpsConfigService.getInstance(project)
        val config = configService.getConfig()
        return config.organization
    }

    /**
     * Executes a PATCH request with a JSON Patch body.
     *
     * @param url The full URL to request
     * @param operations List of JSON patch operations
     * @param token The authentication token
     * @param method HTTP method (POST or PATCH)
     * @return Response body as string
     * @throws AzureDevOpsApiException if the request fails
     */
    @Throws(AzureDevOpsApiException::class)
    fun executeJsonPatch(
        url: String,
        operations: List<azuredevops.model.JsonPatchOperation>,
        token: String,
        method: String = "PATCH",
    ): String {
        val jsonBody = gson.toJson(operations)
        val requestBody = jsonBody.toRequestBody("application/json-patch+json".toMediaType())

        val request =
            Request
                .Builder()
                .url(url)
                .method(method, requestBody)
                .addHeader("Authorization", createAuthHeader(token))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json-patch+json")
                .build()

        return executeRequest(request)
    }

    /**
     * Builds a URL for Build API endpoints.
     */
    fun buildBuildApiUrl(
        project: String,
        endpoint: String,
    ): String {
        val encodedProject = encodePathSegment(project)
        val baseUrl = getApiBaseUrl()
        return "$baseUrl/$encodedProject/_apis/build$endpoint"
    }

    /**
     * Builds a URL for Work Item Tracking (WIT) API endpoints.
     */
    fun buildWitApiUrl(
        project: String,
        endpoint: String,
    ): String {
        val encodedProject = encodePathSegment(project)
        val baseUrl = getApiBaseUrl()
        return "$baseUrl/$encodedProject/_apis/wit$endpoint"
    }

    /**
     * Builds a URL for Work API endpoints.
     */
    fun buildWorkApiUrl(
        project: String,
        team: String,
        endpoint: String,
    ): String {
        val encodedProject = encodePathSegment(project)
        val encodedTeam = encodePathSegment(team)
        val baseUrl = getApiBaseUrl()
        return "$baseUrl/$encodedProject/$encodedTeam/_apis/work$endpoint"
    }

    /**
     * Builds a general API URL with project and repository scope.
     */
    fun buildApiUrl(
        project: String,
        repository: String,
        endpoint: String,
    ): String {
        val encodedProject = encodePathSegment(project)
        val encodedRepo = encodePathSegment(repository)
        val baseUrl = getApiBaseUrl()
        return "$baseUrl/$encodedProject/_apis/git/repositories/$encodedRepo$endpoint"
    }
}
