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
class KellvynSecurityAuditStudioInstrumentedTest {
    @Test
    fun localSecurityAuditFindsRiskPatternsWithoutEchoingCredentialValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val secretValue = "ghp_012345678901234567890123456789012345"
        val project = workspace.createProject(
            name = "Security Audit Instrumented " + System.currentTimeMillis(),
            description = "Fixture temporário de auditoria.",
            files = listOf(
                ProjectFileInput(
                    path = "AndroidManifest.xml",
                    content = """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                            <application android:usesCleartextTraffic="true">
                                <activity android:name=".MainActivity" android:exported="true" />
                            </application>
                        </manifest>
                    """.trimIndent()
                ),
                ProjectFileInput(
                    path = "src/Unsafe.kt",
                    content = """
                        package sample
                        fun run(command: String) {
                            Runtime.getRuntime().exec(command)
                            webView.setJavaScriptEnabled(true)
                        }
                    """.trimIndent()
                ),
                ProjectFileInput(
                    path = "secrets.properties",
                    content = "api_key = \"$secretValue\""
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
        manager.register(KellvynSecurityAuditStudio.definition(workspace))
        val tools = manager.toolsForCapabilities(setOf(KellvynStudioCapability.SECURITY_AUDIT))
        assertTrue(manager.loadedModuleIds().isEmpty())

        val report = tools.single().execute(mapOf("project_id" to project.id))

        assertTrue(report.success)
        assertTrue(report.content.contains("ANDROID_CLEAR_TEXT"))
        assertTrue(report.content.contains("ANDROID_EXPORTED_COMPONENT"))
        assertTrue(report.content.contains("PROCESS_EXECUTION"))
        assertTrue(report.content.contains("WEBVIEW_JAVASCRIPT"))
        assertTrue(report.content.contains("SECRET_MATERIAL_FILE_SKIPPED"))
        assertFalse(report.content.contains(secretValue))

        manager.trimIdleModules(KellvynCachePressure.CRITICAL)
        workspace.deleteProject(project.id)
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
