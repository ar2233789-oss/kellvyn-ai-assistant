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
    fun recordsAgentModelLifecycleAutomaticallyWithoutPersistingConversationContent() = runBlocking {
        val recorder = RecordingTraceRecorder()
        val clock = java.util.concurrent.atomic.AtomicLong(1_000L)
        val trace = KellvynAgentLabTraceSession.create(recorder) { clock.getAndIncrement() }
        val model = FakeEngine(KellvynModelDecision.Final("RESPOSTA_PRIVADA_QUE_NAO_DEVE_IR_PARA_O_TRACE"))
        val agent = KellvynAgent(
            inference = model,
            tools = KellvynToolRegistry(emptyList()),
            traceSessionFactory = { trace }
        )

        val result = agent.run("PROMPT_PRIVADO_QUE_NAO_DEVE_IR_PARA_O_TRACE")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(
            listOf(
                KellvynAgentLabEventKind.RUN_STARTED,
                KellvynAgentLabEventKind.MODEL_CALL_STARTED,
                KellvynAgentLabEventKind.MODEL_DECISION,
                KellvynAgentLabEventKind.RUN_COMPLETED
            ),
            recorder.events.map { it.kind }
        )
        assertEquals(
            listOf(
                KellvynAgentLabOutcome.STARTED,
                KellvynAgentLabOutcome.STARTED,
                KellvynAgentLabOutcome.SUCCEEDED,
                KellvynAgentLabOutcome.SUCCEEDED
            ),
            recorder.events.mapNotNull { it.outcome }
        )
        assertFalse(recorder.events.toString().contains("PROMPT_PRIVADO"))
        assertFalse(recorder.events.toString().contains("RESPOSTA_PRIVADA"))
    }

    @Test
    fun recordsMemoryHeapPssBatteryAndThermalSnapshotsAtRunBoundaries() = runBlocking {
        val recorder = RecordingTraceRecorder()
        val trace = KellvynAgentLabTraceSession.create(recorder)
        val snapshots = listOf(
            KellvynResourceSnapshot(
                totalSystemMemoryBytes = 8L * 1024L * 1024L * 1024L,
                availableSystemMemoryBytes = 3L * 1024L * 1024L * 1024L,
                processHeapUsedBytes = 200L * 1024L * 1024L,
                processHeapMaxBytes = 1L * 1024L * 1024L * 1024L,
                processPssBytes = 260L * 1024L * 1024L,
                lowMemory = false,
                thermalBand = KellvynThermalBand.LIGHT,
                thermalHeadroomForecast = null,
                powerSaveMode = false,
                batteryPercent = 87,
                charging = false,
                processCpuTimeMillis = 100L,
                sampledAtElapsedRealtimeMillis = 1_000L,
                availableProcessors = 8
            ),
            KellvynResourceSnapshot(
                totalSystemMemoryBytes = 8L * 1024L * 1024L * 1024L,
                availableSystemMemoryBytes = 2L * 1024L * 1024L * 1024L,
                processHeapUsedBytes = 220L * 1024L * 1024L,
                processHeapMaxBytes = 1L * 1024L * 1024L * 1024L,
                processPssBytes = 280L * 1024L * 1024L,
                lowMemory = false,
                thermalBand = KellvynThermalBand.MODERATE,
                thermalHeadroomForecast = null,
                powerSaveMode = false,
                batteryPercent = 86,
                charging = false,
                processCpuTimeMillis = 420L,
                sampledAtElapsedRealtimeMillis = 3_000L,
                availableProcessors = 8
            )
        )
        var snapshotIndex = 0
        val agent = KellvynAgent(
            inference = FakeEngine(KellvynModelDecision.Final("Concluído.")),
            tools = KellvynToolRegistry(emptyList()),
            traceSessionFactory = { trace },
            resourceSnapshotProvider = { snapshots[snapshotIndex++] }
        )

        val result = agent.run("Execute uma tarefa sem ferramentas.")

        assertTrue(result is KellvynAgentResult.Completed)
        val resourceEvents = recorder.events.filter {
            it.kind == KellvynAgentLabEventKind.RESOURCE_SNAPSHOT
        }
        assertEquals(2, resourceEvents.size)
        assertTrue(resourceEvents[0].detailCode!!.startsWith("START-"))
        assertTrue(resourceEvents[0].detailCode!!.contains("-B87-TLIGHT"))
        assertTrue(resourceEvents[1].detailCode!!.startsWith("END-"))
        assertTrue(resourceEvents[1].detailCode!!.contains("-B86-TMODERATE"))
        assertTrue(resourceEvents[1].detailCode!!.contains("-DC320-CPU2"))
    }

    @Test
    fun unknownToolIsNeverExecutedAndBlockedRequestIsTracedWithoutArguments() = runBlocking {
        val recorder = RecordingTraceRecorder()
        val trace = KellvynAgentLabTraceSession.create(recorder)
        val model = FakeEngine(
            KellvynModelDecision.CallTool("shell_everything", mapOf("token" to "PRIVATE_ARGUMENT")),
            KellvynModelDecision.Final("Não existe essa ferramenta registrada.")
        )
        val agent = KellvynAgent(
            inference = model,
            tools = KellvynToolRegistry(emptyList()),
            traceSessionFactory = { trace }
        )

        val result = agent.run("Execute algo.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertTrue(model.historySeen.last().any { message -> message.content.contains("Ferramenta inexistente") })
        val blocked = recorder.events.single {
            it.kind == KellvynAgentLabEventKind.TOOL_FINISHED &&
                it.outcome == KellvynAgentLabOutcome.BLOCKED
        }
        assertEquals("shell_everything", blocked.toolName)
        assertEquals("NOT_REGISTERED", blocked.detailCode)
        assertFalse(recorder.events.toString().contains("PRIVATE_ARGUMENT"))
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
    fun activeProjectResultsAreInjectedAsUntrustedData() = runBlocking {
        val search = FakeProjectSearch(
            KellvynProjectSearchReport(
                projectId = "kellvyn",
                query = "onde está a memória",
                hits = listOf(
                    KellvynProjectSearchHit(
                        path = "src/Memory.kt",
                        snippet = "12: class KellvynMemory",
                        score = 0.95,
                        matchedTerms = setOf("memoria"),
                        truncated = false,
                        sizeBytes = 120
                    )
                ),
                filesScanned = 4,
                filesSkipped = 1
            )
        )
        val model = FakeEngine(KellvynModelDecision.Final("Encontrei o arquivo."))
        val agent = KellvynAgent(
            inference = model,
            tools = KellvynToolRegistry(emptyList()),
            projectContextRetriever = search,
            activeProjectId = "kellvyn"
        )

        val result = agent.run("Onde está a memória?")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(1, search.calls)
        val fileContext = model.historySeen.single().single { it.source == "workspace:kellvyn:src/Memory.kt" }
        assertEquals(KellvynTrustLevel.UNTRUSTED, fileContext.trust)
        assertTrue(fileContext.content.contains("class KellvynMemory"))
    }

    @Test
    fun projectFilesAreNotReadWhenNoProjectIsExplicitlyActive() = runBlocking {
        val search = FakeProjectSearch(
            KellvynProjectSearchReport("kellvyn", "pedido", emptyList(), 0, 0)
        )
        val model = FakeEngine(KellvynModelDecision.Final("Resposta."))
        val agent = KellvynAgent(
            inference = model,
            tools = KellvynToolRegistry(emptyList()),
            projectContextRetriever = search
        )

        agent.run("Explique a arquitetura.")

        assertEquals(0, search.calls)
    }
    @Test
    fun finalAnswerCanCompleteWithoutToolCalls() = runBlocking {
        val model = FakeEngine(KellvynModelDecision.Final("Olá."))
        val result = KellvynAgent(model, KellvynToolRegistry(emptyList())).run("Olá.")

        assertEquals(KellvynAgentResult.Completed("Olá.", 1, 0), result)
    }

    @Test
    fun typedIntegerSchemaRejectsMalformedValuesBeforeToolExecution() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool("set_count", mapOf("count" to "4.5")),
            KellvynModelDecision.Final("O valor foi recusado.")
        )
        val spec = KellvynToolSpec(
            name = "set_count",
            description = "Define um contador",
            requiredArguments = setOf("count"),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "count",
                    type = KellvynToolArgumentType.INTEGER,
                    minInteger = 0,
                    maxInteger = 10
                )
            )
        )
        val tool = FakeTool(spec, KellvynToolResult(true, "count=4"))
        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool))).run("Defina o contador.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(0, tool.executions)
        assertTrue(model.historySeen.last().any {
            it.content.contains("argumento 'count' inválido")
        })
    }

    @Test
    fun relativePathSchemaRejectsTraversalBeforeToolExecution() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool("read_file", mapOf("path" to "../secrets.txt")),
            KellvynModelDecision.Final("O caminho foi recusado.")
        )
        val spec = KellvynToolSpec(
            name = "read_file",
            description = "Lê um arquivo do workspace",
            requiredArguments = setOf("path"),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "path",
                    type = KellvynToolArgumentType.RELATIVE_PATH
                )
            )
        )
        val tool = FakeTool(spec, KellvynToolResult(true, "secret"))
        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool))).run("Leia um arquivo.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(0, tool.executions)
        assertTrue(model.historySeen.last().any {
            it.content.contains("caminho relativo seguro")
        })
    }

    @Test
    fun enumAndBooleanSchemasRejectValuesOutsideTheirDomains() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool(
                "configure",
                mapOf("enabled" to "sometimes", "mode" to "root")
            ),
            KellvynModelDecision.Final("As opções foram recusadas.")
        )
        val spec = KellvynToolSpec(
            name = "configure",
            description = "Configura um recurso",
            requiredArguments = setOf("enabled", "mode"),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "enabled",
                    type = KellvynToolArgumentType.BOOLEAN
                ),
                KellvynToolArgumentSchema(
                    name = "mode",
                    type = KellvynToolArgumentType.ENUM,
                    allowedValues = setOf("safe", "read_only")
                )
            )
        )
        val tool = FakeTool(spec, KellvynToolResult(true, "configured"))
        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool))).run("Configure o recurso.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(0, tool.executions)
        assertTrue(model.historySeen.last().any {
            it.content.contains("precisa ser true ou false")
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun typedSchemaMustDescribeEveryAcceptedArgument() {
        KellvynToolSpec(
            name = "configure",
            description = "Configura um recurso",
            requiredArguments = setOf("enabled", "mode"),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "enabled",
                    type = KellvynToolArgumentType.BOOLEAN
                )
            )
        )
    }

    @Test
    fun validTypedArgumentsReachTheTool() = runBlocking {
        val model = FakeEngine(
            KellvynModelDecision.CallTool(
                "configure",
                mapOf("enabled" to "true", "mode" to "safe")
            ),
            KellvynModelDecision.Final("Configuração aplicada.")
        )
        val spec = KellvynToolSpec(
            name = "configure",
            description = "Configura um recurso",
            requiredArguments = setOf("enabled", "mode"),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "enabled",
                    type = KellvynToolArgumentType.BOOLEAN
                ),
                KellvynToolArgumentSchema(
                    name = "mode",
                    type = KellvynToolArgumentType.ENUM,
                    allowedValues = setOf("safe", "read_only")
                )
            )
        )
        val tool = FakeTool(spec, KellvynToolResult(true, "configured"))
        val result = KellvynAgent(model, KellvynToolRegistry(listOf(tool))).run("Configure o recurso.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(1, tool.executions)
    }

    private class RecordingTraceRecorder : KellvynAgentLabTraceRecorder {
        val events = mutableListOf<KellvynAgentLabEvent>()

        override suspend fun record(event: KellvynAgentLabEvent) {
            events += event
        }
    }

    private class FakeProjectSearch(
        private val report: KellvynProjectSearchReport
    ) : KellvynProjectContextSearch {
        var calls = 0
            private set

        override suspend fun search(request: KellvynProjectSearchRequest): KellvynProjectSearchReport {
            calls++
            return report
        }
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
