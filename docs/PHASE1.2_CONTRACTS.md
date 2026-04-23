# Phase 1.2 - Documentation des Contrats

Ce fichier documente les comportements attendus des méthodes critiques du plugin. Cette documentation sert de référence pour les refactorings futurs et garantit que les comportements essentiels sont préservés.

---

## 1. `getPullRequests()` - AzureDevOpsApiClient

### Signature
```kotlin
fun getPullRequests(status: String = "active", top: Int = 100): List<PullRequest>
```

### Comportement attendu

1. **Construction de l'URL** :
   - Utilise `buildApiUrl(project, repository, "/pullrequests?...")`
   - Ajoute les query params :
     - `searchCriteria.status=$statusParam` (où `statusParam = "all"` si status == "all", sinon status)
     - `$top=$top`
     - `api-version=7.0`
   - Exemple : `https://dev.azure.com/org/project/_apis/git/repositories/repo-id/pullrequests?searchCriteria.status=active&$top=100&api-version=7.0`

2. **Exécution HTTP** :
   - Utilise `executeGet(url, token)` avec authentification Basic Auth (PAT)
   - Ajoute header `Authorization: Basic <base64(PAT)>`
   - Ajoute header `Accept: application/json`

3. **Parsing de la réponse** :
   - Parse JSON avec Gson vers `PullRequestListResponse`
   - Extrait la liste `value: List<PullRequest>`
   - Retourne la liste directement

4. **Gestion des erreurs** :
   - Catch `Exception` (inclut IOException, JsonSyntaxException, etc.)
   - Log avec `logger.error("Failed to fetch pull requests", e)`
   - Rethrow dans `AzureDevOpsApiException("Error while retrieving Pull Requests: ${e.message}", e)`

5. **Valeurs de status supportées** :
   - `"active"` : PRs en cours (défaut)
   - `"completed"` : PRs fusionnés
   - `"abandoned"` : PRs abandonnés
   - `"all"` : Tous les PRs (cas spécial)

### Invariants à préserver

- ✅ Retourne une liste vide (pas null) si aucun PR trouvé
- ✅ Jette `AzureDevOpsApiException` en cas d'erreur (pas d'Exception brute)
- ✅ Utilise background thread (caller responsibility)
- ✅ Log les erreurs avant de rethrow

### Tests de caractérisation associés

- `AzureDevOpsApiClientTest.getPullRequests fetches active PRs by default`
- `AzureDevOpsApiClientTest.getPullRequests supports status filtering`
- `AzureDevOpsApiClientTest.getPullRequests uses top parameter for limiting results`

---

## 2. `getCommentThreads()` - AzureDevOpsApiClient

### Signature
```kotlin
fun getCommentThreads(pullRequestId: Int): List<CommentThread>
fun getCommentThreads(pullRequestId: Int, projectName: String?, repositoryId: String?): List<CommentThread>
```

### Comportement attendu

1. **Construction de l'URL** :
   - Utilise `buildApiUrl(project, repository, "/pullrequests/{id}/threads?...")`
   - Ajoute query param : `api-version=7.0`
   - Pour cross-repo : utilise `projectName` et `repositoryId` fournis ou fallback sur config

2. **Exécution HTTP** :
   - Même pattern que `getPullRequests` avec `executeGet`

3. **Parsing de la réponse** :
   - Parse JSON vers `CommentThreadListResponse`
   - Extrait `value: List<CommentThread>`
   - Retourne la liste directement

4. **⚠️ IMPORTANT - Ne filtre PAS les threads** :
   - Retourne TOUS les threads : actifs, résolus, supprimés
   - **Le caller DOIT filtrer** avec : `threads.filter { it.isDeleted != true && it.isActive() }`
   - Ceci est un **bug connu** si le caller ne filtre pas

### Invariants à préserver

- ✅ Retourne une liste vide (pas null) si aucun thread
- ✅ Inclut les threads `isDeleted = true` (caller doit filtrer)
- ✅ Inclut les threads résolus (`status != Active`)
- ✅ Jette `AzureDevOpsApiException` en cas d'erreur

### Pattern de filtrage CRITIQUE

```kotlin
// ✅ Pattern correct (utilisé dans DiffViewerPanel, CommentsNavigatorToolWindow, TimelineModels)
val threads = allThreads.filter { it.isDeleted != true && it.isActive() }

// ❌ Pattern incorrect (bug fixé dans PullRequestCommentsService le 2026-04-22)
val threads = allThreads.filter { it.getFilePath() == filePath }
```

### Pourquoi filtrer `isDeleted` ?

Azure DevOps marque `isDeleted = true` quand :
- Le fichier a été supprimé du PR
- Le fichier a été déplacé/renommé
- Le thread a été supprimé manuellement

**Afficher ces threads cause des erreurs Java** car le fichier cible n'existe plus localement.

### Tests de caractérisation associés

- `AzureDevOpsApiClientTest.getCommentThreads fetches threads for PR`
- `AzureDevOpsApiClientTest.comment thread filtering pattern for deleted files`
- `PathMatchingTest.test isDeleted filtering pattern`
- `PathMatchingTest.test combined filtering pattern for PR comments`

---

## 3. `filterDeletedFiles()` - Pattern de Filtrage

### Emplacement

Ce pattern doit être appliqué dans :
- `PullRequestCommentsService.loadCommentsInEditor()` ✅ (fixé le 2026-04-22)
- `DiffViewerPanel` ✅ (déjà présent)
- `CommentsNavigatorToolWindow` ✅ (déjà présent)
- `TimelineModels` ✅ (déjà présent)
- **Tout nouveau code manipulant des threads**

### Pattern à suivre

```kotlin
val threads = allThreads.filter { thread ->
    // ✅ TOUJOURS filtrer les threads supprimés
    thread.isDeleted != true && 
    // ✅ Et optionnellement filtrer par status
    thread.isActive()
}
```

### Invariants à préserver

- ✅ `isDeleted != true` (gère null comme "non supprimé")
- ✅ `isActive()` pour avoir seulement les threads actifs (Active ou Pending)
- ✅ Appliquer ce filtrage **avant** toute autre logique

### Tests de caractérisation associés

- `PathMatchingTest.test isDeleted filtering pattern`
- `PathMatchingTest.test combined filtering pattern for PR comments`
- `PullRequestCommentsServiceTest.test deleted thread filtering is NOT implemented` (documente le bug)

---

## 4. `path normalization logic` - Correspondance Thread vs File

### Contexte

- **Thread path** : Relatif au repo Azure DevOps (ex: `/src/main.kt`)
- **File path** : Absolu sur le filesystem local (ex: `C:\project\src\main.kt` ou `/home/user/project/src/main.kt`)

### Algorithme de correspondance

```kotlin
fun matchThreadToFilePath(threadPath: String, filePath: String): Boolean {
    // 1. Normaliser le thread path (relatif -> absolu sans drive)
    val normalizedThreadPath = threadPath
        .replace('/', '\\')      // Unix -> Windows
        .trimStart('\\')          // Enlever slash initial
    
    // 2. Normaliser le file path
    val normalizedFilePath = filePath.replace('/', '\\')
    
    // 3. Vérifier que le file path se termine par le thread path
    return normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)
}
```

### Exemples

| Thread Path | File Path | Match | Explication |
|-------------|-----------|-------|-------------|
| `/src/main.kt` | `C:\project\src\main.kt` | ✅ | EndsWith ignoreCase |
| `/src/main.kt` | `/home/user/src/main.kt` | ✅ | Unix aussi supporté |
| `/README.md` | `C:\project\README.md` | ✅ | Fichier racine |
| `/src/main.kt` | `C:\project\src\test.kt` | ❌ | Fichiers différents |

### Invariants à préserver

- ✅ Remplacer `/` par `\` pour uniformiser
- ✅ Enlever le slash initial avec `trimStart('\\')`
- ✅ Comparaison `endsWith` avec `ignoreCase = true`
- ✅ Gérer les chemins avec espaces, accents, caractères spéciaux

### Cas particuliers

1. **Espaces dans les chemins** :
   - Thread: `/src/My File.kt`
   - File: `C:\My Project\src\My File.kt`
   - ✅ Doit matcher

2. **Chemins profonds** :
   - Thread: `/src/main/java/com/example/MyClass.java`
   - File: `C:\Users\dev\project\src\main\java\com\example\MyClass.java`
   - ✅ Doit matcher

3. **Différences de casse** :
   - Thread: `/SRC/Main.KT`
   - File: `C:\project\src\main.kt`
   - ✅ Doit matcher (ignoreCase)

### Tests de caractérisation associés

- `PathMatchingTest.test basic path matching with unix thread path and windows file path`
- `PathMatchingTest.test path matching with nested directories`
- `PathMatchingTest.test path matching is case insensitive`
- `PathMatchingTest.test path matching with spaces in paths`
- `CommentThread.getFilePath()` tests

---

## 5. `loadCommentsInEditor()` - PullRequestCommentsService

### Signature
```kotlin
fun loadCommentsInEditor(editor: Editor, file: VirtualFile, pullRequest: PullRequest)
```

### Comportement attendu

1. **Background thread** :
   - Utilise `ApplicationManager.getApplication().executeOnPooledThread { ... }`
   - Pour éviter de blocker l'EDT (UI)

2. **Récupération des threads** :
   - Appelle `apiClient.getCommentThreads(pullRequest.pullRequestId)`
   - Récupère TOUS les threads (y compris deleted/résolus)

3. **Filtrage par fichier** :
   - Pour chaque thread, vérifie si le path correspond au fichier
   - Utilise `thread.getFilePath()` (priorise `pullRequestThreadContext`, fallback `threadContext`)
   - Normalise les chemins avec `replace('/', '\\').trimStart('\\')`
   - Compare avec `endsWith(normalizedThreadPath, ignoreCase = true)`
   - **⚠️ BUG : Ne filtre PAS `isDeleted`** (à corriger lors du refactoring)

4. **Affichage UI** :
   - Appelle `ApplicationManager.getApplication().invokeLater { ... }`
   - Pour mettre à jour l'éditeur sur l'EDT
   - Affiche les threads avec `displayCommentsInEditor()`

5. **Mise à jour du tracker** :
   - Appelle `PullRequestCommentsTracker.setCommentsForFile(file, fileThreads)`
   - Pour afficher les badges dans l'arbre de fichiers

6. **Gestion des erreurs** :
   - Catch `Exception`
   - Log avec `logger.error("Error loading comments", e)`
   - **Ne propage pas l'erreur** (silencieuse pour l'utilisateur)

### Invariants à préserver

- ✅ Background thread pour API calls
- ✅ EDT pour UI updates
- ✅ Filtrage par fichier avec path normalization
- ✅ Met à jour le tracker pour les badges
- ✅ ⚠️ **DOIT filtrer `isDeleted != true`** (actuellement manquant - bug connu)

### Problèmes connus

1. **Ne filtre pas les threads supprimés** :
   - Contrairement à `DiffViewerPanel` et autres
   - Causait des erreurs Java quand le fichier n'existe plus
   - **À corriger en priorité lors du refactoring**

2. **Gestion d'erreur silencieuse** :
   - L'erreur est loggée mais pas montrée à l'utilisateur
   - Peut être amélioré avec une notification

### Tests de caractérisation associés

- `PullRequestCommentsServiceTest.test loadCommentsInEditor filters threads for current file`
- `PullRequestCommentsServiceTest.test loadCommentsInEditor uses background thread for API call`
- `PullRequestCommentsServiceTest.test file path matching normalizes slashes`
- `PullRequestCommentsServiceTest.test deleted thread filtering is NOT implemented` (bug documenté)

---

## 6. `isActive()` et `isResolved()` - CommentThread

### Signatures
```kotlin
fun CommentThread.isActive(): Boolean
fun CommentThread.isResolved(): Boolean
```

### Comportement attendu

```kotlin
// isActive() = status == Active OR status == Pending
fun isActive(): Boolean = status == ThreadStatus.Active || status == ThreadStatus.Pending

// isResolved() = !isActive()
fun isResolved(): Boolean = !isActive()
```

### ThreadStatus values

| Status | isActive() | isResolved() | Description |
|--------|-----------|--------------|-------------|
| `Active` | ✅ true | ❌ false | Commentaire actif, en discussion |
| `Pending` | ✅ true | ❌ false | En attente (ex: suggestion non appliquée) |
| `Fixed` | ❌ false | ✅ true | Résolu (problème corrigé) |
| `WontFix` | ❌ false | ✅ true | Ne sera pas corrigé |
| `Closed` | ❌ false | ✅ true | Fermé |
| `ByDesign` | ❌ false | ✅ true | Comportement voulu |
| `Unknown` | ❌ false | ✅ true | Status inconnu |

### Invariants à préserver

- ✅ `Active` et `Pending` sont considérés "actifs"
- ✅ Tous les autres status sont "résolus"
- ✅ `isResolved()` est l'inverse de `isActive()`
- ✅ Gère `status == null` comme non-actif (safe default)

### Tests de caractérisation associés

- `PathMatchingTest.test isActive and isResolved helpers`
- `AzureDevOpsApiClientTest.test ThreadStatus enum values and helpers`

---

## 7. `getFilePath()` - CommentThread

### Signature
```kotlin
fun CommentThread.getFilePath(): String?
```

### Comportement attendu

```kotlin
fun getFilePath(): String? = 
    pullRequestThreadContext?.filePath ?: threadContext?.filePath
```

### Priorité des sources

1. **Priorise** `pullRequestThreadContext.filePath`
2. **Fallback** sur `threadContext.filePath`
3. **Retourne** `null` si les deux sont null

### Pourquoi deux contexts ?

- `pullRequestThreadContext` : Spécifique au PR, contient les infos de ligne dans le diff
- `threadContext` : Contexte général, peut être utilisé pour d'autres types de threads

### Invariants à préserver

- ✅ Retourne `null` si aucun context n'est présent
- ✅ Priorise toujours `pullRequestThreadContext`
- ✅ Ne jette jamais d'exception

### Tests de caractérisation associés

- `PathMatchingTest.test getFilePath from CommentThread prioritizes pullRequestThreadContext`
- `PathMatchingTest.test getFilePath falls back to threadContext when pullRequestThreadContext is null`
- `PathMatchingTest.test getFilePath returns null when both contexts are null`

---

## 8. `getRightFileStart()` - CommentThread

### Signature
```kotlin
fun CommentThread.getRightFileStart(): Int?
```

### Comportement attendu

```kotlin
fun getRightFileStart(): Int? = 
    pullRequestThreadContext?.rightFileStart?.line ?: threadContext?.rightFileStart?.line
```

### Signification

- **Right file** : Fichier de droite dans le diff (source branch, modifications)
- **Left file** : Fichier de gauche dans le diff (target branch, base)
- **Start line** : Première ligne du commentaire

### Invariants à préserver

- ✅ Retourne `null` si aucune info de ligne
- ✅ Priorise `pullRequestThreadContext`
- ✅ Extrait uniquement le `line`, pas `offset`

### Usage

Utilisé pour positionner le marker de commentaire dans l'éditeur :
```kotlin
val lineNumber = thread.getRightFileStart()
if (lineNumber != null) {
    val lineIndex = (lineNumber - 1).coerceIn(0, document.lineCount - 1)
    val startOffset = document.getLineStartOffset(lineIndex)
    // ... ajouter marker
}
```

---

## Résumé des Invariants Critiques

| Invariant | Priorité | Test associé |
|-----------|----------|--------------|
| Filtrer `isDeleted != true` sur les threads | 🔴 Critique | `PathMatchingTest.test isDeleted filtering pattern` |
| Path normalization (replace '/', trimStart) | 🔴 Critique | `PathMatchingTest.test basic path matching` |
| Background thread pour API calls | 🔴 Critique | `PullRequestCommentsServiceTest.test loadCommentsInEditor uses background thread` |
| EDT pour UI updates | 🔴 Critique | (Pattern threading documenté) |
| `isActive()` = Active OR Pending | 🟠 High | `PathMatchingTest.test isActive and isResolved helpers` |
| `getFilePath()` priorise pullRequestThreadContext | 🟠 High | `PathMatchingTest.test getFilePath prioritizes` |
| Error handling avec AzureDevOpsApiException | 🟠 High | `AzureDevOpsApiClientTest.test error handling wraps exceptions` |

---

## Prochaines Étapes

Cette documentation sera utilisée pour :

1. **Phase 2** : Créer les interfaces avec les bons contrats
2. **Phase 3** : Injecter les dépendances sans casser les comportements
3. **Phase 4** : Découper `AzureDevOpsApiClient` en préservant les API contracts
4. **Phase 9** : Valider que tous les tests de caractérisation passent toujours

**Règle d'or** : Si un test de caractérisation échoue après refactoring, c'est soit :
- Un bug dans le refactoring (à corriger)
- Un comportement qu'on veut changer intentionnellement (mettre à jour le test et la doc)
