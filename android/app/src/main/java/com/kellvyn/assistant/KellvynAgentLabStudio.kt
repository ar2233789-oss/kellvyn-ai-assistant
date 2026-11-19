package com.kellvyn.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only workbench surface for inspecting the agent environment and its execution traces.
 * Profile editing/promotion remains in the trusted owner APIs; this module cannot mutate agents.
 */
internal class KellvynAgentLabStudio(
    private val traceStore: KellvynAgentLabTraceStore,
    private val moduleManager: KellvynStudioModuleManager,
    private val resourceSnapshotProvider: suspend () -> KellvynResourceSnapshot,
    private val traceRecorderHealthProvider: () -> KellvynAgentLabTraceRecorderHealth,
    private val networkReadEnabled: () -> Boolean = { false }
) : KellvynStudioModule {

    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[0]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "Esta consulta não aceita argumentos.")
                }
                val snapshot = resourceSnapshotProvider()
                val modules = moduleManager.manifests()
                val loadedModules = moduleManager.loadedModuleIds()
                val traceHealth = traceRecorderHealthProvider()
                val output = buildString {
                    append("KELLVYN AGENT LAB — AMBIENTE\n")
                    append("Módulos registrados: ").append(modules.size)
                        .append("; motores carregados agora: ").append(loadedModules.size).append("\n")
                    append("Ferramentas registradas: ").append(modules.sumOf { it.toolSpecs.size }).append("\n")
                    append("RAM disponível: ")
                        .append(snapshot.availableSystemMemoryBytes / MEBIBYTE)
                        .append(" / ")
                        .append(snapshot.totalSystemMemoryBytes / MEBIBYTE)
                        .append(" MiB\n")
                    append("Heap do processo: ")
                        .append((snapshot.processHeapUtilization * 100f).toInt())
                        .append("%; PSS: ")
                        .append(snapshot.processPssBytes / MEBIBYTE)
                        .append(" MiB\n")
                    append("CPU do processo (acumulado): ")
                        .append(snapshot.processCpuTimeMillis)
                        .append(" ms; núcleos disponíveis: ")
                        .append(snapshot.availableProcessors)
                        .append("\n")
                    append("Pressão de memória: ").append(snapshot.memoryPressure.name).append("\n")
                    append("Estado térmico: ").append(snapshot.thermalBand.name).append("\n")
                    append("Economia de energia: ").append(snapshot.powerSaveMode).append("\n")
                    append("Gravação de traces: ")
                        .append(
                            when {
                                traceHealth.healthy -> "SAUDÁVEL"
                                !traceHealth.hasSuccessfulWrite && traceHealth.failureCount == 0 ->
                                    "AINDA NÃO TESTADA"
                                traceHealth.currentlyWritable -> "RECUPERADA COM FALHAS ANTERIORES"
                                else -> "DEGRADADA"
                            }
                        )
                    if (traceHealth.failureCount > 0) {
                        append(" | falhas históricas: ").append(traceHealth.failureCount)
                    }
                    traceHealth.lastFailureCode?.let {
                        append(" | erro: ").append(it)
                    }
                    traceHealth.lastFailureEventKind?.let {
                        append(" | evento perdido: ").append(it.name)
                    }
                    traceHealth.lastFailureTraceId?.let {
                        append(" | trace afetado: ").append(it)
                    }
                    append("\n")
                    append("Thermal headroom previsto: ")
                        .append(snapshot.thermalHeadroomForecast?.toString() ?: "indisponível")
                    append("; limiar OEM moderado: ")
                        .append(snapshot.thermalModerateHeadroomThreshold?.toString() ?: "indisponível")
                    append("; limiar OEM severo: ")
                        .append(snapshot.thermalSevereHeadroomThreshold?.toString() ?: "indisponível")
                    append("\n")
                    append("Armazenamento privado do app disponível/total: ")
                        .append(snapshot.availableAppDataBytes?.div(MEBIBYTE)?.toString() ?: "indisponível")
                        .append(" / ")
                        .append(snapshot.totalAppDataBytes?.div(MEBIBYTE)?.toString() ?: "indisponível")
                        .append(" MiB\n")
                    append("Bateria: ")
                        .append(snapshot.batteryPercent?.let { "$it%" } ?: "indisponível")
                    append("; carregando: ").append(snapshot.charging).append("\n\n")
                    append("MÓDULOS DISPONÍVEIS (motores carregados sob demanda)\n")
                    modules.forEach { module ->
                        append("- ").append(module.moduleId)
                            .append(" v").append(module.version)
                            .append(" | workload=").append(module.workloadClass.name)
                            .append(" | tools=").append(module.toolSpecs.size)
                            .append(" | working-set=")
                            .append(module.estimatedLoadWorkingSetBytes / KIBIBYTE)
                            .append(" KiB | resident=")
                            .append(module.estimatedResidentBytes / KIBIBYTE)
                            .append(" KiB | estado=")
                            .append(if (module.moduleId in loadedModules) "CARREGADO" else "DESCARREGADO")
                            .append(" | capabilities=")
                            .append(module.capabilities.map { it.name }.sorted().joinToString(", "))
                            .append("\n")
                    }
                    append("\nEste status lista manifests registrados; não afirma que motores estejam carregados ou testados.")
                }
                return KellvynToolResult(true, output.take(MAX_OUTPUT_CHARS))
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[1]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "Esta consulta não aceita argumentos.")
                }
                val summaries = withContext(Dispatchers.IO) {
                    traceStore.summaries().take(MAX_TRACE_LIST)
                }
                val output = buildString {
                    append("EXECUÇÕES RECENTES DO AGENT LAB — ")
                        .append(summaries.size).append(" traces\n")
                    if (summaries.isEmpty()) {
                        append("Nenhuma execução persistida foi encontrada.")
                    } else {
                        summaries.forEach { trace ->
                            append(trace.traceId)
                                .append(" | eventos=").append(trace.eventCount)
                                .append(" | início_ms=").append(trace.startedAtMillis)
                                .append(" | último_evento_ms=").append(trace.lastEventAtMillis)
                                .append(" | terminal=").append(trace.terminalKind?.name ?: "EM_ABERTO")
                                .append("\n")
                        }
                    }
                    append("\nOs traces guardam metadados de execução, não prompts, respostas ou conteúdo de arquivos.")
                }
                return KellvynToolResult(true, output.take(MAX_OUTPUT_CHARS))
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[2]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val unknown = arguments.keys - setOf("trace_id")
                if (unknown.isNotEmpty()) {
                    return KellvynToolResult(false, "Argumentos não reconhecidos.")
                }
                val traceId = arguments["trace_id"]?.trim().orEmpty()
                if (!TRACE_ID.matches(traceId)) {
                    return KellvynToolResult(false, "O trace_id está ausente ou tem formato inválido.")
                }
                val records = withContext(Dispatchers.IO) {
                    traceStore.historyTail(traceId, MAX_TRACE_EVENTS)
                }
                if (records.isEmpty()) {
                    return KellvynToolResult(false, "Trace inexistente ou sem eventos.")
                }
                val output = buildString {
                    append("LINHA DO TEMPO VERIFICADA — ").append(traceId).append("\n")
                    append("Eventos exibidos: ").append(records.size)
                        .append("; cadeia SHA-256 validada pelo armazenamento.\n")
                    records.forEach { record ->
                        val event = record.event
                        append("#").append(record.sequence)
                            .append(" | ").append(event.occurredAtMillis)
                            .append(" | ").append(event.kind.name)
                            .append(" | ator=").append(event.actor)
                        event.step?.let { append(" | etapa=").append(it) }
                        event.specialistRole?.let { append(" | papel=").append(it.name) }
                        event.toolName?.let { append(" | ferramenta=").append(it) }
                        event.profileSha256?.let { append(" | perfil_sha=").append(it.take(12)) }
                        event.durationMillis?.let { append(" | duração_ms=").append(it) }
                        event.outcome?.let { append(" | resultado=").append(it.name) }
                        event.detailCode?.let { append(" | código=").append(it) }
                        append("\n")
                    }
                    append("\nIntegridade: os registros e sua ordem foram validados. Conteúdo privado não é armazenado no trace.")
                }
                return KellvynToolResult(true, output.take(MAX_OUTPUT_CHARS))
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[3]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val unknown = arguments.keys - setOf("baseline_trace_id", "candidate_trace_id")
                if (unknown.isNotEmpty()) {
                    return KellvynToolResult(false, "Argumentos não reconhecidos.")
                }
                val baselineId = arguments["baseline_trace_id"]?.trim().orEmpty()
                val candidateId = arguments["candidate_trace_id"]?.trim().orEmpty()
                if (!TRACE_ID.matches(baselineId) || !TRACE_ID.matches(candidateId) ||
                    baselineId == candidateId
                ) {
                    return KellvynToolResult(false, "Informe dois trace IDs válidos e diferentes.")
                }
                val baseline = withContext(Dispatchers.IO) { traceStore.historyTail(baselineId, MAX_COMPARE_EVENTS) }
                val candidate = withContext(Dispatchers.IO) { traceStore.historyTail(candidateId, MAX_COMPARE_EVENTS) }
                val baselineCases = baseline.map { it.event }
                    .filter { it.kind == KellvynAgentLabEventKind.BENCHMARK_CASE_FINISHED && it.detailCode != null }
                    .associateBy { it.detailCode!! }
                val candidateCases = candidate.map { it.event }
                    .filter { it.kind == KellvynAgentLabEventKind.BENCHMARK_CASE_FINISHED && it.detailCode != null }
                    .associateBy { it.detailCode!! }
                if (baselineCases.isEmpty() || candidateCases.isEmpty()) {
                    return KellvynToolResult(
                        false,
                        "Um dos traces não contém resultados de casos de benchmark comparáveis."
                    )
                }
                val caseIds = (baselineCases.keys + candidateCases.keys).toSortedSet()
                val baselinePassed = baselineCases.values.count {
                    it.outcome == KellvynAgentLabOutcome.SUCCEEDED
                }
                val candidatePassed = candidateCases.values.count {
                    it.outcome == KellvynAgentLabOutcome.SUCCEEDED
                }
                val baselineReview = baselineCases.values.count {
                    it.outcome == KellvynAgentLabOutcome.REVIEW_REQUIRED
                }
                val candidateReview = candidateCases.values.count {
                    it.outcome == KellvynAgentLabOutcome.REVIEW_REQUIRED
                }
                val output = buildString {
                    append("COMPARAÇÃO DE EXPERIMENTOS\n")
                    append("BASELINE: ").append(baselineId).append("\n")
                    append("CANDIDATO: ").append(candidateId).append("\n")
                    val baselineFingerprint = baseline.map { it.event }
                        .firstOrNull { it.kind == KellvynAgentLabEventKind.EXPERIMENT_STARTED }
                        ?.detailCode ?: "não registrado"
                    val candidateFingerprint = candidate.map { it.event }
                        .firstOrNull { it.kind == KellvynAgentLabEventKind.EXPERIMENT_STARTED }
                        ?.detailCode ?: "não registrado"
                    append("Suite fingerprint: ")
                        .append(baselineFingerprint).append(" → ").append(candidateFingerprint).append("\n")
                    append("Casos aprovados: ").append(baselinePassed)
                        .append("/").append(baselineCases.size)
                        .append(" → ").append(candidatePassed)
                        .append("/").append(candidateCases.size).append("\n")
                    append("Variação de aprovações: ")
                        .append(candidatePassed - baselinePassed).append("\n")
                    append("Revisão necessária: ")
                        .append(baselineReview).append("/").append(baselineCases.size)
                        .append(" → ").append(candidateReview).append("/").append(candidateCases.size)
                        .append("\n\n")
                    append("CASO | BASELINE | CANDIDATO | DURAÇÃO_MS (BASE → CANDIDATO)\n")
                    caseIds.forEach { caseId ->
                        val before = baselineCases[caseId]
                        val after = candidateCases[caseId]
                        append(caseId).append(" | ")
                            .append(before?.outcome?.name ?: "AUSENTE")
                            .append(" | ")
                            .append(after?.outcome?.name ?: "AUSENTE")
                            .append(" | ")
                            .append(before?.durationMillis?.toString() ?: "-")
                            .append(" → ")
                            .append(after?.durationMillis?.toString() ?: "-")
                            .append("\n")
                    }
                    append("\nHashes de perfil baseline: ")
                        .append(baselineCases.values.mapNotNull { it.profileSha256 }.distinct().joinToString().ifBlank { "não registrados" })
                    append("\nHashes de perfil candidato: ")
                        .append(candidateCases.values.mapNotNull { it.profileSha256 }.distinct().joinToString().ifBlank { "não registrados" })
                    val baselineResources = baseline.map { it.event }
                        .filter { it.kind == KellvynAgentLabEventKind.RESOURCE_SNAPSHOT }
                        .mapNotNull { event ->
                            event.detailCode?.let { "${event.actor}:$it" }
                        }
                    val candidateResources = candidate.map { it.event }
                        .filter { it.kind == KellvynAgentLabEventKind.RESOURCE_SNAPSHOT }
                        .mapNotNull { event ->
                            event.detailCode?.let { "${event.actor}:$it" }
                        }
                    append("\n\nSNAPSHOTS DE RECURSOS (início/fim)\n")
                    append("Baseline: ")
                        .append(baselineResources.joinToString(" | ").ifBlank { "não registrados" })
                    append("\nCandidato: ")
                        .append(candidateResources.joinToString(" | ").ifBlank { "não registrados" })
                    append("\nA comparação usa estados, durações e snapshots registrados; não mede qualidade sem os verificadores do benchmark.")
                }
                return KellvynToolResult(true, output.take(MAX_OUTPUT_CHARS))
            }
,
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[4]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "A matriz de especialistas não aceita argumentos.")
                }
                val allowNetwork = networkReadEnabled()
                val registeredSpecs = moduleManager.manifests().flatMap { it.toolSpecs }
                    .sortedBy { it.name }
                val output = buildString {
                    append("MATRIZ DE COORDENAÇÃO DOS 14 ESPECIALISTAS\n")
                    append("Fonte única de permissões: KellvynSpecialistToolPolicy.\n")
                    append("Pesquisa de rede controlada pelo proprietário: ")
                        .append(if (allowNetwork) "ATIVADA" else "DESATIVADA")
                        .append("\n")
                    KellvynSpecialistRole.values().sortedBy { it.name }.forEach { role ->
                        val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(role)
                            .map { it.name }.sorted()
                        val permittedNames = KellvynSpecialistToolPolicy.permittedToolNames(
                            role = role,
                            specs = registeredSpecs,
                            allowNetworkRead = allowNetwork
                        )
                        val permitted = registeredSpecs.filter { it.name in permittedNames }
                        append("\n").append(role.name).append("\n")
                        append("  capacidades: ")
                            .append(capabilities.joinToString(", ").ifBlank { "nenhuma" }).append("\n")
                        append("  ferramentas permitidas: ")
                            .append(permitted.joinToString { "${it.name}[${it.risk.name}]" }
                                .ifBlank { "nenhuma ferramenta registrada para este papel" })
                            .append("\n")
                        append("  delegação aninhada: NÃO; mutação por especialista: NÃO\n")
                    }
                    append("\nA matriz mostra permissões calculadas; não afirma que um motor esteja carregado ou que um benchmark tenha sido aprovado.")
                }
                return KellvynToolResult(true, output.take(MAX_OUTPUT_CHARS))
            }
        }        }
    )

    override fun trimTransientResources(level: KellvynCachePressure) = Unit

    override fun close() = Unit

    companion object {
        const val MODULE_ID = "agent-lab"
        const val ENVIRONMENT_STATUS_TOOL = "agent_lab_environment_status"
        const val TRACE_LIST_TOOL = "agent_lab_traces_list"
        const val TRACE_INSPECT_TOOL = "agent_lab_trace_inspect"
        const val TRACE_COMPARE_TOOL = "agent_lab_trace_compare"
        const val SPECIALIST_MATRIX_TOOL = "agent_lab_specialist_matrix"

        private const val KIBIBYTE = 1024L
        private const val MEBIBYTE = 1024L * KIBIBYTE
        private const val MAX_TRACE_LIST = 12
        private const val MAX_TRACE_EVENTS = 48
        private const val MAX_COMPARE_EVENTS = 1_536
        private const val MAX_OUTPUT_CHARS = 7_500
        private val TRACE_ID = Regex("[0-9a-fA-F-]{36}")

        private val TOOL_SPECS = listOf(
            KellvynToolSpec(
                name = ENVIRONMENT_STATUS_TOOL,
                description = "Mostra o inventário dos módulos, ferramentas registradas e o snapshot atual de RAM, heap, temperatura e bateria do Agent Lab.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 8_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 16L * MEBIBYTE,
                capabilities = setOf(
                    KellvynStudioCapability.AGENT_EVALUATION,
                    KellvynStudioCapability.DEVICE_DIAGNOSTICS
                )
            ),
            KellvynToolSpec(
                name = TRACE_LIST_TOOL,
                description = "Lista as últimas execuções do agente com horários, quantidade de eventos e estado terminal; não exibe conteúdo privado.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 8_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 24L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.AGENT_EVALUATION)
            ),
            KellvynToolSpec(
                name = TRACE_INSPECT_TOOL,
                description = "Inspeciona uma linha do tempo de execução por trace_id, incluindo coordenador, especialistas, ferramentas, duração, resultado e integridade.",
                requiredArguments = setOf("trace_id"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "trace_id",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 36,
                        pattern = "^[0-9a-fA-F-]{36}$"
                    )
                ),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 8_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 24L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.AGENT_EVALUATION)
            ),
            KellvynToolSpec(
                name = TRACE_COMPARE_TOOL,
                description = "Compara duas execuções de benchmark por caso, estado, duração e hashes de perfil; não lê respostas privadas.",
                requiredArguments = setOf("baseline_trace_id", "candidate_trace_id"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "baseline_trace_id",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 36,
                        pattern = "^[0-9a-fA-F-]{36}$"
                    ),
                    KellvynToolArgumentSchema(
                        name = "candidate_trace_id",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 36,
                        pattern = "^[0-9a-fA-F-]{36}$"
                    )
                ),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 8_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 24L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.AGENT_EVALUATION)
            )
            ),
            KellvynToolSpec(
                name = SPECIALIST_MATRIX_TOOL,
                description = "Expõe a matriz central de capacidades e ferramentas permitidas para cada um dos 14 especialistas; respeita a chave de pesquisa Web do proprietário.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 8_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 2L * MEBIBYTE,
                capabilities = setOf(
                    KellvynStudioCapability.AGENT_EVALUATION,
                    KellvynStudioCapability.AGENT_DESIGN
                )
            )        )

        private val MANIFEST = KellvynStudioModuleManifest(
            moduleId = MODULE_ID,
            version = 1,
            capabilities = setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.AGENT_DESIGN,
                KellvynStudioCapability.DEVICE_DIAGNOSTICS
            ),
            supportedExtensions = emptySet(),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedLoadWorkingSetBytes = 512L * KIBIBYTE,
            estimatedResidentBytes = 96L * KIBIBYTE,
            permissions = emptySet(),
            toolSpecs = TOOL_SPECS
        )

        fun definition(
            traceStore: KellvynAgentLabTraceStore,
            moduleManager: KellvynStudioModuleManager,
            resourceSnapshotProvider: suspend () -> KellvynResourceSnapshot,
            traceRecorderHealthProvider: () -> KellvynAgentLabTraceRecorderHealth,
            networkReadEnabled: () -> Boolean = { false }
        ) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynAgentLabStudio(
                traceStore = traceStore,
                moduleManager = moduleManager,
                resourceSnapshotProvider = resourceSnapshotProvider,
                traceRecorderHealthProvider = traceRecorderHealthProvider,
                networkReadEnabled = networkReadEnabled
            )
        }
    }
}
