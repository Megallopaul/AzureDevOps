package azuredevops.api.impl

import azuredevops.api.IdentityApi
import azuredevops.http.HttpClient
import azuredevops.model.AzureDevOpsConfig
import azuredevops.model.Identity
import azuredevops.model.PullRequest
import azuredevops.model.PullRequestListResponse
import azuredevops.services.AzureDevOpsApiException
import com.intellij.openapi.project.Project

/**
 * Default implementation of IdentityApi using HttpClient.
 *
 * Note: This implementation uses a pragmatic approach by extracting user information
 * from recent Pull Requests instead of calling the identity API directly, which may
 * require additional permissions.
 */
class DefaultIdentityApi(
    private val project: Project,
    private val httpClient: HttpClient,
    private val pullRequestApi: DefaultPullRequestApi,
) : IdentityApi {
    override fun searchIdentities(searchText: String): List<Identity> {
        try {
            // Strategy: get users from recent PRs (createdBy + reviewers)
            // This uses the same permissions already working for getPullRequests
            val pullRequests = pullRequestApi.getPullRequests(status = "all", top = 100)
            val identitiesMap = mutableMapOf<String, Identity>()

            // Collect all unique users from createdBy and reviewers
            pullRequests.forEach { pr ->
                // Add the PR creator
                pr.createdBy?.let { creator ->
                    creator.id?.let { id ->
                        if (!identitiesMap.containsKey(id)) {
                            identitiesMap[id] =
                                Identity(
                                    id = creator.id,
                                    displayName = creator.displayName,
                                    uniqueName = creator.uniqueName,
                                    imageUrl = creator.imageUrl,
                                    descriptor = null,
                                )
                        }
                    }
                }

                // Add reviewers
                pr.reviewers?.forEach { reviewer ->
                    reviewer.id?.let { id ->
                        if (!identitiesMap.containsKey(id)) {
                            identitiesMap[id] =
                                Identity(
                                    id = reviewer.id,
                                    displayName = reviewer.displayName,
                                    uniqueName = reviewer.uniqueName,
                                    imageUrl = reviewer.imageUrl,
                                    descriptor = null,
                                )
                        }
                    }
                }
            }

            // Filter by search text
            val searchTextLower = searchText.lowercase()
            return identitiesMap.values.filter { identity ->
                identity.displayName?.lowercase()?.contains(searchTextLower) == true ||
                    identity.uniqueName?.lowercase()?.contains(searchTextLower) == true
            }
        } catch (e: Exception) {
            throw AzureDevOpsApiException("Error searching identities: ${e.message}", e)
        }
    }
}
