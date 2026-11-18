package com.kellvyn.assistant

/**
 * Read-only first phase of the invisible Agent Creation Studio.
 *
 * It exposes profile discovery, inspection and structural validation. It cannot edit,
 * promote, publish, or grant permissions to any specialist profile.
 */
internal class KellvynAgentCreationStudio(
    private val profiles: KellvynSpecialistProfileRegistry
) : KellvynStudioModule {
    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS[0]

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "Esta ferramenta não aceita argumentos.")
                }
                val compilation = profiles.compileAll()
                if (!compilation.success) {
                    return KellvynToolResult(
                        false,
                        "O catálogo não passou pelo Profile Compiler: " + compilation.errors.joinToString(" ")
                    )
                }
                val compiledByRole = compilation.profiles.associateBy { it.role }
                val catalogue = profiles.profiles().joinToString("\n") { profile ->
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
                val compilation = profiles.compileAll()
                if (!compilation.success) {
                    return KellvynToolResult(false, "O perfil não passou pelo Profile Compiler.")
                }
                val profile = profiles.requireProfile(role)
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
                val report = profiles.compileAll()
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
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "Esta ferramenta não aceita argumentos.")
                }
                val cases = KellvynSpecialistBenchmarkRegistry.default()
                val errors = cases.validationErrors()
                if (errors.isNotEmpty()) {
                    return KellvynToolResult(
                        false,
                        "CATÁLOGO DE BENCHMARK REPROVADO:\\n" + errors.joinToString(" ")
                    )
                }
                val output = buildString {
                    append("CATÁLOGO DE AVALIAÇÃO DOS 14 ESPECIALISTAS — ")
                    append(cases.cases().size).append(" casos; 2 por papel.\\n")
                    append("Isto é um plano de testes, não um resultado de competência.\\n")
                    cases.cases().forEach { testCase ->
                        append(testCase.caseId).append(" | ")
                            .append(testCase.role.name).append(" | ")
                            .append(testCase.kind.name).append(" | ")
                            .append(testCase.title).append("\\n")
                        testCase.requiredEvidence.forEach { criterion ->
                            append("  + ").append(criterion).append("\\n")
                        }
                        testCase.prohibitedBehaviors.forEach { prohibited ->
                            append("  - ").append(prohibited).append("\\n")
                        }
                    }
                }
                return KellvynToolResult(true, output.take(MAX_BENCHMARK_OUTPUT_CHARS))
            }
        }
    )

    override fun trimTransientResources(level: KellvynCachePressure) = Unit
    override fun close() = Unit

    companion object {
        const val MODULE_ID = "agent-creation"
        const val CATALOG_TOOL = "agent_profiles_list"
        const val INSPECT_TOOL = "agent_profile_inspect"
        const val VALIDATE_TOOL = "agent_profiles_validate"
        const val BENCHMARK_CATALOG_TOOL = "agent_benchmark_catalog"
        private const val KIBIBYTE = 1024L
        private const val MAX_BENCHMARK_OUTPUT_CHARS = 7_500

        private val ROLE_PATTERN = "^(" +
            KellvynSpecialistRole.values().joinToString("|") { it.name } +
            ")$"

        private val TOOL_SPECS = listOf(
            KellvynToolSpec(
                name = CATALOG_TOOL,
                description = "Lista os 14 perfis internos, missões, versões e capacidades autorizadas.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 4_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 256L * KIBIBYTE,
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
                timeoutMs = 4_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 256L * KIBIBYTE,
                capabilities = setOf(KellvynStudioCapability.AGENT_DESIGN)
            ),
            KellvynToolSpec(
                name = VALIDATE_TOOL,
                description = "Valida a estrutura dos 14 perfis sem alegar competência do modelo.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 4_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 256L * KIBIBYTE,
                capabilities = setOf(KellvynStudioCapability.AGENT_EVALUATION)
            ),
            KellvynToolSpec(
                name = BENCHMARK_CATALOG_TOOL,
                description = "Lista os 28 cenários e rubricas de avaliação dos 14 papéis; não executa o modelo.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 4_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 512L * KIBIBYTE,
                capabilities = setOf(KellvynStudioCapability.AGENT_EVALUATION)
            )
        )

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
            profiles: KellvynSpecialistProfileRegistry = KellvynSpecialistProfileRegistry.default()
        ) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynAgentCreationStudio(profiles)
        }
    }
}
