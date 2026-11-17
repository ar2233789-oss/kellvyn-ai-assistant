package com.kellvyn.assistant

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KellvynDataArchitectureStudioInstrumentedTest {
    @Test
    fun jsonAndCsvStructureIsInspectedWithoutReturningCellValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.createProject(
            name = "Data Studio Instrumented " + System.currentTimeMillis(),
            description = "Fixture temporário de dados.",
            files = listOf(
                ProjectFileInput(
                    path = "schema.json",
                    content = """{"user":{"id":17,"password":"never-return-this"},"active":true,"items":[{"code":"abc"}]}"""
                ),
                ProjectFileInput(
                    path = "records.csv",
                    content = "id,name,api_key,total\n1,Alice,hidden-key-1,18.5\n2,Bob,hidden-key-2,21.0"
                )
            )
        )
        val broker = KellvynTaskBroker(
            governor = KellvynResourceGovernor(),
            queueWaitMillis = 1_000L,
            snapshotProvider = {
                KellvynResourceSnapshot(
                    totalSystemMemoryBytes = 8L * MEBIBYTE * 1024L,
                    availableSystemMemoryBytes = 3L * MEBIBYTE * 1024L,
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
            }
        )
        val manager = KellvynStudioModuleManager(android.os.Build.VERSION.SDK_INT, broker)
        manager.register(KellvynDataArchitectureStudio.definition(workspace))
        val tools = manager.toolsForCapabilities(setOf(KellvynStudioCapability.DATA_MODELING))
        assertTrue(manager.loadedModuleIds().isEmpty())
        val inspect = tools.single()

        val json = inspect.execute(mapOf("project_id" to project.id, "path" to "schema.json"))
        assertTrue(json.success)
        assertTrue(json.content.contains("FORMATO: JSON"))
        assertTrue(json.content.contains("objeto"))
        assertTrue(json.content.contains("campo-sensivel"))
        assertFalse(json.content.contains("never-return-this"))

        val csv = inspect.execute(mapOf("project_id" to project.id, "path" to "records.csv"))
        assertTrue(csv.success)
        assertTrue(csv.content.contains("FORMATO: CSV"))
        assertTrue(csv.content.contains("api_key".replace("api_key", "coluna-sensivel")))
        assertTrue(csv.content.contains("decimal"))
        assertFalse(csv.content.contains("Alice"))
        assertFalse(csv.content.contains("hidden-key"))

        manager.trimIdleModules(KellvynCachePressure.CRITICAL)
        workspace.deleteProject(project.id)
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
