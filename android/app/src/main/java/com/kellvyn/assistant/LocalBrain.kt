package com.kellvyn.assistant

import android.content.Context
import android.net.Uri

/** Compatibility boundary; the original inference engine was removed and is not connected. */
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
        val identityReply = KellvynPersona.creatorAnswer(prompt)
        if (identityReply != null) {
            listener.onPartial(identityReply)
            listener.onComplete(identityReply, "IDENTIDADE DO PROJETO")
            return
        }
        listener.onError("O mecanismo neural não está conectado. Nenhum modelo substituto está disponível.")
    }

    override fun close() = Unit
}
