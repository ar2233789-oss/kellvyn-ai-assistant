package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynNetworkReadAuthorizationTest {

    @Test
    fun networkReadRequiresOwnerToggleAndWebResearchCapability() = runBlocking {
        var enabled = false
        val authorization = KellvynNetworkReadAuthorization(
            delegate = ReadOnlyKellvynAuthorization,
            networkReadEnabled = { enabled }
        )
        val webTool = spec(
            risk = KellvynToolRisk.NETWORK_READ,
            capabilities = setOf(KellvynStudioCapability.WEB_RESEARCH)
        )

        assertFalse(authorization.authorize(webTool, emptyMap(), "pesquisar"))
        enabled = true
        assertTrue(authorization.authorize(webTool, emptyMap(), "pesquisar"))

        val wrongCapability = spec(
            risk = KellvynToolRisk.NETWORK_READ,
            capabilities = setOf(KellvynStudioCapability.DEVICE_DIAGNOSTICS)
        )
        assertFalse(authorization.authorize(wrongCapability, emptyMap(), "diagnosticar"))
    }

    @Test
    fun networkConsentDoesNotAuthorizeWritesOrDeviceControl() = runBlocking {
        val authorization = KellvynNetworkReadAuthorization(
            delegate = ReadOnlyKellvynAuthorization,
            networkReadEnabled = { true }
        )

        assertFalse(
            authorization.authorize(
                spec(
                    risk = KellvynToolRisk.REVERSIBLE_WRITE,
                    capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
                ),
                emptyMap(),
                "editar"
            )
        )
        assertFalse(
            authorization.authorize(
                spec(
                    risk = KellvynToolRisk.DEVICE_CONTROL,
                    capabilities = setOf(KellvynStudioCapability.ANDROID_DEVICE_TEST)
                ),
                emptyMap(),
                "controlar dispositivo"
            )
        )
        assertTrue(
            authorization.authorize(
                spec(
                    risk = KellvynToolRisk.READ_ONLY,
                    capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
                ),
                emptyMap(),
                "ler"
            )
        )
    }

    private fun spec(
        risk: KellvynToolRisk,
        capabilities: Set<KellvynStudioCapability>
    ) = KellvynToolSpec(
        name = "network_or_local_tool",
        description = "Spec de teste.",
        risk = risk,
        capabilities = capabilities
    )
}
