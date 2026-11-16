package com.kellvyn.assistant

import java.text.Normalizer
import java.util.Locale

/**
 * Small, versioned-by-code procedural knowledge registry.
 *
 * Only short trigger metadata is inspected for every task. Full instructions are injected
 * into trusted system context only when a task matches the skill. Skill text is authored
 * for Kellvyn and cannot grant tools or override the authorization layer.
 */
internal data class KellvynSkillDefinition(
    val id: String,
    val description: String,
    val triggerTerms: Set<String>,
    val instructions: String
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{1,63}")))
        require(description.isNotBlank() && description.length <= 240)
        require(triggerTerms.isNotEmpty() && triggerTerms.size <= 40)
        require(triggerTerms.all { normalize(it).isNotBlank() && normalize(it).length <= 48 })
        require(instructions.isNotBlank() && instructions.length <= 8_000)
    }
}

internal data class KellvynSkillMatch(
    val skill: KellvynSkillDefinition,
    val matchedTerms: Set<String>
)

internal class KellvynSkillRegistry(skills: List<KellvynSkillDefinition>) {
    private val registered = skills.associateBy { it.id }

    init {
        require(registered.size == skills.size) { "IDs de skills duplicados." }
    }

    fun selectFor(
        goal: String,
        limit: Int = 2,
        maxChars: Int = 6_000
    ): List<KellvynSkillMatch> {
        require(limit in 0..MAX_SKILLS_PER_TASK)
        require(maxChars in 0..MAX_SKILL_CONTEXT_CHARS)
        if (limit == 0 || maxChars == 0 || goal.isBlank()) return emptyList()

        val goalTerms = tokenize(goal)
        if (goalTerms.isEmpty()) return emptyList()

        val ranked = registered.values.mapNotNull { skill ->
            val matched = skill.triggerTerms.map(::normalize).toSet().intersect(goalTerms)
            if (matched.isEmpty()) null else KellvynSkillMatch(skill, matched)
        }.sortedWith(
            compareByDescending<KellvynSkillMatch> { it.matchedTerms.size }
                .thenBy { it.skill.id }
        )

        val selected = mutableListOf<KellvynSkillMatch>()
        var usedChars = 0
        for (match in ranked) {
            if (selected.size >= limit) break
            val cost = match.skill.instructions.length
            if (usedChars + cost > maxChars) continue
            selected += match
            usedChars += cost
        }
        return selected
    }

    companion object {
        private const val MAX_SKILLS_PER_TASK = 5
        private const val MAX_SKILL_CONTEXT_CHARS = 12_000

        fun empty() = KellvynSkillRegistry(emptyList())

        fun default() = KellvynSkillRegistry(
            listOf(
                KellvynSkillDefinition(
                    id = "android-build-verification",
                    description = "Construção Android, Gradle, APK e comprovação por etapas.",
                    triggerTerms = setOf(
                        "android", "apk", "gradle", "manifest", "build", "compilar",
                        "compilacao", "instalar", "emulador", "assinatura"
                    ),
                    instructions = """
                        SKILL: CONSTRUÇÃO E VERIFICAÇÃO ANDROID
                        1. Inspecione primeiro o módulo, o Gradle, o manifesto e os arquivos realmente envolvidos.
                        2. Preserve as partes protegidas e não faça alterações visuais fora do pedido.
                        3. Faça a menor alteração coerente e acompanhe imports, usos e testes afetados.
                        4. Separe rigorosamente: compilação, testes, geração do APK, assinatura, instalação,
                           inicialização e validação dos fluxos da tela. Cada etapa precisa de evidência própria.
                        5. Se houver emulador/dispositivo e ferramentas registradas, instale, abra, observe a tela,
                           consulte logs e exercite o fluxo solicitado. Não simule resultados ausentes.
                        6. Em caso de falha, encontre a primeira causa real, corrija preservando a intenção do
                           teste e repita o mesmo gate. Relate exatamente o que passou e o que ficou pendente.
                        7. Nunca declare APK funcional só porque o build terminou.
                    """.trimIndent()
                ),
                KellvynSkillDefinition(
                    id = "failure-diagnosis",
                    description = "Diagnóstico de falhas de compilação, testes, CI e execução.",
                    triggerTerms = setOf(
                        "erro", "falha", "quebrado", "teste", "tests", "ci", "stacktrace",
                        "compilacao", "logcat", "crash", "regressao", "exception"
                    ),
                    instructions = """
                        SKILL: DIAGNÓSTICO E RECUPERAÇÃO
                        1. Obtenha a mensagem e o log completos antes de propor uma correção.
                        2. Separe o primeiro erro causal dos erros em cascata.
                        3. Leia o código e os testes relacionados; não apague nem enfraqueça testes para
                           conseguir um resultado verde.
                        4. Formule uma hipótese verificável e faça uma correção mínima.
                        5. Rode novamente o teste ou comando que falhou e os testes de regressão relevantes.
                        6. Se não houver ferramenta de execução disponível, diga que a correção está escrita,
                           mas não validada. Nunca transforme ausência de log em sucesso.
                    """.trimIndent()
                ),
                KellvynSkillDefinition(
                    id = "code-review",
                    description = "Revisão de código, refatoração e prevenção de regressões.",
                    triggerTerms = setOf(
                        "codigo", "refatorar", "refatoracao", "implementacao", "funcao",
                        "classe", "bug", "diff", "revisar", "correcao", "fonte"
                    ),
                    instructions = """
                        SKILL: REVISÃO E ALTERAÇÃO DE CÓDIGO
                        1. Trace o fluxo da chamada e os consumidores antes de editar.
                        2. Verifique invariantes, contratos, limites, concorrência, cancelamento e tratamento
                           de erros relevantes ao trecho.
                        3. Prefira alteração pequena, tipada e compatível com o estilo existente.
                        4. Inclua testes para o comportamento novo e para o caso de falha; preserve a intenção
                           dos testes antigos.
                        5. Inspecione o diff final à procura de arquivos não relacionados, código morto,
                           segredos, permissões excessivas e afirmações sem evidência.
                    """.trimIndent()
                ),
                KellvynSkillDefinition(
                    id = "web-research",
                    description = "Pesquisa comparativa de ferramentas, documentação e técnicas atuais.",
                    triggerTerms = setOf(
                        "pesquisar", "pesquisa", "replit", "cursor", "gemini", "claude",
                        "documentacao", "comparar", "agente", "agent", "atual", "recursos"
                    ),
                    instructions = """
                        SKILL: PESQUISA TÉCNICA
                        1. Priorize documentação oficial atual e identifique versão/data quando disponível.
                        2. Separe recursos confirmados, disponibilidade por plano/versão e inferências.
                        3. Extraia o fluxo técnico observável, não copie identidade, prompts internos ou código
                           proprietário.
                        4. Para cada recurso útil, registre: problema que resolve, mecanismo, dependências,
                           risco/custo, adaptação própria para Kellvyn e teste de aceitação.
                        5. Não recomende capacidade que o projeto não possui. Verifique se uma ferramenta
                           equivalente está realmente registrada antes de instruir seu uso.
                    """.trimIndent()
                ),
                KellvynSkillDefinition(
                    id = "app-functional-testing",
                    description = "Validação de comportamento real de aplicativos e telas.",
                    triggerTerms = setOf(
                        "aplicativo", "app", "tela", "interface", "fluxo", "usabilidade",
                        "botao", "clique", "navegacao", "visual", "funcional"
                    ),
                    instructions = """
                        SKILL: TESTE FUNCIONAL DE APLICATIVO
                        1. Converta o pedido em ações observáveis e resultados esperados.
                        2. Valide os caminhos principais e ao menos um caminho de erro ou cancelamento.
                        3. Quando houver automação de dispositivo, use screenshots, árvore de acessibilidade
                           e logs como evidências complementares.
                        4. Não considere uma tela renderizada como prova de que seus controles funcionam.
                        5. Preserve a interface aprovada quando o pedido for sobre lógica ou inteligência.
                    """.trimIndent()
                )
            )
        )

        private fun tokenize(value: String): Set<String> =
            Regex("[\\p{L}\\p{N}]{2,}")
                .findAll(normalize(value))
                .map { it.value }
                .toSet()

        private fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
    }
}
