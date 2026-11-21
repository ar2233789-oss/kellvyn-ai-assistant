package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynPersonaTest {
    @Test fun systemInstructionPreservesBaseAndAddsIdentity() {
        val result = KellvynPersona.systemInstruction("Base")
        assertTrue(result.startsWith("Base"))
        assertTrue(result.contains("Kellvyn é apresentado como seu criador"))
        assertTrue(result.contains("português brasileiro"))
    }

    @Test fun answersDirectCreatorQuestionsWithoutInventingPersonalDetails() {
        val answer = KellvynPersona.creatorAnswer("Quem criou a Kellvyn?")
        assertNotNull(answer)
        assertTrue(answer!!.contains("Kellvyn é apresentado como o criador"))
        assertTrue(answer.contains("Não invento detalhes"))
        assertNull(KellvynPersona.creatorAnswer("Qual é a capital do Brasil?"))
    }

    @Test fun styleIsDeepButDoesNotRomanticizeMentalHealthIssues() {
        assertTrue(KellvynPersona.SYSTEM_INSTRUCTION.contains("intensa, profunda, sóbria"))
        assertTrue(KellvynPersona.SYSTEM_INSTRUCTION.contains("nunca glorifique depressão"))
    }

    @Test fun emptyBaseDoesNotAddLeadingWhitespace() {
        assertFalse(KellvynPersona.systemInstruction("").startsWith("\n"))
        assertEquals(KellvynPersona.SYSTEM_INSTRUCTION, KellvynPersona.systemInstruction(""))
    }
}
