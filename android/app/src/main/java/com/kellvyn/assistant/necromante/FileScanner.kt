package com.kellvyn.assistant.necromante

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Assinatura de teste padrão da indústria (inofensiva). Serve para testar o scanner. */
internal const val EICAR_TEST_STRING = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"

internal data class ApkInfo(
    val permissions: List<String>,
    val signedV1: Boolean,
    val signedV2OrNewer: Boolean,
    val dexCount: Int,
    val nativeLibs: Int,
)

internal data class FileScanResult(
    val result: ScanResult,
    val name: String,
    val size: Long,
    val kind: String,
    val sha256: String?,
    val apk: ApkInfo?,
)

/**
 * Varredura estática de arquivos do Necromante. O arquivo nunca é executado.
 *
 * Cobre: nome enganoso, tipo real x extensão, EICAR, hashes conhecidos, PDF (JavaScript,
 * ações automáticas, anexos, ofuscação), ZIP/Office (macros, executáveis dentro, path
 * traversal, zip bomb) e APK (assinatura, permissões, combinações típicas de trojan
 * bancário, payload escondido, marcadores no classes.dex).
 */
internal object FileScanner {
    private const val MAX_FULL_HASH = 96L * 1024 * 1024
    private const val MAX_DEX = 40 * 1024 * 1024
    private const val MAX_MANIFEST = 4 * 1024 * 1024
    private const val PDF_LIMIT = 16 * 1024 * 1024

    private val knownBad: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Alimente com hashes SHA-256 (hex) de ameaças conhecidas. */
    fun addKnownBadHashes(hashes: Collection<String>) {
        for (h in hashes) knownBad.add(h.trim().lowercase())
    }

    private val DANGEROUS_EXT = setOf(
        "exe", "scr", "bat", "cmd", "vbs", "vbe", "js", "jse", "wsf", "ps1", "msi", "jar", "lnk", "com", "pif", "hta", "cpl",
    )

    private val EXPECTED_KIND = mapOf(
        "pdf" to "pdf", "png" to "png", "jpg" to "jpeg", "jpeg" to "jpeg", "gif" to "gif",
        "apk" to "zip", "jar" to "zip", "zip" to "zip", "docx" to "zip", "xlsx" to "zip",
        "pptx" to "zip", "dex" to "dex",
    )
    private val STRICT_EXT = setOf("pdf", "apk", "zip", "docx", "xlsx", "pptx", "jar")
    private val NATIVE_EXT = setOf("exe", "dll", "sys", "scr", "so", "bin", "elf", "")

    private val PERMISSION_WEIGHTS = mapOf(
        "BIND_ACCESSIBILITY_SERVICE" to 30,
        "BIND_DEVICE_ADMIN" to 25,
        "BIND_NOTIFICATION_LISTENER_SERVICE" to 20,
        "READ_SMS" to 15,
        "RECEIVE_SMS" to 15,
        "SEND_SMS" to 15,
        "REQUEST_INSTALL_PACKAGES" to 15,
        "SYSTEM_ALERT_WINDOW" to 15,
        "MANAGE_EXTERNAL_STORAGE" to 10,
        "REQUEST_DELETE_PACKAGES" to 10,
        "READ_CALL_LOG" to 8,
        "RECORD_AUDIO" to 8,
        "READ_CONTACTS" to 6,
        "CALL_PHONE" to 6,
        "WRITE_SETTINGS" to 6,
        "QUERY_ALL_PACKAGES" to 6,
        "READ_PHONE_STATE" to 4,
        "RECEIVE_BOOT_COMPLETED" to 4,
        "ACCESS_FINE_LOCATION" to 4,
    )

    private class DexMarker(text: String, val weight: Int, val reason: String) {
        val bytes: ByteArray = text.toByteArray(Charsets.ISO_8859_1)
    }

    private val DEX_MARKERS = listOf(
        DexMarker("Ldalvik/system/DexClassLoader;", 10, "carrega código externo em tempo de execução"),
        DexMarker("InMemoryDexClassLoader", 10, "carrega código externo em tempo de execução"),
        DexMarker("/system/bin/su", 25, "procura acesso root"),
        DexMarker("/system/xbin/su", 25, "procura acesso root"),
        DexMarker("Landroid/telephony/SmsManager;", 8, "usa envio de SMS"),
        DexMarker("createScreenCaptureIntent", 10, "pode gravar a tela"),
        DexMarker("Landroid/app/admin/DevicePolicyManager;", 10, "usa administrador do dispositivo"),
    )

    private val MANIFEST_RE = Regex(
        """(?:android\.permission|android\.intent\.action|android\.accessibilityservice|android\.service\.notification|android\.app\.admin|android\.app\.action)[A-Za-z0-9_.]*""",
    )
    private val DOUBLE_EXT = Regex(
        """.*\.(pdf|docx?|xlsx?|pptx?|jpe?g|png|gif|txt|mp3|mp4)\.(apk|exe|scr|bat|cmd|js|vbs|jar|msi|com|lnk)""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAVERSAL = Regex("""(^|[\\/])\.\.([\\/]|\z)""")
    private val DEX_NAME = Regex("""classes\d*\.dex""")
    private val SIG_NAME = Regex("""META-INF/.+\.(RSA|DSA|EC)""", RegexOption.IGNORE_CASE)
    private val HIDDEN_PAYLOAD = Regex("""(assets|res/raw)/.+\.(dex|jar|apk|zip|bin|so)""", RegexOption.IGNORE_CASE)
    private val NATIVE_LIB = Regex("""lib/.+\.so""")
    private val SUSPICIOUS_INSIDE = Regex(""".*\.(exe|scr|bat|cmd|vbs|js|lnk|apk|msi|ps1|hta)""", RegexOption.IGNORE_CASE)
    private val VBA = Regex(""".*vbaProject\.bin""", RegexOption.IGNORE_CASE)

    // ---------- utilidades ----------

    private fun starts(b: ByteArray, n: Int, at: Int, vararg sig: Int): Boolean {
        if (at + sig.size > n) return false
        for (i in sig.indices) {
            if ((b[at + i].toInt() and 0xFF) != sig[i]) return false
        }
        return true
    }

    private fun detectKind(b: ByteArray): String {
        val n = b.size
        return when {
            starts(b, n, 0, 0x25, 0x50, 0x44, 0x46) -> "pdf"
            starts(b, n, 0, 0x50, 0x4b, 0x03, 0x04) || starts(b, n, 0, 0x50, 0x4b, 0x05, 0x06) -> "zip"
            starts(b, n, 0, 0x4d, 0x5a) -> "pe"
            starts(b, n, 0, 0x7f, 0x45, 0x4c, 0x46) -> "elf"
            starts(b, n, 0, 0x89, 0x50, 0x4e, 0x47) -> "png"
            starts(b, n, 0, 0xff, 0xd8, 0xff) -> "jpeg"
            starts(b, n, 0, 0x47, 0x49, 0x46, 0x38) -> "gif"
            starts(b, n, 0, 0x64, 0x65, 0x78, 0x0a) -> "dex"
            starts(b, n, 0, 0x23, 0x21) -> "script"
            starts(b, n, 0, 0x52, 0x61, 0x72, 0x21) -> "rar"
            starts(b, n, 0, 0x37, 0x7a, 0xbc, 0xaf) -> "7z"
            starts(b, n, 4, 0x66, 0x74, 0x79, 0x70) -> "mp4"
            else -> "desconhecido"
        }
    }

    private fun latin1(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun readUpTo(file: File, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        file.inputStream().use { ins ->
            val buf = ByteArray(65536)
            var total = 0
            while (total < limit) {
                val r = ins.read(buf, 0, minOf(buf.size, limit - total))
                if (r < 0) break
                out.write(buf, 0, r)
                total += r
            }
        }
        return out.toByteArray()
    }

    /** Lê o stream inteiro; devolve null se passar do limite. */
    private fun readLimited(ins: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        var total = 0
        while (true) {
            val r = ins.read(buf)
            if (r < 0) break
            total += r
            if (total > limit) return null
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    private fun sha256(file: File): String? {
        if (file.length() > MAX_FULL_HASH) return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(65536)
                while (true) {
                    val r = ins.read(buf)
                    if (r < 0) break
                    md.update(buf, 0, r)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun containsBytes(h: ByteArray, n: ByteArray): Boolean {
        if (n.isEmpty() || h.size < n.size) return false
        val first = n[0]
        val last = h.size - n.size
        var i = 0
        while (i <= last) {
            if (h[i] == first) {
                var j = 1
                while (j < n.size && h[i + j] == n[j]) j++
                if (j == n.size) return true
            }
            i++
        }
        return false
    }

    private fun readLe32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or
            ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or
            ((b[o + 3].toLong() and 0xFF) shl 24)

    /** Procura o "APK Signing Block" (assinaturas v2+), que fica antes do diretório central. */
    private fun hasApkSigningBlock(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len < 22) return@use false
                val tailLen = minOf(len, 65557L).toInt()
                val tail = ByteArray(tailLen)
                raf.seek(len - tailLen)
                raf.readFully(tail)
                var eocd = -1
                var i = tailLen - 22
                while (i >= 0) {
                    if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() &&
                        tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()
                    ) {
                        eocd = i
                        break
                    }
                    i--
                }
                if (eocd < 0) return@use false
                val cdOffset = readLe32(tail, eocd + 16)
                if (cdOffset < 16 || cdOffset > len) return@use false
                val magic = ByteArray(16)
                raf.seek(cdOffset - 16)
                raf.readFully(magic)
                latin1(magic) == "APK Sig Block 42"
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun manifestStrings(bytes: ByteArray): Set<String> {
        val out = LinkedHashSet<String>()
        for (m in MANIFEST_RE.findAll(latin1(bytes))) out.add(m.value)
        val even = bytes.size - (bytes.size % 2)
        if (even > 0) {
            for (m in MANIFEST_RE.findAll(String(bytes, 0, even, Charsets.UTF_16LE))) out.add(m.value)
        }
        return out
    }

    // ---------- PDF ----------

    private fun scanPdf(file: File, findings: MutableList<Finding>, notes: MutableList<String>) {
        val text = latin1(readUpTo(file, PDF_LIMIT))
        if (file.length() > PDF_LIMIT) notes.add("PDF grande: analisei só os primeiros 16 MB.")
        fun count(pattern: String): Int = Regex(pattern).findAll(text).count()

        val js = count("""/(JavaScript|JS)\b""")
        val auto = count("""/(OpenAction|AA)\b""")
        val launch = count("""/Launch\b""")
        val embedded = count("""/EmbeddedFile\b""")
        val rich = count("""/RichMedia\b""")
        val submit = count("""/SubmitForm\b""")
        val uris = count("""/URI\b""")
        val objStm = count("""/ObjStm\b""")
        val obf = count("""/[A-Za-z]{1,20}#[0-9A-Fa-f]{2}""")

        if (js > 0 && auto > 0) {
            findings.add(Finding("pdf-js-auto", 55, "O PDF tem JavaScript que pode rodar sozinho ao abrir."))
        } else if (js > 0) {
            findings.add(Finding("pdf-js", 25, "O PDF contém JavaScript."))
        }
        if (launch > 0) findings.add(Finding("pdf-launch", 50, "O PDF tenta executar programas (ação Launch)."))
        if (embedded > 0) findings.add(Finding("pdf-embutido", 25, "O PDF carrega outro arquivo escondido dentro dele."))
        if (rich > 0) findings.add(Finding("pdf-rich", 20, "O PDF tem conteúdo multimídia/Flash embutido."))
        if (obf > 0) findings.add(Finding("pdf-ofuscado", 25, "Nomes internos do PDF estão ofuscados, técnica usada para fugir de antivírus."))
        if (submit > 0) findings.add(Finding("pdf-submit", 10, "O PDF envia formulário para fora."))
        if (uris > 10) findings.add(Finding("pdf-links", 10, "O PDF tem $uris links embutidos."))
        if (objStm > 0) notes.add("Partes do PDF estão comprimidas; itens escondidos nelas podem não ter sido vistos.")
    }

    // ---------- ZIP / APK ----------

    private fun scanZip(file: File, ext: String, findings: MutableList<Finding>, notes: MutableList<String>): ApkInfo? {
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            notes.add("Não consegui ler a estrutura do ZIP (corrompido ou formato incomum).")
            if (ext == "apk") findings.add(Finding("apk-ilegivel", 25, "APK com estrutura ilegível."))
            return null
        }
        try {
            return analyzeZip(zip, file, ext, findings, notes)
        } finally {
            try {
                zip.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun analyzeZip(
        zip: ZipFile,
        file: File,
        ext: String,
        findings: MutableList<Finding>,
        notes: MutableList<String>,
    ): ApkInfo? {
        val entries = zip.entries().toList()
        val names = entries.map { it.name }

        if (names.any { TRAVERSAL.containsMatchIn(it) }) {
            findings.add(Finding("zip-traversal", 50, "O pacote tem caminhos que tentam escrever fora da pasta (path traversal)."))
        }
        var totalComp = 0L
        var totalSize = 0L
        for (e in entries) {
            if (e.compressedSize >= 0) totalComp += e.compressedSize
            if (e.size >= 0) totalSize += e.size
        }
        if (totalComp > 0 && totalSize > 2L * 1024 * 1024 * 1024 && totalSize / totalComp > 200) {
            findings.add(Finding("zip-bomba", 40, "Taxa de compressão absurda: possível zip bomb que trava o aparelho."))
        }

        val isApk = ext == "apk" || (names.contains("AndroidManifest.xml") && names.any { DEX_NAME.matches(it) })
        if (!isApk) {
            if (names.any { VBA.matches(it) }) {
                findings.add(Finding("office-macro", 40, "Documento do Office com macros (vbaProject.bin)."))
            }
            val inside = names.filter { SUSPICIOUS_INSIDE.matches(it) }
            if (inside.isNotEmpty()) {
                findings.add(Finding("zip-executavel", 30, "Contém executáveis dentro: ${inside.take(3).joinToString(", ")}."))
            }
            return null
        }

        val dexEntries = entries.filter { DEX_NAME.matches(it.name) }
        val nativeLibs = names.count { NATIVE_LIB.matches(it) }
        val signedV1 = names.any { SIG_NAME.matches(it) }
        val signedV2 = hasApkSigningBlock(file)
        if (!signedV1 && !signedV2) {
            findings.add(Finding("apk-sem-assinatura", 30, "APK sem assinatura de desenvolvedor."))
        }

        val hidden = names.filter { HIDDEN_PAYLOAD.matches(it) }
        if (hidden.isNotEmpty()) {
            findings.add(Finding("apk-payload", 25, "Arquivos executáveis escondidos dentro do app (${hidden.take(2).joinToString(", ")})."))
        }

        // Permissões do manifesto
        val perms = LinkedHashSet<String>()
        val flags = LinkedHashSet<String>()
        val manifestEntry = entries.firstOrNull { it.name == "AndroidManifest.xml" }
        if (manifestEntry != null) {
            val manifest = try {
                zip.getInputStream(manifestEntry).use { readLimited(it, MAX_MANIFEST) }
            } catch (e: Exception) {
                null
            }
            if (manifest != null) {
                for (s in manifestStrings(manifest)) {
                    if (s.startsWith("android.permission.")) perms.add(s) else flags.add(s)
                }
            } else {
                notes.add("Não consegui abrir o AndroidManifest.xml.")
            }
        }
        fun has(name: String): Boolean = perms.contains("android.permission.$name")

        var permWeight = 0
        val risky = mutableListOf<String>()
        for ((name, w) in PERMISSION_WEIGHTS) {
            if (has(name)) {
                permWeight += w
                if (w >= 8) risky.add(name)
            }
        }
        if (permWeight > 0) {
            val reason = if (risky.isNotEmpty()) {
                "Pede permissões sensíveis: ${risky.take(5).joinToString(", ")}."
            } else {
                "Pede permissões moderadamente sensíveis."
            }
            findings.add(Finding("apk-permissoes", minOf(40, permWeight), reason))
        }
        if ((has("READ_SMS") || has("RECEIVE_SMS")) && has("INTERNET")) {
            findings.add(Finding("combo-sms", 15, "Lê SMS e tem internet: pode interceptar códigos de verificação."))
        }
        if (has("BIND_ACCESSIBILITY_SERVICE") && has("SYSTEM_ALERT_WINDOW")) {
            findings.add(Finding("combo-acessibilidade", 30, "Acessibilidade + sobreposição de tela: padrão típico de trojan bancário."))
        }
        if (has("REQUEST_INSTALL_PACKAGES") && hidden.isNotEmpty()) {
            findings.add(Finding("combo-dropper", 25, "Instala pacotes e carrega arquivos escondidos: comportamento de dropper."))
        }
        if (flags.contains("android.app.action.DEVICE_ADMIN_ENABLED") || flags.contains("android.app.admin.action.DEVICE_ADMIN_ENABLED")) {
            findings.add(Finding("apk-admin", 15, "Quer virar administrador do aparelho, o que dificulta desinstalar."))
        }

        // Marcadores dentro do classes.dex
        val seen = LinkedHashMap<String, Int>()
        var skippedDex = 0
        for (e in dexEntries.take(4)) {
            val bytes = if (e.size in 0..MAX_DEX.toLong()) {
                try {
                    zip.getInputStream(e).use { readLimited(it, MAX_DEX) }
                } catch (ex: Exception) {
                    null
                }
            } else {
                null
            }
            if (bytes == null) {
                skippedDex++
                continue
            }
            for (m in DEX_MARKERS) {
                if (containsBytes(bytes, m.bytes)) {
                    val prev = seen[m.reason] ?: 0
                    seen[m.reason] = maxOf(prev, m.weight)
                }
            }
        }
        if (skippedDex > 0) notes.add("$skippedDex arquivo(s) classes.dex não foram lidos (grande demais ou ilegível).")
        if (seen.isNotEmpty()) {
            var sum = 0
            for (w in seen.values) sum += w
            findings.add(Finding("apk-codigo", minOf(35, sum), "O código do app ${seen.keys.joinToString("; ")}."))
        }

        notes.add("Apps legítimos também pedem várias permissões: considere de onde o APK veio (loja oficial ou link avulso).")
        return ApkInfo(perms.sorted(), signedV1, signedV2, dexEntries.size, nativeLibs)
    }

    // ---------- entrada principal ----------

    fun scan(file: File): FileScanResult {
        val findings = mutableListOf<Finding>()
        val notes = mutableListOf(
            "Análise estática local: o arquivo nunca é executado. Não substitui um antivírus nativo e não garante que o arquivo seja seguro.",
        )
        val name = file.name
        val ext = if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
        val size = file.length()
        if (size == 0L) notes.add("O arquivo está vazio.")

        val head = readUpTo(file, 4096)
        val kind = detectKind(head)

        // Nome
        if (name.contains('\u202e')) {
            findings.add(Finding("nome-rlo", 70, "O nome usa um caractere invisível que inverte a extensão para enganar você."))
        }
        if (DOUBLE_EXT.matches(name)) {
            findings.add(Finding("dupla-extensao", 60, "Extensão dupla: finge ser documento ou mídia, mas é executável."))
        } else if (ext in DANGEROUS_EXT) {
            findings.add(Finding("ext-executavel", 30, "Arquivo .$ext é executável."))
        }

        // Tipo real x extensão
        if ((kind == "pe" || kind == "elf") && ext !in NATIVE_EXT) {
            findings.add(Finding("executavel-disfarcado", 60, "É um executável disfarçado de .$ext."))
        } else {
            val expected = EXPECTED_KIND[ext]
            if (expected != null && kind != expected) {
                when {
                    kind == "script" -> findings.add(Finding("script-disfarcado", 50, "É um script disfarçado de .$ext."))
                    kind == "desconhecido" && ext in STRICT_EXT ->
                        findings.add(Finding("tipo-nao-bate", 30, "O conteúdo não parece um .$ext de verdade."))
                    kind != "desconhecido" ->
                        findings.add(Finding("tipo-diferente", 10, "Dizia ser .$ext, mas o conteúdo é $kind."))
                }
            }
        }

        // EICAR e hashes
        if (latin1(head).contains(EICAR_TEST_STRING)) {
            findings.add(Finding("eicar", 100, "Assinatura de teste EICAR detectada (arquivo de teste de antivírus, inofensivo)."))
        }
        val hash = sha256(file)
        if (hash != null && knownBad.contains(hash)) {
            findings.add(Finding("hash-conhecido", 100, "Este arquivo é idêntico a uma ameaça conhecida."))
        }
        if (hash == null) notes.add("Hash SHA-256 não calculado (arquivo muito grande ou ilegível).")

        // Análise por tipo
        var apk: ApkInfo? = null
        when {
            kind == "pdf" || ext == "pdf" -> scanPdf(file, findings, notes)
            kind == "zip" -> apk = scanZip(file, ext, findings, notes)
            ext == "apk" -> findings.add(Finding("apk-nao-zip", 40, "Diz ser APK, mas não tem estrutura de APK."))
        }

        return FileScanResult(ScanScoring.build(findings, notes), name, size, kind, hash, apk)
    }
}
