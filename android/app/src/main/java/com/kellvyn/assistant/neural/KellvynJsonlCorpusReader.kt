package com.kellvyn.assistant.neural

import com.kellvyn.assistant.KellvynDecisionProtocolBehaviorBenchmark

import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader
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
    data class LicenseCount(
        val licenseId: String,
        val records: Long
    )

    data class Report(
        val sourceSha256: String,
        val sourceUtf8Bytes: Long,
        val splitPolicyVersion: String,
        val samplingSeed: Long,
        val maximumSelectedExamples: Int,
        val maximumRetainedUtf8Bytes: Long,
        val maximumSourceUtf8Bytes: Long,
        val maximumSourceLines: Long,
        val minimumTrainExamples: Int,
        val minimumValidationExamples: Int,
        val minimumTestExamples: Int,
        val configuredContextLength: Int,
        val configuredEmbeddingSize: Int,
        val configuredHeadCount: Int,
        val configuredFeedForwardSize: Int,
        val configuredDepth: Int,
        val configuredMaxMerges: Int,
        val configuredEpochsPerExample: Int,
        val configuredSeed: Long,
        val configuredLearningRate: Float,
        val configuredWeightDecay: Float,
        val configuredMaxTrainingSteps: Long,
        val totalLines: Long,
        val validRecords: Long,
        val validTrainRecords: Long,
        val validValidationRecords: Long,
        val validTestRecords: Long,
        val permissionCounts: Map<String, Long>,
        val licenseCounts: List<LicenseCount>,
        val licenseRecordsOutsideSummary: Long,
        val malformedRecords: Long,
        val sensitiveRecords: Long,
        val tokenizerWorkRejectedRecords: Long,
        val perExampleStepRejectedRecords: Long,
        val selectedOutByBudget: Long,
        val duplicateSelectedRecords: Long,
        val selectedTrain: Int,
        val selectedValidation: Int,
        val selectedTest: Int,
        val selectedUtf8Bytes: Long,
        val selectedTokenizerTrainingBytes: Long,
        val tokenizerTrainingBudgetBytes: Long,
        val estimatedTrainingWorkingSetBytes: Long,
        val trainingWorkingSetBudgetBytes: Long,
        val trainingTargetTokenUpperBound: Long,
        val needsManualReview: Boolean,
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
        val tokenizerTrainingBytes: Long,
        val example: KellvynInstructionTrainingExample
    )

    private data class BoundedLine(
        val text: String?,
        val exceededLimit: Boolean
    )

    private data class Bucket(
        val quota: Int,
        val byteBudget: Long,
        val targetBudget: Long = Long.MAX_VALUE,
        val tokenizerTrainingBudget: Long = Long.MAX_VALUE,
        val queue: PriorityQueue<Candidate> = PriorityQueue(
            16,
            Comparator { left, right -> right.rank.compareTo(left.rank) }
        ),
        var retainedBytes: Long = 0L,
        var targetTokens: Long = 0L,
        var tokenizerTrainingBytes: Long = 0L
    )

    fun read(
        input: InputStream,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        samplingSeed: Long = config.seed,
        maxExamples: Int = 1_000,
        maxRetainedUtf8Bytes: Long = 480_000L,
        maxSourceUtf8Bytes: Long = 512L * 1024L * 1024L,
        maxLines: Long = 10_000_000L,
        minimumTrainExamples: Int = 100,
        minimumValidationExamples: Int = 20,
        minimumTestExamples: Int = KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_EXAMPLES,
        cancellationCheck: () -> Unit = {}
    ): Selection {
        require(maxExamples in 3..1_000)
        require(maxExamples >= minimumTrainExamples + minimumValidationExamples + minimumTestExamples)
        require(maxRetainedUtf8Bytes in 1L..500_000L)
        require(maxSourceUtf8Bytes in 1L..(2L * 1024L * 1024L * 1024L))
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
        val tokenizerTrainingBudget = if (config.maxMerges == 0) {
            Long.MAX_VALUE
        } else {
            MAX_TOKENIZER_TRAINING_WORK / config.maxMerges.toLong()
        }
        val maxTokenizerInputBytes = if (config.maxMerges == 0) {
            MAX_EXAMPLE_BYTES.toLong()
        } else {
            minOf(
                MAX_EXAMPLE_BYTES.toLong(),
                MAX_TOKENIZER_TRAINING_WORK / config.maxMerges.toLong()
            )
        }
        // Use the maximum vocabulary implied by maxMerges so admission cannot underestimate
        // model memory merely because this corpus happens to produce fewer BPE merges.
        val estimatedTrainingWorkingSetBytes = try {
            KellvynMultiBlockTransformer.Config(
                vocabularySize = BASE_VOCABULARY_SIZE + config.maxMerges + 1,
                contextLength = config.contextLength,
                embeddingSize = config.embeddingSize,
                headCount = config.headCount,
                feedForwardSize = config.feedForwardSize,
                depth = config.depth,
                seed = config.seed
            ).estimatedTrainingWorkingSetBytes()
        } catch (_: IllegalArgumentException) {
            Long.MAX_VALUE
        }

        val buckets = mapOf(
            KellvynTrainingSplit.TRAIN to Bucket(
                quota = trainQuota,
                byteBudget = trainBytes,
                targetBudget = trainTargetBudget,
                tokenizerTrainingBudget = tokenizerTrainingBudget
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
        val countedInput = BoundedCountingInputStream(input, maxSourceUtf8Bytes)
        val digestInput = DigestInputStream(countedInput, digest)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val reader = PushbackReader(InputStreamReader(digestInput, decoder), 8_192)

        var totalLines = 0L
        var validRecords = 0L
        var malformedRecords = 0L
        var sensitiveRecords = 0L
        var validTrainRecords = 0L
        var validValidationRecords = 0L
        var validTestRecords = 0L
        val permissionCounts = mutableMapOf<String, Long>()
        val licenseCounts = linkedMapOf<String, Long>()
        var licenseRecordsOutsideSummary = 0L
        var tokenizerWorkRejectedRecords = 0L
        var perExampleStepRejectedRecords = 0L

        while (true) {
            cancellationCheck()
            val boundedLine = readBoundedLine(reader, MAX_JSONL_LINE_CHARS) ?: break
            totalLines++
            require(totalLines <= maxLines) {
                "O corpus JSONL excedeu o limite de $maxLines linhas."
            }
            if (boundedLine.exceededLimit) {
                malformedRecords++
                continue
            }
            val line = boundedLine.text ?: ""
            if (line.isBlank()) continue

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
                if (allText.any { KellvynTrainingInputGuard.containsSensitiveMaterial(it) }) {
                    sensitiveRecords++
                    continue
                }

                val promptBytes = prompt.toByteArray(Charsets.UTF_8).size.toLong()
                val completionBytes = completion.toByteArray(Charsets.UTF_8).size.toLong()
                require(prompt.isNotBlank() && completion.isNotBlank())
                require(normalizePrompt(prompt).isNotBlank()) {
                    "O prompt não contém letras ou números após normalização."
                }
                require(promptBytes + completionBytes in 2L..MAX_EXAMPLE_BYTES.toLong())
                if (promptBytes > maxTokenizerInputBytes || completionBytes > maxTokenizerInputBytes) {
                    tokenizerWorkRejectedRecords++
                    continue
                }
                if (completionBytes + 1L >
                    MAX_STEPS_PER_EXAMPLE / config.epochsPerExample.toLong()
                ) {
                    perExampleStepRejectedRecords++
                    continue
                }

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
            when (record.split) {
                KellvynTrainingSplit.TRAIN -> validTrainRecords++
                KellvynTrainingSplit.VALIDATION -> validValidationRecords++
                KellvynTrainingSplit.TEST -> validTestRecords++
            }
            val permissionName = record.provenance.permission.name
            permissionCounts[permissionName] = (permissionCounts[permissionName] ?: 0L) + 1L
            val licenseId = record.provenance.licenseId
            if (licenseCounts.containsKey(licenseId)) {
                licenseCounts[licenseId] = licenseCounts.getValue(licenseId) + 1L
            } else if (licenseCounts.size < MAX_LICENSE_SUMMARY_ENTRIES) {
                licenseCounts[licenseId] = 1L
            } else {
                licenseRecordsOutsideSummary++
            }
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
            val promptBytes = record.prompt.toByteArray(Charsets.UTF_8).size.toLong()
            val completionBytes = record.completion.toByteArray(Charsets.UTF_8).size.toLong()
            val targetTokenUpperBound = if (record.split == KellvynTrainingSplit.TRAIN) {
                completionBytes + 1L
            } else {
                0L
            }
            val tokenizerTrainingBytes = if (record.split == KellvynTrainingSplit.TRAIN) {
                promptBytes + completionBytes
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
                tokenizerTrainingBytes = tokenizerTrainingBytes,
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
        val tokenizerTrainingBytes = unique.sumOf { it.tokenizerTrainingBytes }
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
        if (estimatedTrainingWorkingSetBytes > config.maxTrainingWorkingSetBytes) {
            reasons += "A configuração KN-004 excede o orçamento de memória antes da alocação do modelo."
        }
        if (tokenizerWorkRejectedRecords > 0L) {
            reasons += "$tokenizerWorkRejectedRecords registros foram recusados pelo limite de processamento BPE por texto."
        }
        if (perExampleStepRejectedRecords > 0L) {
            reasons += "$perExampleStepRejectedRecords registros foram recusados pelo limite de passos por exemplo."
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

        val needsManualReview = sensitiveRecords > 0L || malformedRecords > 0L
        val readyForTraining =
            !needsManualReview &&
                trainCount >= minimumTrainExamples &&
                validationCount >= minimumValidationExamples &&
                testCount >= minimumTestExamples &&
                targetUpperBound <= trainTargetBudget &&
                tokenizerTrainingBytes <= tokenizerTrainingBudget &&
                estimatedTrainingWorkingSetBytes <= config.maxTrainingWorkingSetBytes &&
                selectedBytes <= maxRetainedUtf8Bytes

        return Selection(
            examples = selectedExamples,
            report = Report(
                sourceSha256 = sourceSha256,
                sourceUtf8Bytes = countedInput.bytesRead,
                splitPolicyVersion = SPLIT_POLICY_VERSION,
                samplingSeed = samplingSeed,
                maximumSelectedExamples = maxExamples,
                maximumRetainedUtf8Bytes = maxRetainedUtf8Bytes,
                maximumSourceUtf8Bytes = maxSourceUtf8Bytes,
                maximumSourceLines = maxLines,
                minimumTrainExamples = minimumTrainExamples,
                minimumValidationExamples = minimumValidationExamples,
                minimumTestExamples = minimumTestExamples,
                configuredContextLength = config.contextLength,
                configuredEmbeddingSize = config.embeddingSize,
                configuredHeadCount = config.headCount,
                configuredFeedForwardSize = config.feedForwardSize,
                configuredDepth = config.depth,
                configuredMaxMerges = config.maxMerges,
                configuredEpochsPerExample = config.epochsPerExample,
                configuredSeed = config.seed,
                configuredLearningRate = config.learningRate,
                configuredWeightDecay = config.weightDecay,
                configuredMaxTrainingSteps = config.maxTotalTrainingSteps,
                totalLines = totalLines,
                validRecords = validRecords,
                validTrainRecords = validTrainRecords,
                validValidationRecords = validValidationRecords,
                validTestRecords = validTestRecords,
                permissionCounts = permissionCounts.toSortedMap(),
                licenseCounts = licenseCounts.entries
                    .map { LicenseCount(it.key, it.value) }
                    .sortedBy { it.licenseId },
                licenseRecordsOutsideSummary = licenseRecordsOutsideSummary,
                malformedRecords = malformedRecords,
                sensitiveRecords = sensitiveRecords,
                tokenizerWorkRejectedRecords = tokenizerWorkRejectedRecords,
                perExampleStepRejectedRecords = perExampleStepRejectedRecords,
                selectedOutByBudget = (validRecords - selected.size.toLong()).coerceAtLeast(0L),
                duplicateSelectedRecords = duplicateSelectedRecords,
                selectedTrain = trainCount,
                selectedValidation = validationCount,
                selectedTest = testCount,
                selectedUtf8Bytes = selectedBytes,
                selectedTokenizerTrainingBytes = tokenizerTrainingBytes,
                tokenizerTrainingBudgetBytes = tokenizerTrainingBudget,
                estimatedTrainingWorkingSetBytes = estimatedTrainingWorkingSetBytes,
                trainingWorkingSetBudgetBytes = config.maxTrainingWorkingSetBytes,
                trainingTargetTokenUpperBound = targetUpperBound,
                needsManualReview = needsManualReview,
                readyForTraining = readyForTraining,
                reasons = reasons
            )
        )
    }

    private fun offer(bucket: Bucket, candidate: Candidate) {
        if (candidate.retainedBytes > bucket.byteBudget ||
            candidate.targetTokenUpperBound > bucket.targetBudget ||
            candidate.tokenizerTrainingBytes > bucket.tokenizerTrainingBudget
        ) return

        bucket.queue += candidate
        bucket.retainedBytes += candidate.retainedBytes
        bucket.targetTokens += candidate.targetTokenUpperBound
        bucket.tokenizerTrainingBytes += candidate.tokenizerTrainingBytes

        while (
            bucket.queue.size > bucket.quota ||
            bucket.retainedBytes > bucket.byteBudget ||
            bucket.targetTokens > bucket.targetBudget ||
            bucket.tokenizerTrainingBytes > bucket.tokenizerTrainingBudget
        ) {
            val removed = bucket.queue.remove()
            bucket.retainedBytes -= removed.retainedBytes
            bucket.targetTokens -= removed.targetTokenUpperBound
            bucket.tokenizerTrainingBytes -= removed.tokenizerTrainingBytes
        }
    }

    /**
     * Reads at most maxCharacters into memory. Once the limit is exceeded, the rest of that
     * physical line is discarded without appending it to a StringBuilder.
     */
    private fun readBoundedLine(
        reader: PushbackReader,
        maxCharacters: Int
    ): BoundedLine? {
        val builder = StringBuilder(minOf(maxCharacters, 8_192))
        val chunk = CharArray(8_192)
        var consumedAny = false
        var exceededLimit = false

        while (true) {
            val count = reader.read(chunk, 0, chunk.size)
            if (count < 0) {
                if (!consumedAny) return null
                return BoundedLine(
                    text = if (exceededLimit) null else builder.toString(),
                    exceededLimit = exceededLimit
                )
            }
            consumedAny = true
            var newlineIndex = -1
            for (index in 0 until count) {
                if (chunk[index] == '\n') {
                    newlineIndex = index
                    break
                }
            }
            val lineCharacterCount = if (newlineIndex >= 0) newlineIndex else count

            if (!exceededLimit) {
                val remaining = maxCharacters - builder.length
                if (lineCharacterCount <= remaining) {
                    builder.append(chunk, 0, lineCharacterCount)
                } else {
                    builder.append(chunk, 0, remaining)
                    builder.setLength(0)
                    exceededLimit = true
                }
            }

            if (newlineIndex >= 0) {
                val remainingChunkCharacters = count - newlineIndex - 1
                if (remainingChunkCharacters > 0) {
                    reader.unread(chunk, newlineIndex + 1, remainingChunkCharacters)
                }
                return BoundedLine(
                    text = if (exceededLimit) null else builder.toString(),
                    exceededLimit = exceededLimit
                )
            }
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

    private class BoundedCountingInputStream(
        input: InputStream,
        private val maximumBytes: Long
    ) : java.io.FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) count(1L)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && length <= buffer.size - offset)
            if (length == 0) return 0

            val remaining = maximumBytes - bytesRead
            val allowedLength = minOf(length.toLong(), remaining).toInt()
            if (allowedLength > 0) {
                val readCount = super.read(buffer, offset, allowedLength)
                if (readCount > 0) count(readCount.toLong())
                return readCount
            }

            // Probe exactly one byte so an exactly-sized file can still end normally.
            // If data remains, count() raises before the byte can reach the JSON decoder.
            val probe = super.read()
            if (probe < 0) return -1
            count(1L)
            return -1
        }

        private fun count(amount: Long) {
            bytesRead += amount
            require(bytesRead <= maximumBytes) {
                "O arquivo JSONL excedeu o limite de leitura de $maximumBytes bytes."
            }
        }
    }

    private const val MAX_LICENSE_SUMMARY_ENTRIES = 32
    private const val SPLIT_POLICY_VERSION = "sha256-normalized-prompt-80-10-10-v1"
    private const val MAX_JSONL_LINE_CHARS = 500_000
    private const val MAX_EXAMPLE_BYTES = 65_536
    private const val BASE_VOCABULARY_SIZE = 256
    private const val MAX_STEPS_PER_EXAMPLE = 50_000L
    private const val MAX_TOKENIZER_TRAINING_WORK = 40_000_000L
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
