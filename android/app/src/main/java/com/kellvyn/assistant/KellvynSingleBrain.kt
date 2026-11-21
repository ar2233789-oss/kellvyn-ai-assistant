package com.kellvyn.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Cérebro central único do KN: o mesmo modelo decide, planeja, escolhe ferramentas
 * e avalia os resultados. As ferramentas são executores determinísticos.
 */
internal class KellvynSingleBrain(
    private val inference: KellvynInferenceEngine,
    private val tools: KellvynToolRegistry,
    private val authorization: KellvynToolAuthorization = ReadOnlyKellvynAuthorization,
    private val config: KellvynAgentConfig = KellvynAgentConfig(),
    private val memory: KellvynMemory? = null,
    private val memoryAddresses: List<KellvynMemoryAddress> = emptyList(),
    private val projectContextRetriever: KellvynProjectContextSearch? = null,
    private val activeProjectId: String? = null,
    private val skillRegistry: KellvynSkillRegistry = KellvynSkillRegistry.default(),
    private val taskCheckpoints: KellvynTaskCheckpointRepository? = null,
    private val taskBroker: KellvynTaskBroker? = null,
    private val traceSessionFactory: () -> KellvynAgentLabTraceSession = {
        KellvynAgentLabTraceSession.create()
    },
    private val resourceSnapshotProvider: (suspend () -> KellvynResourceSnapshot)? = null
) {
    private val taskPlanner = KellvynTaskPlanner()
    /**
     * Executes exactly one ready plan step. A step is checkpointed as RUNNING before the
     * tool executes and is marked SUCCEEDED only when the independent verifier proves every
     * declared success criterion. Approval and tool authorization remain separate gates.
     */
    suspend fun executePlannedStep(
        taskId: String,
        stepId: String,
        toolName: String,
        arguments: Map<String, String>,
        verifier: KellvynIndependentVerifier,
        preconditionEvidence: KellvynPreconditionEvidence? = null
    ): KellvynBrainResult {
        val traceSession = traceSessionFactory()
        val resourceBaseline = recordResourceSnapshot(traceSession, "START")
        traceSession.record(
            kind = KellvynAgentLabEventKind.RUN_STARTED,
            outcome = KellvynAgentLabOutcome.STARTED,
            detailCode = "PLANNED_STEP"
        )
        return try {
            val result = executePlannedStepInternal(
                taskId = taskId,
                stepId = stepId,
                toolName = toolName,
                arguments = arguments,
                verifier = verifier,
                preconditionEvidence = preconditionEvidence,
                traceSession = traceSession
            )
            recordResourceSnapshot(traceSession, "END", resourceBaseline)
            val terminal = when (result) {
                is KellvynBrainResult.Completed ->
                    KellvynAgentLabEventKind.RUN_COMPLETED to KellvynAgentLabOutcome.SUCCEEDED
                is KellvynBrainResult.Failed ->
                    KellvynAgentLabEventKind.RUN_FAILED to KellvynAgentLabOutcome.FAILED
                is KellvynBrainResult.LimitReached ->
                    KellvynAgentLabEventKind.RUN_LIMIT_REACHED to KellvynAgentLabOutcome.LIMITED
                is KellvynBrainResult.Planned ->
                    KellvynAgentLabEventKind.RUN_PLANNED to KellvynAgentLabOutcome.SUCCEEDED
            }
            traceSession.record(
                kind = terminal.first,
                outcome = terminal.second,
                detailCode = "PLANNED_STEP"
            )
            result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                recordResourceSnapshot(traceSession, "CANCELLED", resourceBaseline)
                traceSession.record(
                    kind = KellvynAgentLabEventKind.RUN_CANCELLED,
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "PLANNED_STEP_CANCELLED"
                )
            }
            throw cancelled
        } catch (error: Exception) {
            withContext(NonCancellable) {
                recordResourceSnapshot(traceSession, "FAILED", resourceBaseline)
                traceSession.record(
                    kind = KellvynAgentLabEventKind.RUN_FAILED,
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "PLANNED_STEP_EXCEPTION"
                )
            }
            throw error
        }
    }

    private suspend fun executePlannedStepInternal(
        taskId: String,
        stepId: String,
        toolName: String,
        arguments: Map<String, String>,
        verifier: KellvynIndependentVerifier,
        preconditionEvidence: KellvynPreconditionEvidence?,
        traceSession: KellvynAgentLabTraceSession
    ): KellvynBrainResult {
        val repository = taskCheckpoints
            ?: return KellvynBrainResult.Failed("O armazenamento persistente de tarefas não está configurado.", 0, 0)
        val latest = repository.loadLatest(taskId)
            ?: return KellvynBrainResult.Failed("O plano solicitado não foi encontrado.", 0, 0)
        val state = latest.state
        val step = state.plan.steps.firstOrNull { it.id == stepId }
            ?: return KellvynBrainResult.Failed("A etapa solicitada não pertence ao plano.", 0, 0)
        if (state.statuses[stepId] != KellvynStepStatus.READY) {
            return KellvynBrainResult.Failed(
                "A etapa não está pronta. Estado atual: ${state.statuses[stepId]}.",
                0,
                0
            )
        }
        if (step.requiredTools != setOf(toolName)) {
            return KellvynBrainResult.Failed(
                "Esta versão exige exatamente uma ferramenta registrada por etapa executável.",
                0,
                0
            )
        }

        val registeredRisks = tools.descriptions().associate { spec ->
            spec.name to spec.risk.toStepRisk()
        }
        val planErrors = taskPlanner.validate(state.plan, registeredRisks)
        if (planErrors.isNotEmpty()) {
            return KellvynBrainResult.Failed(
                "O plano não passou pela revalidação: ${planErrors.joinToString(" ")}",
                0,
                0
            )
        }

        val tool = tools.find(toolName)
            ?: return KellvynBrainResult.Failed("A ferramenta não está mais registrada.", 0, 0)
        val invalidArguments = tools.validate(tool, arguments)
        if (invalidArguments != null) {
            return KellvynBrainResult.Failed("Parâmetros da ferramenta recusados: $invalidArguments", 0, 0)
        }

        val allowed = try {
            authorization.authorize(tool.spec, arguments, state.plan.goal)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!allowed) {
            return KellvynBrainResult.Failed(
                "A política de autorização bloqueou a ferramenta, mesmo com a etapa aprovada.",
                0,
                0
            )
        }

        val running = try {
            taskPlanner.startStep(state, stepId, preconditionEvidence)
        } catch (error: Exception) {
            return KellvynBrainResult.Failed(
                "Não foi possível iniciar a etapa: ${safeMessage(error)}",
                0,
                0
            )
        }
        val startedCheckpoint = try {
            repository.append(
                state = running,
                createdAtMillis = System.currentTimeMillis(),
                expectedPreviousSha256 = latest.metadata.sha256
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return KellvynBrainResult.Failed(
                "Não foi possível persistir o início da etapa: ${safeMessage(error)}",
                0,
                0
            )
        }

        val toolResult = try {
            executeTool(
                tool = tool,
                arguments = arguments,
                requestId = "${taskId}-${stepId}",
                traceSession = traceSession,
                step = 1
            )
        } catch (deferred: KellvynTaskDeferredException) {
            val retryState = taskPlanner.returnStepToReady(running, stepId)
            try {
                repository.append(
                    state = retryState,
                    createdAtMillis = System.currentTimeMillis(),
                    expectedPreviousSha256 = startedCheckpoint.sha256
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return KellvynBrainResult.Failed(
                    "A tarefa foi adiada por recursos, mas não foi possível salvar o estado de retomada: ${safeMessage(error)}",
                    1,
                    0
                )
            }
            return KellvynBrainResult.LimitReached(
                "Etapa pausada pelo gerenciador de recursos (${deferred.reasonCode}). " +
                    "${deferred.message} Tente novamente após ${deferred.retryAfterMillis} ms.",
                1,
                0
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val cancelledState = taskPlanner.cancel(running)
                runCatching {
                    repository.append(
                        state = cancelledState,
                        createdAtMillis = System.currentTimeMillis(),
                        expectedPreviousSha256 = startedCheckpoint.sha256
                    )
                }
            }
            throw cancelled
        } catch (error: Exception) {
            KellvynToolResult(false, "A ferramenta falhou: ${safeMessage(error)}")
        }

        val observedAt = System.currentTimeMillis()
        val outcome = KellvynTaskOutcome(
            summary = toolResult.content.take(config.maxObservationChars),
            observedAtMillis = observedAt
        )
        val updatedState = if (!toolResult.success) {
            taskPlanner.failStep(
                running,
                stepId,
                toolResult.content.ifBlank { "A ferramenta informou falha." }.take(500)
            )
        } else {
            val proof = try {
                verifier.verify(step, outcome)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    val cancelledState = taskPlanner.cancel(running)
                    runCatching {
                        repository.append(
                            state = cancelledState,
                            createdAtMillis = System.currentTimeMillis(),
                            expectedPreviousSha256 = startedCheckpoint.sha256
                        )
                    }
                }
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (proof == null) {
                taskPlanner.failStep(
                    running,
                    stepId,
                    "A ferramenta retornou sucesso, mas não houve prova independente dos critérios."
                )
            } else {
                try {
                    taskPlanner.completeStep(running, stepId, outcome, KellvynIndependentVerifier { _, _ -> proof })
                } catch (error: Exception) {
                    taskPlanner.failStep(
                        running,
                        stepId,
                        "A prova independente foi inválida ou incompleta: ${safeMessage(error)}"
                    )
                }
            }
        }

        val finalCheckpoint = try {
            repository.append(
                state = updatedState,
                createdAtMillis = System.currentTimeMillis(),
                expectedPreviousSha256 = startedCheckpoint.sha256
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return KellvynBrainResult.Failed(
                "A etapa terminou, mas o resultado não pôde ser persistido: ${safeMessage(error)}",
                1,
                1
            )
        }
        return KellvynBrainResult.Planned(updatedState, finalCheckpoint)
    }

    /**
     * Advances a persisted plan through ready steps in dependency order.
     *
     * It stops naturally when a step awaits owner approval, fails verification, or the
     * per-call step budget is exhausted. Every individual step still goes through the same
     * checkpoint, authorization, timeout and independent-verifier gates.
     */
    suspend fun executeReadySteps(
        taskId: String,
        verifiersByStepId: Map<String, KellvynIndependentVerifier>,
        argumentsByStepId: Map<String, Map<String, String>> = emptyMap(),
        maxSteps: Int = 8
    ): KellvynBrainResult {
        require(maxSteps in 1..MAX_AUTOMATED_PLAN_STEPS)
        val repository = taskCheckpoints
            ?: return KellvynBrainResult.Failed(
                "O armazenamento persistente de tarefas não está configurado.",
                0,
                0
            )
        var executed = 0
        var latest = repository.loadLatest(taskId)
            ?: return KellvynBrainResult.Failed("O plano solicitado não foi encontrado.", 0, 0)

        while (executed < maxSteps) {
            val state = latest.state
            val nextStep = state.plan.steps.firstOrNull {
                state.statuses[it.id] == KellvynStepStatus.READY
            } ?: return KellvynBrainResult.Planned(state, latest.metadata)

            val toolName = nextStep.requiredTools.singleOrNull()
                ?: return KellvynBrainResult.Failed(
                    "A etapa ${nextStep.id} precisa declarar exatamente uma ferramenta para execução automática.",
                    executed,
                    executed
                )
            val verifier = verifiersByStepId[nextStep.id]
                ?: return KellvynBrainResult.Failed(
                    "Não existe verificador independente registrado para a etapa ${nextStep.id}.",
                    executed,
                    executed
                )

            val result = executePlannedStep(
                taskId = taskId,
                stepId = nextStep.id,
                toolName = toolName,
                arguments = argumentsByStepId[nextStep.id].orEmpty(),
                verifier = verifier
            )
            if (result !is KellvynBrainResult.Planned) return result
            if (result.state.statuses[nextStep.id] != KellvynStepStatus.SUCCEEDED) {
                return result
            }
            executed++
            latest = repository.loadLatest(taskId)
                ?: return KellvynBrainResult.Failed(
                    "O plano desapareceu após uma etapa concluída.",
                    executed,
                    executed
                )
        }

        return latest.let { KellvynBrainResult.Planned(it.state, it.metadata) }
    }

    suspend fun run(goal: String): KellvynBrainResult {
        val traceSession = traceSessionFactory()
        val resourceBaseline = recordResourceSnapshot(traceSession, "START")
        traceSession.record(
            kind = KellvynAgentLabEventKind.RUN_STARTED,
            outcome = KellvynAgentLabOutcome.STARTED
        )
        return try {
            val result = runLoop(goal, traceSession)
            recordResourceSnapshot(traceSession, "END", resourceBaseline)
            val kindAndOutcome = when (result) {
                is KellvynBrainResult.Completed ->
                    (KellvynAgentLabEventKind.RUN_COMPLETED to KellvynAgentLabOutcome.SUCCEEDED)
                is KellvynBrainResult.Failed ->
                    (KellvynAgentLabEventKind.RUN_FAILED to KellvynAgentLabOutcome.FAILED)
                is KellvynBrainResult.LimitReached ->
                    (KellvynAgentLabEventKind.RUN_LIMIT_REACHED to KellvynAgentLabOutcome.LIMITED)
                is KellvynBrainResult.Planned ->
                    (KellvynAgentLabEventKind.RUN_PLANNED to KellvynAgentLabOutcome.SUCCEEDED)
            }
            traceSession.record(
                kind = kindAndOutcome.first,
                outcome = kindAndOutcome.second,
                detailCode = result.javaClass.simpleName.uppercase()
                    .replace(Regex("[^A-Z0-9_.:-]"), "_")
                    .take(80)
            )
            result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                recordResourceSnapshot(traceSession, "CANCELLED", resourceBaseline)
                traceSession.record(
                    kind = KellvynAgentLabEventKind.RUN_CANCELLED,
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "CANCELLED"
                )
            }
            throw cancelled
        } catch (error: Exception) {
            withContext(NonCancellable) {
                recordResourceSnapshot(traceSession, "FAILED", resourceBaseline)
                traceSession.record(
                    kind = KellvynAgentLabEventKind.RUN_FAILED,
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "UNHANDLED_EXCEPTION"
                )
            }
            throw error
        }
    }

    private suspend fun recordResourceSnapshot(
        traceSession: KellvynAgentLabTraceSession,
        phase: String,
        baseline: KellvynResourceSnapshot? = null
    ): KellvynResourceSnapshot? {
        val provider = resourceSnapshotProvider ?: return null
        val snapshot = try {
            provider()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        val elapsedDelta = baseline?.let {
            (snapshot.sampledAtElapsedRealtimeMillis - it.sampledAtElapsedRealtimeMillis)
                .takeIf { delta -> delta > 0L }
        }
        val cpuDelta = baseline?.let {
            (snapshot.processCpuTimeMillis - it.processCpuTimeMillis)
                .takeIf { delta -> delta >= 0L }
        }
        val normalizedCpuPercent = if (elapsedDelta != null && cpuDelta != null) {
            ((cpuDelta.toDouble() * 100.0) /
                (elapsedDelta.toDouble() * snapshot.availableProcessors.toDouble()))
                .toInt()
                .coerceIn(0, 100)
        } else {
            null
        }
        val detailCode = buildString {
            append(phase)
            append("-F").append(snapshot.availableSystemMemoryBytes / MEBIBYTE)
            append("-H").append((snapshot.processHeapUtilization * 100f).toInt())
            append("-P").append(snapshot.processPssBytes / MEBIBYTE)
            append("-S").append(snapshot.availableAppDataBytes?.div(MEBIBYTE) ?: "NA")
            append("-B").append(snapshot.batteryPercent ?: "NA")
            append("-T").append(snapshot.thermalBand.name)
            snapshot.thermalHeadroomForecast?.let {
                append("-TH").append((it * 100f).toInt())
            }
            cpuDelta?.let { append("-DC").append(it) }
            normalizedCpuPercent?.let { append("-CPU").append(it) }
        }.take(80)
        traceSession.record(
            kind = KellvynAgentLabEventKind.RESOURCE_SNAPSHOT,
            detailCode = detailCode
        )
        return snapshot
    }

    private suspend fun runLoop(
        goal: String,
        traceSession: KellvynAgentLabTraceSession
    ): KellvynBrainResult {
        val normalizedGoal = goal.trim()
        if (normalizedGoal.isEmpty()) {
            return KellvynBrainResult.Failed("O objetivo da tarefa está vazio.", 0, 0)
        }

        val messages = mutableListOf(
            KellvynAgentMessage(
                role = KellvynMessageRole.SYSTEM,
                trust = KellvynTrustLevel.TRUSTED,
                source = "kellvyn-core",
                content = KellvynDecisionPromptContext.build(
                    goal = normalizedGoal,
                    config = config,
                    skillRegistry = skillRegistry
                )
            )
        )

        if (memory != null && memoryAddresses.isNotEmpty()) {
            val recalled = try {
                memory.recall(
                    KellvynMemoryQuery(
                        text = normalizedGoal.take(160),
                        addresses = memoryAddresses,
                        nowMillis = System.currentTimeMillis(),
                        limit = config.maxMemoryResults
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return KellvynBrainResult.Failed(
                    "Não foi possível consultar a memória local com segurança.",
                    0,
                    0
                )
            }

            recalled.forEach { match ->
                messages += KellvynAgentMessage(
                    role = KellvynMessageRole.TOOL,
                    trust = KellvynTrustLevel.UNTRUSTED,
                    source = "memory:${match.entry.id}",
                    content = "CONTEXTO DE MEMÓRIA APROVADO PELO PROPRIETÁRIO\n" +
                        "Tipo: ${match.entry.kind.name}\n" +
                        "Título: ${match.entry.title}\n" +
                        "Origem: ${match.entry.provenance}\n" +
                        "Conteúdo: ${match.entry.content}"
                )
            }
        }

        if (projectContextRetriever != null && !activeProjectId.isNullOrBlank() &&
            normalizedGoal.trim().length >= 2
        ) {
            val projectContext = try {
                val search: suspend () -> KellvynProjectSearchReport = {
                    projectContextRetriever.search(
                        KellvynProjectSearchRequest(
                            projectId = activeProjectId,
                            query = normalizedGoal.take(120),
                            maxFilesToScan = config.maxProjectFilesToScan,
                            maxResults = config.maxProjectResults
                        )
                    )
                }
                val broker = taskBroker
                if (broker == null) {
                    search()
                } else {
                    broker.execute(
                        KellvynResourceRequest(
                            taskId = "project-index-$activeProjectId",
                            workload = KellvynWorkloadClass.STANDARD,
                            estimatedWorkingSetBytes = 64L * MEBIBYTE
                        ),
                        operation = search
                    )
                }
            } catch (deferred: KellvynTaskDeferredException) {
                return KellvynBrainResult.LimitReached(
                    "A análise do projeto foi adiada pelo gerenciador de recursos: ${deferred.message}",
                    0,
                    0
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return KellvynBrainResult.Failed(
                    "Não foi possível pesquisar o projeto ativo com segurança.",
                    0,
                    0
                )
            }

            projectContext.hits.forEach { hit ->
                messages += KellvynAgentMessage(
                    role = KellvynMessageRole.TOOL,
                    trust = KellvynTrustLevel.UNTRUSTED,
                    source = "workspace:$activeProjectId:${hit.path}",
                    content = "TRECHO DE ARQUIVO DO PROJETO (DADO NÃO CONFIÁVEL)\n" +
                        "Caminho: ${hit.path}\n" +
                        "Correspondências: ${hit.matchedTerms.joinToString()}\n" +
                        "Símbolos/imports reconhecidos: ${hit.matchedIdentifiers.sorted().joinToString()}\n" +
                        "Trecho:\n${hit.snippet}" +
                        if (hit.truncated) "\n[trecho truncado]" else ""
                )
            }
        }

        messages += KellvynAgentMessage(
            role = KellvynMessageRole.USER,
            trust = KellvynTrustLevel.USER_PROVIDED,
            source = "user",
            content = normalizedGoal
        )
        val repeatedCalls = mutableMapOf<String, Int>()
        var toolCalls = 0
        var hasExecutedMutation = false

        for (step in 1..config.maxSteps) {
            val contextMessages = when (
                val context = KellvynContextAssembler.fit(messages, config.maxContextChars)
            ) {
                is KellvynContextAssembler.Result.Ready -> context.messages
                is KellvynContextAssembler.Result.TooLarge -> {
                    return KellvynBrainResult.Failed(
                        "As instruções e a solicitação essenciais excedem o orçamento de contexto. " +
                            "Reduza o pedido sem remover as regras de segurança.",
                        step,
                        toolCalls
                    )
                }
            }
            val inferenceResult = try {
                executeInference(
                    history = contextMessages,
                    requestId = "kn-inference-$step",
                    traceSession = traceSession,
                    step = step
                )
            } catch (deferred: KellvynTaskDeferredException) {
                return KellvynBrainResult.LimitReached(
                    "A inferência foi adiada pelo gerenciador de recursos: ${deferred.message}",
                    step,
                    toolCalls
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return KellvynBrainResult.Failed(
                    "Falha ao consultar o modelo neural Kellvyn: ${safeMessage(error)}",
                    step,
                    toolCalls
                )
            }
            val decision = inferenceResult.decision
            val presentedToolNames = inferenceResult.presentedToolNames

            when (decision) {
                is KellvynModelDecision.Delegate -> {
                    return KellvynBrainResult.Failed(
                        "A decisão foi recusada porque o cérebro KN trabalha como uma única inteligência.",
                        step,
                        toolCalls
                    )
                }

                is KellvynModelDecision.Plan -> {
                    if (hasExecutedMutation) {
                        return KellvynBrainResult.Failed(
                            "Um plano estruturado não pode substituir uma alteração já executada.",
                            step,
                            toolCalls
                        )
                    }
                    val repository = taskCheckpoints ?: return KellvynBrainResult.Failed(
                        "O plano foi proposto, mas o armazenamento persistente de tarefas não está configurado.",
                        step,
                        toolCalls
                    )
                    val requiredPlanTools = decision.plan.steps
                        .flatMap { it.requiredTools }
                        .toSet()
                    val unavailablePlanTools = requiredPlanTools - presentedToolNames
                    if (unavailablePlanTools.isNotEmpty()) {
                        messages += observation(
                            source = "task-plan:unavailable-tools",
                            success = false,
                            content = "Plano recusado porque estas ferramentas não estavam no catálogo " +
                                "apresentado nesta decisão: ${unavailablePlanTools.sorted().joinToString()}. " +
                                "Consulte o inventário local e reformule o plano apenas com ferramentas apresentadas."
                        )
                        continue
                    }
                    val registeredRisks = tools.descriptions().associate { spec ->
                        spec.name to spec.risk.toStepRisk()
                    }
                    val errors = taskPlanner.validate(decision.plan, registeredRisks)
                    if (errors.isNotEmpty()) {
                        messages += observation(
                            source = "task-plan",
                            success = false,
                            content = "Plano recusado pelo validador: ${errors.joinToString(" ")}"
                        )
                        continue
                    }
                    val state = try {
                        taskPlanner.createState(decision.plan, registeredRisks)
                    } catch (error: Exception) {
                        messages += observation(
                            source = "task-plan",
                            success = false,
                            content = "Não foi possível criar o estado do plano: ${safeMessage(error)}"
                        )
                        continue
                    }
                    val metadata = try {
                        repository.append(
                            state = state,
                            createdAtMillis = System.currentTimeMillis(),
                            expectedPreviousSha256 = null
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        return KellvynBrainResult.Failed(
                            "O plano foi validado, mas não pôde ser persistido: ${safeMessage(error)}",
                            step,
                            toolCalls
                        )
                    }
                    return KellvynBrainResult.Planned(state, metadata)
                }

                is KellvynModelDecision.Final -> {
                    val answer = decision.text.trim()
                    return if (answer.isEmpty()) {
                        KellvynBrainResult.Failed("O modelo retornou uma resposta vazia.", step, toolCalls)
                    } else {
                        KellvynBrainResult.Completed(answer, step, toolCalls)
                    }
                }

                is KellvynModelDecision.CallTool -> {
                    messages += KellvynAgentMessage(
                        role = KellvynMessageRole.ASSISTANT,
                        trust = KellvynTrustLevel.UNTRUSTED,
                        source = "agent-action",
                        content = "Ferramenta proposta: ${decision.name}\nParâmetros: " +
                            decision.arguments.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }
                    )
                    if (toolCalls >= config.maxToolCalls) {
                        recordBlockedTool(traceSession, step, decision.name, "TOOL_CALL_LIMIT")
                        return KellvynBrainResult.LimitReached(
                            "O limite de chamadas de ferramentas desta tarefa foi atingido.",
                            step,
                            toolCalls
                        )
                    }
                    toolCalls++

                    if (decision.name !in presentedToolNames) {
                        recordBlockedTool(traceSession, step, decision.name, "NOT_PRESENTED")
                        messages += observation(
                            "tool:${decision.name}",
                            false,
                            "A ferramenta solicitada não estava no catálogo apresentado ao KN nesta decisão. " +
                                "Ela não foi executada; consulte o inventário local para descobrir capacidades."
                        )
                        continue
                    }

                    val tool = tools.find(decision.name)
                    if (tool == null) {
                        recordBlockedTool(traceSession, step, decision.name, "NOT_REGISTERED")
                        messages += observation(
                            "tool:${decision.name}",
                            false,
                            "Ferramenta inexistente ou não registrada. Ferramentas disponíveis: ${tools.names().joinToString()}."
                        )
                        continue
                    }

                    val invalidArguments = tools.validate(tool, decision.arguments)
                    if (invalidArguments != null) {
                        recordBlockedTool(traceSession, step, decision.name, "INVALID_ARGUMENTS")
                        messages += observation(
                            "tool:${decision.name}",
                            false,
                            "Parâmetros recusados: $invalidArguments"
                        )
                        continue
                    }

                    val signature = tools.signature(tool, decision.arguments)
                    val repetitions = (repeatedCalls[signature] ?: 0) + 1
                    repeatedCalls[signature] = repetitions
                    if (repetitions > config.maxSameCallRepeats) {
                        recordBlockedTool(traceSession, step, decision.name, "REPEAT_LIMIT")
                        messages += observation(
                            "tool:${decision.name}",
                            false,
                            "Chamada repetida bloqueada para evitar um ciclo sem progresso."
                        )
                        continue
                    }

                    val allowed = try {
                        authorization.authorize(tool.spec, decision.arguments, normalizedGoal)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        false
                    }
                    if (!allowed) {
                        recordBlockedTool(traceSession, step, decision.name, "AUTHORIZATION_DENIED")
                        messages += observation(
                            "tool:${decision.name}",
                            false,
                            "Ação bloqueada: a política atual não autorizou esta ferramenta."
                        )
                        continue
                    }

                    if (tool.spec.risk !in setOf(
                            KellvynToolRisk.READ_ONLY,
                            KellvynToolRisk.NETWORK_READ
                        )
                    ) {
                        hasExecutedMutation = true
                    }
                    val result = try {
                        executeTool(
                            tool = tool,
                            arguments = decision.arguments,
                            requestId = "run-${step}-${toolCalls}-${tool.spec.name}",
                            traceSession = traceSession,
                            step = step
                        )
                    } catch (deferred: KellvynTaskDeferredException) {
                        return KellvynBrainResult.LimitReached(
                            "A ferramenta foi adiada pelo gerenciador de recursos (${deferred.reasonCode}). " +
                                "${deferred.message} Tente novamente após ${deferred.retryAfterMillis} ms.",
                            step,
                            toolCalls
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        KellvynToolResult(
                            success = false,
                            content = "A ferramenta falhou: ${safeMessage(error)}"
                        )
                    }

                    messages += observation(
                        source = "tool:${decision.name}",
                        success = result.success,
                        content = result.content.take(config.maxObservationChars)
                    )
                }
            }
        }

        return KellvynBrainResult.LimitReached(
            "A Kellvyn atingiu o limite de etapas sem concluir a tarefa.",
            config.maxSteps,
            toolCalls
        )
    }

    private suspend fun executeInference(
        history: List<KellvynAgentMessage>,
        requestId: String,
        traceSession: KellvynAgentLabTraceSession,
        step: Int
    ): InferenceResult {
        val startedAt = System.currentTimeMillis()
        traceSession.record(
            kind = KellvynAgentLabEventKind.MODEL_CALL_STARTED,
            step = step,
            outcome = KellvynAgentLabOutcome.STARTED
        )
        return try {
            val presentedTools = tools.descriptionsForPrompt(
                history,
                config.maxToolsInModelPrompt
            )
            val executeWithTimeout: suspend () -> KellvynModelDecision = {
                try {
                    withTimeout(config.modelTimeoutMs) {
                        inference.next(history, presentedTools)
                    }
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw IllegalStateException(
                        "O modelo neural excedeu o tempo de inferência permitido.",
                        timeout
                    )
                }
            }
            val broker = taskBroker
            val decision = if (broker == null) {
                executeWithTimeout()
            } else {
                broker.execute(
                    KellvynResourceRequest(
                        taskId = requestId.take(96),
                        workload = KellvynWorkloadClass.STANDARD,
                        estimatedWorkingSetBytes = 128L * MEBIBYTE
                    ),
                    operation = executeWithTimeout
                )
            }
            traceSession.record(
                kind = KellvynAgentLabEventKind.MODEL_DECISION,
                step = step,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
                outcome = KellvynAgentLabOutcome.SUCCEEDED,
                detailCode = decision.javaClass.simpleName.uppercase()
            )
            InferenceResult(
                decision = decision,
                presentedToolNames = presentedTools.map { it.name }.toSet()
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                traceSession.record(
                    kind = KellvynAgentLabEventKind.MODEL_CALL_FAILED,
                    step = step,
                    durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "CANCELLED"
                )
            }
            throw cancelled
        } catch (error: Exception) {
            traceSession.record(
                kind = KellvynAgentLabEventKind.MODEL_CALL_FAILED,
                step = step,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
                outcome = KellvynAgentLabOutcome.FAILED,
                detailCode = "MODEL_ERROR"
            )
            throw error
        }
    }

    private suspend fun executeTool(
        tool: KellvynAgentTool,
        arguments: Map<String, String>,
        requestId: String,
        traceSession: KellvynAgentLabTraceSession? = null,
        step: Int? = null
    ): KellvynToolResult {
        val startedAt = System.currentTimeMillis()
        traceSession?.record(
            kind = KellvynAgentLabEventKind.TOOL_STARTED,
            step = step,
            toolName = tool.spec.name,
            outcome = KellvynAgentLabOutcome.STARTED
        )
        return try {
            val result = if (tool is KellvynResourceManagedAgentTool) {
                tool.executeWithResourceControl(arguments)
            } else {
                val executeWithTimeout: suspend () -> KellvynToolResult = {
                    try {
                        withTimeout(minOf(tool.spec.timeoutMs, config.maxToolTimeoutMs)) {
                            tool.execute(arguments)
                        }
                    } catch (timeout: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        KellvynToolResult(
                            false,
                            "A ferramenta excedeu o tempo limite depois de obter os recursos necessários."
                        )
                    }
                }
                val broker = taskBroker
                if (broker == null) executeWithTimeout() else broker.execute(
                    KellvynResourceRequest(
                        taskId = requestId.take(96),
                        workload = tool.spec.workloadClass,
                        estimatedWorkingSetBytes = tool.spec.estimatedWorkingSetBytes
                    ),
                    operation = executeWithTimeout
                )
            }
            traceSession?.record(
                kind = KellvynAgentLabEventKind.TOOL_FINISHED,
                step = step,
                toolName = tool.spec.name,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
                outcome = if (result.success) KellvynAgentLabOutcome.SUCCEEDED
                    else KellvynAgentLabOutcome.FAILED,
                detailCode = if (result.success) "TOOL_OK" else "TOOL_REPORTED_FAILURE"
            )
            result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                traceSession?.record(
                    kind = KellvynAgentLabEventKind.TOOL_FINISHED,
                    step = step,
                    toolName = tool.spec.name,
                    durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "CANCELLED"
                )
            }
            throw cancelled
        } catch (error: Exception) {
            traceSession?.record(
                kind = KellvynAgentLabEventKind.TOOL_FINISHED,
                step = step,
                toolName = tool.spec.name,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
                outcome = KellvynAgentLabOutcome.FAILED,
                detailCode = "TOOL_EXCEPTION"
            )
            throw error
        }
    }

    private fun observation(source: String, success: Boolean, content: String) =
        KellvynAgentMessage(
            role = KellvynMessageRole.TOOL,
            trust = KellvynTrustLevel.UNTRUSTED,
            source = source,
            content = (if (success) "A FERRAMENTA RELATOU SUCESSO; AINDA NÃO FOI VERIFICADO INDEPENDENTEMENTE\n" else "RESULTADO DE ERRO\n") + content
        )

    private suspend fun recordBlockedTool(
        traceSession: KellvynAgentLabTraceSession,
        step: Int,
        requestedName: String,
        reasonCode: String
    ) {
        val safeName = requestedName.takeIf {
            it.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,63}"))
        }
        traceSession.record(
            kind = KellvynAgentLabEventKind.TOOL_FINISHED,
            step = step,
            toolName = safeName,
            outcome = KellvynAgentLabOutcome.BLOCKED,
            detailCode = if (safeName == null) "INVALID_TOOL_NAME" else reasonCode
        )
    }

    private fun safeMessage(error: Exception): String =
        (error.message ?: error.javaClass.simpleName).take(500)

    private companion object {
        const val MAX_AUTOMATED_PLAN_STEPS = 32
        const val MEBIBYTE = 1024L * 1024L
    }
}


private data class InferenceResult(
    val decision: KellvynModelDecision,
    val presentedToolNames: Set<String>
)

internal sealed class KellvynBrainResult {
    abstract val steps: Int
    abstract val toolCalls: Int
    data class Completed(val answer: String, override val steps: Int, override val toolCalls: Int) : KellvynBrainResult()
    data class Failed(val reason: String, override val steps: Int, override val toolCalls: Int) : KellvynBrainResult()
    data class LimitReached(val reason: String, override val steps: Int, override val toolCalls: Int) : KellvynBrainResult()
    data class Planned(val state: KellvynTaskState, val checkpoint: KellvynTaskCheckpointMetadata) : KellvynBrainResult() {
        override val steps: Int = state.statuses.values.count { it == KellvynStepStatus.SUCCEEDED }
        override val toolCalls: Int = state.attempts.values.sum()
    }
}
