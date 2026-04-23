package azuredevops.api.impl

import azuredevops.api.AuthenticationApi
import azuredevops.http.HttpClient
import azuredevops.model.AzureDevOpsConfig
import azuredevops.model.User
import azuredevops.services.AzureDevOpsApiException
import azuredevops.services.AzureDevOpsConfigService
import com.intellij.openapi.project.Project

/**
 * Default implementation of AuthenticationApi using HttpClient.
 */
class DefaultAuthenticationApi(
    private val project: Project,
    private val httpClient: HttpClient,
) : AuthenticationApi {
    companion object {
        private const val API_VERSION = "7.0"
    }

    override fun getCurrentUser(): User {
        val config = requireValidConfig()
        val url = "${httpClient.getApiBaseUrl()}/_apis/connectionData?api-version=$API_VERSION"

        return try {
            val response = httpClient.executeGet(url, config.personalAccessToken)
            val connectionData =
                com.google.gson
                    .Gson()
                    .fromJson(response, com.google.gson.JsonObject::class.java)

            val authenticatedUser =
                connectionData.getAsJsonObject("authenticatedUser")
                    ?: throw AzureDevOpsApiException("No authenticatedUser in connectionData")

            val id =
                authenticatedUser.get("id")?.asString
                    ?: throw AzureDevOpsApiException("No user ID in connectionData")
            val displayName = authenticatedUser.get("providerDisplayName")?.asString ?: "Unknown"

            // Get uniqueName from properties if available
            val properties = authenticatedUser.getAsJsonObject("properties")
            val uniqueName = properties?.getAsJsonObject("Account")?.get("\$value")?.asString

            User(
                id = id,
                displayName = displayName,
                uniqueName = uniqueName,
                imageUrl = null,
            )
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error retrieving user identity: ${e.message}", e)
        }
    }

    override fun getCurrentUserIdCached(): String? =
        try {
            getCurrentUser().id
        } catch (e: Exception) {
            null
        }

    override fun validateCredentials() {
        try {
            getCurrentUser()
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Invalid credentials: ${e.message}", e)
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
