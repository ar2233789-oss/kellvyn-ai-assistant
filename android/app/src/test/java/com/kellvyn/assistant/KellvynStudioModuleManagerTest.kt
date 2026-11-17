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
            toolNames = setOf(toolName)
        )
        return KellvynStudioModuleDefinition(manifest) {
            factoryCalls()
            object : KellvynStudioModule {
                override val manifest = manifest
                override val tools = listOf(
                    object : KellvynAgentTool {
                        override val spec = KellvynToolSpec(
                            name = actualToolName,
                            description = "Ferramenta de teste"
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
