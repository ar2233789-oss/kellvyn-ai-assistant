package com.kellvyn.assistant

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kellvyn.assistant.workspace.LocalProjectBuilder

/**
 * Main-thread callback boundary between the native chat and the on-device APK builder.
 *
 * The bridge does not generate source or claim that a build succeeded. It forwards the
 * model's complete file response to LocalProjectBuilder, which validates the project,
 * obtains explicit SDK-license consent, compiles locally, signs and verifies the APK.
 */
class LocalProjectBuilderBridge(
    context: Context,
    private val listener: Listener
) : AutoCloseable {
    interface Listener {
        fun onProgress(message: String)
        fun onComplete(message: String)
    }

    private val builder = LocalProjectBuilder(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun addCompilerContract(prompt: String): String = builder.addCompilerContract(prompt)

    fun isNativeApkRequest(prompt: String): Boolean = builder.isNativeApkRequest(prompt)

    fun hasPendingLicenseAcceptance(prompt: String): Boolean =
        builder.hasPendingLicenseAcceptance(prompt)

    /**
     * Compiles only explicit native APK requests. The response is treated as untrusted
     * source data and is never executed by this bridge.
     */
    fun compileGeneratedProject(prompt: String, response: String) {
        if (!builder.isNativeApkRequest(prompt)) {
            scope.launch(Dispatchers.Main.immediate) { listener.onComplete(response) }
            return
        }
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    builder.compileGeneratedProject(
                        prompt = prompt,
                        response = response,
                        onBeforeBuild = {
                            withContext(Dispatchers.Main.immediate) {
                                listener.onProgress("Preparando a compilação Android local…")
                            }
                        },
                        onProgress = { message ->
                            scope.launch(Dispatchers.Main.immediate) { listener.onProgress(message) }
                        }
                    )
                }
                listener.onComplete(
                    result ?: "O compilador local não reconheceu esta solicitação como um pedido de APK."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                listener.onComplete(
                    "A compilação Android local foi interrompida com segurança: " +
                        (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    /**
     * Records the user's exact SDK-license acceptance and resumes the pending build.
     * Returns false when no project is awaiting that exact consent phrase.
     */
    fun acceptSdkLicense(prompt: String): Boolean {
        if (!builder.hasPendingLicenseAcceptance(prompt)) return false
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    builder.handleSdkLicenseAcceptance(
                        prompt = prompt,
                        onBeforeBuild = {
                            withContext(Dispatchers.Main.immediate) {
                                listener.onProgress("Licença aceita. Preparando o compilador Android local…")
                            }
                        },
                        onProgress = { message ->
                            scope.launch(Dispatchers.Main.immediate) { listener.onProgress(message) }
                        }
                    )
                }
                listener.onComplete(
                    result ?: "Não há um projeto aguardando aceite da licença do Android SDK."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                listener.onComplete(
                    "Não foi possível retomar a compilação local: " +
                        (error.message ?: error.javaClass.simpleName)
                )
            }
        }
        return true
    }

    override fun close() {
        scope.cancel()
    }
}
