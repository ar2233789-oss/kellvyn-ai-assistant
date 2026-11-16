package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynMemoryTest {
    private val now = 10_000_000_000L
    private val projectA = KellvynMemoryAddress.project("project-a")
    private val projectB = KellvynMemoryAddress.project("project-b")

    @Test
    fun explicitUserMemoryCanBeStored() = runBlocking {
        val memory = memory()
        val result = memory.remember(entry(), now)

        assertEquals(KellvynMemoryWriteResult.Stored("m-1"), result)
    }

    @Test
    fun modelInferenceCannotBecomeDurableMemoryAutomatically() = runBlocking {
        val memory = memory()
        val result = memory.remember(
            entry(source = KellvynMemorySource.MODEL_INFERENCE, userApproved = true),
            now
        )

        assertTrue(result is KellvynMemoryWriteResult.Rejected)
        assertEquals(0, memory(projectA).size)
    }

    @Test
    fun memoryWithoutExplicitOwnerConsentIsRejected() = runBlocking {
        val result = memory().remember(entry(userApproved = false), now)

        assertTrue(result is KellvynMemoryWriteResult.Rejected)
    }

    @Test
    fun likelyCredentialsAreNotStored() = runBlocking {
        val result = memory().remember(
            entry(content = "A chave api_key = sk-abcdefghijklmnopqrstuv deve ficar protegida."),
            now
        )

        assertTrue(result is KellvynMemoryWriteResult.Rejected)
    }

    @Test
    fun toolVerifiedMemoryStillNeedsOwnerApproval() = runBlocking {
        val approved = memory().remember(
            entry(source = KellvynMemorySource.TOOL_VERIFIED, userApproved = true),
            now
        )
        val unapproved = memory().remember(
            entry(id = "m-2", source = KellvynMemorySource.TOOL_VERIFIED, userApproved = false),
            now
        )

        assertTrue(approved is KellvynMemoryWriteResult.Stored)
        assertTrue(unapproved is KellvynMemoryWriteResult.Rejected)
    }

    @Test
    fun recallNeverCrossesProjectScope() = runBlocking {
        val repository = FakeRepository()
        val memory = KellvynMemory(repository)
        memory.remember(
            entry(content = "A chuva verde do projeto A usa animação fluida."),
            now
        )
        memory.remember(
            entry(
                id = "m-2",
                address = projectB,
                title = "Projeto B",
                content = "A chuva vermelha usa efeito de partículas."
            ),
            now
        )

        val results = memory.recall(
            KellvynMemoryQuery(
                text = "chuva animação",
                addresses = listOf(projectA),
                nowMillis = now
            )
        )

        assertEquals(1, results.size)
        assertEquals(projectA, results.single().entry.address)
    }

    @Test
    fun rankingNormalizesPortugueseAccentsAndReturnsMatchedTerms() {
        val item = entry(
            title = "Preferência de animação",
            content = "A chuva deve ter transição suave e partículas discretas."
        )
        val results = KellvynMemoryRanker.rank(
            listOf(item),
            KellvynMemoryQuery(
                text = "animacao chuva transicao suave",
                addresses = listOf(projectA),
                nowMillis = now
            )
        )

        assertEquals(1, results.size)
        assertTrue(results.single().matchedTerms.contains("animacao"))
        assertTrue(results.single().score > 0.5)
    }

    @Test
    fun expiredEntriesAreNotReturned() {
        val expired = entry(expiresAtMillis = now - 1L)
        val results = KellvynMemoryRanker.rank(
            listOf(expired),
            KellvynMemoryQuery(
                text = "chuva animacao",
                addresses = listOf(projectA),
                nowMillis = now
            )
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun irrelevantEntriesAreNotReturned() {
        val unrelated = entry(
            title = "Receita",
            content = "Pudim de leite condensado com ovos."
        )
        val results = KellvynMemoryRanker.rank(
            listOf(unrelated),
            KellvynMemoryQuery(
                text = "arquitetura agente android",
                addresses = listOf(projectA),
                nowMillis = now
            )
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun forgetDeletesOnlyTheSelectedRecordInItsScope() = runBlocking {
        val repository = FakeRepository()
        val memory = KellvynMemory(repository)
        memory.remember(entry(), now)
        memory.remember(entry(id = "m-2", address = projectB), now)

        assertTrue(memory.forget("m-1", projectA))
        assertEquals(listOf("m-2"), repository.all.map { it.id })
    }

    @Test
    fun forgetScopeDoesNotDeleteOtherProjects() = runBlocking {
        val repository = FakeRepository()
        val memory = KellvynMemory(repository)
        memory.remember(entry(), now)
        memory.remember(entry(id = "m-2", address = projectB), now)

        assertEquals(1, memory.forgetScope(projectA))
        assertEquals(listOf("m-2"), repository.all.map { it.id })
    }

    @Test
    fun expiredMemoryCanBePurged() = runBlocking {
        val repository = FakeRepository()
        val memory = KellvynMemory(repository)
        memory.remember(entry(id = "old", expiresAtMillis = now + 1L), now)
        memory.remember(entry(id = "live", expiresAtMillis = now + 50_000L), now)

        assertEquals(1, memory.purgeExpired(now + 2L))
        assertEquals(listOf("live"), repository.all.map { it.id })
    }

    @Test
    fun queryRejectsUnboundedScopeOrResultLimits() {
        try {
            KellvynMemoryRanker.rank(
                emptyList(),
                KellvynMemoryQuery(
                    text = "agent",
                    addresses = (1..9).map { KellvynMemoryAddress.project("p-$it") },
                    nowMillis = now
                )
            )
            throw AssertionError("A consulta aceitou escopos demais.")
        } catch (_: IllegalArgumentException) {
            assertFalse(false)
        }
    }

    private fun memory(repository: FakeRepository = FakeRepository()) = KellvynMemory(repository)

    private fun memory(address: KellvynMemoryAddress): List<KellvynMemoryEntry> =
        runBlocking { repositoryForTests.loadCandidates(
            KellvynMemoryQuery("chuva", listOf(address), now)
        ) }

    private fun entry(
        id: String = "m-1",
        address: KellvynMemoryAddress = projectA,
        title: String = "Preferência visual",
        content: String = "A chuva verde usa animação fluida.",
        source: KellvynMemorySource = KellvynMemorySource.USER_EXPLICIT,
        userApproved: Boolean = true,
        expiresAtMillis: Long? = null
    ) = KellvynMemoryEntry(
        id = id,
        address = address,
        kind = KellvynMemoryKind.PROJECT_DECISION,
        title = title,
        content = content,
        tags = listOf("chuva", "animação"),
        source = source,
        provenance = "Confirmação explícita do proprietário no projeto de laboratório.",
        confidence = 1.0,
        createdAtMillis = now - 1_000L,
        expiresAtMillis = expiresAtMillis,
        userApproved = userApproved
    )

    private val repositoryForTests = FakeRepository()

    private class FakeRepository : KellvynMemoryRepository {
        val all = mutableListOf<KellvynMemoryEntry>()

        override suspend fun put(entry: KellvynMemoryEntry) {
            all.removeAll { it.id == entry.id && it.address == entry.address }
            all += entry
        }

        override suspend fun loadCandidates(query: KellvynMemoryQuery): List<KellvynMemoryEntry> =
            all.filter {
                it.address in query.addresses &&
                    (it.expiresAtMillis == null || it.expiresAtMillis > query.nowMillis)
            }

        override suspend fun delete(id: String, address: KellvynMemoryAddress): Boolean =
            all.removeAll { it.id == id && it.address == address }

        override suspend fun deleteScope(address: KellvynMemoryAddress): Int {
            val oldSize = all.size
            all.removeAll { it.address == address }
            return oldSize - all.size
        }

        override suspend fun purgeExpired(nowMillis: Long): Int {
            val oldSize = all.size
            all.removeAll { it.expiresAtMillis != null && it.expiresAtMillis <= nowMillis }
            return oldSize - all.size
        }
    }
}
