# Testing Guide

## Overview

This project has two types of tests:

1. **Unit Tests** - Pure Kotlin tests with MockK (no IntelliJ Platform dependencies)
2. **Platform Tests** - Tests that extend `BasePlatformTestCase` (require IntelliJ Platform)

## Running Tests

### From IntelliJ IDEA (Recommended)

1. **Single Test Class:**
   - Open the test file (e.g., `PathNormalizerTest.kt`)
   - Right-click in the editor
   - Select `Run 'PathNormalizerTest'`

2. **Test Package:**
   - Right-click on `azuredevops.util` package
   - Select `Run Tests in 'azuredevops.util'`

3. **All Unit Tests:**
   - Right-click on `src/test/kotlin/azuredevops`
   - Select `Run Tests in 'azuredevops'`

### From Command Line

Due to IntelliJ Platform test framework conflicts with JBR, running tests from Gradle may crash. Workarounds:

**Option 1: Use IntelliJ's built-in test runner**
```bash
# Open IntelliJ and run from IDE (recommended)
```

**Option 2: Skip platform tests and run only unit tests**
```bash
# This may still crash due to JBR issues
./gradlew test --tests "azuredevops.util.*" --tests "azuredevops.model.*" --tests "azuredevops.api.*"
```

**Option 3: Use the test script (experimental)**
```bash
chmod +x run-unit-tests.sh
./run-unit-tests.sh
```

## Test Structure

### Unit Tests (No Platform Dependencies)

Located in:
- `azuredevops.util.*` - Utility class tests
- `azuredevops.model.*` - Model and extension function tests
- `azuredevops.api.impl.*` - API implementation tests

These can be run from IntelliJ IDEA without issues.

### Platform Tests (Require IntelliJ Platform)

Located in:
- `azuredevops.services.*` - Service tests with `BasePlatformTestCase`
- `azuredevops.toolwindow.*` - UI component tests

These require the IntelliJ Platform test framework and may crash when run from Gradle CLI.

## Writing Tests

### Unit Test Example

```kotlin
package azuredevops.util

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MyUtilityTest {

    @Test
    fun `test should do something`() {
        // Given
        val mock = mockk<MyService> {
            every { doSomething() } returns "result"
        }
        
        // When
        val result = MyUtility.process(mock)
        
        // Then
        assertEquals("expected", result)
    }
}
```

### Platform Test Example

```kotlin
package azuredevops.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class MyServiceTest : BasePlatformTestCase() {

    @Test
    fun `test service with platform`() {
        // Can use project, fixtures, etc.
        val service = MyService.getInstance(project)
        // ...
    }
}
```

## Dependencies

- **JUnit 5** - Test framework
- **MockK** - Mocking library for Kotlin
- **AssertJ** - Fluent assertions
- **IntelliJ Platform Test Framework** - For platform tests

## Troubleshooting

### Test crashes with exit code 134

This is a known issue with JBR (JetBrains Runtime) and the IntelliJ Platform test framework.

**Solution:** Run tests from IntelliJ IDEA instead of Gradle CLI.

### MockK doesn't work in platform tests

MockK may conflict with IntelliJ's classloader.

**Solution:** Use IntelliJ's mocking utilities or avoid mocking platform classes.

### Tests not found

Make sure test sources are compiled:
```bash
./gradlew compileTestKotlin
```
