package com.kellvyn.assistant.neural

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.PriorityQueue

/**
 * Streaming preflight for local JSONL corpora.
 *
 * Input contract: one JSON object per line with exactly these string fields:
 * id, prompt, completion, permission, licenseId, sourceReference, rightsEvidence.
 * The source does not choose TRAIN/VALIDATION/TEST; the split is assigned deterministically
 * from the normalized prompt so duplicate prompts cannot be separated across partitions.
 *
 * The reader scans the whole file but retains only a bounded, deterministic sample. It never
 * logs raw records and does not close the caller-owned InputStream.
 */
internal object KellvynJsonlCorpusReader {
    data class Report(
        val sourceSha256: String,
        val totalLines: Long,
        val validRecords: Long,
        val malformedRecords: Long,
        val sensitiveRecords: Long,
        val selectedOutByBudget: Long,
        val duplicateSelectedRecords: Long,
        val selectedTrain: Int,
        val selectedValidation: Int,
        val selectedTest: Int,
        val selectedUtf8Bytes: Long,
        val trainingTargetTokenUpperBound: Long,
        val readyForTraining: Boolean,
        val reasons: List<String>
    )

    data class Selection(
        val examples: List<KellvynInstructionTrainingExample>,
        val report: Report
    )

    private data class Candidate(
        val rank: String,
        val promptHash: String,
        val contentHash: String,
        val retainedBytes: Long,
        val targetTokenUpperBound: Long,
        val example: KellvynInstructionTrainingExample
    )

    private data class Bucket(
        val quota: Int,
        val byteBudget: Long,
        val targetBudget: Long = Long.MAX_VALUE,
        val queue: PriorityQueue<Candidate> = PriorityQueue(
            16,
            Comparator { left, right -> right.rank.compareTo(left.rank) }
        ),
        var retainedBytes: Long = 0L,
        var targetTokens: Long = 0L
    )

    fun read(
        input: InputStream,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        samplingSeed: Long = config.seed,
        maxExamples: Int = 1_000,
        maxRetainedUtf8Bytes: Long = 480_000L,
        maxLines: Long = 10_000_000L,
        minimumTrainExamples: Int = 100,
        minimumValidationExamples: Int = 20,
        minimumTestExamples: Int = KellvynNeuralBehaviorBenchmark.MINIMUM_EXAMPLES,
        cancellationCheck: () -> Unit = {}
    ): Selection {
        require(maxExamples in 3..1_000)
        require(maxExamples >= minimumTrainExamples + minimumValidationExamples + minimumTestExamples)
        require(maxRetainedUtf8Bytes in 1L..500_000L)
        require(maxLines > 0L)
        require(minimumTrainExamples > 0 && minimumValidationExamples > 0 && minimumTestExamples > 0)

        val trainQuota = maxExamples * 80 / 100
        val validationQuota = maxExamples * 10 / 100
        val testQuota = maxExamples - trainQuota - validationQuota
        require(trainQuota >= minimumTrainExamples)
        require(validationQuota >= minimumValidationExamples)
        require(testQuota >= minimumTestExamples)

        val validationBytes = maxRetainedUtf8Bytes / 6L
        val testBytes = maxRetainedUtf8Bytes / 6L
        val trainBytes = maxRetainedUtf8Bytes - validationBytes - testBytes
        val trainTargetBudget = config.maxTotalTrainingSteps / config.epochsPerExample.toLong()

        val buckets = mapOf(
            KellvynTrainingSplit.TRAIN to Bucket(
                quota = trainQuota,
                byteBudget = trainBytes,
                targetBudget = trainTargetBudget
            ),
            KellvynTrainingSplit.VALIDATION to Bucket(
                quota = validationQuota,
                byteBudget = validationBytes
            ),
            KellvynTrainingSplit.TEST to Bucket(
                quota = testQuota,
                byteBudget = testBytes
            )
        )

        val digest = MessageDigest.getInstance("SHA-256")
        val digestInput = DigestInputStream(input, digest)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val reader = BufferedReader(InputStreamReader(digestInput, decoder), 16 * 1024)

        var totalLines = 0L
        var validRecords = 0L
        var malformedRecords = 0L
        var sensitiveRecords = 0L

        while (true) {
            cancellationCheck()
            val line = reader.readLine() ?: break
            totalLines++
            require(totalLines <= maxLines) {
                "O corpus JSONL excedeu o limite de $maxLines linhas."
            }
            if (line.isBlank()) continue
            if (line.length > MAX_JSONL_LINE_CHARS) {
                malformedRecords++
                continue
            }

            val fields = try {
                StrictJsonStringObject.parse(line)
            } catch (_: IllegalArgumentException) {
                malformedRecords++
                continue
            }

            val record = try {
                require(fields.keys == REQUIRED_FIELDS) {
                    "O registro JSONL não corresponde ao contrato de campos."
                }
                val id = fields.getValue("id")
                val prompt = fields.getValue("prompt")
                val completion = fields.getValue("completion")
                val permission = KellvynTrainingPermission.valueOf(fields.getValue("permission"))
                val licenseId = fields.getValue("licenseId")
                val sourceReference = fields.getValue("sourceReference")
                val rightsEvidence = fields.getValue("rightsEvidence")

                val allText = listOf(
                    id, prompt, completion, permission.name, licenseId, sourceReference, rightsEvidence
                )
                if (allText.any(KellvynTrainingInputGuard::containsSensitiveMaterial)) {
                    sensitiveRecords++
                    continue
                }

                val promptBytes = prompt.toByteArray(Charsets.UTF_8).size.toLong()
                val completionBytes = completion.toByteArray(Charsets.UTF_8).size.toLong()
                require(prompt.isNotBlank() && completion.isNotBlank())
                require(promptBytes + completionBytes in 2L..MAX_EXAMPLE_BYTES.toLong())

                val provenance = KellvynTrainingProvenance(
                    permission = permission,
                    licenseId = licenseId,
                    sourceReference = sourceReference,
                    rightsEvidence = rightsEvidence
                )
                KellvynInstructionTrainingExample(
                    id = id,
                    prompt = prompt,
                    completion = completion,
                    split = splitFor(prompt, samplingSeed),
                    provenance = provenance
                )
            } catch (_: IllegalArgumentException) {
                malformedRecords++
                continue
            }

            validRecords++
            val promptHash = sha256(normalizePrompt(record.prompt))
            val contentHash = sha256(record.prompt + "\u0000" + record.completion)
            val retainedBytes = listOf(
                record.id,
                record.prompt,
                record.completion,
                record.provenance.permission.name,
                record.provenance.licenseId,
                record.provenance.sourceReference,
                record.provenance.rightsEvidence
            ).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
            val targetTokenUpperBound = if (record.split == KellvynTrainingSplit.TRAIN) {
                record.completion.toByteArray(Charsets.UTF_8).size.toLong() + 1L
            } else {
                0L
            }
            val rank = sha256("$samplingSeed\u0000${record.id}\u0000$contentHash")
            val candidate = Candidate(
                rank = rank,
                promptHash = promptHash,
                contentHash = contentHash,
                retainedBytes = retainedBytes,
                targetTokenUpperBound = targetTokenUpperBound,
                example = record
            )
            offer(buckets.getValue(record.split), candidate)
        }

        val selected = buckets.values
            .flatMap { it.queue.toList() }
            .sortedBy { it.rank }

        val unique = ArrayList<Candidate>(selected.size)
        val ids = HashSet<String>()
        val benchmarkPrompts = HashSet<String>()
        val content = HashSet<String>()
        var duplicateSelectedRecords = 0L
        for (candidate in selected) {
            val id = candidate.example.id
            if (!ids.add(id) ||
                !content.add(candidate.contentHash) ||
                (candidate.example.split == KellvynTrainingSplit.TEST &&
                    !benchmarkPrompts.add(candidate.promptHash))
            ) {
                duplicateSelectedRecords++
                continue
            }
            unique += candidate
        }

        val selectedExamples = unique.map { it.example }
        val selectedCounts = selectedExamples.groupingBy { it.split }.eachCount()
        val selectedBytes = unique.sumOf { it.retainedBytes }
        val targetUpperBound = unique.sumOf { it.targetTokenUpperBound }
        val sourceSha256 = digest.digest().toHex()
        val reasons = mutableListOf<String>()

        val trainCount = selectedCounts[KellvynTrainingSplit.TRAIN] ?: 0
        val validationCount = selectedCounts[KellvynTrainingSplit.VALIDATION] ?: 0
        val testCount = selectedCounts[KellvynTrainingSplit.TEST] ?: 0
        if (trainCount < minimumTrainExamples) {
            reasons += "A amostra selecionada precisa de pelo menos $minimumTrainExamples exemplos TRAIN."
        }
        if (validationCount < minimumValidationExamples) {
            reasons += "A amostra selecionada precisa de pelo menos $minimumValidationExamples exemplos VALIDATION."
        }
        if (testCount < minimumTestExamples) {
            reasons += "A amostra selecionada precisa de pelo menos $minimumTestExamples exemplos TEST independentes."
        }
        if (targetUpperBound > trainTargetBudget) {
            reasons += "A amostra excederia o orçamento de passos antes do treinamento."
        }
        if (sensitiveRecords > 0L) {
            reasons += "$sensitiveRecords registros foram recusados por credenciais ou dados pessoais reconhecíveis."
        }
        if (malformedRecords > 0L) {
            reasons += "$malformedRecords registros inválidos foram ignorados."
        }
        if (duplicateSelectedRecords > 0L) {
            reasons += "$duplicateSelectedRecords registros duplicados foram removidos da amostra."
        }

        return Selection(
            examples = selectedExamples,
            report = Report(
                sourceSha256 = sourceSha256,
                totalLines = totalLines,
                validRecords = validRecords,
                malformedRecords = malformedRecords,
                sensitiveRecords = sensitiveRecords,
                selectedOutByBudget = (validRecords - selected.size.toLong()).coerceAtLeast(0L),
                duplicateSelectedRecords = duplicateSelectedRecords,
                selectedTrain = trainCount,
                selectedValidation = validationCount,
                selectedTest = testCount,
                selectedUtf8Bytes = selectedBytes,
                trainingTargetTokenUpperBound = targetUpperBound,
                readyForTraining = reasons.none {
                    it.startsWith("A amostra selecionada") ||
                        it.startsWith("A amostra excederia")
                },
                reasons = reasons
            )
        )
    }

    private fun offer(bucket: Bucket, candidate: Candidate) {
        if (candidate.retainedBytes > bucket.byteBudget ||
            candidate.targetTokenUpperBound > bucket.targetBudget
        ) return

        bucket.queue += candidate
        bucket.retainedBytes += candidate.retainedBytes
        bucket.targetTokens += candidate.targetTokenUpperBound

        while (
            bucket.queue.size > bucket.quota ||
            bucket.retainedBytes > bucket.byteBudget ||
            bucket.targetTokens > bucket.targetBudget
        ) {
            val removed = bucket.queue.remove()
            bucket.retainedBytes -= removed.retainedBytes
            bucket.targetTokens -= removed.targetTokenUpperBound
        }
    }

    private fun splitFor(prompt: String, seed: Long): KellvynTrainingSplit {
        val hash = sha256("$seed\u0000${normalizePrompt(prompt)}")
        val shard = hash.substring(0, 8).toLong(16) % 100L
        return when {
            shard < 80L -> KellvynTrainingSplit.TRAIN
            shard < 90L -> KellvynTrainingSplit.VALIDATION
            else -> KellvynTrainingSplit.TEST
        }
    }

    private fun normalizePrompt(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private const val MAX_JSONL_LINE_CHARS = 500_000
    private const val MAX_EXAMPLE_BYTES = 65_536
    private val REQUIRED_FIELDS = setOf(
        "id", "prompt", "completion", "permission",
        "licenseId", "sourceReference", "rightsEvidence"
    )

    /** Strict, dependency-free JSON object parser for string-to-string records only. */
    private object StrictJsonStringObject {
        fun parse(source: String): Map<String, String> = Parser(source).parse()

        private class Parser(private val source: String) {
            private var index = 0

            fun parse(): Map<String, String> {
                skipWhitespace()
                expect('{')
                skipWhitespace()
                val values = LinkedHashMap<String, String>()
                if (peek('}')) {
                    index++
                    finish()
                    return values
                }
                while (true) {
                    skipWhitespace()
                    val key = readString()
                    skipWhitespace()
                    expect(':')
                    skipWhitespace()
                    val value = readString()
                    require(!values.containsKey(key)) { "Chave JSON duplicada." }
                    values[key] = value
                    skipWhitespace()
                    if (peek('}')) {
                        index++
                        break
                    }
                    expect(',')
                }
                finish()
                return values
            }

            private fun readString(): String {
                expect('"')
                val result = StringBuilder()
                while (index < source.length) {
                    val current = source[index++]
                    when {
                        current == '"' -> {
                            val decoded = result.toString()
                            require(isWellFormedUtf16(decoded)) { "String JSON contém Unicode inválido." }
                            return decoded
                        }
                        current == '\\' -> {
                            require(index < source.length) { "Escape JSON incompleto." }
                            when (val escaped = source[index++]) {
                                '"' -> result.append('"')
                                '\\' -> result.append('\\')
                                '/' -> result.append('/')
                                'b' -> result.append('\b')
                                'f' -> result.append('\u000c')
                                'n' -> result.append('\n')
                                'r' -> result.append('\r')
                                't' -> result.append('\t')
                                'u' -> result.append(readUnicodeEscape())
                                else -> throw IllegalArgumentException("Escape JSON inválido: $escaped")
                            }
                        }
                        current.code < 0x20 -> throw IllegalArgumentException("Controle não escapado em string JSON.")
                        else -> result.append(current)
                    }
                }
                throw IllegalArgumentException("String JSON não terminada.")
            }

            private fun readUnicodeEscape(): Char {
                require(index + 4 <= source.length) { "Escape Unicode incompleto." }
                val code = source.substring(index, index + 4).toIntOrNull(16)
                    ?: throw IllegalArgumentException("Escape Unicode inválido.")
                index += 4
                return code.toChar()
            }

            private fun finish() {
                skipWhitespace()
                require(index == source.length) { "Conteúdo extra após objeto JSON." }
            }

            private fun skipWhitespace() {
                while (index < source.length && source[index] in charArrayOf(' ', '\t', '\r', '\n')) {
                    index++
                }
            }

            private fun expect(character: Char) {
                require(index < source.length && source[index] == character) {
                    "Estrutura JSON inválida."
                }
                index++
            }

            private fun peek(character: Char): Boolean =
                index < source.length && source[index] == character

            private fun isWellFormedUtf16(value: String): Boolean {
                var cursor = 0
                while (cursor < value.length) {
                    val character = value[cursor]
                    when {
                        Character.isHighSurrogate(character) -> {
                            if (cursor + 1 >= value.length ||
                                !Character.isLowSurrogate(value[cursor + 1])
                            ) return false
                            cursor += 2
                        }
                        Character.isLowSurrogate(character) -> return false
                        else -> cursor++
                    }
                }
                return true
            }
        }
    }
}
