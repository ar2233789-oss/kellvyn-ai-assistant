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
                val catalogue = profiles.profiles().joinToString("\n") { profile ->
                    val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(profile.role)
                        .map { it.name }
                        .sorted()
                        .joinToString(", ")
                    "- ${profile.role.name}: ${profile.displayName} (v${profile.version}) — " +
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
                val profile = profiles.requireProfile(role)
                val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(role)
                    .map { it.name }
                    .sorted()
                    .joinToString(", ")
                return KellvynToolResult(
                    true,
                    "PERFIL: ${profile.displayName}\n" +
                        "PAPEL: ${profile.role.name}\n" +
                        "VERSÃO: ${profile.version}\n" +
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
                val errors = profiles.validationErrors()
                return if (errors.isEmpty()) {
                    KellvynToolResult(
                        true,
                        "VALIDAÇÃO ESTRUTURAL APROVADA: 14/14 perfis existem, são únicos, versionados, " +
                            "somente leitura e respeitam os limites. Isto não prova competência cognitiva; " +
                            "o benchmark comportamental independente continua obrigatório."
                    )
                } else {
                    KellvynToolResult(
                        false,
                        "VALIDAÇÃO ESTRUTURAL REPROVADA:\n" + errors.joinToString("\n")
                    )
                }
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
        private const val KIBIBYTE = 1024L

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
