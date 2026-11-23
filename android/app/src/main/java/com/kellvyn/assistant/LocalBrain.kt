package com.kellvyn.assistant

import android.content.Context
import android.net.Uri

/**
 * Compatibility boundary only.
 *
 * No third-party model, model downloader, external inference SDK, or provider is
 * connected here. The original KN neural runtime must be explicitly integrated
 * before chat inference can be enabled.
 */
class LocalBrain(context: Context) : AutoCloseable {
    interface Listener {
        fun onStatus(message: String)
        fun onPartial(text: String)
        fun onComplete(text: String, backend: String)
        fun onError(message: String)
    }

    fun hasModel(): Boolean = false
    fun hasActiveConversation(): Boolean = false
    fun resetConversation() = Unit

    fun reply(prompt: String, attachmentUri: Uri?, attachmentName: String?, listener: Listener) {
        listener.onError("O cérebro neural próprio do KN ainda não está conectado a esta versão.")
    }

    override fun close() = Unit
}