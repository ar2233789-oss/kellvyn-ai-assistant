package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAgentTest {
    @Test
    fun toolResultIsReturnedToModelBeforeFinalAnswer() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool("read_note", mapOf("name" to "plan")),
            KellvynModelDecision.Final("Li o plano e encontrei três etapas.")
        )
        val tool = FakeTool(
            KellvynToolSpec("read_note", "Lê uma nota", requiredArguments = setOf("name")),
            result = KellvynToolResult(true, "Três etapas.")
        )
        val agent = KellvynAgent(model, KellvynToolRegistry(listOf(tool)))

        val result = agent.run("Leia a nota plan.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(1, (result as KellvynAgentResult.Completed).toolCalls)
        assertEquals(2, model.calls)
        assertTrue(model.historySeen.last().any { it.role == KellvynMessageRole.TOOL && it.trust == KellvynTrustLevel.UNTRUSTED })
        assertTrue(model.historySeen.last().any { it.content.contains("AINDA NÃO FOI VERIFICADO INDEPENDENTEMENTE") })
        assertEquals(1, tool.executions)
    }

    @Test
    fun unknownToolIsNeverExecuted() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool("shell_everything", emptyMap()),
            KellvynModelDecision.Final("Não existe essa ferramenta registrada.")
        )
        val agent = KellvynAgent(model, KellvynToolRegistry(emptyList()))

        val result = agent.run("Execute algo.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertTrue(model.historySeen.last().any { message -> message.content.contains("Ferramenta inexistente") })
    }

    @Test
    fun mutatingToolIsDeniedByDefaultPolicy() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool("delete_file", mapOf("path" to "a.txt")),
            KellvynModelDecision.Final("A exclusão foi bloqueada.")
        )
        val tool = FakeTool(
            KellvynToolSpec(
                "delete_file",
                "Exclui arquivo",
                requiredArguments = setOf("path"),
                risk = KellvynToolRisk.DESTRUCTIVE
            ),
            KellvynToolResult(true, "deleted")
        )
        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool))).run("Apague a.txt.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(0, tool.executions)
    }

    @Test
    fun invalidArgumentsAreRejectedBeforeExecution() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool("read_note", mapOf("unexpected" to "value")),
            KellvynModelDecision.Final("A chamada tinha parâmetros inválidos.")
        )
        val tool = FakeTool(
            KellvynToolSpec("read_note", "Lê uma nota", requiredArguments = setOf("name")),
            KellvynToolResult(true, "should not happen")
        )

        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool))).run("Leia a nota.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(0, tool.executions)
    }

    @Test
    fun repeatedIdenticalToolCallIsBlocked() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool("read_note", mapOf("name" to "plan")),
            KellvynModelDecision.CallTool("read_note", mapOf("name" to "plan")),
            KellvynModelDecision.Final("A repetição foi interrompida.")
        )
        val tool = FakeTool(
            KellvynToolSpec("read_note", "Lê uma nota", requiredArguments = setOf("name")),
            KellvynToolResult(true, "ok")
        )

        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool))).run("Leia a nota.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(1, tool.executions)
        assertTrue(model.historySeen.last().any { message -> message.content.contains("Chamada repetida bloqueada") })
    }

    @Test
    fun agentStopsAtConfiguredStepLimit() = runBlocking {
        val model = FakeEngine(
            *Array(4) { KellvynModelDecision.CallTool("read_note", mapOf("name" to "next")) }
        )
        val tool = FakeTool(
            KellvynToolSpec("read_note", "Lê uma nota", requiredArguments = setOf("name")),
            KellvynToolResult(true, "ok")
        )
        val config = KellvynAgentConfig(maxSteps = 2, maxToolCalls = 5, maxSameCallRepeats = 10)
        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool)), config = config).run("Continue.")

        assertTrue(result is KellvynAgentResult.LimitReached)
        assertEquals(2, result.steps)
    }

    @Test
    fun approvedScopedMemoryIsProvidedAsUntrustedContext() = runBlocking {
        val repository = FakeMemoryRepository()
        val memory = KellvynMemory(repository)
        val address = KellvynMemoryAddress.project("kellvyn")
        memory.remember(
            KellvynMemoryEntry(
                id = "memory-1",
                address = address,
                kind = KellvynMemoryKind.PROJECT_DECISION,
                title = "Efeitos visuais aprovados",
                content = "Chuva e relâmpagos podem funcionar simultaneamente.",
                tags = listOf("chuva", "relâmpago"),
                source = KellvynMemorySource.USER_EXPLICIT,
                provenance = "Decisão confirmada pelo proprietário.",
                confidence = 1.0,
                createdAtMillis = System.currentTimeMillis(),
                privacyClass = KellvynMemoryPrivacyClass.NON_SENSITIVE,
                userApproved = true
            ),
            System.currentTimeMillis()
        )

        val model = FakeEngine(KellvynModelDecision.Final("Entendido."))
        val agent = KellvynAgent(
            inference = model,
            tools = KellvynToolRegistry(emptyList()),
            memory = memory,
            memoryAddresses = listOf(address)
        )

        val result = agent.run("Como os efeitos visuais devem funcionar?")

        assertTrue(result is KellvynAgentResult.Completed)
        val messages = model.historySeen.single()
        val recalled = messages.single { it.source == "memory:memory-1" }
        assertEquals(KellvynTrustLevel.UNTRUSTED, recalled.trust)
        assertTrue(recalled.content.contains("Chuva e relâmpagos"))
        assertTrue(messages.first().content.contains("Memórias recuperadas são contexto"))
        assertEquals(1, repository.putCount)
    }

    @Test
    fun agentDoesNotRetrieveMemoryFromAnotherProject() = runBlocking {
        val repository = FakeMemoryRepository()
        repository.put(
            KellvynMemoryEntry(
                id = "other-project-memory",
                address = KellvynMemoryAddress.project("other-project"),
                kind = KellvynMemoryKind.PROJECT_FACT,
                title = "Outro projeto",
                content = "O projeto usa tema azul.",
                tags = listOf("tema", "azul"),
                source = KellvynMemorySource.USER_EXPLICIT,
                provenance = "Registro explícito.",
                confidence = 1.0,
                createdAtMillis = System.currentTimeMillis(),
                privacyClass = KellvynMemoryPrivacyClass.NON_SENSITIVE,
                userApproved = true
            )
        )
        val model = FakeEngine(KellvynModelDecision.Final("Sem contexto externo."))
        val agent = KellvynAgent(
            inference = model,
            tools = KellvynToolRegistry(emptyList()),
            memory = KellvynMemory(repository),
            memoryAddresses = listOf(KellvynMemoryAddress.project("kellvyn"))
        )

        agent.run("Qual é o tema do projeto?")

        assertFalse(model.historySeen.single().any { it.source.startsWith("memory:") })
    }

    @Test
    fun finalAnswerCanCompleteWithoutToolCalls() = runBlocking {
        val model = FakeEngine(KellvynModelDecision.Final("Olá."))
        val result = KellvynAgent(model, KellvynToolRegistry(emptyList())).run("Olá.")

        assertEquals(KellvynAgentResult.Completed("Olá.", 1, 0), result)
    }

    private class FakeMemoryRepository : KellvynMemoryRepository {
        private val entries = mutableListOf<KellvynMemoryEntry>()
        var putCount = 0
            private set

        override suspend fun put(entry: KellvynMemoryEntry) {
            putCount++
            entries.removeAll { it.id == entry.id && it.address == entry.address }
            entries += entry
        }

        override suspend fun loadCandidates(query: KellvynMemoryQuery): List<KellvynMemoryEntry> =
            entries.filter {
                it.address in query.addresses &&
                    (it.expiresAtMillis == null || it.expiresAtMillis > query.nowMillis)
            }

        override suspend fun delete(id: String, address: KellvynMemoryAddress): Boolean =
            entries.removeAll { it.id == id && it.address == address }

        override suspend fun deleteScope(address: KellvynMemoryAddress): Int {
            val previous = entries.size
            entries.removeAll { it.address == address }
            return previous - entries.size
        }

        override suspend fun purgeExpired(nowMillis: Long): Int {
            val previous = entries.size
            entries.removeAll { it.expiresAtMillis != null && it.expiresAtMillis <= nowMillis }
            return previous - entries.size
        }
    }

    private class FakeEngine(vararg decisions: KellvynModelDecision) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        var calls = 0
        val historySeen = mutableListOf<List<KellvynAgentMessage>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            calls++
            historySeen += history
            return pending.removeFirst()
        }
    }

    private class FakeTool(
        override val spec: KellvynToolSpec,
        private val result: KellvynToolResult
    ) : KellvynAgentTool {
        var executions = 0
        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            executions++
            return result
        }
    }
}
