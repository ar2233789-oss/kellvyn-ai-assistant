package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynStudioModuleManagerTest {
    @Test
    fun registrationIsMetadataOnlyAndDoesNotConstructTheEngine() = runBlocking {
        var factoryCalls = 0
        val manager = manager()
        manager.register(definition(factoryCalls = { factoryCalls++ }))

        assertEquals(0, factoryCalls)
        assertTrue(manager.loadedModuleIds().isEmpty())
        assertEquals(1, manager.manifests().size)
        assertEquals(
            "code-navigation",
            manager.findByCapability(KellvynStudioCapability.CODE_NAVIGATION).single().moduleId
        )
    }

    @Test
    fun concurrentLeasesShareOneLoadedModuleAndMemoryTrimClosesItOnlyWhenIdle() = runBlocking {
        var factoryCalls = 0
        var closeCalls = 0
        var trimCalls = 0
        val manager = manager()
        manager.register(definition(
            factoryCalls = { factoryCalls++ },
            closeCalls = { closeCalls++ },
            trimCalls = { trimCalls++ }
        ))

        val first = manager.acquire("code-navigation")
        val second = manager.acquire("code-navigation")

        assertEquals(1, factoryCalls)
        assertEquals(2, manager.activeLeaseCount("code-navigation"))
        assertEquals(setOf("code-navigation"), manager.loadedModuleIds())

        first.close()
        assertEquals(1, manager.trimIdleModules(KellvynCachePressure.CRITICAL))
        assertEquals(0, closeCalls)
        assertEquals(0, trimCalls)

        second.close()
        assertEquals(1, manager.trimIdleModules(KellvynCachePressure.CRITICAL))
        assertEquals(1, closeCalls)
        assertEquals(1, trimCalls)
        assertTrue(manager.loadedModuleIds().isEmpty())
    }

    @Test
    fun moduleCannotLoadToolsNotDeclaredInItsManifest() = runBlocking {
        val manager = manager()
        manager.register(definition(actualToolName = "undeclared_tool"))

        val error = runCatching { manager.acquire("code-navigation") }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(manager.loadedModuleIds().isEmpty())
    }

    @Test
    fun capabilityToolAdapterLoadsModuleLazilyAndUsesOnlyOneResourceAdmission() = runBlocking {
        var factoryCalls = 0
        val manager = manager()
        manager.register(definition(factoryCalls = { factoryCalls++ }))

        val adapters = manager.toolsForCapabilities(setOf(KellvynStudioCapability.CODE_NAVIGATION))
        assertEquals(0, factoryCalls)
        assertEquals(1, adapters.size)
        assertEquals("code_find_symbol", adapters.single().spec.name)

        val result = adapters.single().execute(emptyMap())

        assertTrue(result.success)
        assertEquals("ok", result.content)
        assertEquals(1, factoryCalls)
        assertEquals(1, manager.activeLeaseCount("code-navigation"))
        // The adapter releases its lease as soon as the tool completes.
        assertEquals(0, manager.activeLeaseCount("code-navigation"))
    }

    @Test
    fun resourceGovernorCanDeferModuleLoadingBeforeFactoryRuns() = runBlocking {
        var factoryCalls = 0
        val constrainedBroker = KellvynTaskBroker(
            governor = KellvynResourceGovernor(),
            queueWaitMillis = 10L,
            snapshotProvider = {
                snapshot().copy(availableSystemMemoryBytes = 300L * MEBIBYTE)
            }
        )
        val manager = manager(constrainedBroker)
        manager.register(definition(
            factoryCalls = { factoryCalls++ },
            workload = KellvynWorkloadClass.HEAVY,
            loadBytes = 512L * MEBIBYTE,
            residentBytes = 512L * MEBIBYTE
        ))

        val error = runCatching { manager.acquire("code-navigation") }.exceptionOrNull()

        assertTrue(error is KellvynTaskDeferredException)
        assertEquals(0, factoryCalls)
        assertTrue(manager.loadedModuleIds().isEmpty())
    }

    @Test
    fun declaredToolRiskMustMatchTheLoadedToolContract() = runBlocking {
        val manager = manager()
        manager.register(definition(actualToolRisk = KellvynToolRisk.REVERSIBLE_WRITE))
        val error = runCatching { manager.acquire("code-navigation") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(manager.loadedModuleIds().isEmpty())
    }

    @Test
    fun activeModuleIsNotUnloadedByIdleTtl() = runBlocking {
        var now = 1_000L
        var closeCalls = 0
        val broker = KellvynTaskBroker(
            governor = KellvynResourceGovernor(),
            queueWaitMillis = 100L,
            snapshotProvider = { snapshot() }
        )
        val manager = KellvynStudioModuleManager(35, broker) { now }
        manager.register(definition(closeCalls = { closeCalls++ }))
        val lease = manager.acquire("code-navigation")

        now = 999_999L
        assertEquals(0, manager.unloadExpiredModules(maxIdleMillis = 1L))
        assertEquals(0, closeCalls)

        lease.close()
        now = 1_000_001L
        assertEquals(1, manager.unloadExpiredModules(maxIdleMillis = 1L))
        assertEquals(1, closeCalls)
    }

    @Test
    fun duplicateToolNamesAcrossModuleManifestsAreRejected() {
        val manager = manager()
        manager.register(definition(moduleId = "code-navigation", toolName = "shared_tool"))

        val error = runCatching {
            manager.register(definition(moduleId = "image-editor", toolName = "shared_tool"))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun manager(
        broker: KellvynTaskBroker = KellvynTaskBroker(
            KellvynResourceGovernor(),
            queueWaitMillis = 100L
        ) { snapshot() }
    ) = KellvynStudioModuleManager(deviceSdkInt = 35, taskBroker = broker)

    private fun definition(
        moduleId: String = "code-navigation",
        toolName: String = "code_find_symbol",
        actualToolName: String = toolName,
        actualToolRisk: KellvynToolRisk = KellvynToolRisk.READ_ONLY,
        workload: KellvynWorkloadClass = KellvynWorkloadClass.LIGHT,
        loadBytes: Long = 16L * MEBIBYTE,
        residentBytes: Long = 8L * MEBIBYTE,
        factoryCalls: () -> Unit = {},
        closeCalls: () -> Unit = {},
        trimCalls: () -> Unit = {}
    ): KellvynStudioModuleDefinition {
        val manifest = KellvynStudioModuleManifest(
            moduleId = moduleId,
            version = 1,
            capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION),
            supportedExtensions = setOf("kt", "java"),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = workload,
            estimatedLoadWorkingSetBytes = loadBytes,
            estimatedResidentBytes = residentBytes,
            permissions = setOf(KellvynStudioPermission.PRIVATE_WORKSPACE_READ),
            toolSpecs = listOf(
                KellvynToolSpec(
                    name = toolName,
                    description = "Ferramenta de teste",
                    risk = KellvynToolRisk.READ_ONLY,
                    capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
                )
            )
        )
        val moduleManifest = manifest
        return KellvynStudioModuleDefinition(moduleManifest) {
            factoryCalls()
            object : KellvynStudioModule {
                override val manifest = moduleManifest
                override val tools = listOf(
                    object : KellvynAgentTool {
                        override val spec = KellvynToolSpec(
                            name = actualToolName,
                            description = "Ferramenta de teste",
                            risk = actualToolRisk,
                            capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
                        )
                        override suspend fun execute(arguments: Map<String, String>) =
                            KellvynToolResult(true, "ok")
                    }
                )
                override fun trimTransientResources(level: KellvynCachePressure) = trimCalls()
                override fun close() = closeCalls()
            }
        }
    }

    private fun snapshot() = KellvynResourceSnapshot(
        totalSystemMemoryBytes = 8L * 1024 * MEBIBYTE,
        availableSystemMemoryBytes = 3L * 1024 * MEBIBYTE,
        processHeapUsedBytes = 96L * MEBIBYTE,
        processHeapMaxBytes = 512L * MEBIBYTE,
        processPssBytes = 128L * MEBIBYTE,
        lowMemory = false,
        thermalBand = KellvynThermalBand.NONE,
        thermalHeadroomForecast = 0.2f,
        powerSaveMode = false,
        batteryPercent = 80,
        charging = false
    )

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
