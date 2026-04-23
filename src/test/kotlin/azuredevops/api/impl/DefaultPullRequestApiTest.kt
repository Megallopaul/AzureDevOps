package azuredevops.api.impl

import azuredevops.http.HttpClient
import azuredevops.model.PullRequest
import azuredevops.model.PullRequestStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Unit tests for DefaultPullRequestApi.
 *
 * These tests verify the business logic without making actual HTTP calls.
 * HttpClient is mocked to isolate the API implementation logic.
 */
class DefaultPullRequestApiTest {
    private lateinit var httpClient: HttpClient
    private lateinit var pullRequestApi: DefaultPullRequestApi

    @BeforeEach
    fun setup() {
        httpClient = mockk()
        // Note: In a real scenario, we would mock the Project dependency too
        // For now, we test the logic that doesn't require Project
        pullRequestApi =
            DefaultPullRequestApi(
                project = mockk(relaxed = true),
                httpClient = httpClient,
            )
    }

    // region findActivePullRequest Tests

    @Test
    fun `findActivePullRequest returns PR when branch names match`() {
        // Given
        val mockPRs =
            listOf(
                createMockPullRequest(1, "refs/heads/feature/test", "refs/heads/main"),
                createMockPullRequest(2, "refs/heads/feature/other", "refs/heads/main"),
            )
        every { pullRequestApi.getPullRequests("active", 100) } returns mockPRs

        // When
        val result = pullRequestApi.findActivePullRequest("refs/heads/feature/test", "refs/heads/main")

        // Then
        assertNotNull(result)
        assertEquals(1, result?.pullRequestId)
    }

    @Test
    fun `findActivePullRequest returns null when no PR matches branches`() {
        // Given
        val mockPRs =
            listOf(
                createMockPullRequest(1, "refs/heads/feature/other", "refs/heads/main"),
            )
        every { pullRequestApi.getPullRequests("active", 100) } returns mockPRs

        // When
        val result = pullRequestApi.findActivePullRequest("refs/heads/feature/test", "refs/heads/main")

        // Then
        assertNull(result)
    }

    @Test
    fun `findActivePullRequest returns null when getPullRequests returns empty list`() {
        // Given
        every { pullRequestApi.getPullRequests("active", 100) } returns emptyList()

        // When
        val result = pullRequestApi.findActivePullRequest("refs/heads/feature/test", "refs/heads/main")

        // Then
        assertNull(result)
    }

    // endregion

    // region findPullRequestForBranch Tests

    @Test
    fun `findPullRequestForBranch returns PR when target branch matches`() {
        // Given
        val mockPRs =
            listOf(
                createMockPullRequest(1, "refs/heads/feature/test", "refs/heads/main"),
                createMockPullRequest(2, "refs/heads/feature/other", "refs/heads/develop"),
            )
        every { pullRequestApi.getPullRequests("active", 100) } returns mockPRs

        // When
        val result = pullRequestApi.findPullRequestForBranch("refs/heads/main")

        // Then
        assertNotNull(result)
        assertEquals(1, result?.pullRequestId)
    }

    @Test
    fun `findPullRequestForBranch returns first match when multiple PRs target same branch`() {
        // Given
        val mockPRs =
            listOf(
                createMockPullRequest(1, "refs/heads/feature/first", "refs/heads/main"),
                createMockPullRequest(2, "refs/heads/feature/second", "refs/heads/main"),
            )
        every { pullRequestApi.getPullRequests("active", 100) } returns mockPRs

        // When
        val result = pullRequestApi.findPullRequestForBranch("refs/heads/main")

        // Then
        assertNotNull(result)
        assertEquals(1, result?.pullRequestId) // Returns first match
    }

    @Test
    fun `findPullRequestForBranch returns null when no PR targets branch`() {
        // Given
        val mockPRs =
            listOf(
                createMockPullRequest(1, "refs/heads/feature/test", "refs/heads/develop"),
            )
        every { pullRequestApi.getPullRequests("active", 100) } returns mockPRs

        // When
        val result = pullRequestApi.findPullRequestForBranch("refs/heads/main")

        // Then
        assertNull(result)
    }

    // endregion

    // region Helper Methods

    private fun createMockPullRequest(
        id: Int,
        sourceBranch: String,
        targetBranch: String,
    ): PullRequest =
        mockk {
            every { pullRequestId } returns id
            every { sourceRefName } returns sourceBranch
            every { targetRefName } returns targetBranch
            every { status } returns PullRequestStatus.Active
            every { title } returns "Test PR #$id"
            every { createdBy } returns null
            every { repository } returns null
        }

    // endregion
}
