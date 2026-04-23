package paol0b.azuredevops.api

import paol0b.azuredevops.model.JsonPatchOperation
import paol0b.azuredevops.model.TeamIteration
import paol0b.azuredevops.model.WorkItem
import paol0b.azuredevops.model.WorkItemComment
import paol0b.azuredevops.model.WorkItemType
import paol0b.azuredevops.services.AzureDevOpsApiException

/**
 * API interface for Work Item operations
 */
interface WorkItemApi {
    /**
     * Executes a WIQL query and returns the list of work item IDs
     * @param wiql WIQL query string
     * @return List of work item IDs
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun executeWiqlQuery(wiql: String): List<Int>

    /**
     * Get work items by their IDs. Handles the 200-ID limit per request by chunking.
     * @param ids List of work item IDs
     * @param expand Expansion mode (default: "All")
     * @return List of Work Items
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getWorkItemsByIds(
        ids: List<Int>,
        expand: String = "All",
    ): List<WorkItem>

    /**
     * Get a single work item by ID with all fields expanded.
     * @param id Work item ID
     * @return Work Item
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getWorkItem(id: Int): WorkItem

    /**
     * Get work items assigned to the current user
     * @param iterationPath Optional iteration path filter
     * @param state Optional state filter
     * @param type Optional work item type filter
     * @param top Maximum number of work items to retrieve
     * @return List of Work Items
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getMyWorkItems(
        iterationPath: String? = null,
        state: String? = null,
        type: String? = null,
        top: Int = 200,
    ): List<WorkItem>

    /**
     * Get all work items for a project, optionally filtered.
     * @param iterationPath Optional iteration path filter
     * @param state Optional state filter
     * @param type Optional work item type filter
     * @param top Maximum number of work items to retrieve
     * @return List of Work Items
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getAllWorkItems(
        iterationPath: String? = null,
        state: String? = null,
        type: String? = null,
        top: Int = 200,
    ): List<WorkItem>

    /**
     * Get all work items matching a WIQL query (full objects).
     * @param wiql WIQL query string
     * @param top Maximum number of work items to retrieve
     * @return List of Work Items
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getWorkItemsByWiql(
        wiql: String,
        top: Int = 200,
    ): List<WorkItem>

    /**
     * Get available work item types for the current project.
     * @return List of Work Item Types
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getWorkItemTypes(): List<WorkItemType>

    /**
     * Get the current iteration/sprint for the project's default team.
     * @return Current TeamIteration or null if not found
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getCurrentIteration(): TeamIteration?

    /**
     * Get all iterations/sprints for the project's default team.
     * @return List of TeamIteration
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getIterations(): List<TeamIteration>

    /**
     * Create a new work item.
     * @param type Work item type (e.g., "Task", "Bug", "User Story")
     * @param operations List of JSON patch operations for fields
     * @return Created Work Item
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun createWorkItem(
        type: String,
        operations: List<JsonPatchOperation>,
    ): WorkItem

    /**
     * Update an existing work item.
     * @param id Work item ID
     * @param operations List of JSON patch operations for fields
     * @return Updated Work Item
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun updateWorkItem(
        id: Int,
        operations: List<JsonPatchOperation>,
    ): WorkItem

    /**
     * Get comments/discussion for a work item.
     * @param id Work item ID
     * @return List of WorkItemComment
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun getWorkItemComments(id: Int): List<WorkItemComment>

    /**
     * Add a comment to a work item.
     * @param id Work item ID
     * @param text Comment text
     * @return Created WorkItemComment
     * @throws AzureDevOpsApiException on API failure
     */
    @Throws(AzureDevOpsApiException::class)
    fun addWorkItemComment(
        id: Int,
        text: String,
    ): WorkItemComment
}
