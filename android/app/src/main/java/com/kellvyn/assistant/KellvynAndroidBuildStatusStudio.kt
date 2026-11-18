package com.kellvyn.assistant

import android.content.Context
import com.kellvyn.assistant.workspace.AndroidBuildVersions
import com.kellvyn.assistant.workspace.LocalAndroidCompiler
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import java.io.File

/**
 * Lightweight, read-only diagnostics for the existing on-device Java -> D8 APK compiler.
 *
 * This does not compile, install, execute, or claim to test an APK. The module is lazy-loaded
 * and reads only a few local metadata fields so build/test specialists can distinguish an
 * unavailable toolchain from a failed build or a missing test result.
 */
internal class KellvynAndroidBuildStatusStudio private constructor(
    context: Context
) : KellvynStudioModule {

    private val appContext = context.applicationContext
    private val compiler = LocalAndroidCompiler(
        appContext,
        ProjectWorkspaceStore(appContext)
    )

    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS.single()

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "A ferramenta não aceita argumentos.")
                }

                val androidJar = File(
                    appContext.filesDir,
                    "kellvyn_toolchain/android-${AndroidBuildVersions.COMPILE_SDK}/android.jar"
                )
                val latestApk = compiler.mostRecentApk()
                val apkAvailable = latestApk?.isFile == true
                val apkName = latestApk?.name?.take(120)
                val apkSize = latestApk?.takeIf { it.isFile }?.length()
                val apkModified = latestApk?.takeIf { it.isFile }?.lastModified()

                return KellvynToolResult(
                    true,
                    buildString {
                        appendLine("Diagnóstico do compilador Android local (somente leitura).")
                        appendLine("compiler=ECJ_D8_LOCAL")
                        appendLine("support_profile=JAVA_ANDROID_LIMITED")
                        appendLine("compile_sdk=${AndroidBuildVersions.COMPILE_SDK}")
                        appendLine("android_jar_cached=${androidJar.isFile}")
                        appendLine("sdk_license_accepted=${compiler.hasAcceptedSdkLicense()}")
                        appendLine("latest_apk_available=$apkAvailable")
                        if (apkAvailable) {
                            appendLine("latest_apk_name=$apkName")
                            appendLine("latest_apk_size_bytes=$apkSize")
                            appendLine("latest_apk_modified_at_ms=$apkModified")
                        }
                        appendLine("apk_installation=not_performed")
                        appendLine("device_test_status=not_provided_by_this_diagnostic")
                        append("Observação: presença de APK ou SDK não comprova compilação recente, instalação ou funcionamento.")
                    }.take(MAX_OUTPUT_CHARS)
                )
            }
        }
    )

    override fun trimTransientResources(level: KellvynCachePressure) = Unit

    override fun close() = Unit

    companion object {
        private const val MAX_OUTPUT_CHARS = 1_600

        private val TOOL_SPECS = listOf(
            KellvynToolSpec(
                name = "android_build_environment_status",
                description = "Consulta se o compilador Android local Java/D8 tem SDK/licença disponíveis e resume o último APK encontrado; não compila, instala nem testa.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 5_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 2L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.ANDROID_BUILD)
            )
        )

        val MANIFEST = KellvynStudioModuleManifest(
            moduleId = "android-build-status",
            version = 1,
            capabilities = setOf(KellvynStudioCapability.ANDROID_BUILD),
            supportedExtensions = setOf("apk"),
            minSdk = 26,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedLoadWorkingSetBytes = 2L * MEBIBYTE,
            estimatedResidentBytes = MEBIBYTE,
            permissions = emptySet(),
            toolSpecs = TOOL_SPECS
        )

        fun definition(context: Context) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynAndroidBuildStatusStudio(context.applicationContext)
        }

        private const val MEBIBYTE = 1024L * 1024L
    }
}
