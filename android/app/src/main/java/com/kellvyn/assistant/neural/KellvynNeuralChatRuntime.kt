package com.kellvyn.assistant.neural

import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

internal data class KellvynSamplingConfig(
    val temperature: Float = 0.65f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val repetitionPenalty: Float = 0.12f
) {
    init {
        require(temperature.isFinite() && temperature in 0.1f..2.0f)
        require(topK in 1..128)
        require(topP.isFinite() && topP in 0.5f..1.0f)
        require(repetitionPenalty.isFinite() && repetitionPenalty in 0.0f..2.0f)
    }
}

/** Bounded top-k/top-p sampler shared by the one local KN brain. */
internal object KellvynTokenSampler {
    private data class Candidate(val tokenId: Int, val score: Double, val weight: Double)

    fun sample(
        probabilities: FloatArray,
        recentTokens: List<Int>,
        config: KellvynSamplingConfig = KellvynSamplingConfig(),
        random: Random = Random()
    ): Int {
        require(probabilities.isNotEmpty())
        require(probabilities.all { it.isFinite() && it >= 0.0f })
        require(probabilities.any { it > 0.0f })

        val repeatCounts = HashMap<Int, Int>()
        recentTokens.forEach { token ->
            if (token in probabilities.indices) {
                repeatCounts[token] = (repeatCounts[token] ?: 0) + 1
            }
        }

        val scored = probabilities.indices.mapNotNull { tokenId ->
            val probability = probabilities[tokenId].toDouble()
            if (probability <= 0.0) return@mapNotNull null
            val penalty = config.repetitionPenalty.toDouble() *
                (repeatCounts[tokenId] ?: 0).toDouble()
            val score = ln(probability) / config.temperature.toDouble() - penalty
            Candidate(tokenId, score, 0.0)
        }.sortedByDescending { it.score }
            .take(minOf(config.topK, probabilities.size))

        require(scored.isNotEmpty())
        val maximum = scored.first().score
        val weighted = scored.map { candidate ->
            candidate.copy(weight = exp(candidate.score - maximum))
        }
        val totalWeight = weighted.sumOf { it.weight }
        require(totalWeight.isFinite() && totalWeight > 0.0)

        val nucleus = ArrayList<Candidate>()
        var cumulative = 0.0
        for (candidate in weighted) {
            nucleus += candidate
            cumulative += candidate.weight / totalWeight
            if (cumulative >= config.topP.toDouble()) break
        }

        val nucleusWeight = nucleus.sumOf { it.weight }
        val target = random.nextDouble() * nucleusWeight
        var running = 0.0
        for (candidate in nucleus) {
            running += candidate.weight
            if (target <= running) return candidate.tokenId
        }
        return nucleus.last().tokenId
    }
}

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
        val qualityApprovedForBenchmark: Boolean = false,
        val qualityReasons: List<String> = emptyList(),
        val depth: Int? = null,
        val contextLength: Int? = null,
        val vocabularySize: Int? = null
    )

    private data class LoadedModel(
        val tokenizer: KellvynBpeTokenizer,
        val model: KellvynMultiBlockTransformer,
        val endTokenId: Int,
        val qualityGate: KellvynMultiBlockTrainingQualityGate.Report
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
            val qualityGate = KellvynMultiBlockTrainingQualityGate.evaluate(restored.manifest)
            loadedModel = LoadedModel(
                tokenizer = restored.tokenizer,
                model = restored.model,
                endTokenId = endTokenId,
                qualityGate = qualityGate
            )
            Status(
                ready = true,
                detail = if (qualityGate.eligibleForProtocolBenchmark) {
                    "Checkpoint KN-004 carregado; passou pelo gate numérico e pode seguir para benchmark comportamental."
                } else {
                    "Checkpoint KN-004 carregado apenas para inspeção experimental; ainda não pode responder no chat."
                },
                qualityApprovedForBenchmark = qualityGate.eligibleForProtocolBenchmark,
                qualityReasons = qualityGate.reasons,
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
        sampling: KellvynSamplingConfig = KellvynSamplingConfig(),
        randomSeed: Long = System.nanoTime(),
        cancellationCheck: () -> Unit = {},
        onPartial: (String) -> Unit = {}
    ): String {
        require(userText.isNotBlank()) { "A mensagem do usuário está vazia." }
        return generateContinuation(
            prompt = "Pergunta: ${userText.trim()} Resposta:",
            maxNewTokens = maxNewTokens,
            sampling = sampling,
            randomSeed = randomSeed,
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
        sampling: KellvynSamplingConfig = KellvynSamplingConfig(),
        randomSeed: Long = System.nanoTime(),
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
        check(active.qualityGate.eligibleForProtocolBenchmark) {
            "O checkpoint KN-004 ainda não passou pelo quality gate: " +
                active.qualityGate.reasons.joinToString(" ")
        }
        val maxTokenizerSafeChars = minOf(
            MAX_PROMPT_CHARS,
            (MAX_TOKENIZER_WORK_BYTES / (maxOf(1, active.tokenizer.mergeCount).toLong() * 4L))
                .coerceAtLeast(1L)
                .toInt()
        )
        val safePrompt = if (prompt.length <= maxTokenizerSafeChars) {
            prompt
        } else {
            // Keep the newest context without cutting a UTF-16 surrogate pair in half.
            var start = prompt.length - maxTokenizerSafeChars
            if (start > 0 && start < prompt.length &&
                Character.isLowSurrogate(prompt[start]) &&
                Character.isHighSurrogate(prompt[start - 1])
            ) {
                start++
            }
            prompt.substring(start)
        }
        val encoded = active.tokenizer.encode(safePrompt)
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
        val random = Random(randomSeed)
        var streamedText = ""
        for (step in 0 until maxNewTokens) {
            cancellationCheck()
            val nextToken = KellvynTokenSampler.sample(
                probabilities = session.nextTokenProbabilities(),
                recentTokens = generatedTokens,
                config = sampling,
                random = random
            )
            if (nextToken == active.endTokenId) break

            session.appendToken(nextToken)
            generatedTokens += nextToken

            // BPE tokens represent UTF-8 byte fragments. Decode the full generated sequence
            // so a multibyte Portuguese character is never split and corrupted per token.
            val decoded = active.tokenizer.decode(generatedTokens.toIntArray())
            if (!decoded.contains('\uFFFD')) {
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
        const val MAX_TOKENIZER_WORK_BYTES = 40_000_000L
    }
}
