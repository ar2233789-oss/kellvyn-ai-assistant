package com.kellvyn.assistant

import android.content.Context
import android.net.Uri

/**
 * Keeps the approved chat screen API unchanged while routing every turn through
 * the single central KN-004 kernel and its registered local tools.
 */
class LocalBrain(context: Context) : AutoCloseable {
    interface Listener {
        fun onStatus(message: String)
        fun onPartial(text: String)
        fun onComplete(text: String, backend: String)
        fun onError(message: String)
    }

    private val runtime = KellvynRuntime(context.applicationContext)

    fun hasModel(): Boolean = runtime.isReady()

    fun hasActiveConversation(): Boolean = runtime.hasActiveConversation()

    fun resetConversation() = runtime.resetConversation()

    fun reply(
        prompt: String,
        attachmentUri: Uri?,
        attachmentName: String?,
        listener: Listener
    ) {
        runtime.reply(
            prompt = prompt,
            attachmentUri = attachmentUri,
            attachmentName = attachmentName,
            listener = object : KellvynRuntime.Listener {
                override fun onStatus(message: String) = listener.onStatus(message)
                override fun onPartial(text: String) = listener.onPartial(text)
                override fun onComplete(text: String, backend: String) =
                    listener.onComplete(text, backend)
                override fun onError(message: String) = listener.onError(message)
            }
        )
    }

    override fun close() = runtime.close()
}
