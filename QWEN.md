# QWEN.md — Guidelines for AI Assistants

## Project Overview

**Azure DevOps Plugin for IntelliJ Platform** — A fork of the open-source project [AzureDevOps by Paol0B](https://github.com/Paol0B/AzureDevOps).

- **Target**: IntelliJ IDEA, WebStorm, and all JetBrains IDEs
- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **SDK**: IntelliJ Platform SDK

## Project Goals

1. **Enrich** — Add new features for Azure DevOps PR review workflow
2. **Consolidate** — Stabilize unpredictable behaviors
3. **Refactor** — Clean up legacy code and improve maintainability

## Key Architecture Patterns

### Services (Singleton Pattern)
Most business logic lives in `@Service` classes:
- `AzureDevOpsApiClient.kt` — HTTP client for Azure DevOps REST API
- `PullRequestCommentsService.kt` — Display comments in editor gutters
- `PullRequestCommentsTracker.kt` — Track comments per file for badges

### Tool Windows
- `CommentsNavigatorToolWindow.kt` — Master view of all PR comments
- `PrReviewToolWindow.kt` — Full-screen PR review workspace
- `FileTreePanel.kt` — File tree with comment count badges

### Decorators
- `FileWithCommentsDecorator.kt` — Project view decorator showing comment badges on files

### Actions
- `ShowPullRequestCommentsAction.kt` — Load comments in current editor

## Critical Patterns & Gotchas

### ⚠️ Comment Threads on Deleted/Moved Files

**Always filter `isDeleted == true` when processing PR comments.**

Azure DevOps marks threads as deleted when files are removed/moved. Without filtering, displaying these comments causes Java errors.

```kotlin
// ✅ Correct pattern (used in DiffViewerPanel, CommentsNavigatorToolWindow, TimelineModels)
val threads = allThreads.filter { it.isDeleted != true && it.isActive() }

// ❌ Avoid — missing isDeleted check (bug fixed in PullRequestCommentsService on 2026-04-22)
val threads = allThreads.filter { it.getFilePath() == filePath }
```

**Why:** Confirmed bug — `PullRequestCommentsService.kt` was missing this filter while all other code had it.

### Path Matching for Comments

Thread paths from Azure DevOps are repo-relative (e.g., `/src/main.kt`). Local file paths are absolute. Always normalize before comparing:

```kotlin
val normalizedThreadPath = threadPath.replace('/', '\\').trimStart('\\')
val normalizedFilePath = file.path.replace('/', '\\')
val matches = normalizedFilePath.endsWith(normalizedThreadPath, ignoreCase = true)
```

### Threading Model

- **Background threads** (`executeOnPooledThread`) — API calls, file I/O, heavy computation
- **EDT** (`invokeLater`) — All UI updates, editor modifications, dialogs

```kotlin
ApplicationManager.getApplication().executeOnPooledThread {
    val data = apiClient.fetchData() // Background
    
    ApplicationManager.getApplication().invokeLater {
        updateUI(data) // EDT
    }
}
```

## Common Commands

```bash
# Build plugin
./gradlew buildPlugin

# Run IDE with plugin
./gradlew runIde

# Run tests
./gradlew test

# Check Kotlin code style
./gradlew ktlintCheck

# Format code
./gradlew ktlintFormat
```

## Code Style

- **Naming**: Kotlin conventions (PascalCase for classes, camelCase for methods/vars)
- **Null Safety**: Prefer `?.` and `?:` over `!!`
- **Logging**: Use `Logger.getInstance(ClassName::class.java)`
- **Error Handling**: Catch specific exceptions, show user-friendly messages
- **Comments**: Minimal — code should be self-documenting

## Testing

- Tests use **JUnit 5** and **AssertJ**
- Mock external APIs with **MockK**
- Test files in `src/test/kotlin/` mirroring `src/main/kotlin/` structure

## Known Legacy Issues

1. **Inconsistent error handling** — Some places catch `Exception`, others catch `AzureDevOpsApiException`
2. **Tight coupling** — Services directly instantiate `AzureDevOpsApiClient` instead of DI
3. **Magic strings** — Some API endpoints and status values are hardcoded
4. **Thread safety** — Some collections are not thread-safe despite concurrent access

When refactoring, address these incrementally. Prefer small, safe changes over big rewrites.

## Memory System

This project uses a file-based memory system at `~/.qwen/projects/.../memory/`:
- **user/** — User preferences and role info
- **feedback/** — Guidance on what approaches work/don't work
- **project/** — Context about ongoing work and decisions
- **reference/** — Pointers to external resources

Check `MEMORY.md` for existing memories before making assumptions.

## Language Preference

Technical communication should be in **French** (user preference). Code and comments remain in English.
