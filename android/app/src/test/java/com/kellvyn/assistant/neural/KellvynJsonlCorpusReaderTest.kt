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
        val source = (0 until 250).joinToString("\n", postfix = "\n") { jsonRecord(it) }
        val bytes = source.toByteArray(Charsets.UTF_8)
        val first = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(bytes),
            maxExamples = 250,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )
        val second = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(source.lineSequence().toList().asReversed().joinToString("\n", postfix = "\n")
                .toByteArray(Charsets.UTF_8)),
            maxExamples = 250,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertTrue(first.report.readyForTraining)
        assertEquals(250L, first.report.totalLines)
        assertEquals(250L, first.report.validRecords)
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
    fun rejectsSensitiveAndMalformedRecordsWithoutRetainingTheirText() {
        val lines = (0 until 250).map(::jsonRecord).toMutableList()
        lines += jsonRecord(
            251,
            completion = "Contato: pessoa@example.com"
        )
        lines += """{"id":"broken","prompt":"" 
        val result = KellvynJsonlCorpusReader.read(
            ByteArrayInputStream(lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)),
            maxExamples = 250,
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
        val bytes = (0 until 250).joinToString("\n", postfix = "\n") { jsonRecord(it) }
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
            maxExamples = 250,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertFalse(closed)
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
        completion: String = "A resposta para o número $index é $index."
    ): String {
        val fields = linkedMapOf(
            "id" to "ptbr-sample-$index",
            "prompt" to "Pergunta: diga o número $index.",
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
