package com.kellvyn.assistant

/**
 * Read-only inventory for the invisible studio.
 *
 * This exposes only capabilities that are actually present in registered module manifests
 * or in the core tool registry. It does not instantiate lazy modules, execute tools, or
 * convert a declared capability into a claim of successful operation.
 */
internal object KellvynInvisibleStudioCatalogTool {
    const val TOOL_NAME = "studio_capability_inventory"

    private const val MAX_OUTPUT_CHARS = 9_000

    fun create(
        moduleManager: KellvynStudioModuleManager,
        coreToolSpecs: List<KellvynToolSpec>
    ): KellvynAgentTool {
        val spec = KellvynToolSpec(
            name = TOOL_NAME,
            description = "Lista capacidades realmente registradas no laboratório invisível, módulos sob demanda, ferramentas centrais, dependências declaradas e lacunas. É somente inventário: não carrega nem executa motores.",
            risk = KellvynToolRisk.READ_ONLY,
            timeoutMs = 5_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedWorkingSetBytes = 256L * 1024L
        )

        return object : KellvynAgentTool {
            override val spec: KellvynToolSpec = spec

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "O inventário não aceita argumentos.")
                }
                return runCatching {
                    KellvynToolResult(
                        success = true,
                        content = render(
                            moduleManager = moduleManager,
                            coreToolSpecs = coreToolSpecs
                        )
                    )
                }.getOrElse { error ->
                    KellvynToolResult(
                        success = false,
                        content = "Não foi possível ler o catálogo interno: " +
                            (error.message ?: error.javaClass.simpleName).take(240)
                    )
                }
            }
        }
    }

    private fun render(
        moduleManager: KellvynStudioModuleManager,
        coreToolSpecs: List<KellvynToolSpec>
    ): String {
        val manifests = moduleManager.manifests().sortedBy { it.moduleId }
        val loaded = moduleManager.loadedModuleIds()
        val core = coreToolSpecs.distinctBy { it.name }.sortedBy { it.name }
        val allCapabilities = KellvynStudioCapability.values()
            .filterNot {
                it == KellvynStudioCapability.AGENT_DESIGN ||
                    it == KellvynStudioCapability.AGENT_EVALUATION
            }

        val body = buildString {
            appendLine("CATÁLOGO DO LABORATÓRIO INVISÍVEL — SOMENTE LEITURA")
            appendLine("módulos_registrados=${manifests.size}")
            appendLine("ferramentas_núcleo=${core.size}")
            appendLine("motores_carregados_agora=${loaded.size}")
            appendLine()
            appendLine("CAPACIDADES")
            allCapabilities.forEach { capability ->
                val moduleIds = manifests.filter { capability in it.capabilities }
                    .map { it.moduleId }
                val coreNames = core.filter { capability in it.capabilities }
                    .map { it.name }
                val status = when {
                    moduleIds.isNotEmpty() -> "REGISTERED_LAZY"
                    coreNames.isNotEmpty() -> "REGISTERED_CORE"
                    else -> "NOT_REGISTERED"
                }
                append(capability.name)
                    .append(" status=").append(status)
                if (moduleIds.isNotEmpty()) {
                    append(" módulos=").append(moduleIds.joinToString(","))
                }
                if (coreNames.isNotEmpty()) {
                    append(" ferramentas=").append(coreNames.joinToString(","))
                }
                appendLine()
            }

            appendLine()
            appendLine("MÓDULOS")
            if (manifests.isEmpty()) {
                appendLine("(nenhum módulo registrado)")
            }
            manifests.forEach { manifest ->
                append(manifest.moduleId)
                    .append(" v").append(manifest.version)
                    .append(" state=REGISTERED_LAZY")
                    .append(" loaded_now=").append(manifest.moduleId in loaded)
                    .append(" workload=").append(manifest.workloadClass.name)
                    .append(" requires_network=").append(manifest.requiresNetwork)
                    .append(" min_sdk=").append(manifest.minSdk)
                    .appendLine()
                append("  capabilities=")
                    .append(manifest.capabilities.map { it.name }.sorted().joinToString(","))
                    .appendLine()
                append("  permissions=")
                    .append(manifest.permissions.map { it.name }.sorted().joinToString(","))
                    .appendLine()
                append("  extensions=")
                    .append(manifest.supportedExtensions.sorted().joinToString(","))
                    .appendLine()
                append("  tools=")
                    .append(manifest.toolSpecs.joinToString(",") {
                        it.name + "[" + it.risk.name + "]"
                    })
                    .appendLine()
            }

            appendLine()
            appendLine("FERRAMENTAS CENTRAIS")
            if (core.isEmpty()) appendLine("(nenhuma ferramenta central registrada)")
            core.forEach { tool ->
                append(tool.name)
                    .append(" risk=").append(tool.risk.name)
                    .append(" workload=").append(tool.workloadClass.name)
                if (tool.capabilities.isNotEmpty()) {
                    append(" capabilities=")
                        .append(tool.capabilities.map { it.name }.sorted().joinToString(","))
                }
                appendLine()
            }

            appendLine()
            appendLine("LIMITES DE CONFIANÇA")
            appendLine("REGISTERED_LAZY significa contrato registrado; o motor pode ainda não estar carregado.")
            appendLine("REGISTERED_CORE significa ferramenta presente no registro central.")
            appendLine("NOT_REGISTERED significa que não foi encontrado módulo nem ferramenta para a capacidade.")
            appendLine("Este inventário não executa ferramentas, não verifica dependências instaladas e não prova funcionamento.")
            appendLine("Execução=NOT_CHECKED; verificação independente=NOT_VERIFIED.")
        }
        if (body.length <= MAX_OUTPUT_CHARS) return body
        val notice = "\n[INVENTÁRIO TRUNCADO POR LIMITE DE CONTEXTO]\n" +
            "Execução=NOT_CHECKED; verificação independente=NOT_VERIFIED."
        return body.take(MAX_OUTPUT_CHARS - notice.length) + notice
    }
}
