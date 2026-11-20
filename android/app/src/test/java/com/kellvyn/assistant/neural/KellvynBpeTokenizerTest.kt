package com.kellvyn.assistant.neural

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

class KellvynBpeTokenizerTest {
    @Test
    fun byteFallbackRoundTripsPortugueseAndUnicodeExactly() {
        val corpus = listOf(
            "A Kellvyn aprende português brasileiro.",
            "memória, raciocínio, programação e criação de aplicativos.",
            "chuva 🌧️, raio ⚡, café, ação, coração e inteligência."
        )
        val tokenizer = KellvynBpeTokenizer.train(corpus, maxMerges = 180)
        val sample = "Kellvyn: ação, coração, programação, ção e 🚀\nsegunda linha."
        val restored = tokenizer.decode(tokenizer.encode(sample))

        assertEquals(sample, restored)
        assertTrue(tokenizer.vocabularySize >= 256)
        assertEquals(tokenizer.mergeCount + 257, tokenizer.vocabularySize)
        assertEquals(tokenizer.vocabularySize - 1, tokenizer.endOfSequenceTokenId)
    }

    @Test
    fun trainingIsDeterministicForTheSameOrderedCorpus() {
        val corpus = listOf(
            "kellvyn aprende do zero",
            "kellvyn aprende português",
            "modelo neural próprio"
        )
        val first = KellvynBpeTokenizer.train(corpus, maxMerges = 80)
        val second = KellvynBpeTokenizer.train(corpus, maxMerges = 80)

        assertArrayEquals(first.encode("kellvyn aprende"), second.encode("kellvyn aprende"))
        val firstBytes = ByteArrayOutputStream().also(first::writeCheckpoint).toByteArray()
        val secondBytes = ByteArrayOutputStream().also(second::writeCheckpoint).toByteArray()
        assertArrayEquals(firstBytes, secondBytes)
    }

    @Test
    fun checkpointRestoresMergeTableAndRoundTripBehavior() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn treina seus próprios tokens.", "tokens próprios e treino próprio."),
            maxMerges = 96
        )
        val bytes = ByteArrayOutputStream().also(tokenizer::writeCheckpoint).toByteArray()
        val restored = KellvynBpeTokenizer.readCheckpoint(ByteArrayInputStream(bytes))

        assertEquals(tokenizer.vocabularySize, restored.vocabularySize)
        assertArrayEquals(tokenizer.encode("treina seus próprios tokens"), restored.encode("treina seus próprios tokens"))
        assertEquals("Kellvyn é própria.", restored.decode(restored.encode("Kellvyn é própria.")))
    }


    @Test
    fun endOfSequenceTokenIsDedicatedAndNeverProducedByTextEncoding() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn aprende português."), maxMerges = 24
        )
        val endTokenId = requireNotNull(tokenizer.endOfSequenceTokenId)
        val encoded = tokenizer.encode("Kellvyn encerra a resposta.")

        assertTrue(endTokenId !in encoded)
        assertEquals(tokenizer.vocabularySize - 1, endTokenId)
        assertEquals("", tokenizer.decode(intArrayOf(endTokenId)))
        assertEquals(
            "Resposta final.",
            tokenizer.decode(tokenizer.encode("Resposta final."))
        )
    }

    @Test
    fun legacyVersionOneCheckpointRestoresWithoutInventingAnEosToken() {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { data ->
            data.writeInt(0x4B564250) // KVBP
            data.writeInt(1) // legacy format
            data.writeInt(0) // no merges
        }
        val body = payload.toByteArray()
        val fullCheckpoint = ByteArrayOutputStream().apply {
            write(body)
            write(MessageDigest.getInstance("SHA-256").digest(body))
        }.toByteArray()

        val restored = KellvynBpeTokenizer.readCheckpoint(
            ByteArrayInputStream(fullCheckpoint)
        )

        assertNull(restored.endOfSequenceTokenId)
        assertEquals(256, restored.vocabularySize)
        assertEquals("compatibilidade", restored.decode(restored.encode("compatibilidade")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkpointRejectsModifiedBytes() {
        val tokenizer = KellvynBpeTokenizer.train(listOf("teste repetido teste repetido"), maxMerges = 20)
        val bytes = ByteArrayOutputStream().also(tokenizer::writeCheckpoint).toByteArray()
        bytes[8] = (bytes[8].toInt() xor 0x01).toByte()
        KellvynBpeTokenizer.readCheckpoint(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tokenizerRejectsEmptyCorpus() {
        KellvynBpeTokenizer.train(emptyList(), maxMerges = 10)
    }
}
