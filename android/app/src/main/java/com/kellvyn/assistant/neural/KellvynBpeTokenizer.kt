package com.kellvyn.assistant.neural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Byte-fallback BPE tokenizer implemented for the original Kellvyn language model.
 *
 * The tokenizer is trained only from the corpus explicitly supplied by the caller.
 * It starts with all 256 UTF-8 byte values, so every Unicode input remains losslessly
 * representable regardless of which merges were learned.
 *
 * This is the first KBN-002 component. It is not a neural model by itself.
 */
internal class KellvynBpeTokenizer private constructor(
    private val merges: List<Merge>,
    private val tokenBytes: List<ByteArray>,
    private val hasEndOfSequenceToken: Boolean
) {
    internal data class Merge(
        val leftTokenId: Int,
        val rightTokenId: Int,
        val mergedTokenId: Int
    )

    val vocabularySize: Int
        get() = tokenBytes.size

    val mergeCount: Int
        get() = merges.size

    /**
     * Dedicated stop token for supervised generation. It has no UTF-8 bytes and is never
     * emitted by encode(text); legacy version-1 checkpoints intentionally return null.
     */
    val endOfSequenceTokenId: Int?
        get() = if (hasEndOfSequenceToken) tokenBytes.lastIndex else null

    /** Encodes text using UTF-8 bytes followed by the learned merge order. */
    fun encode(text: String): IntArray {
        val inputBytes = text.toByteArray(Charsets.UTF_8)
        require(inputBytes.size <= MAX_ENCODE_BYTES) { "Texto acima do limite de codificação." }
        require(inputBytes.size.toLong() * merges.size.toLong() <= MAX_TRAINING_WORK) {
            "A codificação excederia o orçamento experimental de processamento."
        }
        var tokens = inputBytes.map { byte -> byte.toInt() and 0xff }
        for (merge in merges) {
            tokens = applyMerge(
                tokens,
                merge.leftTokenId,
                merge.rightTokenId,
                merge.mergedTokenId
            )
        }
        return tokens.toIntArray()
    }

    /** Decodes tokens back to their exact UTF-8 byte stream. */
    fun decode(tokenIds: IntArray): String {
        require(tokenIds.size <= MAX_DECODE_TOKENS) { "Sequência de tokens acima do limite." }
        val output = ByteArrayOutputStream()
        var outputBytes = 0L
        for (tokenId in tokenIds) {
            require(tokenId in tokenBytes.indices) { "ID de token fora do vocabulário Kellvyn." }
            val bytes = tokenBytes[tokenId]
            outputBytes += bytes.size.toLong()
            require(outputBytes <= MAX_DECODE_BYTES) { "Texto decodificado acima do limite." }
            output.write(bytes)
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }

    /** Saves only this tokenizer's merge table; model weights are stored separately. */
    fun writeCheckpoint(output: OutputStream) {
        val payloadBuffer = ByteArrayOutputStream()
        DataOutputStream(payloadBuffer).use { data ->
            data.writeInt(CHECKPOINT_MAGIC)
            data.writeInt(CHECKPOINT_VERSION)
            data.writeInt(merges.size)
            for (merge in merges) {
                data.writeInt(merge.leftTokenId)
                data.writeInt(merge.rightTokenId)
                data.writeInt(merge.mergedTokenId)
            }
        }
        val payload = payloadBuffer.toByteArray()
        val checksum = MessageDigest.getInstance("SHA-256").digest(payload)
        output.write(payload)
        output.write(checksum)
        output.flush()
    }

    private fun applyMerge(
        input: List<Int>,
        leftTokenId: Int,
        rightTokenId: Int,
        mergedTokenId: Int
    ): List<Int> {
        if (input.size < 2) return input
        val output = ArrayList<Int>(input.size)
        var index = 0
        while (index < input.size) {
            if (
                index + 1 < input.size &&
                input[index] == leftTokenId &&
                input[index + 1] == rightTokenId
            ) {
                output += mergedTokenId
                index += 2
            } else {
                output += input[index]
                index++
            }
        }
        return output
    }

    companion object {
        private const val BASE_VOCABULARY_SIZE = 256
        private const val CHECKPOINT_MAGIC = 0x4B564250 // "KVBP"
        private const val CHECKPOINT_VERSION = 2
        private const val CHECKSUM_BYTES = 32
        private const val MAX_MERGES = 4096
        private const val MAX_CORPUS_BYTES = 1_000_000
        private const val MAX_ENCODE_BYTES = 65_536
        private const val MAX_DECODE_BYTES = 1_000_000
        private const val MAX_TOKEN_BYTES = 16_384
        private const val MAX_TOKEN_TABLE_BYTES = 8 * 1024 * 1024
        private const val MAX_DECODE_TOKENS = 1_000_000
        private const val MAX_TRAINING_WORK = 40_000_000L
        private const val MAX_CHECKPOINT_BYTES = 64 * 1024

        /**
         * Learns deterministic byte-pair merges from the exact corpus supplied.
         *
         * Pair frequencies are recomputed after each merge. Ties are resolved by token
         * IDs, making the resulting tokenizer reproducible for the same input ordering.
         */
        fun train(
            corpus: List<String>,
            maxMerges: Int = 512,
            cancellationCheck: (() -> Unit)? = null
        ): KellvynBpeTokenizer {
            require(corpus.isNotEmpty()) { "O corpus de treinamento está vazio." }
            require(maxMerges in 0..MAX_MERGES) { "Quantidade de merges fora do limite." }

            val sequences = mutableListOf<List<Int>>()
            var totalBytes = 0L
            for (text in corpus) {
                cancellationCheck?.invoke()
                val bytes = text.toByteArray(Charsets.UTF_8)
                totalBytes += bytes.size.toLong()
                require(totalBytes <= MAX_CORPUS_BYTES) {
                    "O corpus excede o limite experimental de bytes."
                }
                if (bytes.isNotEmpty()) {
                    sequences += bytes.map { byte -> byte.toInt() and 0xff }
                }
            }
            require(sequences.isNotEmpty()) { "O corpus não contém texto treinável." }
            require(totalBytes.toLong() * maxMerges.toLong() <= MAX_TRAINING_WORK) {
                "O treinamento do tokenizer excederia o orçamento experimental de processamento."
            }

            val tokenBytes = MutableList(BASE_VOCABULARY_SIZE) { tokenId ->
                byteArrayOf(tokenId.toByte())
            }
            var tokenTableBytes = BASE_VOCABULARY_SIZE
            val merges = mutableListOf<Merge>()

            for (round in 0 until maxMerges) {
                cancellationCheck?.invoke()
                val frequencies = HashMap<Long, Int>()
                for (sequence in sequences) {
                    cancellationCheck?.invoke()
                    for (index in 0 until sequence.size - 1) {
                        val key = pairKey(sequence[index], sequence[index + 1])
                        frequencies[key] = (frequencies[key] ?: 0) + 1
                    }
                }

                var bestPair = -1L
                var bestFrequency = 1
                for ((pair, frequency) in frequencies) {
                    if (
                        frequency > bestFrequency ||
                        (frequency == bestFrequency && (bestPair == -1L || pair < bestPair))
                    ) {
                        bestPair = pair
                        bestFrequency = frequency
                    }
                }
                if (bestPair == -1L) break

                val left = (bestPair ushr 32).toInt()
                val right = bestPair.toInt()
                val mergedId = tokenBytes.size
                val mergedBytes = tokenBytes[left] + tokenBytes[right]
                require(mergedBytes.size <= MAX_TOKEN_BYTES) {
                    "Um token aprendido excedeu o tamanho máximo."
                }
                tokenTableBytes += mergedBytes.size
                require(tokenTableBytes <= MAX_TOKEN_TABLE_BYTES) {
                    "A tabela de tokens excedeu o limite de memória experimental."
                }
                tokenBytes += mergedBytes
                merges += Merge(left, right, mergedId)

                for (sequenceIndex in sequences.indices) {
                    if (sequenceIndex and 31 == 0) cancellationCheck?.invoke()
                    sequences[sequenceIndex] = applyStaticMerge(
                        sequences[sequenceIndex],
                        left,
                        right,
                        mergedId
                    )
                }
            }

            val vocabularyWithStop = tokenBytes.toMutableList().apply { add(ByteArray(0)) }
            return KellvynBpeTokenizer(
                merges = merges.toList(),
                tokenBytes = vocabularyWithStop,
                hasEndOfSequenceToken = true
            )
        }

        /** Restores a bounded tokenizer checkpoint and reconstructs its byte table. */
        fun readCheckpoint(input: InputStream): KellvynBpeTokenizer {
            val bytes = readBounded(input)
            require(bytes.size in (CHECKSUM_BYTES + 12)..MAX_CHECKPOINT_BYTES) {
                "Checkpoint do tokenizer inválido ou acima do limite."
            }

            val payloadSize = bytes.size - CHECKSUM_BYTES
            val payload = bytes.copyOfRange(0, payloadSize)
            val storedChecksum = bytes.copyOfRange(payloadSize, bytes.size)
            val actualChecksum = MessageDigest.getInstance("SHA-256").digest(payload)
            require(MessageDigest.isEqual(storedChecksum, actualChecksum)) {
                "A integridade do tokenizer não confere."
            }

            val data = DataInputStream(ByteArrayInputStream(payload))
            require(data.readInt() == CHECKPOINT_MAGIC) {
                "Checkpoint não pertence ao tokenizer Kellvyn."
            }
            val checkpointVersion = data.readInt()
            require(checkpointVersion in 1..CHECKPOINT_VERSION) {
                "Versão do tokenizer Kellvyn não suportada."
            }
            val count = data.readInt()
            require(count in 0..MAX_MERGES) { "Quantidade de merges inválida." }

            val tokenBytes = MutableList(BASE_VOCABULARY_SIZE) { tokenId ->
                byteArrayOf(tokenId.toByte())
            }
            val merges = mutableListOf<Merge>()
            val seenPairs = HashSet<Long>()
            var tokenTableBytes = BASE_VOCABULARY_SIZE

            repeat(count) {
                val left = data.readInt()
                val right = data.readInt()
                val mergedId = data.readInt()
                require(left in tokenBytes.indices && right in tokenBytes.indices) {
                    "O merge referencia um token inexistente."
                }
                require(mergedId == tokenBytes.size) {
                    "A sequência de IDs do tokenizer está inválida."
                }
                require(seenPairs.add(pairKey(left, right))) {
                    "O checkpoint repete um merge já registrado."
                }
                val mergedBytes = tokenBytes[left] + tokenBytes[right]
                require(mergedBytes.size <= MAX_TOKEN_BYTES) {
                    "Token do checkpoint acima do limite."
                }
                tokenTableBytes += mergedBytes.size
                require(tokenTableBytes <= MAX_TOKEN_TABLE_BYTES) {
                    "Tabela de tokens do checkpoint acima do limite."
                }
                tokenBytes += mergedBytes
                merges += Merge(left, right, mergedId)
            }
            require(data.available() == 0) { "Checkpoint do tokenizer contém dados extras." }
            val hasEndOfSequenceToken = checkpointVersion >= 2
            if (hasEndOfSequenceToken) tokenBytes += ByteArray(0)

            return KellvynBpeTokenizer(
                merges = merges.toList(),
                tokenBytes = tokenBytes.toList(),
                hasEndOfSequenceToken = hasEndOfSequenceToken
            )
        }

        private fun readBounded(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val single = input.read()
                    if (single < 0) break
                    require(total < MAX_CHECKPOINT_BYTES) {
                        "Checkpoint do tokenizer acima do limite."
                    }
                    output.write(single)
                    total++
                } else {
                    require(total <= MAX_CHECKPOINT_BYTES - count) {
                        "Checkpoint do tokenizer acima do limite."
                    }
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            return output.toByteArray()
        }

        private fun applyStaticMerge(
            input: List<Int>,
            leftTokenId: Int,
            rightTokenId: Int,
            mergedTokenId: Int
        ): List<Int> {
            if (input.size < 2) return input
            val output = ArrayList<Int>(input.size)
            var index = 0
            while (index < input.size) {
                if (
                    index + 1 < input.size &&
                    input[index] == leftTokenId &&
                    input[index + 1] == rightTokenId
                ) {
                    output += mergedTokenId
                    index += 2
                } else {
                    output += input[index]
                    index++
                }
            }
            return output
        }

        private fun pairKey(left: Int, right: Int): Long =
            (left.toLong() shl 32) or (right.toLong() and 0xffff_ffffL)
    }
}
