package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynSpecialistAgentBenchmarkExecutorTest {

    @Test
    fun runsCompiledSpecialistAndCapturesToolExecutionInSharedTrace() = runBlocking {
        val testCase = KellvynSpecialistBenchmarkRegistry.default()
            .forRole(KellvynSpecialistRole.CODE_EXPLORER).first()
        val profile = compiledProfile(KellvynSpecialistRole.CODE_EXPLORER)
        val tool = RecordingTool(
            KellvynToolSpec(
                name = "read_source",
                description = "Lê um arquivo de código.",
                requiredArguments = setOf("path"),
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolResult(true, "A função SourceIndex.find() está no arquivo SourceIndex.kt.")
        )
        val engine = RecordingEngine(
            KellvynModelDecision.CallTool("read_source", mapOf("path" to "SourceIndex.kt")),
            KellvynModelDecision.Final("A função está em SourceIndex.kt.")
        )
        val recorder = RecordingTraceRecorder()
        val parent = KellvynAgentLabTraceSession.create(recorder)
        val caseTrace = parent.forActor("benchmark:code_explorer")
        val executor = KellvynSpecialistAgentBenchmarkExecutor(
            inference = engine,
            registeredTools = KellvynToolRegistry(listOf(tool))
        )

        val result = executor.executeTraced(
            testCase = testCase,
            profile = profile,
            allowedTools = listOf(tool.spec),
            traceSession = caseTrace
        )

        assertEquals(testCase.caseId, result.caseId)
        assertEquals(profile.profileSha256, result.profileSha256)
        assertEquals(setOf("read_source"), result.requestedToolNames)
        assertEquals(setOf("read_source"), result.executedToolNames)
        assertTrue(result.policyViolations.isEmpty())
        assertTrue(result.failureReason == null)
        assertEquals(1, tool.executions)
        val allTraceEvents = recorder.events
        assertTrue(allTraceEvents.any {
            it.kind == KellvynAgentLabEventKind.AGENT_STARTED &&
                it.actor == "benchmark:code_explorer"
        })
        assertTrue(allTraceEvents.any {
            it.kind == KellvynAgentLabEventKind.TOOL_FINISHED &&
                it.toolName == "read_source"
        })
        assertTrue(allTraceEvents.any {
            it.kind == KellvynAgentLabEventKind.AGENT_FINISHED
        })
    }

    @Test
    fun toolOutsideRoleMatrixIsNeverExposedOrExecuted() = runBlocking {
        val testCase = KellvynSpecialistBenchmarkRegistry.default()
            .forRole(KellvynSpecialistRole.CODE_EXPLORER).first()
        val profile = compiledProfile(KellvynSpecialistRole.CODE_EXPLORER)
        val writeTool = RecordingTool(
            KellvynToolSpec(
                name = "workspace_write_file",
                description = "Escreve um arquivo.",
                requiredArguments = setOf("path"),
                risk = KellvynToolRisk.REVERSIBLE_WRITE,
                capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
            ),
            KellvynToolResult(true, "não deveria executar")
        )
        val engine = RecordingEngine(
            KellvynModelDecision.CallTool("workspace_write_file", mapOf("path" to "README.md")),
            KellvynModelDecision.Final("A operação foi bloqueada.")
        )
        val executor = KellvynSpecialistAgentBenchmarkExecutor(
            inference = engine,
            registeredTools = KellvynToolRegistry(listOf(writeTool))
        )

        val result = executor.execute(
            testCase = testCase,
            profile = profile,
            allowedTools = listOf(writeTool.spec)
        )

        assertEquals(setOf("workspace_write_file"), result.requestedToolNames)
        assertTrue(result.executedToolNames.isEmpty())
        assertEquals(0, writeTool.executions)
        assertTrue(result.policyViolations.any {
            it.contains("OUTSIDE_ROLE_POLICY") || it.contains("NOT_ALLOWED")
        })
    }

    @Test
    fun benchmarkExecutorNeverAllowsNestedDelegation() = runBlocking {
        val testCase = KellvynSpecialistBenchmarkRegistry.default()
            .forRole(KellvynSpecialistRole.RESEARCHER).first()
        val profile = compiledProfile(KellvynSpecialistRole.RESEARCHER)
        val engine = RecordingEngine(
            KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.CODE_EXPLORER,
                task = "Tente delegar novamente."
            ),
            KellvynModelDecision.Final("Delegação bloqueada.")
        )
        val executor = KellvynSpecialistAgentBenchmarkExecutor(
            inference = engine,
            registeredTools = KellvynToolRegistry(emptyList())
        )

        val result = executor.execute(testCase, profile, emptyList())

        assertTrue(result.policyViolations.contains("BENCHMARK_NESTED_DELEGATION_REQUESTED"))
        assertTrue(result.failureReason == null)
        assertEquals(0, result.executedToolNames.size)
    }

    private fun compiledProfile(role: KellvynSpecialistRole): KellvynCompiledSpecialistProfile =
        KellvynSpecialistProfileRegistry.default().compileAll().profiles.single { it.role == role }

    private class RecordingEngine(vararg decisions: KellvynModelDecision) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        val availableToolHistory = mutableListOf<List<KellvynToolSpec>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            availableToolHistory += availableTools.toList()
            check(pending.isNotEmpty()) { "O teste pediu uma decisão adicional inesperada." }
            return pending.removeFirst()
        }
    }

    private class RecordingTool(
        override val spec: KellvynToolSpec,
        private val result: KellvynToolResult
    ) : KellvynAgentTool {
        var executions = 0

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            executions++
            return result
        }
    }

    private class RecordingTraceRecorder : KellvynAgentLabTraceRecorder {
        val events = mutableListOf<KellvynAgentLabEvent>()

        override suspend fun record(event: KellvynAgentLabEvent) {
            events += event
        }
    }
}
