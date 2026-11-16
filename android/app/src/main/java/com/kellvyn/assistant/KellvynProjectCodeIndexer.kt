package com.kellvyn.assistant

/**
 * Bounded lexical structure index for Kotlin/Java project excerpts.
 *
 * This is deliberately not a full compiler frontend: it extracts stable navigation hints
 * (packages, imports, declarations and call-like references) without executing project code.
 * Search results still carry the original source excerpt for inspection.
 */
internal data class KellvynCodeDeclaration(
    val name: String,
    val kind: String,
    val line: Int
)

internal data class KellvynProjectCodeIndex(
    val path: String,
    val packageName: String?,
    val imports: Set<String>,
    val declarations: List<KellvynCodeDeclaration>,
    val references: Set<String>,
    val truncatedSource: Boolean
) {
    val searchableIdentifiers: Set<String>
        get() = buildSet {
            packageName?.substringAfterLast('.')?.let(::add)
            imports.forEach { add(it.substringAfterLast('.')) }
            declarations.forEach { add(it.name) }
            addAll(references)
        }
}

internal object KellvynProjectCodeIndexer {
    private const val MAX_SOURCE_CHARS = 12_000
    private const val MAX_LINES = 2_000
    private const val MAX_IMPORTS = 80
    private const val MAX_DECLARATIONS = 128
    private const val MAX_REFERENCES = 192

    private val PACKAGE = Regex("""^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
    private val IMPORT = Regex("""^\s*import\s+(?:static\s+)?([A-Za-z_][A-Za-z0-9_.*]*)""")
    private val DECLARATION = Regex(
        """^\s*(?:(?:public|private|protected|internal|open|abstract|final|data|sealed|enum|annotation|expect|actual|suspend|inline|override|operator|tailrec|external|static|native|synchronized)\s+)*(class|interface|object|fun|val|var|typealias|record|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)"""
    )
    private val CALL = Regex("""\b([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^<>\n]{1,100}>)?\s*\(""")
    private val KEYWORDS = setOf(
        "if", "for", "while", "switch", "when", "catch", "return", "throw",
        "new", "super", "this", "synchronized", "try", "assert", "sizeof"
    )

    private data class CleanLine(
        val content: String,
        val insideBlockComment: Boolean,
        val insideTripleQuotedString: Boolean
    )

    /** Removes comments and string bodies before extracting identifiers. */
    private fun stripCommentsAndStrings(
        rawLine: String,
        startsInBlockComment: Boolean,
        startsInTripleString: Boolean
    ): CleanLine {
        val output = StringBuilder(rawLine.length)
        var inBlock = startsInBlockComment
        var inTriple = startsInTripleString
        var quote: Char? = null
        var escaped = false
        var index = 0

        while (index < rawLine.length) {
            if (inBlock) {
                val close = rawLine.indexOf("*/", index)
                if (close < 0) {
                    output.append(" ".repeat(rawLine.length - index))
                    index = rawLine.length
                } else {
                    output.append(" ".repeat(close + 2 - index))
                    index = close + 2
                    inBlock = false
                }
                continue
            }

            if (inTriple) {
                if (rawLine.startsWith("\"\"\"", index)) {
                    output.append("   ")
                    index += 3
                    inTriple = false
                } else {
                    output.append(' ')
                    index++
                }
                continue
            }

            val current = rawLine[index]
            if (quote != null) {
                output.append(' ')
                if (escaped) {
                    escaped = false
                } else if (current == '\\') {
                    escaped = true
                } else if (current == quote) {
                    quote = null
                }
                index++
                continue
            }

            if (rawLine.startsWith("\"\"\"", index)) {
                output.append("   ")
                index += 3
                inTriple = true
                continue
            }
            if (current == '"' || current == '\'') {
                output.append(' ')
                quote = current
                index++
                continue
            }
            if (rawLine.startsWith("//", index)) {
                output.append(" ".repeat(rawLine.length - index))
                index = rawLine.length
                continue
            }
            if (rawLine.startsWith("/*", index)) {
                output.append("  ")
                index += 2
                inBlock = true
                continue
            }

            output.append(current)
            index++
        }

        return CleanLine(output.toString(), inBlock, inTriple)
    }

    fun index(path: String, source: String): KellvynProjectCodeIndex {
        require(path.isNotBlank() && path.length <= 240)
        val bounded = source.take(MAX_SOURCE_CHARS)
        val lines = bounded.lineSequence().take(MAX_LINES).toList()
        var packageName: String? = null
        val imports = linkedSetOf<String>()
        val declarations = mutableListOf<KellvynCodeDeclaration>()
        val references = linkedSetOf<String>()
        var insideBlockComment = false
        var insideTripleQuotedString = false

        lines.forEachIndexed { index, rawLine ->
            val cleaned = stripCommentsAndStrings(
                rawLine,
                insideBlockComment,
                insideTripleQuotedString
            )
            insideBlockComment = cleaned.insideBlockComment
            insideTripleQuotedString = cleaned.insideTripleQuotedString
            val line = cleaned.content

            if (packageName == null) {
                packageName = PACKAGE.find(line)?.groupValues?.getOrNull(1)
            }
            IMPORT.find(line)?.groupValues?.getOrNull(1)?.let { imported ->
                if (imports.size < MAX_IMPORTS) imports += imported
            }
            DECLARATION.find(line)?.let { match ->
                if (declarations.size < MAX_DECLARATIONS) {
                    declarations += KellvynCodeDeclaration(
                        name = match.groupValues[2],
                        kind = match.groupValues[1].replace(Regex("""\s+"""), " "),
                        line = index + 1
                    )
                }
            }
            CALL.findAll(line).forEach { match ->
                val name = match.groupValues[1]
                if (name.lowercase() !in KEYWORDS && references.size < MAX_REFERENCES) {
                    references += name
                }
            }
        }

        return KellvynProjectCodeIndex(
            path = path,
            packageName = packageName,
            imports = imports,
            declarations = declarations,
            references = references,
            truncatedSource = source.length > bounded.length || lines.size == MAX_LINES
        )
    }
}
