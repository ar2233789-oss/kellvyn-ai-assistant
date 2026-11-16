package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynProjectCodeIndexerTest {
    @Test
    fun extractsPackageImportsDeclarationsAndCallReferences() {
        val source = """
            package com.example.profile
            import com.example.data.UserRepository

            class ProfileScreen {
                private val repository = UserRepository()

                fun loadProfile(id: String): User {
                    return repository.findById(id)
                }
            }
        """.trimIndent()

        val index = KellvynProjectCodeIndexer.index("src/ProfileScreen.kt", source)

        assertEquals("com.example.profile", index.packageName)
        assertTrue(index.imports.contains("com.example.data.UserRepository"))
        assertTrue(index.declarations.any { it.name == "ProfileScreen" && it.kind == "class" })
        assertTrue(index.declarations.any { it.name == "loadProfile" && it.kind == "fun" })
        assertTrue(index.references.contains("findById"))
        assertTrue(index.searchableIdentifiers.contains("UserRepository"))
    }

    @Test
    fun codeStructureImprovesSearchForImportedTypesAndMethodReferences() {
        val source = """
            package com.example.profile
            import com.example.data.UserRepository
            class ProfileScreen {
                fun loadProfile() {
                    repository.findById("42")
                }
            }
        """.trimIndent()
        val index = KellvynProjectCodeIndexer.index("src/ProfileScreen.kt", source)
        val hits = KellvynProjectSearchRanker.rank(
            documents = listOf(
                KellvynProjectSearchDocument(
                    path = "src/ProfileScreen.kt",
                    excerpt = "class ProfileScreen",
                    truncated = false,
                    sizeBytes = source.length.toLong(),
                    codeIndex = index
                ),
                KellvynProjectSearchDocument(
                    path = "docs/readme.md",
                    excerpt = "Guia geral do perfil.",
                    truncated = false,
                    sizeBytes = 40L
                )
            ),
            query = "UserRepository",
            limit = 5
        )

        assertEquals(1, hits.size)
        assertEquals("src/ProfileScreen.kt", hits.single().path)
        assertTrue(hits.single().matchedIdentifiers.contains("userrepository"))
    }

    @Test
    fun ignoresCommentDeclarationsAndBoundsLargeSources() {
        val index = KellvynProjectCodeIndexer.index(
            "src/Large.kt",
            """
                // class FakeDeclaration
                /* fun FakeCall() */
                class ActualDeclaration
            """.trimIndent() + " ".repeat(13_000)
        )

        assertTrue(index.declarations.any { it.name == "ActualDeclaration" })
        assertFalse(index.declarations.any { it.name == "FakeDeclaration" })
        assertTrue(index.truncatedSource)
    }
}
