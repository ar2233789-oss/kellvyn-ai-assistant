package com.kellvyn.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only first phase of the invisible Agent Creation Studio.
 *
 * It exposes profile discovery, inspection and structural validation. It cannot edit,
 * promote, publish, or grant permissions to any specialist profile.
 */
internal class KellvynAgentCreationStudio(
    private val profiles: KellvynSpecialistProfileRegistry,
    private val profileStore: KellvynSpecialistProfileStore? = null
) : KellvynStudioModule {

    private suspend fun activeProfileRegistry(): KellvynSpecialistProfileRegistry =
        withContext(Dispatchers.IO) {
            profileStore?.let {
                KellvynSpecialistProfileRegistry(it.activeProfiles(profiles.profiles()))
            } ?: profiles
        }
    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[0]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "Esta ferramenta não aceita argumentos.")
                }
                val activeProfiles = activeProfileRegistry()
                val compilation = activeProfiles.compileAll()
                if (!compilation.success) {
                    return KellvynToolResult(
                        false,
                        "O catálogo não passou pelo Profile Compiler: " + compilation.errors.joinToString(" ")
                    )
                }
                val compiledByRole = compilation.profiles.associateBy { it.role }
                val catalogue = activeProfiles.profiles().joinToString("\n") { profile ->
                    val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(profile.role)
                        .map { it.name }
                        .sorted()
                        .joinToString(", ")
                    val hash = compiledByRole[profile.role]?.profileSha256?.take(12) ?: "sem-hash"
                    "- ${profile.role.name}: ${profile.displayName} (v${profile.version}, sha256:$hash) — " +
                        "${profile.mission} Capacidades: $capabilities."
                }
                return KellvynToolResult(
                    true,
                    "CATÁLOGO INTERNO DOS 14 PAPÉIS\n" +
                        "Uma inferência Kellvyn; perfis versionados; sem delegação aninhada ou mutação.\n" +
                        catalogue
                )
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[1]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val unknown = arguments.keys - setOf("role")
                if (unknown.isNotEmpty()) {
                    return KellvynToolResult(false, "Argumentos não reconhecidos.")
                }
                val rawRole = arguments["role"]?.trim().orEmpty()
                val role = KellvynSpecialistRole.values().firstOrNull { it.name == rawRole }
                    ?: return KellvynToolResult(false, "Papel especialista inválido.")
                val activeProfiles = activeProfileRegistry()
                val compilation = activeProfiles.compileAll()
                if (!compilation.success) {
                    return KellvynToolResult(false, "O perfil não passou pelo Profile Compiler.")
                }
                val profile = activeProfiles.requireProfile(role)
                val compiledProfile = compilation.profiles.firstOrNull { it.role == role }
                    ?: return KellvynToolResult(false, "O perfil não pôde ser compilado.")
                val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(role)
                    .map { it.name }
                    .sorted()
                    .joinToString(", ")
                return KellvynToolResult(
                    true,
                    "PERFIL: ${profile.displayName}\n" +
                        "PAPEL: ${profile.role.name}\n" +
                        "VERSÃO: ${profile.version}\n" +
                        "SHA-256: ${compiledProfile.profileSha256}\n" +
                        "MISSÃO: ${profile.mission}\n" +
                        "INSTRUÇÃO: ${profile.instruction}\n" +
                        "CAPACIDADES DA POLÍTICA CENTRAL: $capabilities\n" +
                        "LIMITE DE PASSOS: ${profile.maxSteps}\n" +
                        "LIMITE DE FERRAMENTAS: ${profile.maxToolCalls}\n" +
                        "DELEGAÇÃO ANINHADA: desativada\n" +
                        "MUTAÇÃO: desativada\n" +
                        "SAÍDA: ${profile.requiredOutputSections.joinToString("; ")}"
                )
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[2]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "Esta ferramenta não aceita argumentos.")
                }
                val report = activeProfileRegistry().compileAll()
                return if (report.success) {
                    KellvynToolResult(
                        true,
                        "PROFILE COMPILER APROVADO: ${report.profiles.size}/14 perfis compilados; " +
                            "cada perfil recebeu hash SHA-256 e capacidades resolvidas pela política central. " +
                            "Isto valida a estrutura, não a competência cognitiva; o benchmark independente continua obrigatório."
                    )
                } else {
                    KellvynToolResult(
                        false,
                        "PROFILE COMPILER REPROVADO:\n" + report.errors.joinToString("\n")
                    )
                }
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[3]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val unknown = arguments.keys - setOf("role")
                if (unknown.isNotEmpty()) {
                    return KellvynToolResult(false, "Argumentos não reconhecidos.")
                }
                val selectedRole = arguments["role"]?.let { raw ->
                    KellvynSpecialistRole.values().firstOrNull { it.name == raw }
                        ?: return KellvynToolResult(false, "Papel especialista inválido.")
                }
                val cases = KellvynSpecialistBenchmarkRegistry.default()
                val errors = cases.validationErrors()
                if (errors.isNotEmpty()) {
                    return KellvynToolResult(
                        false,
                        "CATÁLOGO DE BENCHMARK REPROVADO:\n" + errors.joinToString(" ")
                    )
                }
                val selectedCases = selectedRole?.let(cases::forRole) ?: cases.cases()
                val output = buildString {
                    append("CATÁLOGO DE AVALIAÇÃO — ")
                    append(cases.cases().size).append(" casos; 2 por papel.\n")
                    append("Isto é um plano de testes, não um resultado de competência.\n")
                    selectedRole?.let { append("PAPEL SELECIONADO: ").append(it.name).append("\n") }
                    selectedCases.forEach { testCase ->
                        append(testCase.caseId).append(" | ")
                            .append(testCase.role.name).append(" | ")
                            .append(testCase.kind.name).append(" | ")
                            .append(testCase.title).append("\n")
                        if (selectedRole != null) {
                            testCase.requiredEvidence.forEach { criterion ->
                                append("  + ").append(criterion).append("\n")
                            }
                            testCase.prohibitedBehaviors.forEach { prohibited ->
                                append("  - ").append(prohibited).append("\n")
                            }
                        }
                    }
                    if (selectedRole == null) {
                        append("Para ver critérios e comportamentos proibidos de um papel, consulte novamente com role=ENUM_DO_PAPEL.")
                    }
                }
                return KellvynToolResult(true, output.take(MAX_BENCHMARK_OUTPUT_CHARS))
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[4]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val unknown = arguments.keys - setOf("role")
                if (unknown.isNotEmpty()) return KellvynToolResult(false, "Argumentos não reconhecidos.")
                val role = KellvynSpecialistRole.values().firstOrNull {
                    it.name == arguments["role"]?.trim()
                } ?: return KellvynToolResult(false, "Papel especialista inválido.")
                val store = profileStore
                    ?: return KellvynToolResult(false, "O histórico persistente ainda não está conectado.")
                val allRecords = withContext(Dispatchers.IO) { store.history(role) }
                if (allRecords.isEmpty()) {
                    return KellvynToolResult(true, "HISTÓRICO: ${role.name} ainda usa somente o perfil-base.")
                }
                val records = allRecords.takeLast(MAX_PROFILE_HISTORY_ITEMS)
                val omittedCount = allRecords.size - records.size
                val output = records.joinToString("\n") { record ->
                    "#${record.sequence} ${record.eventType.name} v${record.profileVersion} " +
                        "sha256:${record.profileSha256} base:v${record.baseProfileVersion} " +
                        "por ${record.actor} em ${record.createdAtMillis}: ${record.reason}"
                }
                val prefix = if (omittedCount > 0) {
                    "HISTÓRICO IMUTÁVEL — ${role.name}; mostrando os últimos ${records.size} de ${allRecords.size} eventos.\n"
                } else {
                    "HISTÓRICO IMUTÁVEL — ${role.name}\n"
                }
                return KellvynToolResult(true, (prefix + output).take(MAX_PROFILE_HISTORY_OUTPUT_CHARS))
            }
        },
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[5]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val unknown = arguments.keys - setOf("role")
                if (unknown.isNotEmpty()) return KellvynToolResult(false, "Argumentos não reconhecidos.")
                val role = KellvynSpecialistRole.values().firstOrNull {
                    it.name == arguments["role"]?.trim()
                } ?: return KellvynToolResult(false, "Papel especialista inválido.")
                val store = profileStore
                    ?: return KellvynToolResult(false, "O histórico persistente ainda não está conectado.")
                val diff = withContext(Dispatchers.IO) {
                    store.pendingDiffDetails(role, profiles.requireProfile(role))
                } ?: return KellvynToolResult(true, "Não existe revisão pendente para ${role.name}.")
                val diffText = diff.joinToString("\n\n") { change ->
                    "CAMPO: ${change.field}\nANTES:\n${change.before}\nDEPOIS:\n${change.after}"
                }
                val resultText =
                    "DIFF DA REVISÃO PENDENTE — ${role.name}\n" +
                        (diffText.takeIf { it.isNotEmpty() } ?: "Nenhum campo alterado.") +
                        "\nA revisão continua inativa até aprovação explícita do proprietário."
                return KellvynToolResult(
                    true,
                    resultText.take(MAX_PROFILE_DIFF_OUTPUT_CHARS)
                )
            }
,
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[6]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val allowedKeys = setOf("role", "display_name", "mission", "instruction")
                if ((arguments.keys - allowedKeys).isNotEmpty()) {
                    return KellvynToolResult(false, "Argumentos não reconhecidos para a prévia do perfil.")
                }
                val rawRole = arguments["role"]?.trim().orEmpty()
                val role = KellvynSpecialistRole.values().firstOrNull { it.name == rawRole }
                    ?: return KellvynToolResult(false, "Papel especialista inválido.")
                val mission = arguments["mission"]?.trim().orEmpty()
                val instruction = arguments["instruction"]?.trim().orEmpty()
                val activeProfiles = activeProfileRegistry()
                val active = activeProfiles.requireProfile(role)
                val history = withContext(Dispatchers.IO) {
                    profileStore?.history(role).orEmpty()
                }
                val pending = withContext(Dispatchers.IO) {
                    profileStore?.pendingProfile(role)
                }
                if (pending != null) {
                    return KellvynToolResult(
                        false,
                        "Já existe uma revisão pendente para ${role.name}. Resolva a revisão atual antes de criar outra."
                    )
                }

                val nextVersion = maxOf(
                    active.version,
                    history.maxOfOrNull { it.profileVersion } ?: active.version
                ) + 1
                val candidate = try {
                    active.copy(
                        version = nextVersion,
                        displayName = arguments["display_name"]?.trim()?.ifBlank { active.displayName }
                            ?: active.displayName,
                        mission = mission,
                        instruction = instruction
                    )
                } catch (error: IllegalArgumentException) {
                    return KellvynToolResult(false, "Perfil candidato inválido: " + safeMessage(error))
                }
                val compilation = KellvynSpecialistProfileCompiler().compile(candidate)
                    ?: return KellvynToolResult(
                        false,
                        "O candidato não passou pelo Profile Compiler; nenhuma alteração foi salva."
                    )
                val diff = buildList {
                    if (active.displayName != candidate.displayName) {
                        add("displayName: ${active.displayName} → ${candidate.displayName}")
                    }
                    if (active.mission != candidate.mission) {
                        add("mission: ${active.mission} → ${candidate.mission}")
                    }
                    if (active.instruction != candidate.instruction) {
                        add("instruction: ${active.instruction} → ${candidate.instruction}")
                    }
                }
                val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(role)
                    .map { it.name }.sorted().joinToString(", ")
                val output = buildString {
                    append("PRÉVIA DE REVISÃO — NÃO SALVA E NÃO ATIVA\n")
                    append("PAPEL: ").append(role.name).append("\n")
                    append("VERSÃO ATIVA: ").append(active.version).append("\n")
                    append("VERSÃO CANDIDATA: ").append(candidate.version).append("\n")
                    append("SHA-256 CANDIDATO: ").append(compilation.profileSha256).append("\n")
                    append("ALTERAÇÕES PROPOSTAS:\n")
                    if (diff.isEmpty()) append("- Nenhuma alteração nos campos editáveis.\n")
                    else diff.forEach { append("- ").append(it).append("\n") }
                    append("CAPACIDADES FIXADAS PELA POLÍTICA CENTRAL: ").append(capabilities).append("\n")
                    append("LIMITE DE PASSOS: ").append(candidate.maxSteps).append(" (inalterado)\n")
                    append("LIMITE DE FERRAMENTAS: ").append(candidate.maxToolCalls).append(" (inalterado)\n")
                    append("CONTEXTO MÁXIMO: ").append(candidate.maxContextChars).append(" caracteres (inalterado)\n")
                    append("DELEGAÇÃO ANINHADA: DESATIVADA\nMUTAÇÃO: DESATIVADA\n")
                    append("ESTADO: PREVIEW_ONLY; nenhum histórico foi escrito e o perfil ativo permanece inalterado.\n")
                    append("Próximo passo: o proprietário precisa revisar o diff e aprovar explicitamente antes de qualquer staging.")
                }
                return KellvynToolResult(true, output.take(MAX_PROFILE_DIFF_OUTPUT_CHARS))
            }
        }        }
    )

    private fun safeMessage(error: Exception): String =
        (error.message ?: error.javaClass.simpleName)
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(240)

    override fun trimTransientResources(level: KellvynCachePressure) = Unit
    override fun close() = Unit

    companion object {
        const val MODULE_ID = "agent-creation"
        const val CATALOG_TOOL = "agent_profiles_list"
        const val INSPECT_TOOL = "agent_profile_inspect"
        const val VALIDATE_TOOL = "agent_profiles_validate"
        const val BENCHMARK_CATALOG_TOOL = "agent_benchmark_catalog"
        const val PROFILE_HISTORY_TOOL = "agent_profile_history"
        const val PROFILE_PENDING_DIFF_TOOL = "agent_profile_pending_diff"
        const val PROFILE_REVISION_PREVIEW_TOOL = "agent_profile_revision_preview"
        private const val KIBIBYTE = 1024L
        private const val MAX_BENCHMARK_OUTPUT_CHARS = 7_500
        private const val MAX_PROFILE_DIFF_OUTPUT_CHARS = 6_500
        private const val MAX_PROFILE_HISTORY_ITEMS = 12
        private const val MAX_PROFILE_HISTORY_OUTPUT_CHARS = 6_500
        private const val PROFILE_HISTORY_WORKING_SET_BYTES = 24L * 1024L * 1024L

        private val ROLE_PATTERN = "^(" +
            KellvynSpecialistRole.values().joinToString("|") { it.name } +
            ")$"

        private val TOOL_SPECS = listOf(
            KellvynToolSpec(
                name = CATALOG_TOOL,
                description = "Lista os 14 perfis internos, missões, versões e capacidades autorizadas.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 10_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = PROFILE_HISTORY_WORKING_SET_BYTES,
                capabilities = setOf(KellvynStudioCapability.AGENT_DESIGN)
            ),
            KellvynToolSpec(
                name = INSPECT_TOOL,
                description = "Inspeciona a missão e os limites de um perfil especialista sem modificá-lo.",
                requiredArguments = setOf("role"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "role",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 40,
                        pattern = ROLE_PATTERN
                    )
                ),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 10_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = PROFILE_HISTORY_WORKING_SET_BYTES,
                capabilities = setOf(KellvynStudioCapability.AGENT_DESIGN)
            ),
            KellvynToolSpec(
                name = VALIDATE_TOOL,
                description = "Valida a estrutura dos 14 perfis sem alegar competência do modelo.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 10_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = PROFILE_HISTORY_WORKING_SET_BYTES,
                capabilities = setOf(KellvynStudioCapability.AGENT_EVALUATION)
            ),
            KellvynToolSpec(
                name = BENCHMARK_CATALOG_TOOL,
                description = "Lista os 28 cenários de avaliação; role opcional retorna a rubrica detalhada daquele papel. Não executa o modelo.",
                optionalArguments = setOf("role"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "role",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 40,
                        pattern = ROLE_PATTERN
                    )
                ),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 4_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 512L * KIBIBYTE,
                capabilities = setOf(KellvynStudioCapability.AGENT_EVALUATION)
            ),
            KellvynToolSpec(
                name = PROFILE_HISTORY_TOOL,
                description = "Consulta o histórico criptografado de versões e aprovações de um perfil, sem alterar o estado.",
                requiredArguments = setOf("role"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "role",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 40,
                        pattern = ROLE_PATTERN
                    )
                ),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 10_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = PROFILE_HISTORY_WORKING_SET_BYTES,
                capabilities = setOf(KellvynStudioCapability.AGENT_DESIGN)
            ),
            KellvynToolSpec(
                name = PROFILE_PENDING_DIFF_TOOL,
                description = "Compara o perfil ativo com uma revisão pendente; nunca a promove.",
                requiredArguments = setOf("role"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "role",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 40,
                        pattern = ROLE_PATTERN
                    )
                ),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 10_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = PROFILE_HISTORY_WORKING_SET_BYTES,
                capabilities = setOf(KellvynStudioCapability.AGENT_DESIGN)
            )
,
            KellvynToolSpec(
                name = PROFILE_REVISION_PREVIEW_TOOL,
                description = "Prepara uma prévia não persistida de uma nova versão de perfil. Não grava, não ativa e não concede capacidades; exige revisão do proprietário.",
                requiredArguments = setOf("role", "mission", "instruction"),
                optionalArguments = setOf("display_name"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "role",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 40,
                        pattern = ROLE_PATTERN
                    ),
                    KellvynToolArgumentSchema(
                        name = "display_name",
                        type = KellvynToolArgumentType.TEXT,
                        required = false,
                        maxLength = 80
                    ),
                    KellvynToolArgumentSchema(
                        name = "mission",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 500
                    ),
                    KellvynToolArgumentSchema(
                        name = "instruction",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 2_000
                    )
                ),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 8_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = PROFILE_HISTORY_WORKING_SET_BYTES,
                capabilities = setOf(KellvynStudioCapability.AGENT_DESIGN)
            )        )

        val MANIFEST = KellvynStudioModuleManifest(
            moduleId = MODULE_ID,
            version = 1,
            capabilities = setOf(
                KellvynStudioCapability.AGENT_DESIGN,
                KellvynStudioCapability.AGENT_EVALUATION
            ),
            supportedExtensions = emptySet(),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedLoadWorkingSetBytes = 512L * KIBIBYTE,
            estimatedResidentBytes = 128L * KIBIBYTE,
            permissions = emptySet(),
            toolSpecs = TOOL_SPECS
        )

        fun definition(
            profiles: KellvynSpecialistProfileRegistry = KellvynSpecialistProfileRegistry.default(),
            profileStore: KellvynSpecialistProfileStore? = null
        ) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynAgentCreationStudio(profiles, profileStore)
        }
    }
}
