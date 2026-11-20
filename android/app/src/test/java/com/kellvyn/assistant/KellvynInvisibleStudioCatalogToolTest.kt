package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynInvisibleStudioCatalogToolTest {
    @Test
    fun inventoryReportsRegisteredLazyModulesAndDoesNotLoadTheirEngines() = runBlocking {
        var factoryCalls = 0
        val manager = manager()
        manager.register(moduleDefinition { factoryCalls++ })
        val tool = KellvynInvisibleStudioCatalogTool.create(
            moduleManager = manager,
            coreToolSpecs = emptyList()
        )

        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("CODE_NAVIGATION status=REGISTERED_LAZY"))
        assertTrue(result.content.contains("SCENE_3D status=NOT_REGISTERED"))
        assertTrue(result.content.contains("IMAGE_GENERATION status=NOT_REGISTERED"))
        assertTrue(result.content.contains("ANIMATION status=NOT_REGISTERED"))
        assertTrue(result.content.contains("VIDEO_GENERATION status=NOT_REGISTERED"))
        assertTrue(result.content.contains("GAME_INTERACTION status=NOT_REGISTERED"))
        assertTrue(result.content.contains("WORKFLOW_AUTOMATION status=NOT_REGISTERED"))
        assertTrue(result.content.contains("loaded_now=false"))
        assertTrue(result.content.contains("Execução=NOT_CHECKED"))
        assertTrue(result.content.contains("verificação independente=NOT_VERIFIED"))
        assertTrue(manager.loadedModuleIds().isEmpty())
        assertTrue(factoryCalls == 0)
    }

    @Test
    fun inventoryDistinguishesCoreToolsFromLazyModules() = runBlocking {
        val coreSpec = KellvynToolSpec(
            name = "workspace_write_file",
            description = "Escreve em workspace após autorização exata.",
            requiredArguments = setOf("project_id", "path", "content", "approval_id"),
            risk = KellvynToolRisk.REVERSIBLE_WRITE,
            capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
        )
        val artifactSpec = KellvynToolSpec(
            name = "artifact_verify",
            description = "Verifica metadados e SHA-256 de um artefato.",
            requiredArguments = setOf("artifact_id"),
            capabilities = setOf(KellvynStudioCapability.ARTIFACT_STORAGE)
        )
        val result = KellvynInvisibleStudioCatalogTool.create(
            moduleManager = manager(),
            coreToolSpecs = listOf(coreSpec, artifactSpec)
        ).execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("CODE_EDITING status=REGISTERED_CORE"))
        assertTrue(result.content.contains("ARTIFACT_STORAGE status=REGISTERED_CORE"))
        assertTrue(result.content.contains("workspace_write_file"))
        assertTrue(result.content.contains("artifact_verify"))
        assertTrue(result.content.contains("SCENE_3D status=NOT_REGISTERED"))
    }

    @Test
    fun inventoryRejectsUnexpectedArguments() = runBlocking {
        val result = KellvynInvisibleStudioCatalogTool.create(
            moduleManager = manager(),
            coreToolSpecs = emptyList()
        ).execute(mapOf("include_hidden" to "true"))

        assertFalse(result.success)
        assertTrue(result.content.contains("não aceita argumentos"))
    }

    private fun manager() = KellvynStudioModuleManager(
        deviceSdkInt = 35,
        taskBroker = KellvynTaskBroker(
            governor = KellvynResourceGovernor(),
            queueWaitMillis = 100L,
        ) { snapshot() }
    )

    private fun moduleDefinition(onCreate: () -> Unit): KellvynStudioModuleDefinition {
        val manifest = KellvynStudioModuleManifest(
            moduleId = "code-navigation",
            version = 1,
            capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION),
            supportedExtensions = setOf("kt", "java"),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedLoadWorkingSetBytes = 16L * MEBIBYTE,
            estimatedResidentBytes = 8L * MEBIBYTE,
            permissions = setOf(KellvynStudioPermission.PRIVATE_WORKSPACE_READ),
            toolSpecs = listOf(
                KellvynToolSpec(
                    name = "code_probe",
                    description = "Ferramenta de leitura de teste.",
                    capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
                )
            )
        )
        val moduleManifest = manifest
        return KellvynStudioModuleDefinition(moduleManifest) {
            onCreate()
            object : KellvynStudioModule {
                override val manifest = moduleManifest
                override val tools = listOf(
                    object : KellvynAgentTool {
                        override val spec = moduleManifest.toolSpecs.single()
                        override suspend fun execute(arguments: Map<String, String>) =
                            KellvynToolResult(true, "ok")
                    }
                )
                override fun trimTransientResources(level: KellvynCachePressure) = Unit
                override fun close() = Unit
            }
        }
    }

    private fun snapshot() = KellvynResourceSnapshot(
        totalSystemMemoryBytes = 8L * 1024L * MEBIBYTE,
        availableSystemMemoryBytes = 3L * 1024L * MEBIBYTE,
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
