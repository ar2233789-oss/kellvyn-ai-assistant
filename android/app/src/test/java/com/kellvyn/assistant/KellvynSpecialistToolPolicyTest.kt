package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynSpecialistToolPolicyTest {
    private val specs = listOf(
        KellvynToolSpec(
            name = "code_find_symbols",
            description = "Busca símbolos.",
            capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
        ),
        KellvynToolSpec(
            name = "device_resource_snapshot",
            description = "Lê recursos.",
            capabilities = setOf(KellvynStudioCapability.DEVICE_DIAGNOSTICS)
        ),
        KellvynToolSpec(
            name = "workspace_write_file",
            description = "Escreve arquivo.",
            risk = KellvynToolRisk.REVERSIBLE_WRITE,
            capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
        ),
        KellvynToolSpec(
            name = "unclassified_tool",
            description = "Sem capacidade declarada."
        )
    )

    @Test
    fun codeExplorerCanRequestCodeNavigationButNotDeviceDiagnostics() {
        assertEquals(
            setOf("code_find_symbols"),
            KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.CODE_EXPLORER,
                specs
            )
        )
    }

    @Test
    fun operationsAnalystReceivesDeviceDiagnosticsOnly() {
        assertEquals(
            setOf("device_resource_snapshot"),
            KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.OPERATIONS_ANALYST,
                specs
            )
        )
    }

    @Test
    fun aSpecialistCannotGrantItselfAWriteTool() {
        val denied = KellvynSpecialistToolPolicy.deniedRequestedNames(
            KellvynSpecialistRole.CODE_EXPLORER,
            setOf("workspace_write_file"),
            specs
        )
        assertEquals(setOf("workspace_write_file"), denied)
        assertFalse(
            "workspace_write_file" in KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.CODE_EXPLORER,
                specs
            )
        )
    }

    @Test
    fun unclassifiedToolsAreDeniedByDefault() {
        assertTrue(
            "unclassified_tool" !in KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.REVIEWER,
                specs
            )
        )
    }

    @Test
    fun researchRoleDoesNotInheritCodeNavigationWithoutAnExplicitCapability() {
        val permitted = KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
            KellvynSpecialistRole.RESEARCHER,
            specs
        )
        assertFalse("code_find_symbols" in permitted)
        assertFalse("workspace_write_file" in permitted)
    }
}
