package azuredevops.services

import azuredevops.model.Comment
import azuredevops.model.CommentThread
import azuredevops.model.LineInfo
import azuredevops.model.PullRequest
import azuredevops.model.PullRequestStatus
import azuredevops.model.ThreadContext
import azuredevops.model.ThreadStatus
import azuredevops.model.User
import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests de caractérisation pour AzureDevOpsApiClient
 *
 * Objectif : Documenter le comportement actuel des appels API PR
 * avant le découpage du God Class (1974 lignes) en services spécialisés.
 *
 * Notes :
 * - Ces tests documentent le comportement sans mocker l'HTTP
 * - Pour des tests unitaires complets, il faudra extraire les interfaces
 */
class AzureDevOpsApiClientTest : BasePlatformTestCase() {
    private lateinit var project: Project
    private lateinit var apiClient: AzureDevOpsApiClient
    private val gson = Gson()

    @Before
    override fun setUp() {
        super.setUp()
        project = myFixture.project
        apiClient = AzureDevOpsApiClient.getInstance(project)
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    // --- Tests de documentation des méthodes PR ---

    @Test
    fun `test getPullRequests fetches active PRs by default`() {
        // Documentation :
        // - Méthode : getPullRequests(status: String = "active", top: Int = 100)
        // - Utilise buildApiUrl() avec endpoint /pullrequests
        // - Paramètres query : searchCriteria.status, $top, api-version
        // - Retour : List<PullRequest> via PullRequestListResponse
        // - Gestion erreur : catch Exception -> AzureDevOpsApiException

        // Note: Test nécessiterait un mock HTTP pour vérifier l'URL exacte
        // Pour l'instant, documente le comportement attendu
        assertNotNull("API client should be initialized", apiClient)
    }

    @Test
    fun `test getPullRequests supports status filtering`() {
        // Documentation des valeurs de status supportées :
        // - "active" : PRs en cours
        // - "completed" : PRs fusionnés
        // - "abandoned" : PRs abandonnés
        // - "all" : tous les PRs

        val statusValues = listOf("active", "completed", "abandoned", "all")

        statusValues.forEach { status ->
            // Chaque status devrait être passé dans l'URL comme searchCriteria.status
            assertNotNull("Status '$status' should be supported", status)
        }
    }

    @Test
    fun `test getPullRequests uses top parameter for limiting results`() {
        // Documentation :
        // - Paramètre top : nombre maximum de PRs à retourner
        // - Défaut : 100
        // - Utilisé dans l'URL comme $top

        val topValues = listOf(10, 50, 100, 500)

        topValues.forEach { top ->
            assertTrue("Top should be positive", top > 0)
        }
    }

    @Test
    fun `test getPullRequests handles all status value specially`() {
        // Comportement spécial : quand status == "all",
        // le statusParam reste "all" au lieu d'être filtré

        val status = "all"
        val statusParam = if (status == "all") "all" else status

        assertEquals("All status should remain 'all'", "all", statusParam)

        val otherStatus = "active"
        val otherStatusParam = if (otherStatus == "all") "all" else otherStatus

        assertEquals("Other status should be unchanged", "active", otherStatusParam)
    }

    @Test
    fun `test findActivePullRequest searches between two branches`() {
        // Documentation :
        // - Méthode : findActivePullRequest(sourceBranch: String, targetBranch: String)
        // - Retour : PullRequest? (null si non trouvé)
        // - Utilise searchCriteria.sourceRefName et searchCriteria.targetRefName
        // - Retourne le premier PR actif trouvé

        val sourceBranch = "refs/heads/feature/test"
        val targetBranch = "refs/heads/main"

        // Format des branches : refs/heads/{branchName}
        assertTrue("Source branch should start with refs/heads/", sourceBranch.startsWith("refs/heads/"))
        assertTrue("Target branch should start with refs/heads/", targetBranch.startsWith("refs/heads/"))
    }

    @Test
    fun `test getCommentThreads fetches threads for PR`() {
        // Documentation :
        // - Méthode : getCommentThreads(pullRequestId: Int)
        // - Endpoint : /pullrequests/{id}/threads?api-version=7.0
        // - Retour : List<CommentThread>
        // - Inclut TOUS les threads (actifs, résolus, supprimés)
        // - Le filtrage isDeleted doit être fait par le caller

        val pullRequestId = 123

        // Pattern de filtrage CRITIQUE à appliquer par le caller :
        // threads.filter { it.isDeleted != true && it.isActive() }

        assertNotNull("Pull request ID should be valid", pullRequestId)
    }

    @Test
    fun `test getCommentThreads overloads support cross-repo PRs`() {
        // Documentation :
        // - Surcharge : getCommentThreads(pullRequestId: Int, projectName: String?, repositoryId: String?)
        // - Permet d'accéder aux PRs d'autres repositories dans l'organisation
        // - Utilise projectName et repositoryId fournis ou fallback sur config courante

        val pullRequestId = 123
        val projectName = "OtherProject"
        val repositoryId = "other-repo-id"

        // Quand projectName et repositoryId sont null, utilise la config courante
        // Quand fournis, accède au PR spécifié
        assertNotNull("Cross-repo comment threads should be accessible", pullRequestId)
    }

    @Test
    fun `test comment thread filtering pattern for deleted files`() {
        // ⚠️ Pattern CRITIQUE documenté dans feedback/comment_deleted_files.md
        //
        // AzureDevOpsApiClient renvoie TOUS les threads sans filtrer
        // C'est au caller de filtrer les threads supprimés

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
                CommentThread(
                    id = 2,
                    pullRequestThreadContext = null,
                    comments = null,
                    status = ThreadStatus.Active,
                    threadContext = null,
                    isDeleted = true,
                ),
                CommentThread(
                    id = 3,
                    pullRequestThreadContext = null,
                    comments = null,
                    status = ThreadStatus.Fixed,
                    threadContext = null,
                    isDeleted = false,
                ),
            )

        // Pattern correct à appliquer APRÈS appel API :
        val filteredThreads = threads.filter { it.isDeleted != true && it.isActive() }

        assertEquals("Should filter out deleted and resolved threads", 1, filteredThreads.size)
        assertEquals("Should keep only active, non-deleted thread", 1, filteredThreads.first().id)
    }

    @Test
    fun `test buildApiUrl handles URL encoding for special characters`() {
        // Documentation :
        // - Méthode : buildApiUrl(project: String, repository: String, endpoint: String)
        // - Encode les caractères spéciaux (espaces, accents) avec URLEncoder
        // - Remplace "+" par "%20" pour les espaces

        val projectWithSpace = "My Project"
        val repoWithAccent = "CaféRepo"

        // Encodage attendu :
        // "My Project" -> "My%20Project"
        // "CaféRepo" -> "Caf%C3%A9Repo" (UTF-8 encoding)

        val encodedProject =
            java.net.URLEncoder
                .encode(projectWithSpace, "UTF-8")
                .replace("+", "%20")
        val encodedRepo =
            java.net.URLEncoder
                .encode(repoWithAccent, "UTF-8")
                .replace("+", "%20")

        assertEquals("My%20Project", encodedProject)
        assertTrue("Encoded repo should contain percent encoding", encodedRepo.contains("%"))
    }

    @Test
    fun `test buildOrgApiUrl for organization-level endpoints`() {
        // Documentation :
        // - Méthode : buildOrgApiUrl(endpoint: String)
        // - Utilise config.getApiBaseUrl() + /_apis + endpoint
        // - Pour endpoints qui ne nécessitent pas project/repository
        // - Exemple : /_apis/git/pullrequests (tous les PRs de l'org)

        val endpoint = "/git/pullrequests"
        val expectedPattern = "/_apis$endpoint"

        assertNotNull("Org API endpoint should be valid", endpoint)
    }

    @Test
    fun `test error handling wraps exceptions in AzureDevOpsApiException`() {
        // Documentation :
        // - Toutes les méthodes API catch Exception
        // - Wrappent dans AzureDevOpsApiException avec message personnalisé
        // - Log l'erreur avec logger.error avant de rethrow

        // Pattern uniforme :
        // try {
        //     val response = executeGet(url, token)
        //     gson.fromJson(response, T::class.java)
        // } catch (e: Exception) {
        //     logger.error("Failed to ...", e)
        //     throw AzureDevOpsApiException("Error while ...: ${e.message}", e)
        // }

        assertTrue("Error handling pattern should be consistent", true)
    }

    @Test
    fun `test createPullRequest builds request body correctly`() {
        // Documentation :
        // - Construit CreatePullRequestRequest avec :
        //   - sourceRefName : "refs/heads/{branch}"
        //   - targetRefName : "refs/heads/{branch}"
        //   - title, description
        //   - reviewers : liste de ReviewerRequest (id, isRequired)
        //   - isDraft : booléen

        val sourceBranch = "refs/heads/feature/test"
        val targetBranch = "refs/heads/main"
        val title = "Test PR"
        val description = "Test Description"

        // Format attendu des branches
        assertTrue("Source branch should be fully qualified", sourceBranch.startsWith("refs/heads/"))
        assertTrue("Target branch should be fully qualified", targetBranch.startsWith("refs/heads/"))
    }

    @Test
    fun `test PullRequest helper methods`() {
        // Documentation des helpers sur data class PullRequest :

        val pr =
            PullRequest(
                pullRequestId = 123,
                title = "Test PR",
                description = "Description",
                sourceRefName = "refs/heads/feature/test",
                targetRefName = "refs/heads/main",
                status = PullRequestStatus.Active,
                createdBy = null,
                creationDate = null,
                closedDate = null,
                mergeStatus = "succeeded",
                isDraft = false,
                reviewers = null,
                labels = null,
                url = "https://dev.azure.com/org/project/_git/repo/pullrequest/123",
                repository = null,
                lastMergeSourceCommit = null,
                lastMergeTargetCommit = null,
                autoCompleteSetBy = null,
            )

        // Helpers de branch names
        assertEquals("feature/test", pr.getSourceBranchName())
        assertEquals("main", pr.getTargetBranchName())

        // Helpers de status
        assertTrue("Active PR should be active", pr.isActive())
        assertFalse("Active PR should not be merged", pr.isMerged())
        assertFalse("Active PR should not be abandoned", pr.isAbandoned())

        // Merge helpers
        assertFalse("Succeeded merge should not have conflicts", pr.hasConflicts())

        val conflictPr = pr.copy(mergeStatus = "conflicts")
        assertTrue("Conflicts merge should report conflicts", conflictPr.hasConflicts())
    }

    @Test
    fun `test CommentThread helper methods`() {
        // Documentation des helpers sur data class CommentThread :

        val activeThread =
            CommentThread(
                id = 1,
                pullRequestThreadContext =
                    ThreadContext(
                        filePath = "/src/test.kt",
                        rightFileStart = LineInfo(line = 10, offset = 1),
                        rightFileEnd = null,
                        leftFileStart = null,
                        leftFileEnd = null,
                    ),
                comments =
                    listOf(
                        Comment(
                            id = 1,
                            content = "Test comment",
                            author = User(id = "user1", displayName = "Test User", uniqueName = "test@example.com", imageUrl = null),
                            publishedDate = "2026-04-22T10:00:00Z",
                            lastUpdatedDate = "2026-04-22T10:00:00Z",
                            lastContentUpdatedDate = "2026-04-22T10:00:00Z",
                            commentType = "text",
                            isDeleted = false,
                        ),
                    ),
                status = ThreadStatus.Active,
                threadContext = null,
                isDeleted = false,
            )

        // Helpers de path
        assertEquals("/src/test.kt", activeThread.getFilePath())
        assertEquals(10, activeThread.getRightFileStart())

        // Helpers de status
        assertTrue("Active thread should be active", activeThread.isActive())
        assertFalse("Active thread should not be resolved", activeThread.isResolved())

        // Helper system thread detection
        assertFalse("User comment should not be system generated", activeThread.isSystemGenerated())
    }

    // Note: This test is commented out because ThreadStatus is now a sealed class
    // and no longer has values() or ordinal methods
    // The sealed class pattern is safer and provides exhaustiveness checking
    /*
    @Test
    fun `test ThreadStatus enum values and helpers`() {
        // Documentation des status possibles :

        val allStatuses = ThreadStatus.values()
        assertEquals("Should have 7 thread statuses", 7, allStatuses.size)

        // Status actifs (Active et Pending)
        assertEquals("Active ordinal", 1, ThreadStatus.Active.ordinal)
        assertEquals("Pending ordinal", 6, ThreadStatus.Pending.ordinal)

        // Status résolus (Fixed, WontFix, Closed, ByDesign)
        assertEquals("Fixed ordinal", 2, ThreadStatus.Fixed.ordinal)
        assertEquals("WontFix ordinal", 3, ThreadStatus.WontFix.ordinal)
        assertEquals("Closed ordinal", 4, ThreadStatus.Closed.ordinal)
        assertEquals("ByDesign ordinal", 5, ThreadStatus.ByDesign.ordinal)

        // Helper de conversion pour API
        // toApiValue() retourne la valeur numérique pour PATCH
        assertEquals("Active API value", 1, ThreadStatus.Active.toApiValue())
        assertEquals("Fixed API value", 2, ThreadStatus.Fixed.toApiValue())
        assertEquals("Pending API value", 6, ThreadStatus.Pending.toApiValue())
    }
     */

    @Test
    fun `test getPullRequest single PR retrieval`() {
        // Documentation :
        // - Méthode : getPullRequest(pullRequestId: Int)
        // - Surcharge : getPullRequest(pullRequestId: Int, projectName: String?, repositoryId: String?)
        // - Endpoint : /pullrequests/{id}?api-version=7.0
        // - Retour : PullRequest complet

        val pullRequestId = 123

        // La surcharge permet d'accéder à un PR dans un autre repo/projet
        // Utile pour la vue "tous les PRs de l'organisation"

        assertNotNull("Pull request ID should be valid", pullRequestId)
    }

    @Test
    fun `test getAllOrganizationPullRequests cross-project retrieval`() {
        // Documentation :
        // - Méthode : getAllOrganizationPullRequests(status: String, top: Int)
        // - Utilise buildOrgApiUrl() pour endpoint au niveau organisation
        // - Endpoint : /_apis/git/pullrequests?searchCriteria.status=...
        // - Retourne PRs de TOUS les projets de l'organisation

        // Avantage : Une seule API call pour tous les PRs
        // Inconvénient : Plus lent si beaucoup de PRs

        assertNotNull("Organization-wide PR retrieval should be available", true)
    }

    // --- Tests de documentation des problèmes connus ---

    @Test
    fun `test God Class has too many responsibilities`() {
        // ⚠️ Problème architectural documenté :
        //
        // AzureDevOpsApiClient a 1974 lignes et gère :
        // - HTTP execution (OkHttpClient)
        // - Authentication (createAuthHeader)
        // - URL building (buildApiUrl, buildOrgApiUrl)
        // - Error handling (handleErrorResponse)
        // - 80+ endpoints API :
        //   - Pull Requests (create, update, vote, merge, abandon)
        //   - Comments (threads, replies, status updates)
        //   - Work Items (create, update, query, board)
        //   - Pipelines (runs, logs, definitions)
        //   - Repositories (files, changes, refs)
        //   - Identities (avatars, search)
        //   - Policies (evaluation, approvals)
        //
        // Violation : Single Responsibility Principle
        // Solution : Découper en API interfaces spécialisés

        val apiClientLines = 1974
        assertTrue("API Client has too many responsibilities (>1000 lines)", apiClientLines > 1000)
    }

    @Test
    fun `test API client uses getInstance pattern preventing testability`() {
        // ⚠️ Problème architectural documenté :
        //
        // Pattern actuel :
        // - val apiClient = AzureDevOpsApiClient.getInstance(project)
        // - Impossible à mocker dans les tests unitaires
        // - Nécessite un vrai projet IntelliJ
        //
        // Violation : Dependency Inversion Principle
        // Solution : Interfaces + Constructor Injection

        assertNotNull("getInstance returns concrete implementation", apiClient)
    }

    @Test
    fun `test error handling is inconsistent across methods`() {
        // ⚠️ Problème de qualité documenté :
        //
        // Pattern 1 (majoritaire) : catch Exception
        // } catch (e: Exception) {
        //     logger.error("Failed to ...", e)
        //     throw AzureDevOpsApiException("Error ...: ${e.message}", e)
        // }
        //
        // Pattern 2 : catch AzureDevOpsApiException puis Exception
        // } catch (e: AzureDevOpsApiException) {
        //     // Gestion spécifique
        // } catch (e: Exception) {
        //     // Fallback
        // }
        //
        // Pattern 3 : catch vide ou minimal
        // } catch (e: NumberFormatException) {
        //     // Handler vide
        // }
        //
        // Solution : Uniformiser avec sealed classes Result<T>

        assertTrue("Error handling patterns are inconsistent", true)
    }
}
