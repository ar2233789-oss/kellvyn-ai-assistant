package com.kellvyn.assistant.necromante

/**
 * Análise de links do Necromante (heurística local, sem rede): endereço disfarçado,
 * imitação de marca, encurtadores, protocolos perigosos, download de executáveis e
 * iscas típicas de golpe.
 */
internal object LinkScanner {
    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy",
        "shorturl.at", "ow.ly", "buff.ly", "tiny.cc", "encurtador.com.br",
    )

    private val RISKY_TLDS = setOf(
        "zip", "mov", "tk", "ml", "ga", "cf", "gq", "top", "xyz", "click", "work",
        "support", "country", "kim", "loan", "men", "buzz", "rest", "icu", "monster", "cyou",
    )

    private val SECOND_LEVEL = setOf("com", "org", "net", "gov", "edu", "co")

    private class Brand(val token: String, val domains: List<String>)

    private val BRANDS = listOf(
        Brand("whatsapp", listOf("whatsapp.com", "whatsapp.net", "wa.me")),
        Brand("google", listOf("google.com", "google.com.br", "googleusercontent.com", "googleapis.com", "gstatic.com", "goo.gl")),
        Brand("facebook", listOf("facebook.com", "fb.com", "fbcdn.net")),
        Brand("instagram", listOf("instagram.com", "cdninstagram.com")),
        Brand("nubank", listOf("nubank.com.br", "nu.com.br")),
        Brand("bradesco", listOf("bradesco.com.br", "bradesco.com")),
        Brand("itau", listOf("itau.com.br", "itau.com")),
        Brand("santander", listOf("santander.com.br", "santander.com")),
        Brand("caixa", listOf("caixa.gov.br")),
        Brand("picpay", listOf("picpay.com")),
        Brand("mercadolivre", listOf("mercadolivre.com.br", "mercadolivre.com", "mercadolibre.com")),
        Brand("mercadopago", listOf("mercadopago.com.br", "mercadopago.com")),
        Brand("paypal", listOf("paypal.com")),
        Brand("microsoft", listOf("microsoft.com", "live.com", "office.com", "microsoftonline.com")),
        Brand("netflix", listOf("netflix.com")),
        Brand("discord", listOf("discord.com", "discord.gg", "discordapp.com", "discord.media")),
        Brand("steam", listOf("steampowered.com", "steamcommunity.com", "steamstatic.com")),
        Brand("roblox", listOf("roblox.com")),
        Brand("correios", listOf("correios.com.br")),
    )

    private val BAIT = listOf(
        "login", "signin", "verify", "verifica", "secure", "seguranca", "segurança", "update",
        "atualiz", "confirm", "senha", "password", "banco", "pix", "premio", "prêmio",
        "ganhe", "bloquead", "suspens", "recuper", "resgate", "gratis", "grátis", "oferta",
    )

    private val URL_RE = Regex("""([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?""")
    private val SCHEME_RE = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://.*""", RegexOption.DOT_MATCHES_ALL)
    private val DANGEROUS_SCHEME = Regex("""(javascript|data|vbscript|file|intent|content):.*""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val EXEC_EXT = Regex("""\.(apk|exe|scr|bat|cmd|msi|jar|vbs|ps1|hta|lnk)(?=[?#]|\z)""", RegexOption.IGNORE_CASE)
    private val REDIRECT_PARAM = Regex("""[?&](url|redirect|redir|next|goto|link|dest|continue)=https?""", RegexOption.IGNORE_CASE)
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val NUMERIC_HOST = Regex("""\d+|0x[0-9a-fA-F]+""")
    private val LINK_IN_TEXT = Regex("""(?:https?://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val PERCENT = Regex("""%[0-9a-fA-F]{2}""")

    private class Parsed(val userinfo: Boolean, val scheme: String, val host: String, val port: String?, val path: String, val query: String)

    private fun parse(candidate: String): Parsed? {
        val m = URL_RE.matchEntire(candidate) ?: return null
        val scheme = m.groupValues[1].lowercase()
        val authority = m.groupValues[2]
        val at = authority.lastIndexOf('@')
        val hostPort = authority.substring(at + 1)
        var host = hostPort
        var port: String? = null
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end < 0) return null
            host = hostPort.substring(0, end + 1)
            val rest = hostPort.substring(end + 1)
            if (rest.startsWith(":")) port = rest.substring(1)
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) {
                host = hostPort.substring(0, colon)
                port = hostPort.substring(colon + 1)
            }
        }
        host = host.lowercase().trimEnd('.')
        if (host.isEmpty()) return null
        return Parsed(at >= 0, scheme, host, port?.takeIf { it.isNotEmpty() }, m.groupValues[3], m.groupValues[4])
    }

    private fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val tld = labels[labels.size - 1]
        val sld = labels[labels.size - 2]
        return if (tld.length == 2 && sld in SECOND_LEVEL) {
            labels.takeLast(3).joinToString(".")
        } else {
            labels.takeLast(2).joinToString(".")
        }
    }

    private fun normalizeLookalike(s: String): String =
        s.replace("rn", "m").replace("vv", "w").replace('0', 'o').replace('1', 'l').replace('3', 'e').replace('5', 's')

    private fun isOn(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

    fun scan(input: String): ScanResult {
        val findings = mutableListOf<Finding>()
        val notes = mutableListOf(
            "Análise local por padrões. Não consulta listas de reputação online, então um link novo e malicioso ainda pode passar.",
        )
        val raw = input.trim()
        if (raw.isEmpty()) {
            return ScanScoring.build(listOf(Finding("vazio", 0, "Nenhum link informado.")), notes)
        }
        if (DANGEROUS_SCHEME.matches(raw)) {
            findings.add(Finding("esquema-perigoso", 80, "Usa um tipo de endereço que executa código ou abre arquivos locais, não uma página normal."))
            return ScanScoring.build(findings, notes)
        }

        val hasScheme = SCHEME_RE.matches(raw)
        val parsed = parse(if (hasScheme) raw else "https://$raw")
        if (parsed == null) {
            return ScanScoring.build(listOf(Finding("invalido", 30, "O texto não é uma URL válida.")), notes)
        }
        if (!hasScheme) notes.add("O texto veio sem protocolo; analisei como https://.")

        val host = parsed.host
        val full = (host + parsed.path + parsed.query).lowercase()

        if (parsed.scheme == "http") {
            findings.add(Finding("sem-https", 15, "Não usa HTTPS: o que você digitar pode ser lido no caminho."))
        } else if (parsed.scheme != "https" && parsed.scheme != "ftp") {
            findings.add(Finding("protocolo-incomum", 60, "Protocolo incomum (${parsed.scheme})."))
        }

        if (parsed.userinfo) {
            findings.add(Finding("arroba", 35, "Usa @ no endereço para esconder o destino real."))
        }

        if (IPV4.matches(host) || host.startsWith("[")) {
            findings.add(Finding("ip", 35, "Aponta direto para um número de IP em vez de um site com nome."))
        } else if (NUMERIC_HOST.matches(host)) {
            findings.add(Finding("ip-disfarcado", 45, "Endereço numérico disfarçado de IP."))
        }

        val labels = host.split('.')
        if (labels.any { it.startsWith("xn--") }) {
            findings.add(Finding("punycode", 30, "Usa caracteres que imitam letras comuns (punycode) para parecer outro site."))
        }
        if (host in SHORTENERS) {
            findings.add(Finding("encurtador", 15, "Link encurtado: não dá para ver o destino antes de abrir."))
        }

        val tld = labels.last()
        if (tld in RISKY_TLDS) {
            val w = if (tld == "zip" || tld == "mov") 25 else 15
            findings.add(Finding("tld-arriscado", w, "Terminação .$tld muito usada em golpes."))
        }
        if (labels.size >= 5) {
            findings.add(Finding("subdominios", 15, "Muitos subdomínios, técnica comum para esconder o domínio verdadeiro."))
        }
        if (host.count { it == '-' } >= 3) {
            findings.add(Finding("hifens", 10, "Muitos hífens no endereço."))
        }
        if (host.length > 40) {
            findings.add(Finding("host-longo", 10, "Nome do site muito longo."))
        }

        val registrable = registrableDomain(host)
        val parts = host.split('.', '-')
        var onLegitBrand = false
        for (brand in BRANDS) {
            if (brand.domains.any { isOn(host, it) }) {
                onLegitBrand = true
                continue
            }
            val direct = parts.any { p ->
                p == brand.token || (brand.token.length >= 7 && (p.startsWith(brand.token) || p.endsWith(brand.token)))
            }
            if (direct) {
                findings.add(Finding("marca-${brand.token}", 40, "Usa o nome \"${brand.token}\", mas o domínio real é $registrable."))
                break
            }
            if (parts.any { normalizeLookalike(it) == brand.token }) {
                findings.add(Finding("imitacao-${brand.token}", 45, "Imita \"${brand.token}\" trocando letras por números parecidos."))
                break
            }
        }

        if (!onLegitBrand) {
            val hits = BAIT.filter { full.contains(it) }
            if (hits.isNotEmpty()) {
                val w = minOf(24, hits.size * 8)
                findings.add(Finding("iscas", w, "Palavras típicas de golpe: ${hits.take(4).joinToString(", ")}."))
            }
        }

        if (EXEC_EXT.containsMatchIn(parsed.path + parsed.query)) {
            findings.add(Finding("executavel", 40, "O link baixa um arquivo instalável/executável (APK, EXE etc.)."))
        }
        if (REDIRECT_PARAM.containsMatchIn(parsed.query)) {
            findings.add(Finding("redirecionamento", 12, "Tem redirecionamento embutido para outro endereço."))
        }
        val port = parsed.port
        if (port != null && port != "80" && port != "443") {
            findings.add(Finding("porta", 12, "Usa porta incomum ($port)."))
        }
        if (PERCENT.findAll(raw).count() > 10) {
            findings.add(Finding("codificado", 10, "Muito texto codificado no endereço (%xx)."))
        }
        if (raw.length > 200) {
            findings.add(Finding("url-longa", 8, "Endereço extremamente longo."))
        }

        return ScanScoring.build(findings, notes)
    }

    /** Extrai links de um texto (mensagem, e-mail etc.). */
    fun extract(text: String): List<String> =
        LINK_IN_TEXT.findAll(text).map { it.value.trimEnd(')', '.', ',', ';', '!', '?') }.toList()
}
