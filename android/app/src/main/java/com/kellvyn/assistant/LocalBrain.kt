package com.kellvyn.assistant

import android.content.Context
import android.net.Uri

/**
 * Compatibilidade temporária com a tela Android antiga.
 *
 * Não baixa, carrega ou executa pesos de modelos externos. A conversa só será ativada
 * quando a inferência neural própria da Kellvyn estiver integrada e validada.
 */
class LocalBrain(@Suppress("UNUSED_PARAMETER") context: Context) {
    interface Listener {
        fun onStatus(message: String)
        fun onPartial(text: String)
        fun onComplete(text: String, backend: String)
        fun onError(message: String)
    }

    fun hasModel(): Boolean = false

    fun hasActiveConversation(): Boolean = false

    fun resetConversation() = Unit

    suspend fun resetConversationAndWait() = Unit

    fun reply(
        @Suppress("UNUSED_PARAMETER") prompt: String,
        @Suppress("UNUSED_PARAMETER") attachmentUri: Uri?,
        @Suppress("UNUSED_PARAMETER") attachmentName: String?,
        listener: Listener
    ) {
        listener.onError(
            "A inferência neural própria da Kellvyn ainda não está conectada. " +
                "Nenhum modelo externo será baixado ou executado."
        )
    }

    fun close() = Unit
}
