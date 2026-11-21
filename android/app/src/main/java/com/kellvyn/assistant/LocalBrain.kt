package com.kellvyn.assistant

import android.content.Context
import android.net.Uri

/**
 * Compatibility boundary only.
 *
 * The former local neural implementation was removed. This class deliberately
 * contains no model, tokenizer, weights, inference, remote provider, or training.
 * It keeps the existing chat UI source-compatible and reports that no engine is
 * connected until a separately audited original implementation is supplied.
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
        listener.onError("O mecanismo neural anterior foi removido. Nenhum modelo substituto está conectado.")
    }

    override fun close() = Unit
}
