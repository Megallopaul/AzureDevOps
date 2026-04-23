package azuredevops.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for PathNormalizer utility object.
 */
class PathNormalizerTest {
    @Test
    fun `normalizeThreadPath removes leading slash and converts to backslashes`() {
        val input = "/src/main/kotlin/File.kt"
        val expected = "src\\main\\kotlin\\File.kt"

        val result = PathNormalizer.normalizeThreadPath(input)

        assertEquals(expected, result)
    }

    @Test
    fun `normalizeThreadPath handles path without leading slash`() {
        val input = "src/main/kotlin/File.kt"
        val expected = "src\\main\\kotlin\\File.kt"

        val result = PathNormalizer.normalizeThreadPath(input)

        assertEquals(expected, result)
    }

    @Test
    fun `normalizeThreadPath handles empty string`() {
        val input = ""
        val expected = ""

        val result = PathNormalizer.normalizeThreadPath(input)

        assertEquals(expected, result)
    }

    @Test
    fun `normalizeFilePath converts forward slashes to backslashes`() {
        val input = "/Users/dev/project/src/main.kt"
        val expected = "\\Users\\dev\\project\\src\\main.kt"

        val result = PathNormalizer.normalizeFilePath(input)

        assertEquals(expected, result)
    }

    @Test
    fun `matchThreadToFilePath returns true when paths match`() {
        val threadPath = "/src/main.kt"
        val filePath = "C:\\project\\src\\main.kt"

        val result = PathNormalizer.matchThreadToFilePath(threadPath, filePath)

        assertTrue(result)
    }

    @Test
    fun `matchThreadToFilePath returns true with different case`() {
        val threadPath = "/src/main.kt"
        val filePath = "C:\\project\\SRC\\MAIN.KT"

        val result = PathNormalizer.matchThreadToFilePath(threadPath, filePath)

        assertTrue(result)
    }

    @Test
    fun `matchThreadToFilePath returns false when paths don't match`() {
        val threadPath = "/src/main.kt"
        val filePath = "C:\\project\\lib\\main.kt"

        val result = PathNormalizer.matchThreadToFilePath(threadPath, filePath)

        assertFalse(result)
    }

    @Test
    fun `matchThreadToFilePath returns false for completely different paths`() {
        val threadPath = "/folder/file.cs"
        val filePath = "C:\\project\\src\\main.kt"

        val result = PathNormalizer.matchThreadToFilePath(threadPath, filePath)

        assertFalse(result)
    }

    @Test
    fun `extractFileName returns just the filename`() {
        val input = "/src/main/kotlin/File.kt"
        val expected = "File.kt"

        val result = PathNormalizer.extractFileName(input)

        assertEquals(expected, result)
    }

    @Test
    fun `extractFileName handles Windows-style path`() {
        val input = "C:\\project\\src\\main.kt"
        val expected = "main.kt"

        val result = PathNormalizer.extractFileName(input)

        assertEquals(expected, result)
    }

    @Test
    fun `isValidFilePath returns true for valid path`() {
        val path = "/src/main.kt"

        val result = PathNormalizer.isValidFilePath(path)

        assertTrue(result)
    }

    @Test
    fun `isValidFilePath returns false for null`() {
        val result = PathNormalizer.isValidFilePath(null)

        assertFalse(result)
    }

    @Test
    fun `isValidFilePath returns false for empty string`() {
        val result = PathNormalizer.isValidFilePath("")

        assertFalse(result)
    }

    @Test
    fun `isValidFilePath returns false for path without extension`() {
        val path = "/src/main"

        val result = PathNormalizer.isValidFilePath(path)

        assertFalse(result)
    }

    @Test
    fun `isValidFilePath returns false for directory path ending with slash`() {
        val path = "/src/main/"

        val result = PathNormalizer.isValidFilePath(path)

        assertFalse(result)
    }
}
