package com.kellvyn.assistant

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets

/**
 * Strict, versioned wire format between the local neural model and the agent.
 *
 * Free-form model output is never executed. Only a complete protocol message with bounded,
 * valid fields becomes a typed decision. Tool/plan/delegation fields use UTF-8 hex.
 * Final user-facing text may use a bounded single-line UTF-8 field to avoid doubling its size.
 */
internal object KellvynDecisionProtocol {
    private const val HEADER = "KELLVYN/1"
    private const val MAX_OUTPUT_CHARS = 24_000
    private const val MAX_TEXT_BYTES = 8_000
    private const val MAX_ARGUMENTS = 24
    private const val MAX_PLAN_STEPS = 32
    private const val MAX_PLAN_ROWS = 256
    private const val MAX_HEX_BYTES = MAX_TEXT_BYTES * 2

    sealed class ParseResult {
        data class Accepted(val decision: KellvynModelDecision) : ParseResult()
        data class Rejected(val reason: String) : ParseResult()
    }

    fun parse(output: String, nowMillis: Long = System.currentTimeMillis()): ParseResult {
        if (output.length > MAX_OUTPUT_CHARS) return ParseResult.Rejected("A saída neural excedeu o limite.")
        if (nowMillis <= 0L) return ParseResult.Rejected("Relógio inválido para a decisão.")
        val normalized = output.trim()
        if (normalized.isEmpty()) return ParseResult.Rejected("A saída neural está vazia.")
        val lines = normalized.lines()
        if (lines.size < 3 || lines.size > MAX_PLAN_ROWS + 4) {
            return ParseResult.Rejected("Quantidade de linhas inválida no protocolo.")
        }
        if (lines[0] != HEADER) return ParseResult.Rejected("Cabeçalho do protocolo não reconhecido.")
        if (lines.last() != "END") return ParseResult.Rejected("A decisão não terminou com END.")

        return try {
            when (lines[1]) {
                "FINAL" -> parseFinal(lines)
                "CALL_TOOL" -> parseCallTool(lines)
                "DELEGATE" -> parseDelegate(lines)
                "PLAN" -> parsePlan(lines, nowMillis)
                else -> ParseResult.Rejected("Tipo de decisão desconhecido.")
            }
        } catch (error: IllegalArgumentException) {
            ParseResult.Rejected(error.message ?: "Campos inválidos na decisão.")
        } catch (_: IndexOutOfBoundsException) {
            ParseResult.Rejected("A decisão está incompleta.")
        }
    }

    private fun parseFinal(lines: List<String>): ParseResult {
        require(lines.size == 4) { "FINAL exige exatamente um texto." }
        val field = lines[2]
        val text = when {
            field.startsWith("TEXT\t") ->
                decodeHex(field.substringAfter('\t')).trim()
            field.startsWith("TEXT_RAW\t") -> {
                val raw = field.substringAfter('\t')
                require(raw.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) {
                    "A resposta final excedeu o limite de bytes."
                }
                require(raw.none { it.isISOControl() }) {
                    "TEXT_RAW aceita somente uma linha UTF-8 sem caracteres de controle."
                }
                raw.trim()
            }
            else -> throw IllegalArgumentException("Campo TEXT/TEXT_RAW ausente.")
        }
        require(text.isNotEmpty()) { "A resposta final está vazia." }
        return ParseResult.Accepted(KellvynModelDecision.Final(text))
    }

    private fun parseCallTool(lines: List<String>): ParseResult {
        require(lines.size in 4..(MAX_ARGUMENTS + 4)) { "CALL_TOOL excedeu o número de campos." }
        require(lines[2].startsWith("NAME\t")) { "Nome da ferramenta ausente." }
        val name = decodeHex(lines[2].substringAfter('\t'))
        require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,63}"))) {
            "Nome de ferramenta inválido."
        }
        val arguments = linkedMapOf<String, String>()
        for (line in lines.drop(3).dropLast(1)) {
            require(line.startsWith("ARG\t")) { "Campo inesperado em CALL_TOOL." }
            val parts = line.split('\t')
            require(parts.size == 3) { "ARG precisa conter chave e valor." }
            val key = decodeHex(parts[1])
            val value = decodeHex(parts[2])
            require(key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) {
                "Nome de argumento inválido."
            }
            require(arguments.put(key, value) == null) { "Argumento duplicado: $key." }
        }
        return ParseResult.Accepted(KellvynModelDecision.CallTool(name, arguments))
    }

    private fun parseDelegate(lines: List<String>): ParseResult {
        require(lines.size >= 6) { "DELEGATE está incompleto." }
        require(lines[2].startsWith("ROLE\t")) { "Papel do especialista ausente." }
        require(lines[3].startsWith("TASK\t")) { "Objetivo do especialista ausente." }
        require(lines[4].startsWith("CONTEXT\t")) { "Contexto do especialista ausente." }
        val role = runCatching {
            KellvynSpecialistRole.valueOf(lines[2].substringAfter('\t'))
        }.getOrElse { throw IllegalArgumentException("Papel de especialista não permitido.") }
        val task = decodeHex(lines[3].substringAfter('\t'))
        val context = decodeHex(lines[4].substringAfter('\t'))
        val tools = linkedSetOf<String>()
        for (line in lines.drop(5).dropLast(1)) {
            require(line.startsWith("TOOL\t")) { "Campo inesperado em DELEGATE." }
            val name = decodeHex(line.substringAfter('\t'))
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,63}"))) {
                "Nome de ferramenta delegada inválido."
            }
            require(tools.add(name)) { "Ferramenta duplicada na delegação." }
            require(tools.size <= 16) { "A delegação excedeu o limite de ferramentas." }
        }
        return ParseResult.Accepted(
            KellvynModelDecision.Delegate(
                role = role,
                task = task,
                context = context,
                allowedTools = tools
            )
        )
    }

    private fun parsePlan(lines: List<String>, nowMillis: Long): ParseResult {
        require(lines.size in 6..MAX_PLAN_ROWS) { "PLAN excedeu o limite de linhas." }
        require(lines[2].startsWith("ID\t")) { "ID do plano ausente." }
        require(lines[3].startsWith("GOAL\t")) { "Objetivo do plano ausente." }
        val id = decodeHex(lines[2].substringAfter('\t'))
        val goal = decodeHex(lines[3].substringAfter('\t'))
        val constraints = mutableListOf<String>()
        data class MutableStep(
            val id: String,
            val title: String,
            val objective: String,
            val risk: KellvynStepRisk,
            val approvalRequired: Boolean,
            val recoveryPlan: String?,
            val dependencies: MutableSet<String> = linkedSetOf(),
            val tools: MutableSet<String> = linkedSetOf(),
            val preconditions: MutableList<String> = mutableListOf(),
            val criteria: MutableList<String> = mutableListOf()
        )
        val steps = linkedMapOf<String, MutableStep>()
        for (line in lines.drop(4).dropLast(1)) {
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "CONSTRAINT" -> {
                    require(parts.size == 2) { "CONSTRAINT inválido." }
                    constraints += decodeHex(parts[1])
                    require(constraints.size <= 40)
                }
                "STEP" -> {
                    require(parts.size == 8) {
                        "STEP exige ID, título, objetivo, risco, aprovação e recuperação."
                    }
                    require(steps.size < MAX_PLAN_STEPS) { "O plano excedeu o número máximo de etapas." }
                    val stepId = decodeHex(parts[1])
                    require(stepId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "ID de etapa inválido." }
                    val title = decodeHex(parts[2])
                    val objective = decodeHex(parts[3])
                    val risk = runCatching {
                        KellvynStepRisk.valueOf(parts[4])
                    }.getOrElse { throw IllegalArgumentException("Risco de etapa não permitido.") }
                    require(parts[5] == "true" || parts[5] == "false") { "Flag de aprovação inválida." }
                    val approval = parts[5].toBoolean()
                    val recovery = if (parts[6] == "-") null else decodeHex(parts[6])
                    require(parts[7] == "V1") { "Versão da etapa não suportada." }
                    require(steps.put(
                        stepId,
                        MutableStep(stepId, title, objective, risk, approval, recovery)
                    ) == null) { "ID de etapa duplicado." }
                }
                "DEP" -> {
                    require(parts.size == 3) { "DEP inválido." }
                    val step = steps[decodeHex(parts[1])] ?: throw IllegalArgumentException("DEP aponta para etapa inexistente.")
                    require(step.dependencies.add(decodeHex(parts[2]))) { "Dependência duplicada." }
                }
                "TOOL" -> {
                    require(parts.size == 3) { "TOOL inválido." }
                    val step = steps[decodeHex(parts[1])] ?: throw IllegalArgumentException("TOOL aponta para etapa inexistente.")
                    val toolName = decodeHex(parts[2])
                    require(toolName.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,63}"))) { "Nome de ferramenta inválido." }
                    require(step.tools.add(toolName)) { "Ferramenta duplicada na etapa." }
                }
                "PRE" -> {
                    require(parts.size == 3) { "PRE inválido." }
                    val step = steps[decodeHex(parts[1])] ?: throw IllegalArgumentException("PRE aponta para etapa inexistente.")
                    step.preconditions += decodeHex(parts[2])
                    require(step.preconditions.size <= 12)
                }
                "CRITERION" -> {
                    require(parts.size == 3) { "CRITERION inválido." }
                    val step = steps[decodeHex(parts[1])] ?: throw IllegalArgumentException("CRITERION aponta para etapa inexistente.")
                    step.criteria += decodeHex(parts[2])
                    require(step.criteria.size <= 12)
                }
                else -> throw IllegalArgumentException("Linha desconhecida no plano.")
            }
        }
        require(steps.isNotEmpty()) { "O plano precisa conter etapas." }
        val plan = KellvynTaskPlan(
            id = id,
            goal = goal,
            constraints = constraints,
            steps = steps.values.map { step ->
                KellvynTaskStep(
                    id = step.id,
                    title = step.title,
                    objective = step.objective,
                    dependencies = step.dependencies,
                    requiredTools = step.tools,
                    risk = step.risk,
                    ownerApprovalRequired = step.approvalRequired,
                    preconditions = step.preconditions,
                    successCriteria = step.criteria,
                    recoveryPlan = step.recoveryPlan
                )
            },
            createdAtMillis = nowMillis
        )
        return ParseResult.Accepted(KellvynModelDecision.Plan(plan))
    }

    internal fun encodeText(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES) { "Texto excedeu o limite do protocolo." }
        val hex = StringBuilder(bytes.size * 2)
        for (byte in bytes) hex.append("%02X".format(byte.toInt() and 0xff))
        return hex.toString()
    }

    private fun decodeHex(value: String): String {
        require(value.length <= MAX_HEX_BYTES && value.length % 2 == 0) { "Texto hexadecimal inválido." }
        require(value.matches(Regex("[0-9A-Fa-f]*"))) { "Texto hexadecimal contém caracteres inválidos." }
        val bytes = ByteArrayOutputStream(value.length / 2)
        var index = 0
        while (index < value.length) {
            bytes.write(value.substring(index, index + 2).toInt(16))
            index += 2
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = try {
            decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: CharacterCodingException) {
            throw IllegalArgumentException("Texto UTF-8 inválido.")
        }
        require(decoded.none { it == '\u0000' }) { "Texto contém byte nulo." }
        return decoded
    }
}
