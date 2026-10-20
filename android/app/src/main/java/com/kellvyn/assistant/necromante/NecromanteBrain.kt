package com.kellvyn.assistant.necromante

/** Transforma resultados de varredura em texto claro, sem depender do modelo. */
internal object ReportFormatter {
    fun format(title: String, r: ScanResult): String {
        val sb = StringBuilder()
        sb.append(title).append(": ").append(r.verdict.name).append(" (").append(r.score).append("/100)\n")
        for (f in r.findings.take(5)) sb.append("- ").append(f.reason).append('\n')
        r.notes.firstOrNull()?.let { sb.append("Limite: ").append(it).append('\n') }
        return sb.toString().trimEnd()
    }

    fun format(g: GuardResult): String {
        val sb = StringBuilder()
        sb.append("Mensagem: ").append(g.level.name).append(" (").append(g.score).append("/100)\n")
        for (f in g.flags.take(5)) sb.append("- ").append(f.reason).append('\n')
        for (a in g.advice) sb.append("> ").append(a).append('\n')
        g.notes.firstOrNull()?.let { sb.append("Limite: ").append(it).append('\n') }
        return sb.toString().trimEnd()
    }

    fun format(f: FileScanResult): String {
        val head = StringBuilder(f.name).append(" (").append(f.kind).append(", ").append(f.size).append(" bytes)")
        f.sha256?.let { head.append("\nSHA-256: ").append(it) }
        val apk = f.apk
        if (apk != null) {
            head.append("\nAPK: ").append(apk.permissions.size).append(" permissões, assinatura v1=")
                .append(apk.signedV1).append(", v2+=").append(apk.signedV2OrNewer)
        }
        return head.append('\n').append(format("Arquivo", f.result)).toString()
    }
}

internal data class PreparedInput(
    val command: ParsedCommand?,
    /** Resposta que não precisa do modelo (por exemplo, /ajuda). */
    val localReply: String?,
    /** Texto que deve ir ao modelo, já com o bloco de varredura real quando houver. */
    val modelInput: String,
    /** Relatórios reais produzidos pelos módulos de proteção. */
    val securityContext: List<String>,
)

/**
 * Lógica do cérebro do Necromante: decide o que é comando, o que precisa de varredura
 * e monta a entrada do modelo. O modelo só explica o que os módulos realmente acharam.
 */
internal object NecromanteBrain {
    private val ANALYZE_HINT = Regex(
        """(recebi|mandaram|me mandou|me mandaram|analisa|analise|verifica|verifique|e golpe|é golpe|sera que|será que)""",
        RegexOption.IGNORE_CASE,
    )

    fun prepare(text: String): PreparedInput {
        val command = NecromantePersona.parseCommand(text)
        if (command != null) {
            val local = if (command.name == "ajuda") NecromantePersona.helpText() else null
            return PreparedInput(command, local, text, emptyList())
        }

        val context = mutableListOf<String>()
        for (link in LinkScanner.extract(text).take(5)) {
            val r = LinkScanner.scan(link)
            if (r.verdict != Verdict.SEGURO) context.add(ReportFormatter.format("Link $link", r))
        }
        if (ANALYZE_HINT.containsMatchIn(text)) {
            val g = MessageGuard.analyzeMessage(text)
            if (g.level != GuardLevel.OK) context.add(ReportFormatter.format(g))
        }

        val modelInput = if (context.isEmpty()) {
            text
        } else {
            text + "\n\n[VARREDURA REAL DO APLICATIVO: explique em linguagem simples e não invente além disso]\n" +
                context.joinToString("\n\n")
        }
        return PreparedInput(null, null, modelInput, context)
    }

    /** Varredura de arquivo pedida pela pessoa (anexo): devolve o texto pronto para mostrar ou enviar ao modelo. */
    fun scanAttachment(file: java.io.File): String = ReportFormatter.format(FileScanner.scan(file))
}
