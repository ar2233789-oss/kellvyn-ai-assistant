package com.kellvyn.assistant

import android.content.Context
import android.net.Uri

/**
 * Temporary application boundary while the original Kellvyn neural backend is built.
 *
 * This class intentionally contains no downloaded model and no hidden fallback provider.
 * It reports that inference is unavailable instead of pretending the agent is ready.
 * The real runtime will be connected only after the neural model and runtime gates pass.
 */
internal class KellvynRuntime(context: Context) {
    interface Listener {
        fun onStatus(message: String)
        fun onPartial(text: String)
        fun onComplete(text: String, backend: String)
        fun onError(message: String)
    }

    @Suppress("unused")
    private val appContext = context.applicationContext

    fun isReady(): Boolean = false

    fun hasActiveConversation(): Boolean = false

    fun reply(prompt: String, attachmentUri: Uri?, attachmentName: String?, listener: Listener) {
        listener.onStatus("NÚCLEO NEURAL KELLVYN EM CONSTRUÇÃO")
        listener.onError(
            "O cérebro neural próprio da Kellvyn ainda não está conectado. " +
                "Nenhum modelo de terceiros será baixado ou usado como substituto."
        )
    }

    fun resetConversation() = Unit

    fun resetConversationAndWait() = Unit

    fun close() = Unit
}
