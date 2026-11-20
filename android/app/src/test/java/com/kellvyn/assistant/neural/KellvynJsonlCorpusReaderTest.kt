package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class KellvynJsonlCorpusReaderTest {
    @Test
    fun streamsFullSourceAndSelectsDeterministicTrainValidationAndTestPartitions() {
        val source = (0 until 300).joinToString("\n", postfix = "\n") { jsonRecord(it) }
        val bytes = source.toByteArray(Charsets.UTF_8)
        val first = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(bytes),
            maxExamples = 300,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )
        val second = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.lineSequence().toList().asReversed().joinToString("\n", postfix = "\n")
                .toByteArray(Charsets.UTF_8)),
            maxExamples = 300,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertTrue(first.report.readyForTraining)
        assertEquals("sha256-normalized-prompt-80-10-10-v1", first.report.splitPolicyVersion)
        assertEquals(20261120L, first.report.samplingSeed)
        assertEquals(300, first.report.maximumSelectedExamples)
        assertEquals(480_000L, first.report.maximumRetainedUtf8Bytes)
        assertEquals(512L * 1024L * 1024L, first.report.maximumSourceUtf8Bytes)
        assertEquals(10_000_000L, first.report.maximumSourceLines)
        assertEquals(100, first.report.minimumTrainExamples)
        assertEquals(20, first.report.minimumValidationExamples)
        assertEquals(20, first.report.minimumTestExamples)
        assertEquals(256, first.report.configuredContextLength)
        assertEquals(32, first.report.configuredEmbeddingSize)
        assertEquals(4, first.report.configuredHeadCount)
        assertEquals(64, first.report.configuredMaxMerges)
        assertEquals(1, first.report.configuredEpochsPerExample)
        assertEquals(20261120L, first.report.configuredSeed)
        assertEquals(0.003f, first.report.configuredLearningRate, 0.0f)
        assertEquals(0.01f, first.report.configuredWeightDecay, 0.0f)
        assertEquals(100_000L, first.report.configuredMaxTrainingSteps)
        assertEquals(300L, first.report.totalLines)
        assertEquals(300L, first.report.validRecords)
        assertEquals(
            300L,
            first.report.validTrainRecords +
                first.report.validValidationRecords +
                first.report.validTestRecords
        )
        assertTrue(first.report.selectedTokenizerTrainingBytes <= first.report.tokenizerTrainingBudgetBytes)
        assertEquals(bytes.size.toLong(), first.report.sourceUtf8Bytes)
        assertTrue(first.report.selectedTrain >= 100)
        assertTrue(first.report.selectedValidation >= 20)
        assertTrue(first.report.selectedTest >= 20)
        assertTrue(first.report.selectedUtf8Bytes <= 480_000L)
        assertTrue(first.report.trainingTargetTokenUpperBound <= 100_000L)
        assertEquals(
            first.examples.map { it.id }.sorted(),
            second.examples.map { it.id }.sorted()
        )
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
            first.report.sourceSha256
        )
        assertNotNull(first.examples.firstOrNull { it.split == KellvynTrainingSplit.TEST })
    }

    @Test
    fun decodesEscapedQuotesNewlinesAndUnicodeWithoutCorruptingPortugueseText() {
        val prompt = "Pergunta:\n \"Qual é o símbolo?\" 🌱"
        val completion = "A resposta contém \"texto\" e acentuação: ação."
        val source = jsonRecord(700, completion = completion, prompt = prompt)
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            maxExamples = 30,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 1
        )

        assertEquals(1, result.examples.size)
        assertEquals(prompt, result.examples.single().prompt)
        assertEquals(completion, result.examples.single().completion)
    }

    @Test
    fun rejectsPromptsThatNormalizeToEmptyText() {
        val source = jsonRecord(702, prompt = "!?... —") + "\n"
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            maxExamples = 30,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 1
        )

        assertEquals(1L, result.report.malformedRecords)
        assertEquals(0L, result.report.validRecords)
        assertTrue(result.examples.isEmpty())
    }

    @Test
    fun rejectsDuplicateJsonObjectKeys() {
        val duplicateKeyLine = jsonRecord(701).replaceFirst(
            "{",
            "{\"id\":\"second-id\","
        )
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(duplicateKeyLine.toByteArray(Charsets.UTF_8)),
            maxExamples = 30,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 1
        )

        assertEquals(1L, result.report.malformedRecords)
        assertTrue(result.examples.isEmpty())
    }

    @Test
    fun keepsAlternateTrainingAnswersButDeduplicatesNormalizedTestPrompts() {
        val initialSource = (0 until 300).joinToString("\n", postfix = "\n") { jsonRecord(it) }
        val initial = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(initialSource.toByteArray(Charsets.UTF_8)),
            maxExamples = 1_000,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 20
        )
        val testPrompt = initial.examples.first { it.split == KellvynTrainingSplit.TEST }.prompt
        val records = (0 until 300).map { jsonRecord(it) }.toMutableList()
        records += jsonRecord(
            301,
            completion = "Uma resposta alternativa para o mesmo prompt de treino.",
            prompt = "Pergunta: diga o número 1."
        )
        records += jsonRecord(
            302,
            completion = "Resposta duplicada do teste.",
            prompt = "$testPrompt?"
        )
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(records.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)),
            maxExamples = 1_000,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 20
        )

        assertEquals(
            2,
            result.examples.count { it.prompt == "Pergunta: diga o número 1." }
        )
        assertEquals(
            1,
            result.examples.count {
                it.split == KellvynTrainingSplit.TEST &&
                    it.prompt.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() ==
                    testPrompt.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
            }
        )
    }

    @Test
    fun refusesModelConfigurationsWhoseWorstCaseVocabularyExceedsMemoryBudget() {
        val source = (0 until 300).joinToString("\n", postfix = "\n") { jsonRecord(it) }
        val config = KellvynMultiBlockTrainingConfig(
            contextLength = 256,
            embeddingSize = 256,
            headCount = 16,
            feedForwardSize = 1_024,
            depth = 8,
            maxMerges = 0,
            maxTrainingWorkingSetBytes = 16L * 1024L * 1024L
        )
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            config = config,
            maxExamples = 300,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertFalse(result.report.readyForTraining)
        assertTrue(
            result.report.estimatedTrainingWorkingSetBytes >
                result.report.trainingWorkingSetBudgetBytes
        )
        assertTrue(result.report.reasons.any { it.contains("orçamento de memória") })
    }

    @Test
    fun enforcesAggregateAndPerTextBpeWorkBeforeAllowingTraining() {
        val records = (0 until 300).map { jsonRecord(it) }.toMutableList()
        records += jsonRecord(
            9999,
            prompt = "X".repeat(10_000),
            completion = "Resposta curta."
        )
        val config = KellvynMultiBlockTrainingConfig(maxMerges = 4_096)
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(records.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)),
            config = config,
            maxExamples = 300,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertTrue(result.report.readyForTraining)
        assertTrue(result.report.selectedTrain >= 100)
        assertEquals(1L, result.report.tokenizerWorkRejectedRecords)
        assertEquals(0L, result.report.perExampleStepRejectedRecords)
        assertTrue(
            result.report.selectedTokenizerTrainingBytes * config.maxMerges.toLong() <=
                40_000_000L
        )
        assertTrue(result.report.reasons.any { it.contains("processamento BPE por texto") })
    }

    @Test
    fun rejectsSensitiveAndMalformedRecordsWithoutRetainingTheirText() {
        val lines = (0 until 300).map { jsonRecord(it) }.toMutableList()
        lines += jsonRecord(
            251,
            completion = "Contato: pessoa@example.com"
        )
        lines += "{\"id\":\"broken\",\"prompt\":\"\""
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)),
            maxExamples = 300,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertTrue(result.report.readyForTraining)
        assertEquals(1L, result.report.sensitiveRecords)
        assertEquals(1L, result.report.malformedRecords)
        assertTrue(result.examples.none { it.completion.contains("example.com") })
        assertTrue(result.report.reasons.any { it.contains("dados pessoais") })
    }

    @Test
    fun acceptsTheFinalJsonlRecordWithoutATrailingNewline() {
        val source = jsonRecord(901)
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            maxExamples = 30,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 1
        )

        assertEquals(1L, result.report.totalLines)
        assertEquals(1L, result.report.validRecords)
        assertEquals("ptbr-sample-901", result.examples.single().id)
    }

    @Test
    fun preflightChecksCancellationBetweenPhysicalLines() {
        val source = (0 until 20).joinToString("\n", postfix = "\n") { jsonRecord(it) }
        var cancellationChecks = 0
        try {
            KellvynJsonlCorpusReader.read(
                ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
                maxExamples = 30,
                minimumTrainExamples = 1,
                minimumValidationExamples = 1,
                minimumTestExamples = 1,
                cancellationCheck = {
                    cancellationChecks++
                    if (cancellationChecks == 4) {
                        throw java.util.concurrent.CancellationException("teste cancelado")
                    }
                }
            )
            throw AssertionError("A leitura precisa respeitar o cancelamento.")
        } catch (expected: java.util.concurrent.CancellationException) {
            assertEquals(4, cancellationChecks)
        }
    }

    @Test
    fun doesNotCloseCallerOwnedInputStream() {
        val bytes = (0 until 300).joinToString("\n", postfix = "\n") { jsonRecord(it) }
            .toByteArray(Charsets.UTF_8)
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        KellvynJsonlCorpusReader.read(
            input,
            maxExamples = 300,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertFalse(closed)
    }

    @Test
    fun discardsAnOversizedPhysicalLineWithoutParsingIt() {
        val oversized = jsonRecord(
            800,
            prompt = "x".repeat(500_100)
        )
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(oversized.toByteArray(Charsets.UTF_8)),
            maxExamples = 30,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 1
        )

        assertEquals(1L, result.report.totalLines)
        assertEquals(1L, result.report.malformedRecords)
        assertEquals(0L, result.report.validRecords)
        assertTrue(result.examples.isEmpty())
    }

    @Test
    fun recoversAfterDiscardingAnOversizedLineAndReadsTheNextRecord() {
        val oversized = jsonRecord(
            899,
            prompt = "x".repeat(500_100)
        )
        val valid = jsonRecord(900, prompt = "Pergunta curta após a linha longa.")
        val source = oversized + "\n" + valid + "\n"
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            maxExamples = 30,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 1
        )

        assertEquals(2L, result.report.totalLines)
        assertEquals(1L, result.report.malformedRecords)
        assertEquals(1L, result.report.validRecords)
        assertEquals("ptbr-sample-900", result.examples.single().id)
    }

    @Test
    fun readsWindowsCrLfLineSeparatorsWithoutMergingRecords() {
        val source = (0 until 3).joinToString("\r\n", postfix = "\r\n") { jsonRecord(it) }
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            maxExamples = 30,
            minimumTrainExamples = 1,
            minimumValidationExamples = 1,
            minimumTestExamples = 1
        )

        assertEquals(3L, result.report.totalLines)
        assertEquals(3L, result.report.validRecords)
    }

    @Test
    fun refusesAStreamThatExceedsTheDeclaredByteLimitWithoutReadingAnotherBuffer() {
        val source = (0 until 3).joinToString("\n", postfix = "\n")
            .toByteArray(Charsets.UTF_8)
        var consumedBytes = 0
        val input = object : ByteArrayInputStream(source) {
            override fun read(): Int {
                val value = super.read()
                if (value >= 0) consumedBytes++
                return value
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count > 0) consumedBytes += count
                return count
            }
        }

        try {
            KellvynJsonlCorpusReader.read(
                input,
                maxExamples = 30,
                maxSourceUtf8Bytes = 64L,
                minimumTrainExamples = 1,
                minimumValidationExamples = 1,
                minimumTestExamples = 1
            )
            throw AssertionError("O leitor não pode ultrapassar o limite de bytes da origem.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("limite de leitura"))
            assertTrue(consumedBytes <= 65)
        }
    }

    @Test
    fun refusesAStreamThatExceedsTheDeclaredLineLimit() {
        val source = (0 until 3).joinToString("\n", postfix = "\n") { jsonRecord(it) }
        try {
            KellvynJsonlCorpusReader.read(
                ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
                maxExamples = 30,
                maxLines = 2,
                minimumTrainExamples = 1,
                minimumValidationExamples = 1,
                minimumTestExamples = 1
            )
            throw AssertionError("O leitor não pode ultrapassar o limite de linhas.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("limite"))
        }
    }

    private fun jsonRecord(
        index: Int,
        completion: String = "A resposta para o número $index é $index.",
        prompt: String = "Pergunta: diga o número $index."
    ): String {
        val fields = linkedMapOf(
            "id" to "ptbr-sample-$index",
            "prompt" to prompt,
            "completion" to completion,
            "permission" to "OWNER_CREATED",
            "licenseId" to "owner-created",
            "sourceReference" to "owner://jsonl-test/$index",
            "rightsEvidence" to "Texto sintético criado para teste local."
        )
        return fields.entries.joinToString(",", prefix = "{", postfix = "}") {
            jsonString(it.key) + ":" + jsonString(it.value)
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
