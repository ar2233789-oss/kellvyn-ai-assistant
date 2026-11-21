package com.kellvyn.assistant

import android.content.Context
import android.net.Uri

/** Inert compatibility adapter. The neural implementation was removed. */
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
        listener.onError("O mecanismo neural anterior foi removido. Nenhum modelo substituto está conectado.")
    }
    override fun close() = Unit
}
