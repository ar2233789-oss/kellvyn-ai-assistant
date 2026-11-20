package com.kellvyn.assistant.neural

/**
 * Small, single-model chat inference boundary for the original KN-004 brain.
 *
 * The caller supplies the conversation/context text from the main chat. This class owns
 * only model loading and autoregressive generation; it does not create agents, tools,
 * another conversation surface, or a fallback model.
 */
internal class KellvynNeuralChatRuntime(
    private val modelStore: KellvynNeuralModelStore
) {
    data class Status(
        val ready: Boolean,
        val detail: String,
        val depth: Int? = null,
        val contextLength: Int? = null,
        val vocabularySize: Int? = null
    )

    private data class LoadedModel(
        val tokenizer: KellvynBpeTokenizer,
        val model: KellvynMultiBlockTransformer,
        val endTokenId: Int
    )

    @Volatile
    private var loadedModel: LoadedModel? = null

    val isReady: Boolean
        get() = loadedModel != null

    /**
     * Loads a checksum-verified KN-004 checkpoint while omitting optimizer moments.
     * No random-weight or third-party fallback is created when the checkpoint is missing.
     */
    @Synchronized
    fun initialize(): Status {
        return try {
            val restored = modelStore.loadMultiBlockForInference()
                ?: run {
                    loadedModel = null
                    return Status(
                        ready = false,
                        detail = "Nenhum checkpoint KN-004 treinado foi instalado."
                    )
                }

            val endTokenId = restored.tokenizer.endOfSequenceTokenId
                ?: run {
                    loadedModel = null
                    return Status(
                        ready = false,
                        detail = "O tokenizer salvo não possui token de encerramento; gere um checkpoint KN-004 v2."
                    )
                }

            check(!restored.model.hasTrainingState()) {
                "O carregamento de inferência manteve estados de treinamento na memória."
            }
            loadedModel = LoadedModel(
                tokenizer = restored.tokenizer,
                model = restored.model,
                endTokenId = endTokenId
            )
            Status(
                ready = true,
                detail = "Checkpoint KN-004 carregado em modo de inferência local.",
                depth = restored.model.config.depth,
                contextLength = restored.model.config.contextLength,
                vocabularySize = restored.tokenizer.vocabularySize
            )
        } catch (error: Exception) {
            loadedModel = null
            Status(
                ready = false,
                detail = error.message?.takeIf { it.isNotBlank() }
                    ?: "Não foi possível validar o checkpoint KN-004."
            )
        }
    }

    /** Releases the in-memory model reference; the verified checkpoint remains on disk. */
    @Synchronized
    fun unload() {
        loadedModel = null
    }

    /**
     * Creates a one-turn prompt using the current supervised format. For multi-turn chat,
     * the caller should include the required history/memory in userContext before calling.
     */
    fun generateReply(
        userText: String,
        maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS,
        cancellationCheck: () -> Unit = {},
        onPartial: (String) -> Unit = {}
    ): String {
        require(userText.isNotBlank()) { "A mensagem do usuário está vazia." }
        return generateContinuation(
            prompt = "Pergunta: ${userText.trim()}\nResposta:",
            maxNewTokens = maxNewTokens,
            cancellationCheck = cancellationCheck,
            onPartial = onPartial
        )
    }

    /**
     * Streams decoded token fragments from the same local model used by the main chat.
     * Cancellation is checked between tokens so a UI/service can stop generation promptly.
     */
    fun generateContinuation(
        prompt: String,
        maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS,
        cancellationCheck: () -> Unit = {},
        onPartial: (String) -> Unit = {}
    ): String {
        require(prompt.isNotBlank()) { "O contexto de geração está vazio." }
        require(prompt.length <= MAX_PROMPT_CHARS) { "O contexto excede o limite de texto KN-004." }
        require(maxNewTokens in 1..MAX_GENERATION_TOKENS) {
            "Quantidade de tokens de resposta fora do limite KN-004."
        }
        cancellationCheck()

        val active = loadedModel ?: throw IllegalStateException(
            "O cérebro neural KN-004 ainda não está carregado. Inicialize um checkpoint validado."
        )
        val encoded = active.tokenizer.encode(prompt)
        require(encoded.isNotEmpty()) { "O tokenizer não produziu tokens para o contexto." }

        // Keep the tail of a long context so the newest user instruction and answer prefix
        // remain visible inside the model's fixed context window.
        val contextLength = active.model.config.contextLength
        val prefix = if (encoded.size <= contextLength) {
            encoded
        } else {
            encoded.copyOfRange(encoded.size - contextLength, encoded.size)
        }

        val session = active.model.newGenerationSession(prefix)
        val generatedTokens = ArrayList<Int>(maxNewTokens)
        var streamedText = ""
        for (step in 0 until maxNewTokens) {
            cancellationCheck()
            val nextToken = session.predictNext()
            if (nextToken == active.endTokenId) break

            session.appendToken(nextToken)
            generatedTokens += nextToken

            // BPE tokens represent UTF-8 byte fragments. Decode the full generated sequence
            // so a multibyte Portuguese character is never split and corrupted per token.
            val decoded = active.tokenizer.decode(generatedTokens.toIntArray())
            if (!decoded.endsWith('\uFFFD')) {
                streamedText = decoded
                onPartial(streamedText)
            }
        }
        cancellationCheck()

        val answer = active.tokenizer.decode(generatedTokens.toIntArray()).trim()
        if (answer != streamedText) onPartial(answer)
        if (answer.isEmpty()) {
            throw IllegalStateException(
                "O checkpoint KN-004 não gerou texto. Não vou apresentar uma resposta vazia como sucesso."
            )
        }
        return answer
    }

    private companion object {
        const val DEFAULT_MAX_NEW_TOKENS = 96
        const val MAX_GENERATION_TOKENS = 256
        const val MAX_PROMPT_CHARS = 16_000
    }
}
