package com.kellvyn.assistant.necromante

/**
 * Modelos e pontuação compartilhados pelos módulos de proteção do Necromante.
 * Kotlin puro (sem API do Android) para rodar nos testes unitários do CI.
 */
internal enum class Verdict { SEGURO, SUSPEITO, PERIGOSO }

internal data class Finding(val id: String, val weight: Int, val reason: String)

internal data class ScanResult(
    val verdict: Verdict,
    /** 0 a 100. Quanto maior, mais perigoso. */
    val score: Int,
    val findings: List<Finding>,
    val summary: String,
    /** Limitações da análise que a pessoa precisa saber. */
    val notes: List<String>,
)

internal object ScanScoring {
    fun verdictFor(score: Int): Verdict = when {
        score >= 60 -> Verdict.PERIGOSO
        score >= 25 -> Verdict.SUSPEITO
        else -> Verdict.SEGURO
    }

    private fun summaryFor(verdict: Verdict): String = when (verdict) {
        Verdict.PERIGOSO -> "Parece perigoso. Não abra, não instale e não informe nenhum dado."
        Verdict.SUSPEITO -> "Tem sinais de golpe ou de comportamento arriscado. Só siga em frente se tiver certeza da origem."
        Verdict.SEGURO -> "Não encontrei sinais de risco na análise local. Isso reduz a dúvida, mas não garante que seja seguro."
    }

    fun build(findings: List<Finding>, notes: List<String> = emptyList()): ScanResult {
        val unique = LinkedHashMap<String, Finding>()
        for (f in findings) {
            if (!unique.containsKey(f.id)) unique[f.id] = f
        }
        val list = unique.values.sortedByDescending { it.weight }
        var total = 0
        for (f in list) total += f.weight
        val score = if (total > 100) 100 else total
        val verdict = verdictFor(score)
        return ScanResult(verdict, score, list, summaryFor(verdict), notes)
    }
}
