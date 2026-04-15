package paol0b.azuredevops.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.net.HttpURLConnection

@Service(Service.Level.PROJECT)
class AzureDevOpsErrorHandler(private val project: Project) {

    private val logger = Logger.getInstance(AzureDevOpsErrorHandler::class.java)

    companion object {
        fun getInstance(project: Project): AzureDevOpsErrorHandler {
            return project.getService(AzureDevOpsErrorHandler::class.java)
        }
    }

    fun handleError(e: Exception, actionDescription: String = "perform action") {
        logger.warn("Failed to $actionDescription", e)

        val message = when (e) {
            is AzureDevOpsApiException -> e.message ?: "Azure DevOps API error"
            else -> "Error while trying to $actionDescription: ${e.message}"
        }

        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(
                project,
                message,
                "Azure DevOps Error"
            )
        }
    }

    fun handleHttpError(statusCode: Int, errorMessage: String): AzureDevOpsApiException {
        logger.warn("Azure DevOps API error - Status: $statusCode, Message: $errorMessage")
        
        return when (statusCode) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> 
                AzureDevOpsApiException("Authentication failed (401). Please login:\n" +
                    "1. Go to File → Settings → Tools → Azure DevOps Accounts\n" +
                    "2. Click 'Add' to login with your Microsoft account\n" +
                    "3. Complete the authentication in your browser")
            HttpURLConnection.HTTP_FORBIDDEN ->
                AzureDevOpsApiException("Insufficient permissions (403). Your account doesn't have access to this resource.\n" +
                    "Please check that you have the required permissions in Azure DevOps.")
            HttpURLConnection.HTTP_NOT_FOUND ->
                AzureDevOpsApiException("Resource not found (404).\n" +
                    "Please verify that the Organization, Project, and Repository names are correct\n" +
                    "and that you have access to them in Azure DevOps.")
            HttpURLConnection.HTTP_CONFLICT ->
                AzureDevOpsApiException("Conflict: $errorMessage (409)")
            HttpURLConnection.HTTP_BAD_REQUEST ->
                AzureDevOpsApiException("Invalid request: $errorMessage (400)")
            else ->
                AzureDevOpsApiException("HTTP Error $statusCode: $errorMessage")
        }
    }
}
