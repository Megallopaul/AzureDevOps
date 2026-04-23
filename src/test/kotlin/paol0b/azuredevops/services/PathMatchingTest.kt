package paol0b.azuredevops.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import paol0b.azuredevops.model.CommentThread
import paol0b.azuredevops.model.LineInfo
import paol0b.azuredevops.model.ThreadContext
import paol0b.azuredevops.model.ThreadStatus

/**
 * Tests de caractérisation pour la logique de path matching
 *
 * Objectif : Documenter et tester le comportement actuel de correspondance
 * entre les chemins de threads Azure DevOps et les chemins de fichiers locaux.
 *
 * Contexte :
 * - Azure DevOps renvoie des chemins relatifs au repo (ex: /src/main.kt)
 * - Les fichiers locaux ont des chemins absolus (ex: C:\project\src\main.kt)
 * - La normalisation est cruciale pour afficher les commentaires au bon endroit
 */
class PathMatchingTest {
    @Test
    fun `test basic path matching with unix thread path and windows file path`() {
        // Cas nominal : thread path Unix-style, file path Windows-style
        val threadPath = "/src/main.kt"
        val filePath = "C:\\project\\src\\main.kt"

        val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
        val normalizedFilePath = filePath.replace('/', '\\')
        val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

        assertTrue("Unix thread path should match Windows file path", matches)
        assertEquals("src\\main.kt", normalizedThreadPath)
    }

    @Test
    fun `test path matching with nested directories`() {
        // Chemin profond avec plusieurs niveaux de dossiers
        val threadPath = "/src/main/java/com/example/MyClass.java"
        val filePath = "C:\\Users\\dev\\project\\src\\main\\java\\com\\example\\MyClass.java"

        val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
        val normalizedFilePath = filePath.replace('/', '\\')
        val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

        assertTrue("Nested path should match", matches)
    }

    @Test
    fun `test path matching is case insensitive`() {
        // La comparaison doit être case-insensitive
        val threadPath = "/SRC/Main.KT"
        val filePath = "C:\\project\\src\\main.kt"

        val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
        val normalizedFilePath = filePath.replace('/', '\\')
        val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

        assertTrue("Path matching should be case insensitive", matches)
    }

    @Test
    fun `test path matching with mismatch should return false`() {
        // Chemins différents doivent retourner false
        val threadPath = "/src/main.kt"
        val filePath = "C:\\project\\src\\test.kt"

        val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
        val normalizedFilePath = filePath.replace('/', '\\')
        val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

        assertFalse("Different paths should not match", matches)
    }

    @Test
    fun `test path matching when file is in subdirectory of repo root`() {
        // Fichier à la racine du repo
        val threadPath = "/README.md"
        val filePath = "C:\\project\\README.md"

        val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
        val normalizedFilePath = filePath.replace('/', '\\')
        val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

        assertTrue("Root file path should match", matches)
    }

    @Test
    fun `test path matching with leading slash variations`() {
        // Tester différentes variations de slash initiaux
        val testCases =
            listOf(
                "/src/main.kt" to "C:\\src\\main.kt",
                "src/main.kt" to "C:\\src\\main.kt",
                "//src/main.kt" to "C:\\src\\main.kt",
            )

        testCases.forEach { (threadPath, filePath) ->
            val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
            val normalizedFilePath = filePath.replace('/', '\\')
            val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

            assertTrue("Path '$threadPath' should match '$filePath'", matches)
        }
    }

    @Test
    fun `test path matching with Unix-style file paths`() {
        // Supporter aussi les chemins Unix (Linux/Mac)
        val threadPath = "/src/main.kt"
        val filePath = "/home/user/project/src/main.kt"

        val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
        val normalizedFilePath = filePath.replace('/', '\\')
        val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

        assertTrue("Unix file paths should also match", matches)
    }

    @Test
    fun `test path matching with spaces in paths`() {
        // Chemins avec espaces (fréquents dans les projets réels)
        val threadPath = "/src/My File.kt"
        val filePath = "C:\\My Project\\src\\My File.kt"

        val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
        val normalizedFilePath = filePath.replace('/', '\\')
        val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)

        assertTrue("Paths with spaces should match", matches)
    }

    @Test
    fun `test getFilePath from CommentThread prioritizes pullRequestThreadContext`() {
        // Documentation : getFilePath() cherche d'abord dans pullRequestThreadContext,
        // puis fallback sur threadContext

        val context1 =
            ThreadContext(
                filePath = "/src/from-pullrequest-context.kt",
                rightFileStart = LineInfo(line = 10, offset = 1),
                rightFileEnd = null,
                leftFileStart = null,
                leftFileEnd = null,
            )

        val context2 =
            ThreadContext(
                filePath = "/src/from-thread-context.kt",
                rightFileStart = LineInfo(line = 20, offset = 1),
                rightFileEnd = null,
                leftFileStart = null,
                leftFileEnd = null,
            )

        val thread =
            CommentThread(
                id = 1,
                pullRequestThreadContext = context1,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = context2,
                isDeleted = false,
            )

        val filePath = thread.getFilePath()

        assertEquals("Should prioritize pullRequestThreadContext", "/src/from-pullrequest-context.kt", filePath)
    }

    @Test
    fun `test getFilePath falls back to threadContext when pullRequestThreadContext is null`() {
        val context =
            ThreadContext(
                filePath = "/src/from-thread-context.kt",
                rightFileStart = LineInfo(line = 20, offset = 1),
                rightFileEnd = null,
                leftFileStart = null,
                leftFileEnd = null,
            )

        val thread =
            CommentThread(
                id = 1,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = context,
                isDeleted = false,
            )

        val filePath = thread.getFilePath()

        assertEquals("Should fallback to threadContext", "/src/from-thread-context.kt", filePath)
    }

    @Test
    fun `test getFilePath returns null when both contexts are null`() {
        val thread =
            CommentThread(
                id = 1,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = null,
                isDeleted = false,
            )

        val filePath = thread.getFilePath()

        assertNull("Should return null when no context", filePath)
    }

    @Test
    fun `test getRightFileStart prioritizes pullRequestThreadContext`() {
        val context1 =
            ThreadContext(
                filePath = null,
                rightFileStart = LineInfo(line = 10, offset = 1),
                rightFileEnd = null,
                leftFileStart = null,
                leftFileEnd = null,
            )

        val context2 =
            ThreadContext(
                filePath = null,
                rightFileStart = LineInfo(line = 99, offset = 1),
                rightFileEnd = null,
                leftFileStart = null,
                leftFileEnd = null,
            )

        val thread =
            CommentThread(
                id = 1,
                pullRequestThreadContext = context1,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = context2,
                isDeleted = false,
            )

        val line = thread.getRightFileStart()

        assertEquals("Should prioritize pullRequestThreadContext line", 10, line)
    }

    @Test
    fun `test isActive and isResolved helpers`() {
        // isActive() = status == Active OR status == Pending
        // isResolved() = !isActive()

        val activeThread =
            CommentThread(
                id = 1,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = null,
                isDeleted = false,
            )

        val pendingThread =
            CommentThread(
                id = 2,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Pending,
                threadContext = null,
                isDeleted = false,
            )

        val fixedThread =
            CommentThread(
                id = 3,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Fixed,
                threadContext = null,
                isDeleted = false,
            )

        assertTrue("Active status should be active", activeThread.isActive())
        assertFalse("Active status should not be resolved", activeThread.isResolved())

        assertTrue("Pending status should be active", pendingThread.isActive())
        assertFalse("Pending status should not be resolved", pendingThread.isResolved())

        assertFalse("Fixed status should not be active", fixedThread.isActive())
        assertTrue("Fixed status should be resolved", fixedThread.isResolved())
    }

    @Test
    fun `test isDeleted filtering pattern`() {
        // ⚠️ Pattern critique : TOUJOURS filtrer isDeleted != true
        // Documenté dans feedback/comment_deleted_files.md
        //
        // Azure DevOps marque isDeleted = true quand :
        // - Le fichier a été supprimé
        // - Le fichier a été déplacé
        // - Le thread a été supprimé manuellement

        val activeThread =
            CommentThread(
                id = 1,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = null,
                isDeleted = false,
            )

        val deletedThread =
            CommentThread(
                id = 2,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = null,
                isDeleted = true,
            )

        val nullDeletedThread =
            CommentThread(
                id = 3,
                pullRequestThreadContext = null,
                comments = null,
                status = ThreadStatus.Active,
                threadContext = null,
                isDeleted = null,
            )

        // Pattern de filtrage correct
        val threads = listOf(activeThread, deletedThread, nullDeletedThread)
        val filteredThreads = threads.filter { it.isDeleted != true }

        assertEquals("Should filter out deleted threads", 2, filteredThreads.size)
        assertTrue("Active thread should be included", filteredThreads.contains(activeThread))
        assertTrue("Null deleted thread should be included", filteredThreads.contains(nullDeletedThread))
        assertFalse("Deleted thread should be excluded", filteredThreads.contains(deletedThread))
    }

    @Test
    fun `test combined filtering pattern for PR comments`() {
        // Pattern complet utilisé dans DiffViewerPanel, CommentsNavigatorToolWindow, TimelineModels :
        // filter { it.isDeleted != true && it.isActive() }

        val threads =
            listOf(
                CommentThread(
                    id = 1,
                    pullRequestThreadContext = null,
                    comments = null,
                    status = ThreadStatus.Active,
                    threadContext = null,
                    isDeleted = false,
                ),
                // ✓ Actif
                CommentThread(
                    id = 2,
                    pullRequestThreadContext = null,
                    comments = null,
                    status = ThreadStatus.Fixed,
                    threadContext = null,
                    isDeleted = false,
                ),
                // ✗ Résolu
                CommentThread(
                    id = 3,
                    pullRequestThreadContext = null,
                    comments = null,
                    status = ThreadStatus.Active,
                    threadContext = null,
                    isDeleted = true,
                ),
                // ✗ Supprimé
                CommentThread(
                    id = 4,
                    pullRequestThreadContext = null,
                    comments = null,
                    status = ThreadStatus.Pending,
                    threadContext = null,
                    isDeleted = false,
                ),
                // ✓ Pending
                CommentThread(
                    id = 5,
                    pullRequestThreadContext = null,
                    comments = null,
                    status = ThreadStatus.Closed,
                    threadContext = null,
                    isDeleted = false,
                ), // ✗ Résolu
            )

        // Pattern de filtrage complet
        val activeNonDeletedThreads = threads.filter { it.isDeleted != true && it.isActive() }

        assertEquals("Should keep only active, non-deleted threads", 2, activeNonDeletedThreads.size)
        assertTrue("Active thread should be included", activeNonDeletedThreads.any { it.id == 1 })
        assertTrue("Pending thread should be included", activeNonDeletedThreads.any { it.id == 4 })
    }
}
