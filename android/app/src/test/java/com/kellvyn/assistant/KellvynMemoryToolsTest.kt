package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynMemoryToolsTest {
    private val now = 10_000_000_000L
    private val global = KellvynMemoryAddress.global()
    private val projectA = KellvynMemoryAddress.project("project-a")
    private val projectB = KellvynMemoryAddress.project("project-b")

    @Test
    fun memorySearchUsesOnlyConversationAddressesAndOmitsSensitiveOrUnknownEntries() = runBlocking {
        val repository = FakeRepository(
            listOf(
                entry("g1", global, "Preferência de idioma", "Responder em português brasileiro."),
                entry("a1", projectA, "Decisão de arquitetura", "O projeto usa Kotlin e SQLite."),
                entry("b1", projectB, "Outro projeto", "O módulo de pagamentos está isolado."),
                entry(
                    "s1",
                    global,
                    "Informação pessoal sensível",
                    "Conteúdo sensível que não deve ser devolvido.",
                    privacy = KellvynMemoryPrivacyClass.SENSITIVE_PERSONAL,
                    sensitiveApproved = true
                ),
                entry(
                    "u1",
                    projectA,
                    "Classificação desconhecida",
                    "Conteúdo cuja privacidade não foi classificada.",
                    privacy = KellvynMemoryPrivacyClass.UNKNOWN
                ),
                entry(
                    "c1",
                    global,
                    "Credencial antiga",
                    "Token antigo que não pode ser exposto.",
                    privacy = KellvynMemoryPrivacyClass.CREDENTIAL
                )
            )
        )
        val tool = KellvynMemoryTools.create(
            memory = KellvynMemory(repository),
            addressProvider = { listOf(global, projectA) },
            clock = { now }
        ).single()

        assertEquals(KellvynToolRisk.READ_ONLY, tool.spec.risk)
        assertEquals(setOf(KellvynStudioCapability.MEMORY_READ), tool.spec.capabilities)
        assertEquals(setOf("query"), tool.spec.requiredArguments)
        assertEquals(setOf("limit"), tool.spec.optionalArguments)

        val result = tool.execute(mapOf("query" to "idioma arquitetura projeto"))

        assertTrue(result.success)
        assertTrue(result.content.contains("id=g1"))
        assertTrue(result.content.contains("id=a1"))
        assertTrue(result.content.contains("Preferência de idioma"))
        assertTrue(result.content.contains("Decisão de arquitetura"))
        assertFalse(result.content.contains("Outro projeto"))
        assertFalse(result.content.contains("Conteúdo sensível"))
        assertFalse(result.content.contains("Classificação desconhecida"))
        assertFalse(result.content.contains("Token antigo"))
        assertTrue(result.content.contains("dado não confiável"))
    }

    @Test
    fun memorySearchDoesNotAllowTheModelToChooseOrExpandMemoryScopes() = runBlocking {
        val repository = FakeRepository(
            listOf(
                entry("g1", global, "Preferência", "Idioma português."),
                entry("a1", projectA, "Projeto A", "Dados de arquitetura."),
                entry("b1", projectB, "Projeto B", "Dados secretos do projeto B.")
            )
        )
        val tool = KellvynMemoryTools.create(
            memory = KellvynMemory(repository),
            addressProvider = { listOf(global) },
            clock = { now }
        ).single()

        val result = tool.execute(
            mapOf("query" to "projeto", "project_id" to "project-b")
        )

        assertFalse(result.success)
        assertFalse(result.content.contains("Dados secretos do projeto B"))
    }

    @Test
    fun memorySearchHasBoundedOutputAndValidResultLimit() = runBlocking {
        val many = (1..12).map { index ->
            entry(
                id = "entry-$index",
                address = global,
                title = "Memória sobre desenvolvimento Kotlin $index",
                content = "O desenvolvimento Kotlin usa contratos de ferramentas e memória local. ".repeat(12)
            )
        }
        val tool = KellvynMemoryTools.create(
            memory = KellvynMemory(FakeRepository(many)),
            addressProvider = { listOf(global) },
            clock = { now }
        ).single()

        val result = tool.execute(mapOf("query" to "desenvolvimento kotlin", "limit" to "2"))

        assertTrue(result.success)
        assertTrue(result.content.length <= 3_200)
        assertEquals(2, Regex("\\n[12]\\) ").findAll(result.content).count())
        assertFalse(tool.execute(mapOf("query" to "kotlin", "limit" to "6")).success)
    }

    @Test
    fun emptyAuthorizedScopeReturnsNoData() = runBlocking {
        var repositoryRead = false
        val repository = FakeRepository(emptyList()) { repositoryRead = true }
        val tool = KellvynMemoryTools.create(
            memory = KellvynMemory(repository),
            addressProvider = { emptyList() },
            clock = { now }
        ).single()

        val result = tool.execute(mapOf("query" to "kotlin"))

        assertTrue(result.success)
        assertFalse(repositoryRead)
        assertTrue(result.content.contains("Nenhum escopo"))
    }

    private fun entry(
        id: String,
        address: KellvynMemoryAddress,
        title: String,
        content: String,
        privacy: KellvynMemoryPrivacyClass = KellvynMemoryPrivacyClass.NON_SENSITIVE,
        sensitiveApproved: Boolean = false
    ) = KellvynMemoryEntry(
        id = id,
        address = address,
        kind = KellvynMemoryKind.PROJECT_DECISION,
        title = title,
        content = content,
        tags = listOf("kotlin", "arquitetura"),
        source = KellvynMemorySource.USER_EXPLICIT,
        provenance = "Teste sintético autorizado.",
        confidence = 0.95,
        createdAtMillis = now - 1_000L,
        privacyClass = privacy,
        sensitiveDataApproved = sensitiveApproved,
        userApproved = true
    )

    private class FakeRepository(
        private val entries: List<KellvynMemoryEntry>,
        private val onRead: () -> Unit = {}
    ) : KellvynMemoryRepository {
        override suspend fun put(entry: KellvynMemoryEntry) = Unit

        override suspend fun loadCandidates(query: KellvynMemoryQuery): List<KellvynMemoryEntry> {
            onRead()
            return entries
        }

        override suspend fun delete(id: String, address: KellvynMemoryAddress) = false
        override suspend fun deleteScope(address: KellvynMemoryAddress) = 0
        override suspend fun purgeExpired(nowMillis: Long) = 0
    }
}
