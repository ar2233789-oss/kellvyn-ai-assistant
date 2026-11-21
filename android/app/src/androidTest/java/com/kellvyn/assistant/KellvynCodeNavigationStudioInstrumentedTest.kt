package com.kellvyn.assistant

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KellvynCodeNavigationStudioInstrumentedTest {
    @Test
    fun lazyCodeNavigationToolsFindDefinitionsReferencesAndFileOutline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.createProject(
            name = "Code Navigation Instrumented " + System.currentTimeMillis(),
            description = "Fixture temporário para navegação.",
            files = listOf(
                ProjectFileInput(
                    path = "src/Sample.kt",
                    content = """
                        package sample
                        class Sample {
                            fun openSettings() {
                                loadData()
                            }
                            private fun loadData() = Unit
                        }
                    """.trimIndent()
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
        val manager = KellvynStudioModuleManager(
            deviceSdkInt = android.os.Build.VERSION.SDK_INT,
            taskBroker = broker
        )
        manager.register(KellvynCodeNavigationStudio.definition(workspace))
        val tools = manager.toolsForCapabilities(setOf(KellvynStudioCapability.CODE_NAVIGATION))
        assertTrue(manager.loadedModuleIds().isEmpty())

        val symbols = tools.single { it.spec.name == "code_find_symbols" }
            .execute(mapOf("project_id" to project.id, "query" to "openSettings"))
        assertTrue(symbols.success)
        assertTrue(symbols.content.contains("src/Sample.kt"))
        assertTrue(symbols.content.contains("openSettings"))

        val references = tools.single { it.spec.name == "code_find_references" }
            .execute(mapOf("project_id" to project.id, "identifier" to "loadData"))
        assertTrue(references.success)
        assertTrue(references.content.contains("loadData"))

        val outline = tools.single { it.spec.name == "code_file_outline" }
            .execute(mapOf("project_id" to project.id, "path" to "src/Sample.kt"))
        assertTrue(outline.success)
        assertTrue(outline.content.contains("package"))
        assertTrue(outline.content.contains("openSettings"))
        assertTrue(manager.loadedModuleIds().contains("code-navigation"))

        manager.trimIdleModules(KellvynCachePressure.CRITICAL)
        assertTrue(manager.loadedModuleIds().isEmpty())
        workspace.deleteProject(project.id)
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
