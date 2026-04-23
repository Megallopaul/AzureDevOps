package paol0b.azuredevops.api

import paol0b.azuredevops.model.User
import paol0b.azuredevops.services.AzureDevOpsApiException

/**
 * API interface for Authentication operations
 */
interface AuthenticationApi {
    /**
     * Gets the current authenticated user from Azure DevOps
     * Uses the connectionData endpoint to retrieve the authenticated user's identity
     * @return Current User with id, displayName, and uniqueName
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getCurrentUser(): User

    /**
     * Gets the current user ID with caching to avoid repeated API calls
     * @return User ID or null if user cannot be retrieved
     */
    fun getCurrentUserIdCached(): String?

    /**
     * Validates that credentials are properly configured
     * @throws AzureDevOpsApiException if credentials are invalid
     */
    @Throws(AzureDevOpsApiException::class)
    fun validateCredentials()
}
