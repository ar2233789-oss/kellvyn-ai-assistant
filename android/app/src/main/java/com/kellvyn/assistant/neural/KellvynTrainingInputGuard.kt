package com.kellvyn.assistant.neural

/**
 * Conservative pre-training input filter for obvious secrets and Brazilian personal identifiers.
 *
 * This is a heuristic guard, not a full PII detector, moderation system, or proof of data rights.
 * It deliberately rejects recognizable emails, Brazilian phone-number formats, and valid CPFs
 * so a raw web corpus cannot be accepted without a separate review/sanitization pass.
 */
internal object KellvynTrainingInputGuard {
    private val SECRET_ASSIGNMENT = Regex(
        """(?i)\\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\\b\\s*[:=]\\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
    )
    private val BEARER_TOKEN = Regex(
        """(?i)\\bauthorization\\s*:\\s*bearer\\s+[A-Za-z0-9._~+/=-]{12,}"""
    )
    private val PRIVATE_KEY = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")
    private val KNOWN_TOKEN = Regex(
        """\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{16,})\\b"""
    )
    private val EMAIL_ADDRESS = Regex(
        """(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9.-])"""
    )
    private val BRAZILIAN_PHONE = Regex(
        """(?<!\\d)(?:\\+?55[\\s.-]*)?\\(?[1-9]\\d\\)?[\\s.-]*(?:9?\\d{4})[\\s.-]?\\d{4}(?!\\d)"""
    )
    private val CPF_CANDIDATE = Regex(
        """(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)"""
    )

    fun containsSensitiveMaterial(text: String): Boolean =
        SECRET_ASSIGNMENT.containsMatchIn(text) ||
            BEARER_TOKEN.containsMatchIn(text) ||
            PRIVATE_KEY.containsMatchIn(text) ||
            KNOWN_TOKEN.containsMatchIn(text) ||
            EMAIL_ADDRESS.containsMatchIn(text) ||
            BRAZILIAN_PHONE.containsMatchIn(text) ||
            containsValidCpf(text)

    private fun containsValidCpf(text: String): Boolean =
        CPF_CANDIDATE.findAll(text).any { match -> isValidCpf(match.value) }

    private fun isValidCpf(value: String): Boolean {
        val digits = value.filter(Char::isDigit).map(Char::digitToInt)
        if (digits.size != 11 || digits.all { it == digits.first() }) return false

        val firstSum = (0 until 9).sumOf { index ->
            digits[index] * (10 - index)
        }
        val firstRemainder = firstSum % 11
        val firstCheck = if (firstRemainder < 2) 0 else 11 - firstRemainder
        if (digits[9] != firstCheck) return false

        val secondSum = (0 until 10).sumOf { index ->
            digits[index] * (11 - index)
        }
        val secondRemainder = secondSum % 11
        val secondCheck = if (secondRemainder < 2) 0 else 11 - secondRemainder
        return digits[10] == secondCheck
    }
}
