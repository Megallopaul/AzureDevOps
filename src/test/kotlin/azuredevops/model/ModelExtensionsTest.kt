package azuredevops.model

import azuredevops.model.CreatedBy
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for model extension functions.
 */
class ModelExtensionsTest {
    // region CommentThread Extensions

    @Test
    fun `isActive returns true when status is Active`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Active
            }

        val result = thread.isActive()

        assertTrue(result)
    }

    @Test
    fun `isActive returns true when status is Pending`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Pending
            }

        val result = thread.isActive()

        assertTrue(result)
    }

    @Test
    fun `isActive returns false when status is Fixed`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Fixed
            }

        val result = thread.isActive()

        assertFalse(result)
    }

    @Test
    fun `isActive returns false when status is Closed`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Closed
            }

        val result = thread.isActive()

        assertFalse(result)
    }

    @Test
    fun `isResolved returns true when thread is not active`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Fixed
            }

        val result = thread.isResolved()

        assertTrue(result)
    }

    @Test
    fun `isResolved returns false when thread is active`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Active
            }

        val result = thread.isResolved()

        assertFalse(result)
    }

    @Test
    fun `isSystemGenerated returns true when commentType is system`() {
        val comment =
            mockk<Comment> {
                every { commentType } returns "system"
                every { author } returns null
            }
        val thread =
            mockk<CommentThread> {
                every { comments } returns listOf(comment)
            }

        val result = thread.isSystemGenerated()

        assertTrue(result)
    }

    @Test
    fun `isSystemGenerated returns true when author is null`() {
        val comment =
            mockk<Comment> {
                every { commentType } returns null
                every { author } returns null
            }
        val thread =
            mockk<CommentThread> {
                every { comments } returns listOf(comment)
            }

        val result = thread.isSystemGenerated()

        assertTrue(result)
    }

    @Test
    fun `isSystemGenerated returns false when thread has human author`() {
        val author =
            mockk<CreatedBy> {
                every { displayName } returns "John Doe"
            }
        val comment =
            mockk<Comment> {
                every { commentType } returns null
                every { author } returns author
            }
        val thread =
            mockk<CommentThread> {
                every { comments } returns listOf(comment)
            }

        val result = thread.isSystemGenerated()

        assertFalse(result)
    }

    @Test
    fun `isSystemGenerated returns false when comments list is empty`() {
        val thread =
            mockk<CommentThread> {
                every { comments } returns emptyList()
            }

        val result = thread.isSystemGenerated()

        assertTrue(result) // Returns false because firstOrNull() is null
    }

    @Test
    fun `getDisplayStatus returns Resolved for fixed thread`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Fixed
                every { isActive() } returns false
            }

        val result = thread.getDisplayStatus()

        assertEquals("✓ Resolved", result)
    }

    @Test
    fun `getDisplayStatus returns Pending for pending thread`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Pending
                every { isActive() } returns true
            }

        val result = thread.getDisplayStatus()

        assertEquals("⏳ Pending", result)
    }

    @Test
    fun `getDisplayStatus returns Active for active thread`() {
        val thread =
            mockk<CommentThread> {
                every { status } returns ThreadStatus.Active
                every { isActive() } returns true
            }

        val result = thread.getDisplayStatus()

        assertEquals("⚠ Active", result)
    }

    // endregion

    // region PullRequest Extensions

    @Test
    fun `PullRequest isActive returns true when status is Active`() {
        val pr =
            mockk<PullRequest> {
                every { status } returns PullRequestStatus.Active
            }

        val result = pr.isActive()

        assertTrue(result)
    }

    @Test
    fun `PullRequest isActive returns false when status is Completed`() {
        val pr =
            mockk<PullRequest> {
                every { status } returns PullRequestStatus.Completed
            }

        val result = pr.isActive()

        assertFalse(result)
    }

    @Test
    fun `PullRequest isMerged returns true when status is Completed`() {
        val pr =
            mockk<PullRequest> {
                every { status } returns PullRequestStatus.Completed
            }

        val result = pr.isMerged()

        assertTrue(result)
    }

    @Test
    fun `PullRequest isAbandoned returns true when status is Abandoned`() {
        val pr =
            mockk<PullRequest> {
                every { status } returns PullRequestStatus.Abandoned
            }

        val result = pr.isAbandoned()

        assertTrue(result)
    }

    @Test
    fun `PullRequest isCreatedByUser returns true when user matches`() {
        val createdBy =
            mockk<CreatedBy> {
                every { uniqueName } returns "user-123"
            }
        val pr =
            mockk<PullRequest> {
                every { createdBy } returns createdBy
            }

        val result = pr.isCreatedByUser("user-123")

        assertTrue(result)
    }

    @Test
    fun `PullRequest isCreatedByUser returns false when user doesn't match`() {
        val createdBy =
            mockk<CreatedBy> {
                every { uniqueName } returns "user-123"
            }
        val pr =
            mockk<PullRequest> {
                every { createdBy } returns createdBy
            }

        val result = pr.isCreatedByUser("user-456")

        assertFalse(result)
    }

    @Test
    fun `PullRequest isCreatedByUser returns false when userId is null`() {
        val pr =
            mockk<PullRequest> {
                every { createdBy } returns null
            }

        val result = pr.isCreatedByUser(null)

        assertFalse(result)
    }

    // endregion

    // region TeamIteration Extensions

    @Test
    fun `TeamIteration getDisplayName returns name with asterisk when current`() {
        val attributes =
            mockk<IterationAttributes> {
                every { timeFrame } returns "current"
            }
        val iteration =
            mockk<TeamIteration> {
                every { name } returns "Sprint 1"
                every { attributes } returns attributes
                every { isCurrent() } returns true
            }

        val result = iteration.getDisplayName()

        assertEquals("* Sprint 1", result)
    }

    @Test
    fun `TeamIteration getDisplayName returns name without asterisk when not current`() {
        val attributes =
            mockk<IterationAttributes> {
                every { timeFrame } returns "past"
            }
        val iteration =
            mockk<TeamIteration> {
                every { name } returns "Sprint 1"
                every { attributes } returns attributes
                every { isCurrent() } returns false
            }

        val result = iteration.getDisplayName()

        assertEquals("Sprint 1", result)
    }

    @Test
    fun `TeamIteration getDisplayName returns empty string when name is null`() {
        val attributes =
            mockk<IterationAttributes> {
                every { timeFrame } returns "past"
            }
        val iteration =
            mockk<TeamIteration> {
                every { name } returns null
                every { attributes } returns attributes
                every { isCurrent() } returns false
            }

        val result = iteration.getDisplayName()

        assertEquals("", result)
    }

    // endregion
}
