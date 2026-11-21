package com.kellvyn.assistant

/**
 * Static, fail-closed capability matrix for specialist delegation.
 *
 * A model may request a smaller subset, but it cannot grant a role a capability absent from
 * this policy. Risk filtering is still performed independently by KellvynToolRegistry.
 */
internal object KellvynSpecialistToolPolicy {
    private val roleCapabilities: Map<KellvynSpecialistRole, Set<KellvynStudioCapability>> = mapOf(
        KellvynSpecialistRole.RESEARCHER to setOf(
            KellvynStudioCapability.WEB_RESEARCH,
            KellvynStudioCapability.DATA_MODELING
        ),
        KellvynSpecialistRole.CODE_EXPLORER to setOf(
            KellvynStudioCapability.CODE_NAVIGATION,
            KellvynStudioCapability.DATA_MODELING
        ),
        KellvynSpecialistRole.REVIEWER to setOf(
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.CODE_NAVIGATION,
            KellvynStudioCapability.SECURITY_AUDIT,
            KellvynStudioCapability.DATA_MODELING,
            KellvynStudioCapability.UI_LAYOUT
        ),
        KellvynSpecialistRole.TEST_ANALYST to setOf(
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.ANDROID_BUILD,
            KellvynStudioCapability.ANDROID_DEVICE_TEST,
            KellvynStudioCapability.DEVICE_DIAGNOSTICS
        ),
        KellvynSpecialistRole.REASONING_ANALYST to setOf(
            KellvynStudioCapability.DATA_MODELING,
            KellvynStudioCapability.DEVICE_DIAGNOSTICS
        ),
        KellvynSpecialistRole.LEARNING_ANALYST to setOf(
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.NEURAL_TRAINING,
            KellvynStudioCapability.NEURAL_INFERENCE,
            KellvynStudioCapability.DATA_MODELING
        ),
        KellvynSpecialistRole.PROGRAMMING_ARCHITECT to setOf(
            KellvynStudioCapability.AGENT_DESIGN,
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.CODE_NAVIGATION,
            KellvynStudioCapability.UI_LAYOUT,
            KellvynStudioCapability.DATA_MODELING
        ),
        KellvynSpecialistRole.MEMORY_ANALYST to setOf(
            KellvynStudioCapability.MEMORY_READ
        ),
        KellvynSpecialistRole.SECURITY_ANALYST to setOf(
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.SECURITY_AUDIT,
            KellvynStudioCapability.CODE_NAVIGATION,
            KellvynStudioCapability.ANDROID_BUILD
        ),
        KellvynSpecialistRole.PLANNING_ANALYST to setOf(
            KellvynStudioCapability.AGENT_DESIGN,
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.WORKFLOW_AUTOMATION,
            KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS,
            KellvynStudioCapability.BOT_DIAGNOSTICS,
            KellvynStudioCapability.DEVICE_DIAGNOSTICS,
            KellvynStudioCapability.DATA_MODELING
        ),
        KellvynSpecialistRole.DEBUGGER to setOf(
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.CODE_NAVIGATION,
            KellvynStudioCapability.ANDROID_BUILD,
            KellvynStudioCapability.ANDROID_DEVICE_TEST,
            KellvynStudioCapability.DEVICE_DIAGNOSTICS
        ),
        KellvynSpecialistRole.AUDITOR to setOf(
            KellvynStudioCapability.AGENT_DESIGN,
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.CODE_NAVIGATION,
            KellvynStudioCapability.ANDROID_BUILD,
            KellvynStudioCapability.ANDROID_DEVICE_TEST,
            KellvynStudioCapability.VISUAL_ANALYSIS,
            KellvynStudioCapability.SECURITY_AUDIT,
            KellvynStudioCapability.DATA_MODELING
        ),
        KellvynSpecialistRole.OPERATIONS_ANALYST to setOf(
            KellvynStudioCapability.AGENT_EVALUATION,
            KellvynStudioCapability.DEVICE_DIAGNOSTICS,
            KellvynStudioCapability.WORKFLOW_AUTOMATION,
            KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS,
            KellvynStudioCapability.BOT_DIAGNOSTICS
        ),
        KellvynSpecialistRole.VISUAL_ANALYST to setOf(
            KellvynStudioCapability.VISUAL_ANALYSIS,
            KellvynStudioCapability.UI_LAYOUT,
            KellvynStudioCapability.RASTER_IMAGE_EDIT,
            KellvynStudioCapability.VECTOR_GRAPHICS,
            KellvynStudioCapability.SCENE_3D,
            KellvynStudioCapability.CAD_PARAMETRIC
        )
    )

    fun capabilitiesFor(role: KellvynSpecialistRole): Set<KellvynStudioCapability> =
        roleCapabilities[role].orEmpty()

    fun permittedReadOnlyToolNames(
        role: KellvynSpecialistRole,
        specs: List<KellvynToolSpec>
    ): Set<String> = permittedToolNames(role, specs, allowNetworkRead = false)

    /**
     * Network reads are a separate risk class. Only the Researcher may receive a tool that
     * declares WEB_RESEARCH, and only when the owner-controlled web-access switch is enabled.
     */
    fun permittedToolNames(
        role: KellvynSpecialistRole,
        specs: List<KellvynToolSpec>,
        allowNetworkRead: Boolean
    ): Set<String> {
        val capabilities = capabilitiesFor(role)
        if (capabilities.isEmpty()) return emptySet()
        return specs.asSequence()
            .filter { spec ->
                spec.risk == KellvynToolRisk.READ_ONLY ||
                    (
                        allowNetworkRead &&
                            spec.risk == KellvynToolRisk.NETWORK_READ &&
                            KellvynStudioCapability.WEB_RESEARCH in spec.capabilities &&
                            KellvynStudioCapability.WEB_RESEARCH in capabilities
                        )
            }
            .filter { it.capabilities.isNotEmpty() && it.capabilities.any { capability ->
                capability in capabilities
            } }
            .map { it.name }
            .toSet()
    }

    fun deniedRequestedNames(
        role: KellvynSpecialistRole,
        requestedNames: Set<String>,
        specs: List<KellvynToolSpec>,
        allowNetworkRead: Boolean = false
    ): Set<String> = requestedNames - permittedToolNames(role, specs, allowNetworkRead)
}
