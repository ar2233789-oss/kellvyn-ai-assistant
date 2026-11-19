package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KellvynAgentLabStudioTest {
    @Test
    fun matrixUsesCentralPolicyAndRespectsOwnerWebSwitch() = runBlocking {
        val root = Files.createTempDirectory("agent-lab-matrix").toFile()
        try {
            val manager = policyProbeManager()
            val enabled = createStudio(root, manager, true).tools.single {
                it.spec.name == KellvynAgentLabStudio.SPECIALIST_MATRIX_TOOL
            }.execute(emptyMap())
            assertTrue(enabled.success)
            assertTrue(enabled.content.contains("wikipedia_search[NETWORK_READ]"))
            assertTrue(enabled.content.contains("workspace_read_file[READ_ONLY]"))
            assertFalse(enabled.content.contains("workspace_write_file[REVERSIBLE_WRITE]"))
            assertFalse(
                enabled.content.substringAfter("CODE_EXPLORER")
                    .substringBefore("\n\n")
                    .contains("wikipedia_search")
            )

            val disabled = createStudio(root, manager, false).tools.single {
                it.spec.name == KellvynAgentLabStudio.SPECIALIST_MATRIX_TOOL
            }.execute(emptyMap())
            assertTrue(disabled.success)
            assertTrue(disabled.content.contains("Pesquisa de rede controlada pelo proprietário: DESATIVADA"))
            assertFalse(disabled.content.contains("wikipedia_search[NETWORK_READ]"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun environmentStatusReportsThermalAndPrivateStorageMetrics() = runBlocking {
        val root = Files.createTempDirectory("agent-lab-status").toFile()
        try {
            val manager = emptyManager()
            val sample = resourceSnapshot().copy(
                thermalHeadroomForecast = 0.41f,
                thermalModerateHeadroomThreshold = 0.68f,
                thermalSevereHeadroomThreshold = 0.91f,
                availableAppDataBytes = 320L * MIB,
                totalAppDataBytes = 512L * MIB
            )
            val studio = createStudio(root, manager, false) { sample }
            val result = studio.tools.single {
                it.spec.name == KellvynAgentLabStudio.ENVIRONMENT_STATUS_TOOL
            }.execute(emptyMap())
            assertTrue(result.success)
            assertTrue(result.content.contains("Thermal headroom previsto: 0.41"))
            assertTrue(result.content.contains("limiar OEM moderado: 0.68"))
            assertTrue(result.content.contains("limiar OEM severo: 0.91"))
            assertTrue(result.content.contains("Armazenamento privado do app disponível/total: 320 / 512 MiB"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun benchmarkReadinessRequiresAllFourteenCompiledProfilesAndTwentyEightValidCases() = runBlocking {
        val root = Files.createTempDirectory("agent-lab-readiness").toFile()
        try {
            val registry = KellvynSpecialistProfileRegistry.default()
            val studio = createStudio(
                root = root,
                manager = emptyManager(),
                network = false,
                profiles = registry.profiles()
            )
            val result = studio.tools.single {
                it.spec.name == KellvynAgentLabStudio.BENCHMARK_READINESS_TOOL
            }.execute(emptyMap())

            assertTrue(result.success)
            assertTrue(result.content.contains("PERFIS ATIVOS: 14/14"))
            assertTrue(result.content.contains("PROFILE COMPILER: APROVADO — 14/14 compilados"))
            assertTrue(result.content.contains("BENCHMARK REGISTRY: VÁLIDO — 28/28 casos"))
            assertTrue(result.content.contains("PAPÉIS COM PERFIL E PELO MENOS 2 CASOS: 14/14"))
            assertTrue(result.content.contains("EXECUÇÃO COGNITIVA: NÃO EXECUTADA"))
            assertTrue(result.content.contains("PRONTO PARA BENCHMARK COM MOTOR E VERIFICADOR"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reasoningAlternativeRouteBenchmarkRemainsInTheFourteenRoleSuite() {
        val profiles = KellvynSpecialistProfileRegistry.default()
        val profile = profiles.requireProfile(KellvynSpecialistRole.REASONING_ANALYST)
        val cases = KellvynSpecialistBenchmarkRegistry.default()
            .forRole(KellvynSpecialistRole.REASONING_ANALYST)

        assertTrue(profile.version == 2)
        assertTrue(profile.instruction.contains("fallback seguro"))
        assertTrue(profiles.compileAll().success)
        assertTrue(cases.size == 2)
        val routeCase = cases.single { it.caseId == "reasoning-alternative-delivery-routes" }
        assertTrue(routeCase.task.contains("pelo menos duas rotas"))
        assertTrue(routeCase.requiredEvidence.any { it.contains("experimento") })
        assertTrue(routeCase.prohibitedBehaviors.any { it.contains("Poco F6 pessoal") })
    }

    @Test
    fun benchmarkReadinessBlocksWhenAnActiveSpecialistProfileIsMissing() = runBlocking {
        val root = Files.createTempDirectory("agent-lab-readiness-missing").toFile()
        try {
            val profiles = KellvynSpecialistProfileRegistry.default().profiles()
                .filterNot { it.role == KellvynSpecialistRole.CODE_EXPLORER }
            val studio = createStudio(
                root = root,
                manager = emptyManager(),
                network = false,
                profiles = profiles
            )
            val result = studio.tools.single {
                it.spec.name == KellvynAgentLabStudio.BENCHMARK_READINESS_TOOL
            }.execute(emptyMap())

            assertTrue(result.success)
            assertTrue(result.content.contains("PERFIS ATIVOS: 13/14"))
            assertTrue(result.content.contains("PROFILE COMPILER: REPROVADO"))
            assertTrue(result.content.contains("BLOQUEADO POR LACUNAS ESTRUTURAIS"))
            assertFalse(result.content.contains("PRONTO PARA BENCHMARK COM MOTOR E VERIFICADOR"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun delegationMetricsAggregateOnlyRecordedOperationalEvidence() = runBlocking {
        val root = Files.createTempDirectory("agent-lab-delegations").toFile()
        try {
            val traceId = "00000000-0000-0000-0000-000000000087"
            val store = KellvynAgentLabTraceStore(root, PassThroughCipher())
            appendDelegation(store, traceId, 1_000L, KellvynSpecialistRole.RESEARCHER, 100L, KellvynAgentLabOutcome.SUCCEEDED)
            appendDelegation(store, traceId, 1_100L, KellvynSpecialistRole.RESEARCHER, 200L, KellvynAgentLabOutcome.BLOCKED)
            appendDelegation(store, traceId, 1_200L, KellvynSpecialistRole.RESEARCHER, 300L, KellvynAgentLabOutcome.FAILED)
            appendDelegation(store, traceId, 1_300L, KellvynSpecialistRole.CODE_EXPLORER, 50L, KellvynAgentLabOutcome.SUCCEEDED)

            val studio = KellvynAgentLabStudio(
                traceStore = store,
                moduleManager = emptyManager(),
                resourceSnapshotProvider = { resourceSnapshot() },
                traceRecorderHealthProvider = { healthyTraceRecorder() }
            )
            val result = studio.tools.single {
                it.spec.name == KellvynAgentLabStudio.DELEGATION_METRICS_TOOL
            }.execute(emptyMap())

            assertTrue(result.success)
            assertTrue(result.content.contains("Delegações iniciadas: 4; encerradas: 4; abertas no recorte: 0"))
            assertTrue(result.content.contains("média=162 ms"))
            assertTrue(result.content.contains("p95=300 ms"))
            assertTrue(result.content.contains("máximo=300 ms"))
            assertTrue(result.content.contains("sucesso=2"))
            assertTrue(result.content.contains("bloqueio=1"))
            assertTrue(result.content.contains("falha=1"))
            assertTrue(result.content.contains("RESEARCHER: delegações=3"))
            assertTrue(result.content.contains("CODE_EXPLORER: delegações=1"))
            assertTrue(result.content.contains("Não medem"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun appendDelegation(
        store: KellvynAgentLabTraceStore,
        traceId: String,
        at: Long,
        role: KellvynSpecialistRole,
        duration: Long,
        outcome: KellvynAgentLabOutcome
    ) {
        store.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = at,
                kind = KellvynAgentLabEventKind.DELEGATION_STARTED,
                specialistRole = role,
                outcome = KellvynAgentLabOutcome.STARTED
            )
        )
        store.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = at + 1L,
                kind = KellvynAgentLabEventKind.DELEGATION_FINISHED,
                specialistRole = role,
                durationMillis = duration,
                outcome = outcome,
                detailCode = "DELEGATION_RESULT"
            )
        )
    }

    private fun createStudio(
        root: File,
        manager: KellvynStudioModuleManager,
        network: Boolean,
        sample: suspend () -> KellvynResourceSnapshot = { resourceSnapshot() },
        profiles: List<KellvynSpecialistProfile> = KellvynSpecialistProfileRegistry.default().profiles()
    ) = KellvynAgentLabStudio(
        traceStore = KellvynAgentLabTraceStore(root, PassThroughCipher()),
        moduleManager = manager,
        resourceSnapshotProvider = sample,
        traceRecorderHealthProvider = { healthyTraceRecorder() },
        networkReadEnabled = { network },
        specialistProfilesProvider = { profiles }
    )

    private fun policyProbeManager(): KellvynStudioModuleManager {
        val manager = emptyManager()
        val specs = listOf(
            KellvynToolSpec(
                name = "workspace_read_file",
                description = "Leitura.",
                requiredArguments = setOf("path"),
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolSpec(
                name = "workspace_write_file",
                description = "Escrita.",
                requiredArguments = setOf("path"),
                risk = KellvynToolRisk.REVERSIBLE_WRITE,
                capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
            ),
            KellvynToolSpec(
                name = "wikipedia_search",
                description = "Pesquisa Web.",
                requiredArguments = setOf("query"),
                risk = KellvynToolRisk.NETWORK_READ,
                capabilities = setOf(KellvynStudioCapability.WEB_RESEARCH)
            )
        )
        val moduleManifest = KellvynStudioModuleManifest(
            moduleId = "agent-policy-probe",
            version = 1,
            capabilities = setOf(
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.CODE_EDITING,
                KellvynStudioCapability.WEB_RESEARCH
            ),
            supportedExtensions = emptySet(),
            minSdk = 24,
            requiresNetwork = true,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedLoadWorkingSetBytes = 32L * KIB,
            estimatedResidentBytes = 16L * KIB,
            permissions = setOf(
                KellvynStudioPermission.NETWORK_ACCESS,
                KellvynStudioPermission.PRIVATE_WORKSPACE_WRITE
            ),
            toolSpecs = specs
        )
        manager.register(KellvynStudioModuleDefinition(moduleManifest) {
            object : KellvynStudioModule {
                override val manifest = moduleManifest
                override val tools = emptyList<KellvynAgentTool>()
                override fun trimTransientResources(level: KellvynCachePressure) = Unit
                override fun close() = Unit
            }
        })
        return manager
    }

    private fun emptyManager() = KellvynStudioModuleManager(
        deviceSdkInt = 35,
        taskBroker = KellvynTaskBroker(
            governor = KellvynResourceGovernor(),
            snapshotProvider = { resourceSnapshot() }
        )
    )

    private fun resourceSnapshot() = KellvynResourceSnapshot(
        totalSystemMemoryBytes = 8L * 1024L * MIB,
        availableSystemMemoryBytes = 3L * 1024L * MIB,
        processHeapUsedBytes = 96L * MIB,
        processHeapMaxBytes = 512L * MIB,
        processPssBytes = 128L * MIB,
        lowMemory = false,
        thermalBand = KellvynThermalBand.NONE,
        thermalHeadroomForecast = 0.2f,
        powerSaveMode = false,
        batteryPercent = 80,
        charging = false
    )

    private fun healthyTraceRecorder() = KellvynAgentLabTraceRecorderHealth(
        hasSuccessfulWrite = true,
        currentlyWritable = true,
        failureCount = 0,
        lastFailureCode = null,
        lastFailureTraceId = null,
        lastFailureEventKind = null
    )

    private class PassThroughCipher : KellvynTaskCheckpointCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = plaintext.copyOf()
        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = ciphertext.copyOf()
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val KIB = 1024L
    }
}
