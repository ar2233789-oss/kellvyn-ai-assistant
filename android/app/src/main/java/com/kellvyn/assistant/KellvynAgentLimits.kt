package com.kellvyn.assistant

/**
 * Hard limits for the first native agent iteration.
 *
 * These are runtime checks, not prompt-only instructions. Keep this policy pure so
 * the boundaries can be exercised by unit tests without loading the language model.
 */
internal object KellvynAgentLimits {
    const val MAX_TOOL_CALLS_PER_TURN = 6
    const val MAX_PROJECTS_RETURNED = 50
    const val MAX_PROJECT_FILES_RETURNED = 80
    const val MAX_FILES_SCANNED_PER_SEARCH = 60
    const val MAX_SEARCH_MATCHES = 20
    const val MAX_FILE_READ_CHARS = 12_000
    const val MAX_SEARCH_QUERY_CHARS = 120
    const val MAX_SEARCH_SNIPPET_CHARS = 400

    private val supportedTextExtensions = setOf(
        "txt", "md", "json", "csv", "xml", "html", "css", "java", "kt", "kts",
        "gradle", "properties", "js", "jsx", "ts", "tsx", "py", "log", "yaml",
        "yml", "toml"
    )

    fun allowsToolCall(callNumber: Int): Boolean =
        callNumber in 1..MAX_TOOL_CALLS_PER_TURN

    fun isValidSearchQuery(query: String): Boolean =
        query.trim().length in 2..MAX_SEARCH_QUERY_CHARS

    fun isSupportedTextFile(relativePath: String): Boolean {
        val extension = relativePath.substringAfterLast('.', "").lowercase()
        return extension in supportedTextExtensions
    }
}
