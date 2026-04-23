📋 Plan de Refactoring - Azure DevOps Plugin

🎯 Objectifs Globaux
- Respecter les 5 principes SOLID
- Améliorer la testabilité
- Réduire le couplage fort
- Standardiser la gestion d'erreurs
- Éliminer les code smells majeurs

---

Phase 1 - Protection (2-3 jours)

1.1 Tests de Caractérisation
Objectif : Capturer le comportement actuel avant modifications

 1 ├── Créer tests critiques pour :
 2 │   ├── PullRequestCommentsService (affichage commentaires)
 3 │   ├── AzureDevOpsApiClient (appels API PR)
 4 │   ├── PrReviewToolWindow (chargement UI)
 5 │   └── Path matching logic (thread paths vs file paths)

Livrable : Suite de tests de régression sur les chemins critiques

1.2 Documentation des Contrats

 1 ├── Documenter comportements attendus de :
 2 │   ├── getPullRequests() 
 3 │   ├── getCommentThreads()
 4 │   ├── filterDeletedFiles()
 5 │   └── path normalization logic

---

Phase 2 - Abstractions (3-4 jours)

2.1 Interfaces Services Core

 1 // src/main/kotlin/paol0b/azuredevops/api/
 2 ├── PullRequestApi.kt        // PR operations
 3 ├── CommentApi.kt            // Commentaires PR
 4 ├── WorkItemApi.kt           // Work items
 5 ├── PipelineApi.kt           // Pipelines  
 6 ├── RepositoryApi.kt         // Repos/files
 7 ├── IdentityApi.kt           // Avatars/users
 8 └── AuthenticationApi.kt     // Token/auth

Exemple :

  1 interface PullRequestApi {
  2     fun getPullRequests(status: String, top: Int): Result<List<PullRequest>>
  3     fun findActivePullRequest(sourceBranch: String, targetBranch: String): PullRequest?
  4     fun createPullRequest(params: CreatePrParams): Result<PullRequest>
  5 }
  6 
  7 interface CommentApi {
  8     fun getCommentThreads(prId: Int): Result<List<CommentThread>>
  9     fun updateCommentStatus(threadId: Int, status: CommentStatus): Result<Unit>
 10     fun addReply(threadId: Int, content: String): Result<Comment>
 11 }

2.2 Sealed Classes pour Domain Constants

  1 sealed class PullRequestStatus(val value: String) {
  2     object Active : PullRequestStatus("active")
  3     object Completed : PullRequestStatus("completed")
  4     object Abandoned : PullRequestStatus("abandoned")
  5 }
  6 
  7 sealed class CommentStatus(val value: String) {
  8     object Approved : CommentStatus("Approved")
  9     object Rejected : CommentStatus("Reject")
 10 }

---

Phase 3 - Dependency Injection (4-5 jours)

3.1 Manuel Constructor Injection Pattern

Strategy : Remplacer getInstance() par injection via constructeur

  1 // Avant ❌
  2 class PrReviewToolWindow(private val project: Project) : JPanel() {
  3     private val apiClient = AzureDevOpsApiClient.getInstance(project)
  4     private val commentsService = PullRequestCommentsService.getInstance(project)
  5 }
  6 
  7 // Après ✅
  8 class PrReviewToolWindow(
  9     private val project: Project,
 10     private val pullRequestApi: PullRequestApi,
 11     private val commentApi: CommentApi,
 12     private val reviewStateService: PrReviewStateService
 13 ) : JPanel()

3.2 Factory/Provider Pattern
Créer une couche d'adaptation entre IntelliJ services et DI :

  1 object ServiceLocator {
  2     fun createPrReviewToolWindow(project: Project): PrReviewToolWindow {
  3         val apiClient = AzureDevOpsApiClient.getInstance(project)
  4         return PrReviewToolWindow(
  5             project = project,
  6             pullRequestApi = apiClient,
  7             commentApi = apiClient,
  8             reviewStateService = PrReviewStateService.getInstance(project)
  9         )
 10     }
 11 }

Avantage : Change un seul point, rend le reste testable

---

Phase 4 - SRP AzureDevOpsApiClient (5-7 jours)

4.1 Découpage en Services Spécialisés

 1 AzureDevOpsApiClient (1974 lines) →
 2 ├── HttpClient.kt                  // Raw HTTP + authentication
 3 ├── DefaultPullRequestApi.kt      // Impl PullRequestApi
 4 ├── DefaultCommentApi.kt          // Impl CommentApi  
 5 ├── DefaultWorkItemApi.kt         // Impl WorkItemApi
 6 ├── DefaultPipelineApi.kt         // Impl PipelineApi
 7 ├── DefaultRepositoryApi.kt       // Impl RepositoryApi
 8 └── DefaultIdentityApi.kt         // Impl IdentityApi

HttpClient (nouveau) :

 1 class HttpClient @Inject constructor(private val configService: ConfigService) {
 2     fun execute(request: HttpRequest): Result<Response>
 3     fun createAuthHeader(token: String): String
 4     fun buildBaseUrl(org: String, project: String): String
 5 }

4.2 Error Handling Unifié
Remplacer exceptions par Result<T> :

  1 // Avant ❌
  2 @Throws(AzureDevOpsApiException::class)
  3 fun getPullRequests(): List<PullRequest>
  4 
  5 // Après ✅
  6 fun getPullRequests(): Result<List<PullRequest>>
  7 
  8 sealed class ApiError {
  9     object Unauthorized : ApiError()
 10     object NotFound : ApiError()
 11     data class NetworkFailure(val cause: Throwable) : ApiError()
 12     data class ServerError(val code: Int, val message: String) : ApiError()
 13 }

---

Phase 5 - Configuration Service (2-3 jours)

5.1 SRP AzureDevOpsConfigService

Actuellement 3 responsabilités mélangées :
1. Gestion PAT/token
2. Détection repos Azure DevOps
3. Stockage configuration

Découpage :

  1 interface ConfigService {
  2     fun getConfig(): AzureDevOpsConfig
  3     fun saveConfig(config: AzureDevOpsConfig): Unit
  4     fun clearCredentials(): Unit
  5 }
  6 
  7 interface RepoDetector {
  8     fun isAzureDevOpsRepository(repoPath: String): Boolean
  9     fun detectRepoInfo(repoPath: String): AzureDevOpsRepoInfo?
 10 }
 11 
 12 interface AuthenticationService {
 13     fun validateToken(token: String): Result<UserIdentity>
 14     fun refreshDeviceCode(deviceCode: DeviceCode): Result<String>
 15 }

---

Phase 6 - ToolWindows & UI (3-4 jours)

6.1 Injecter Dependencies dans Composants UI

Liste critique (par ordre de complexité) :
1. DiffViewerPanel (558 lignes)
2. PrReviewTabPanel (754 lignes)
3. PrReviewToolWindow (690 lignes)
4. CreateWorkItemDialog
5. PullRequestReviewDialog

Pattern à suivre :

  1 class DiffViewerPanel(
  2     private val project: Project,
  3     private val pullRequestId: Int,
  4     private val commentApi: CommentApi,
  5     private val repositoryApi: RepositoryApi,
  6     externalProjectName: String? = null,
  7     externalRepositoryId: String? = null
  8 ) : JPanel(), Disposable {
  9     // Plus de getInstance() interne
 10 }

6.2 Actions Simplifiées

 1 class ShowPullRequestCommentsAction(
 2     private val commentApiFactory: (Project) -> CommentApi
 3 ) : AnAction() {
 4     override fun actionPerformed(e: AnActionEvent) {
 5         val project = e.project ?: return
 6         val commentApi = commentApiFactory(project)
 7         // Testable maintenant !
 8     }
 9 }

---

Phase 7 - Thread Safety (1-2 jours)

7.1 Corriger Collections Non Thread-Safe

Problèmes identifiés :

 1 // ❌ PullRequestCommentsService
 2 private val commentMarkers = mutableMapOf<VirtualFile, MutableList<RangeHighlighter>>()
 3 
 4 // ✅ Solution
 5 private val commentMarkers = ConcurrentHashMap<VirtualFile, MutableList<RangeHighlighter>>()

Vérifier tous les :
- mutableMapOf → ConcurrentHashMap
- mutableListOf → CopyOnWriteArrayList (si lecture fréquente)
- Ajouter synchronized si état mutable partagé

---

Phase 8 - Code Quality (2-3 jours)

8.1 Éliminer Magic Strings/Numbers

  1 // ❌ Avant
  2 val url = buildApiUrl(..., "/pullrequests?searchCriteria.status=$statusParam...")
  3 if (status == "active" || status == "completed") { ... }
  4 
  5 // ✅ Après
  6 object AzureDevOpsEndpoints {
  7     const val PULL_REQUESTS = "/pullrequests"
  8     const val COMMENTS = "/pullrequests/{id}/threads"
  9     const val WORK_ITEMS = "_workitems/edit"
 10 }
 11 
 12 object ApiParameters {
 13     const val STATUS_ACTIVE = "active"
 14     const val STATUS_COMPLETED = "completed"
 15     const val API_VERSION = "7.1-preview.1"
 16 }

8.2 Extract Utility Objects

  1 object PathNormalizer {
  2     fun normalizeThreadPath(threadPath: String): String = 
  3         threadPath.replace('/', '\\').trimStart('\\')
  4     
  5     fun matchThreadToFilePath(threadPath: String, filePath: String): Boolean {
  6         val normalizedThread = normalizeThreadPath(threadPath)
  7         val normalizedFile = filePath.replace('/', '\\')
  8         return normalizedFile.endsWith(normalizedThread, ignoreCase = true)
  9     }
 10 }

---

Phase 9 - Tests & Validation (2-3 jours)

9.1 Mock-Based Unit Tests

 1 @Test
 2 fun `getPullRequests returns success result when API responds OK`() {
 3     val mockApi = MockPullRequestApi()
 4     mockApi.stub { getPullRequests("active", 10) returns SUCCESS(listOf(...)) }
 5     
 6     val result = service.fetchActivePRs()
 7     
 8     assertThat(result).isSuccess.containsExactly(...)
 9 }

9.2 Build & Lint Verification

 1 ./gradlew clean buildPlugin
 2 ./gradlew test
 3 ./gradlew ktlintCheck
 4 ./gradlew ktlintFormat

---

Tableau de Priorité


┌───────────────────────────────┬───────────┬────────┬──────────┬────────┐
│ Phase                         │ Priority  │ Effort │ Impact   │ Risk   │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 1. Tests caractérisation      │ 🔴 High   │ 2-3j   │ Critical │ Low    │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 2. Interfaces abstractions    │ 🔴 High   │ 3-4j   │ Critical │ Medium │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 3. Dependency Injection       │ 🔴 High   │ 4-5j   │ Critical │ Medium │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 4. Split AzureDevOpsApiClient │ 🟠 Medium │ 5-7j   │ High     │ High   │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 5. Error handling Result<T>   │ 🟠 Medium │ 2-3j   │ Medium   │ Medium │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 6. ConfigService SRP          │ 🟡 Low    │ 2-3j   │ Medium   │ Low    │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 7. ToolWindows refactoring    │ 🟡 Low    │ 3-4j   │ High     │ Medium │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 8. Thread Safety fixes        │ 🟡 Low    │ 1-2j   │ Medium   │ Low    │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 9. Magic strings cleanup      │ 🟢 Low    │ 2-3j   │ Low      │ Low    │
├───────────────────────────────┼───────────┼────────┼──────────┼────────┤
│ 10. Validation & tests        │ 🔴 High   │ 2-3j   │ Critical │ Low    │
└───────────────────────────────┴───────────┴────────┴──────────┴────────┘


Total estimé : 26-37 jours de travail

---

Stratégie d'Implémentation

Je recommande une approche incrémentale :

1. Commencer par la Phase 2 (Interfaces) - sans casser le code existant
2. En parallèle, créer les tests de la Phase 1 sur les fonctionnalités critiques
3. Migration progressive : un service à la fois vers DI
4. Valider après chaque phase avec build + tests

Approche recommandée :
- Faire les phases 1-3 d'abord (fondations)
- Ensuite découper AzureDevOpsApiClient progressivement
- Maintenir backward compatibility pendant toute la migration