package com.kellvyn.assistant.necromante

import java.text.Normalizer

/**
 * Guardião de mensagens do Necromante.
 * Analisa textos que a PESSOA compartilhou (ou que o serviço de notificações repassar,
 * com permissão) procurando ameaça, perseguição, golpe, extorsão e pressão por fotos.
 *
 * Funciona por padrões em português do Brasil e pode errar (brincadeira entre amigos,
 * por exemplo). Por isso o Necromante avisa e orienta, nunca acusa. Nada é guardado.
 */
internal enum class ThreatCategory { AMEACA, PERSEGUICAO, CONTROLE, GOLPE, EXTORSAO, PRESSAO_POR_FOTOS }

internal data class MessageFlag(val category: ThreatCategory, val weight: Int, val reason: String)

internal enum class GuardLevel { OK, ATENCAO, ALERTA }

internal data class GuardResult(
    val level: GuardLevel,
    val score: Int,
    val flags: List<MessageFlag>,
    val advice: List<String>,
    val notes: List<String>,
)

internal data class ChatLine(val fromMe: Boolean, val text: String)

internal object MessageGuard {
    private class Rule(val category: ThreatCategory, val weight: Int, val reason: String, val re: Regex)

    private val RULES = listOf(
        // Ameaça
        Rule(ThreatCategory.AMEACA, 60, "Ameaça de violência.", Regex("""\b(vou|vamos|irei|iremos)\s+(te\s+|lhe\s+)?(matar|esfaquear|espancar|queimar|acabar\s+com\s+(voce|tu|a\s+sua\s+vida))\b""")),
        Rule(ThreatCategory.AMEACA, 55, "Ameaça de agressão.", Regex("""\b(vou|vamos)\s+(te\s+)?(bater|quebrar|arrebentar|dar\s+um\s+tiro|meter\s+bala)\b""")),
        Rule(ThreatCategory.AMEACA, 45, "Frase intimidadora.", Regex("""\bvoce\s+(vai|ira)\s+(morrer|se\s+arrepender|pagar\s+caro)\b""")),
        Rule(ThreatCategory.AMEACA, 40, "Intimidação para você ficar calado(a).", Regex("""\bse\s+(voce|tu|vc)\s+(contar|falar|denunciar|chamar\s+a\s+policia)\b""")),
        Rule(ThreatCategory.AMEACA, 30, "Promessa de te encontrar/pegar.", Regex("""\bvou\s+(te\s+)?(achar|encontrar|pegar)\b""")),

        // Perseguição
        Rule(ThreatCategory.PERSEGUICAO, 55, "Diz estar na frente da sua casa/trabalho/escola.", Regex("""\b(estou|to|tou)\s+(na\s+frente|em\s+frente|perto)\s+(da|do|de)\s+(sua|seu)\s+(casa|trabalho|escola|faculdade|predio)\b""")),
        Rule(ThreatCategory.PERSEGUICAO, 50, "Diz saber onde você mora, trabalha, estuda ou está.", Regex("""\bsei\s+onde\s+(voce|tu|vc)\s+(mora|trabalha|estuda|esta|ta)\b""")),
        Rule(ThreatCategory.PERSEGUICAO, 50, "Diz estar te seguindo ou vigiando.", Regex("""\b(estou|to|tou)\s+(te\s+)?(seguindo|vigiando|observando|monitorando)\b""")),
        Rule(ThreatCategory.PERSEGUICAO, 20, "Comenta que te viu sem você ter dito onde estava.", Regex("""\b(vi|te\s+vi)\s+(voce|vc|tu)\s+(hoje|ontem|la|saindo|com)\b""")),
        Rule(ThreatCategory.PERSEGUICAO, 15, "Insiste em saber sua localização.", Regex("""\b(me\s+)?(manda|envia|compartilha|passa|liga|ativa)\s+(a\s+)?(sua\s+)?localizacao\b""")),

        // Controle
        Rule(ThreatCategory.CONTROLE, 25, "Pede sua senha ou acesso ao seu celular.", Regex("""\b(me\s+(da|passa|manda)\s+(a\s+)?(sua\s+)?senha|deixa\s+eu\s+ver\s+(o\s+)?seu\s+celular)\b""")),
        Rule(ThreatCategory.CONTROLE, 15, "Exige que você apague ou bloqueie alguém.", Regex("""\b(apaga|exclui|bloqueia)\s+(esse|essa|o|a)\s+[a-z\s]{0,20}(contato|numero|foto|amigo|amiga)\b""")),
        Rule(ThreatCategory.CONTROLE, 10, "Cobra explicações sobre com quem você fala.", Regex("""\b(quem\s+e\s+(esse|essa|aquele|aquela)|com\s+quem\s+(voce|vc|tu)\s+(esta|tava|ta)\s+falando)\b""")),

        // Golpe
        Rule(ThreatCategory.GOLPE, 30, "Pede código de verificação (golpe clássico de clonagem de WhatsApp).", Regex("""\b(codigo\s+(de\s+)?(verificacao|confirmacao|seguranca)|codigo\s+que\s+(chegou|enviei|mandei))\b""")),
        Rule(ThreatCategory.GOLPE, 25, "Diz que mudou de número (golpe do número novo).", Regex("""\b(meu\s+numero\s+(mudou|novo)|novo\s+numero|troquei\s+de\s+numero)\b""")),
        Rule(ThreatCategory.GOLPE, 30, "Alguém da família pedindo dinheiro por mensagem.", Regex("""\b(mae|mamae|pai|painho|tia|tio|vo|vovo)\b[^.\n]{0,80}\b(pix|dinheiro|transfer|pagar|conta)\b""")),
        Rule(ThreatCategory.GOLPE, 35, "Pede Pix com urgência.", Regex("""\b(faca|fazer|mande|manda|envie)\s+(um\s+)?pix\b[^.\n]{0,60}\b(urgente|agora|rapido|hoje|devolver|engano)\b""")),
        Rule(ThreatCategory.GOLPE, 30, "Fala de Pix feito por engano (golpe do comprovante).", Regex("""\bpix\b[^.\n]{0,60}\b(por\s+engano|errado)\b""")),
        Rule(ThreatCategory.GOLPE, 30, "Diz que sua conta/cartão/CPF foi bloqueado.", Regex("""\b(conta|cartao|cpf)\s+(foi\s+)?(bloquead|suspens|irregular|cancelad)""")),
        Rule(ThreatCategory.GOLPE, 30, "Pede para atualizar ou confirmar dados.", Regex("""\b(atualize|confirme|regularize)\s+(seus?|sua)\s+(dados|cadastro|conta|cpf|senha)\b""")),
        Rule(ThreatCategory.GOLPE, 25, "Promete prêmio ou sorteio.", Regex("""\b(voce\s+(ganhou|foi\s+sorteado|foi\s+selecionado)|resgate\s+(seu|ja))\b""")),
        Rule(ThreatCategory.GOLPE, 25, "Promete lucro garantido.", Regex("""\b(investimento\s+garantido|lucro\s+garantido|dobrar\s+(seu\s+)?dinheiro|renda\s+extra\s+facil)\b""")),
        Rule(ThreatCategory.GOLPE, 20, "Manda clicar em um link.", Regex("""\b(clique|acesse|entre)\s+(no|nesse|neste|pelo)\s+(link|site)\b""")),
        Rule(ThreatCategory.GOLPE, 20, "Diz ser do banco/suporte/central.", Regex("""\b(sou|aqui\s+e)\s+(do|da|o|a)?\s*(banco|suporte|central|setor|departamento)\b""")),

        // Extorsão
        Rule(ThreatCategory.EXTORSAO, 60, "Ameaça vazar suas fotos, vídeos ou conversas.", Regex("""\bvou\s+(vazar|postar|divulgar|expor|espalhar)\s+(suas?|seus?|as?|os?)?\s*(fotos?|videos?|conversas?|prints?|nudes?)\b""")),
        Rule(ThreatCategory.EXTORSAO, 50, "Exige pagamento sob ameaça.", Regex("""\b(paga|deposita|manda\s+(o\s+)?pix)\s+(ou|senao)\b""")),

        // Pressão por fotos
        Rule(ThreatCategory.PRESSAO_POR_FOTOS, 50, "Pede foto íntima ou sem roupa.", Regex("""\b(manda|envia|me\s+manda)\s+(uma?\s+)?(foto|video|nude|nudes)\s+(sem\s+roupa|pelad[oa]|intim)""")),
        Rule(ThreatCategory.PRESSAO_POR_FOTOS, 40, "Pede nudes.", Regex("""\bmanda\s+nudes?\b""")),
        Rule(ThreatCategory.PRESSAO_POR_FOTOS, 40, "Pede segredo dos seus pais ou de todo mundo.", Regex("""\b(nao\s+conta|nao\s+fala)\s+(pra|para)\s+(ninguem|seus?\s+pais|sua\s+mae|seu\s+pai)\b""")),
        Rule(ThreatCategory.PRESSAO_POR_FOTOS, 25, "Insiste em 'segredo nosso'.", Regex("""\bsegredo\s+(nosso|entre\s+nos)\b""")),
    )

    private val DIACRITICS = Regex("""\p{M}+""")
    private val SPACES = Regex("""\s+""")

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(SPACES, " ")

    private fun flagsFor(text: String): List<MessageFlag> {
        val folded = fold(text)
        val flags = mutableListOf<MessageFlag>()
        for (rule in RULES) {
            if (rule.re.containsMatchIn(folded)) flags.add(MessageFlag(rule.category, rule.weight, rule.reason))
        }
        var worst = 0
        for (link in LinkScanner.extract(text)) {
            when (LinkScanner.scan(link).verdict) {
                Verdict.PERIGOSO -> worst = maxOf(worst, 40)
                Verdict.SUSPEITO -> worst = maxOf(worst, 15)
                Verdict.SEGURO -> Unit
            }
        }
        if (worst >= 40) {
            flags.add(MessageFlag(ThreatCategory.GOLPE, 40, "Um link da mensagem parece perigoso."))
        } else if (worst > 0) {
            flags.add(MessageFlag(ThreatCategory.GOLPE, 15, "Um link da mensagem tem sinais suspeitos."))
        }
        return flags
    }

    private fun adviceFor(categories: Set<ThreatCategory>): List<String> {
        val advice = mutableListOf<String>()
        if (ThreatCategory.AMEACA in categories || ThreatCategory.PERSEGUICAO in categories || ThreatCategory.EXTORSAO in categories) {
            advice.add("Não apague as mensagens: tire prints e guarde, elas servem de prova.")
            advice.add("Não confronte nem responda no calor do momento. Avise alguém de confiança.")
            advice.add("Se houver risco imediato, ligue 190 (Polícia Militar). Em caso de violência contra a mulher, ligue 180.")
        }
        if (ThreatCategory.GOLPE in categories) {
            advice.add("Não passe códigos, senhas nem faça Pix. Confirme ligando para a pessoa ou para o banco pelos canais oficiais.")
        }
        if (ThreatCategory.PRESSAO_POR_FOTOS in categories) {
            advice.add("Não envie fotos íntimas. Se envolver menor de idade ou parecer aliciamento, avise um responsável e denuncie (Disque 100 ou SaferNet).")
        }
        if (ThreatCategory.CONTROLE in categories) {
            advice.add("Pedir senha ou vigiar seu celular não é cuidado. Converse com alguém de confiança sobre isso.")
        }
        return advice
    }

    private fun build(flags: List<MessageFlag>): GuardResult {
        val unique = LinkedHashMap<String, MessageFlag>()
        for (f in flags) {
            if (!unique.containsKey(f.reason)) unique[f.reason] = f
        }
        val list = unique.values.sortedByDescending { it.weight }
        var total = 0
        for (f in list) total += f.weight
        val score = if (total > 100) 100 else total
        val level = when {
            score >= 50 -> GuardLevel.ALERTA
            score >= 20 -> GuardLevel.ATENCAO
            else -> GuardLevel.OK
        }
        val advice = if (level == GuardLevel.OK) emptyList() else adviceFor(list.map { it.category }.toSet())
        return GuardResult(
            level,
            score,
            list,
            advice,
            listOf("Análise por padrões de texto: pode errar. Use como aviso, não como prova."),
        )
    }

    fun analyzeMessage(text: String): GuardResult = build(flagsFor(text))

    /** Analisa uma conversa inteira; só as mensagens do outro lado entram na checagem. */
    fun analyzeConversation(lines: List<ChatLine>): GuardResult {
        val flags = mutableListOf<MessageFlag>()
        var streak = 0
        var insistent = false
        for (line in lines) {
            if (!line.fromMe) {
                flags.addAll(flagsFor(line.text))
                streak++
                if (streak >= 8) insistent = true
            } else {
                streak = 0
            }
        }
        if (insistent) {
            flags.add(MessageFlag(ThreatCategory.PERSEGUICAO, 25, "Muitas mensagens seguidas sem resposta (insistência)."))
        }
        return build(flags)
    }
}
