package com.kellvyn.assistant.neural

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynTrainingInputGuardTest {
    @Test
    fun rejectsRecognizableEmailAddresses() {
        assertTrue(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "Contato para retorno: suporte@example.com"
            )
        )
    }

    @Test
    fun rejectsFormattedBrazilianPhoneNumbers() {
        assertTrue(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "WhatsApp: +55 (91) 0000-0000"
            )
        )
        assertTrue(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "Telefone: 91 98765-4321"
            )
        )
        assertTrue(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "WhatsApp sem máscara: 91987654321"
            )
        )
    }

    @Test
    fun rejectsCpfOnlyWhenItsCheckDigitsAreValid() {
        assertTrue(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "CPF de teste: 529.982.247-25"
            )
        )
        assertFalse(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "Número fictício inválido: 111.222.333-44"
            )
        )
    }

    @Test
    fun keepsOrdinaryPortugueseTextAndNonPersonalNumbers() {
        assertFalse(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "Kellvyn aprende português. O ano é 2026 e a cidade tem 5.000 habitantes."
            )
        )
    }

    @Test
    fun retainsCredentialAndPrivateKeyDetection() {
        assertTrue(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "api_key = \\"ghp_abcdefghijklmnopqrstuvwxyz1234567890\\""
            )
        )
        assertTrue(
            KellvynTrainingInputGuard.containsSensitiveMaterial(
                "-----BEGIN PRIVATE KEY-----"
            )
        )
    }
}
