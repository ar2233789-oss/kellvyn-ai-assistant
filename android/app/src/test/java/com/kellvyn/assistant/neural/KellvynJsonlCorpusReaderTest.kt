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
        assertEquals(300L, first.report.totalLines)
        assertEquals(300L, first.report.validRecords)
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
    fun refusesAStreamThatExceedsTheDeclaredByteLimit() {
        val source = (0 until 3).joinToString("\n", postfix = "\n") { jsonRecord(it) }
        try {
            KellvynJsonlCorpusReader.read(
                ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
                maxExamples = 30,
                maxSourceUtf8Bytes = 64L,
                minimumTrainExamples = 1,
                minimumValidationExamples = 1,
                minimumTestExamples = 1
            )
            throw AssertionError("O leitor não pode ultrapassar o limite de bytes da origem.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("limite de leitura"))
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
