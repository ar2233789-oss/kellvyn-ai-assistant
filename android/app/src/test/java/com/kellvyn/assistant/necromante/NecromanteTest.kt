package com.kellvyn.assistant.necromante

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NecromanteTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }

    // ---------- links ----------

    @Test
    fun linkImitandoBancoComIscaEPerigoso() {
        val r = LinkScanner.scan("http://nubank-login.com/verifica")
        assertEquals(Verdict.PERIGOSO, r.verdict)
        assertTrue(r.findings.any { it.id == "marca-nubank" })
    }

    @Test
    fun linkOficialNaoGeraAlerta() {
        val r = LinkScanner.scan("https://www.google.com/search?q=gato")
        assertEquals(Verdict.SEGURO, r.verdict)
        assertEquals(0, r.score)
    }

    @Test
    fun linkComIpPortaEApkEPerigoso() {
        val r = LinkScanner.scan("http://192.168.0.1:8080/app.apk")
        assertEquals(Verdict.PERIGOSO, r.verdict)
        assertTrue(r.findings.any { it.id == "ip" })
        assertTrue(r.findings.any { it.id == "executavel" })
    }

    @Test
    fun esquemaJavascriptEPerigoso() {
        assertEquals(Verdict.PERIGOSO, LinkScanner.scan("javascript:alert(1)").verdict)
    }

    @Test
    fun extraiLinksSemPontuacaoFinal() {
        val links = LinkScanner.extract("olha isso (https://exemplo.com/a). e www.teste.com.br, ok")
        assertEquals(listOf("https://exemplo.com/a", "www.teste.com.br"), links)
    }

    // ---------- mensagens ----------

    @Test
    fun ameacaEPerseguicaoGeramAlertaComOrientacao() {
        val g = MessageGuard.analyzeMessage("Sei onde você mora. Vou te matar")
        assertEquals(GuardLevel.ALERTA, g.level)
        assertTrue(g.advice.any { it.contains("190") })
    }

    @Test
    fun golpeDoNumeroNovoComCodigoEAlerta() {
        val g = MessageGuard.analyzeMessage("Oi, esse é meu número novo. Me passa o código de verificação que chegou")
        assertEquals(GuardLevel.ALERTA, g.level)
        assertTrue(g.flags.any { it.category == ThreatCategory.GOLPE })
    }

    @Test
    fun conversaNormalNaoGeraAlerta() {
        val g = MessageGuard.analyzeMessage("Bom dia, vamos jantar hoje?")
        assertEquals(GuardLevel.OK, g.level)
        assertTrue(g.advice.isEmpty())
    }

    @Test
    fun insistenciaSemRespostaEContada() {
        val lines = (1..9).map { ChatLine(false, "oi $it") }
        val g = MessageGuard.analyzeConversation(lines)
        assertTrue(g.flags.any { it.category == ThreatCategory.PERSEGUICAO })
    }

    // ---------- arquivos ----------

    @Test
    fun eicarEDetectado() {
        val f = tmp.newFile("teste.txt")
        f.writeText(EICAR_TEST_STRING)
        val r = FileScanner.scan(f)
        assertTrue(r.result.findings.any { it.id == "eicar" })
        assertEquals(Verdict.PERIGOSO, r.result.verdict)
        assertNotNull(r.sha256)
    }

    @Test
    fun duplaExtensaoEPerigosa() {
        val f = tmp.newFile("fatura.pdf.exe")
        f.writeText("conteudo qualquer")
        val r = FileScanner.scan(f)
        assertEquals(Verdict.PERIGOSO, r.result.verdict)
        assertTrue(r.result.findings.any { it.id == "dupla-extensao" })
    }

    @Test
    fun executavelDisfarcadoDeImagemEPerigoso() {
        val f = tmp.newFile("foto.jpg")
        f.writeBytes(byteArrayOf(0x4d, 0x5a, 0x90.toByte(), 0x00, 0x03, 0x00))
        val r = FileScanner.scan(f)
        assertTrue(r.result.findings.any { it.id == "executavel-disfarcado" })
        assertEquals(Verdict.PERIGOSO, r.result.verdict)
    }

    @Test
    fun pdfComJavascriptAutomaticoEPerigoso() {
        val f = tmp.newFile("doc.pdf")
        f.writeText("%PDF-1.7\n1 0 obj << /OpenAction << /S /JavaScript /JS (app.alert(1)) >> >> endobj\n")
        val r = FileScanner.scan(f)
        assertTrue(r.result.findings.any { it.id == "pdf-js-auto" })
        assertEquals(Verdict.PERIGOSO, r.result.verdict)
    }

    @Test
    fun pdfSimplesNaoGeraAlerta() {
        val f = tmp.newFile("ok.pdf")
        f.writeText("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n")
        val r = FileScanner.scan(f)
        assertEquals(Verdict.SEGURO, r.result.verdict)
    }

    @Test
    fun zipComCaminhoForaDaPastaEAvisado() {
        val f = tmp.newFile("a.zip")
        zip(f, mapOf("../evil.txt" to "x".toByteArray()))
        val r = FileScanner.scan(f)
        assertTrue(r.result.findings.any { it.id == "zip-traversal" })
    }

    @Test
    fun apkComPadraoDeTrojanBancarioEPerigoso() {
        val f = tmp.newFile("falso.apk")
        val manifest = "android.permission.BIND_ACCESSIBILITY_SERVICE\nandroid.permission.SYSTEM_ALERT_WINDOW\nandroid.permission.INTERNET\n"
        zip(
            f,
            mapOf(
                "AndroidManifest.xml" to manifest.toByteArray(Charsets.ISO_8859_1),
                "classes.dex" to "dex\n035".toByteArray(Charsets.ISO_8859_1),
            ),
        )
        val r = FileScanner.scan(f)
        assertEquals(Verdict.PERIGOSO, r.result.verdict)
        assertTrue(r.result.findings.any { it.id == "combo-acessibilidade" })
        assertTrue(r.result.findings.any { it.id == "apk-sem-assinatura" })
        val apk = r.apk
        assertNotNull(apk)
        assertTrue(apk!!.permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertEquals(1, apk.dexCount)
    }

    @Test
    fun hashConhecidoEMarcadoComoAmeaca() {
        val f = tmp.newFile("qualquer.bin")
        f.writeText("amostra conhecida")
        val hash = FileScanner.scan(f).sha256
        assertNotNull(hash)
        FileScanner.addKnownBadHashes(listOf(hash!!))
        val again = FileScanner.scan(f)
        assertTrue(again.result.findings.any { it.id == "hash-conhecido" })
    }

    // ---------- persona e cérebro ----------

    @Test
    fun comandosSaoReconhecidos() {
        val c = NecromantePersona.parseCommand("/gerar apk Meu App")
        assertNotNull(c)
        assertEquals("gerar apk", c!!.name)
        assertEquals("Meu App", c.args)
        assertNull(NecromantePersona.parseCommand("oi, tudo bem?"))
    }

    @Test
    fun ajudaNaoPrecisaDoModelo() {
        val p = NecromanteBrain.prepare("/ajuda")
        assertNotNull(p.localReply)
        assertTrue(p.localReply!!.contains("/gerar apk"))
    }

    @Test
    fun linkPerigosoVaiParaOModeloComRelatorioReal() {
        val p = NecromanteBrain.prepare("recebi isso, é golpe? http://nubank-login.com/verifica")
        assertTrue(p.securityContext.isNotEmpty())
        assertTrue(p.modelInput.contains("VARREDURA REAL"))
    }

    @Test
    fun conversaSimplesPassaDireto() {
        val p = NecromanteBrain.prepare("Qual é a sua cor favorita?")
        assertEquals("Qual é a sua cor favorita?", p.modelInput)
        assertTrue(p.securityContext.isEmpty())
        assertFalse(p.modelInput.contains("VARREDURA"))
    }

    @Test
    fun promptDizQueKellvynECriador() {
        assertTrue(NecromantePersona.SYSTEM_PROMPT.contains("Kellvyn é seu único criador"))
    }
}
