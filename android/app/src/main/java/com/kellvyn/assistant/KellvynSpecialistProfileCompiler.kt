package com.kellvyn.assistant

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class KellvynCompiledSpecialistProfile(
    val role: KellvynSpecialistRole,
    val version: Int,
    val profileSha256: String,
    val authorizedCapabilities: Set<KellvynStudioCapability>,
    val maxSteps: Int,
    val maxToolCalls: Int,
    val maxContextChars: Int,
    val systemInstruction: String
)

internal data class KellvynProfileCompilationReport(
    val profiles: List<KellvynCompiledSpecialistProfile>,
    val errors: List<String>
) {
    val success: Boolean get() = errors.isEmpty() && profiles.size == KellvynSpecialistRole.values().size
}

/**
 * Deterministic compiler for internal role profiles.
 *
 * Permissions are resolved from the central policy, never from profile content. The compiler
 * emits a content hash for audit/reproducibility but does not publish or activate new profiles.
 */
internal class KellvynSpecialistProfileCompiler {
    fun compileAll(
        profiles: List<KellvynSpecialistProfile>
    ): KellvynProfileCompilationReport {
        val errors = mutableListOf<String>()
        val duplicateRoles = profiles.groupingBy { it.role }.eachCount()
            .filterValues { it > 1 }.keys
        duplicateRoles.forEach { errors += "Papel duplicado: ${it.name}." }

        val unique = profiles.distinctBy { it.role }
        val missing = KellvynSpecialistRole.values().toSet() - unique.map { it.role }.toSet()
        missing.forEach { errors += "Perfil ausente: ${it.name}." }

        val compiled = unique.mapNotNull { profile ->
            val profileErrors = validate(profile)
            if (profileErrors.isNotEmpty()) {
                errors += profileErrors
                null
            } else {
                compile(profile)
            }
        }.sortedBy { it.role.name }

        return KellvynProfileCompilationReport(
            profiles = compiled,
            errors = errors.sorted()
        )
    }

    fun validate(profile: KellvynSpecialistProfile): List<String> {
        val errors = mutableListOf<String>()
        val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(profile.role)
        if (capabilities.isEmpty()) errors += "O papel ${profile.role.name} não possui capacidades na política central."
        if (profile.allowMutation) errors += "O perfil ${profile.role.name} solicita mutação."
        if (profile.allowDelegation) errors += "O perfil ${profile.role.name} solicita delegação aninhada."
        if (profile.maxSteps !in 1..8) errors += "Orçamento de passos inválido para ${profile.role.name}."
        if (profile.maxToolCalls !in 0..6) errors += "Orçamento de ferramentas inválido para ${profile.role.name}."
        if (profile.maxContextChars !in 1_024..24_000) errors += "Orçamento de contexto inválido para ${profile.role.name}."
        if (profile.requiredOutputSections.size !in 3..8) errors += "Contrato de saída incompleto para ${profile.role.name}."
        return errors
    }

    fun compile(profile: KellvynSpecialistProfile): KellvynCompiledSpecialistProfile? {
        if (validate(profile).isNotEmpty()) return null
        val capabilities = KellvynSpecialistToolPolicy.capabilitiesFor(profile.role)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalContent(profile, capabilities).toByteArray(StandardCharsets.UTF_8))
        val hash = digest.joinToString("") { byte -> "%02x".format(byte) }
        return KellvynCompiledSpecialistProfile(
            role = profile.role,
            version = profile.version,
            profileSha256 = hash,
            authorizedCapabilities = capabilities,
            maxSteps = profile.maxSteps,
            maxToolCalls = profile.maxToolCalls,
            maxContextChars = profile.maxContextChars,
            systemInstruction = profile.systemInstruction()
        )
    }

    private fun canonicalContent(
        profile: KellvynSpecialistProfile,
        capabilities: Set<KellvynStudioCapability>
    ): String = listOf(
        profile.role.name,
        profile.version.toString(),
        profile.displayName,
        profile.mission,
        profile.instruction,
        profile.requiredOutputSections.joinToString("\u001f"),
        profile.maxSteps.toString(),
        profile.maxToolCalls.toString(),
        profile.maxContextChars.toString(),
        profile.allowDelegation.toString(),
        profile.allowMutation.toString(),
        capabilities.map { it.name }.sorted().joinToString(",")
    ).joinToString("\u001e")
}
