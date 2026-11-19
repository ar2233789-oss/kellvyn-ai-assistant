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

private fun tokenizeSkillText(value: String): Set<String> =
    Regex("[\\p{L}\\p{N}]{2,}")
        .findAll(normalizeSkillText(value))
        .map { it.value }
        .toSet()

private fun normalizeSkillText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)

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
        require(triggerTerms.all { normalizeSkillText(it).isNotBlank() && normalizeSkillText(it).length <= 48 })
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

        val goalTerms = tokenizeSkillText(goal)
        if (goalTerms.isEmpty()) return emptyList()

        val ranked = registered.values.mapNotNull { skill ->
            val matched = skill.triggerTerms.map(::normalizeSkillText).toSet().intersect(goalTerms)
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
                    id = "project-code-navigation",
                    description = "Navegação estrutural por classes, funções, imports e referências Kotlin/Java.",
                    triggerTerms = setOf(
                        "kotlin", "java", "import", "imports", "simbolo", "simbolos",
                        "referencia", "referencias", "declaracao", "dependencia", "chamada"
                    ),
                    instructions = """
                        SKILL: NAVEGAÇÃO ESTRUTURAL DO PROJETO
                        1. Use os sinais do índice de código (pacotes, imports, declarações e chamadas) para
                           localizar definições e possíveis usos; o índice é lexical, não substitui AST nem compilador.
                        2. Siga imports e chamadas para localizar dependências, callers e callees relevantes.
                        3. Confirme cada hipótese lendo o trecho-fonte original; nunca altere código apenas com
                           base no índice ou em um resultado de busca.
                        4. Respeite o limite de arquivos, trechos e filtros de segredos do workspace.
                        5. Quando o símbolo não for encontrado, informe a cobertura limitada da busca e não
                           invente relações de dependência.
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
                ),
                KellvynSkillDefinition(
                    id = "app-builder-workflow",
                    description = "Planejamento, construção e entrega verificável de projetos e aplicativos.",
                    triggerTerms = setOf(
                        "criar", "crie", "construir", "construa", "aplicativo", "app", "android",
                        "software", "projeto", "gerar", "produto", "funcionalidade", "telas",
                        "builder", "lovable", "replit", "copilot", "claude"
                    ),
                    instructions = """
                        SKILL: FÁBRICA DE APLICATIVOS — PLAN → BUILD → TEST
                        1. Converta o pedido em requisitos, telas, estados, dados, permissões, modo offline,
                           integrações e critérios de aceite observáveis. Pergunte somente quando faltar um
                           dado realmente bloqueante.
                        2. Explore a estrutura e a stack existentes antes de propor mudanças. Preserve o layout,
                           os fluxos e os arquivos que o proprietário marcou como protegidos.
                        3. Produza um plano revisável, com dependências, arquivos afetados, riscos, estimativa
                           de recursos e teste para cada requisito. Não comece a mutação durante a análise.
                        4. Construa em workspace isolado e gere diffs pequenos. Reutilize componentes coerentes;
                           não invente APIs, SDKs, bibliotecas ou arquivos que não existem.
                        5. Use somente builders, toolchains e comandos efetivamente registrados. O compilador
                           Java local da primeira versão não suporta Kotlin/Compose, bibliotecas externas ou
                           recursos Android arbitrários; não prometa projetos que esse motor não consegue gerar.
                        6. Escrita, exclusão, instalação e controle de dispositivo exigem ferramentas e aprovações
                           específicas do host. Uma skill não concede permissões nem transforma uma leitura em
                           autorização de mutação.
                        7. Teste em camadas: validação estrutural, compilação, assinatura, instalação, abertura,
                           interação real, persistência, erros, offline/rede, acessibilidade e regressão visual.
                           Só mencione as camadas para as quais há ferramentas concretas.
                        8. Entregue projeto/diff e artefatos com hashes, logs, testes e limitações. Build passou
                           não significa app instalado; tela abriu não significa todos os fluxos funcionando.
                        9. Nunca use hospedagem, tokens, APIs pagas ou modelos externos como dependência
                           obrigatória quando o pedido exigir execução local/offline.
                    """.trimIndent()
                ),
                KellvynSkillDefinition(
                    id = "agent-lab-evaluation",
                    description = "Avaliação independente de agentes, perfis, traces, benchmarks e regressões.",
                    triggerTerms = setOf(
                        "agente", "agentes", "agent", "benchmark", "benchmarks", "perfil", "perfis",
                        "trace", "traces", "baseline", "candidato", "avaliar", "avaliacao",
                        "competencia", "verificador", "evidencia", "orquestrador"
                    ),
                    instructions = """
                        SKILL: EXPERIMENTAÇÃO E AVALIAÇÃO DE AGENTES
                        1. Separe validade estrutural do perfil, contrato de segurança, execução de ferramenta,
                           competência cognitiva, integração e operação real. Uma prova não substitui outra.
                        2. Fixe commit, hash do perfil, configuração/modelo, suíte, ferramentas e orçamento para
                           que baseline e candidato sejam comparáveis.
                        3. Execute uma tarefa por vez no perfil de recursos apropriado; mantenha contexto e
                           workspace isolados. Não paralelize no telefone sem headroom medido.
                        4. Registre decisão, ferramenta solicitada, ferramenta permitida/negada, execução,
                           observação, evidência, checkpoint, duração e snapshot. Redija segredos.
                        5. O próprio agente nunca dá nota à própria resposta. Exija verificador independente
                           para cada critério; sem oráculo confiável, marque REVIEW_REQUIRED ou BLOCKED.
                        6. Diferencie PASSED, FAILED, REVIEW_REQUIRED, BLOCKED, SKIPPED e NOT_RUN. Perfil
                           compilado, catálogo válido ou trace íntegro não prova inteligência.
                        7. Compare candidato com baseline usando os mesmos casos e ambientes; mostre falhas
                           críticas separadamente e não esconda regressões numa média.
                        8. Preserve os traces e a evidência original; replay não deve reexecutar mutações sem
                           nova autorização do proprietário.
                    """.trimIndent()
                ),
                KellvynSkillDefinition(
                    id = "bot-operations",
                    description = "Diagnóstico e recuperação controlada de serviços e bots de mensagens.",
                    triggerTerms = setOf(
                        "bot", "bots", "telegram", "discord", "whatsapp", "heartbeat", "gateway",
                        "reconectar", "reconexao", "conexao", "ativo", "status", "uptime",
                        "webhook", "token", "servico", "mensagens"
                    ),
                    instructions = """
                        SKILL: OPERAÇÃO E SAÚDE DE BOTS
                        1. Comece pelo diagnóstico READ_ONLY registrado. Separe adaptador implementado,
                           toggle habilitado, credencial presente, transporte conectado, heartbeat recente
                           e cérebro local pronto para responder.
                        2. Nunca solicite que o proprietário cole o token no relatório; não leia, exponha,
                           copie ou grave credenciais em prompt, trace, log ou documentação.
                        3. Telegram: interprete resposta bem-sucedida do long polling como sinal de transporte.
                           Discord: interprete ACK de heartbeat do Gateway como sinal de conexão. Sinal com
                           mais de 90 segundos é desatualizado no monitor atual.
                        4. Transporte conectado não comprova que o bot respondeu com a IA. Confira também
                           a prontidão do cérebro e o resultado real de uma resposta autorizada.
                        5. Não reinicie serviço, altere preferência, envie mensagem ou revogue credencial sem
                           ferramenta mutável específica e aprovação do proprietário.
                        6. Sugira reconexão com backoff e teste read-only antes de qualquer ação. Não crie
                           polling agressivo, wake lock permanente nem prometa 24/7 em Android/HyperOS.
                        7. WhatsApp permanece indisponível enquanto não houver adaptador oficial e endpoint
                           público HTTPS de webhook permitido. Não recorra a automação não oficial do WhatsApp Web.
                        8. Relate plataforma, último sinal, idade, estado do cérebro e limitação do dispositivo.
                    """.trimIndent()
                )
            )
        )

    }
}
