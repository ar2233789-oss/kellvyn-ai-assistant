package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionPolicyTest {
    @Test fun disabledProtectionLeavesBaseAndAddsKellvynIdentity() {
        val base = "Instrução-base"
        val effective = ProtectionPolicy.systemInstruction(base, false)
        assertTrue(effective.startsWith(base))
        assertTrue(effective.contains("Kellvyn é apresentado como seu criador"))
        assertTrue(effective.contains("português brasileiro"))
        assertTrue(!effective.contains("PROTEÇÃO REFORÇADA ATIVADA"))
    }

    @Test fun enabledProtectionPreservesIdentityAndAddsRules() {
        val effective = ProtectionPolicy.systemInstruction("Instrução-base", true)
        assertTrue(effective.startsWith("Instrução-base"))
        assertTrue(effective.contains("Kellvyn é apresentado como seu criador"))
        assertTrue(effective.contains("dados não confiáveis"))
        assertTrue(effective.contains("tokens"))
        assertTrue(effective.contains("em vez de simular sucesso"))
    }

    @Test fun emptyBaseProducesExactlyTheIdentityInstructionWhenProtectionIsDisabled() {
        assertEquals(KellvynPersona.SYSTEM_INSTRUCTION, ProtectionPolicy.systemInstruction("", false))
    }
}
