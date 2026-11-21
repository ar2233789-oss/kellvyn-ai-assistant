package com.kellvyn.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAgentLimitsTest {
    @Test
    fun toolCallBudgetHasAClosedUpperBound() {
        assertFalse(KellvynAgentLimits.allowsToolCall(0))
        assertTrue(KellvynAgentLimits.allowsToolCall(1))
        assertTrue(KellvynAgentLimits.allowsToolCall(KellvynAgentLimits.MAX_TOOL_CALLS_PER_TURN))
        assertFalse(KellvynAgentLimits.allowsToolCall(KellvynAgentLimits.MAX_TOOL_CALLS_PER_TURN + 1))
    }

    @Test
    fun searchQueryMustBeBoundedAndNonTrivial() {
        assertFalse(KellvynAgentLimits.isValidSearchQuery(""))
        assertFalse(KellvynAgentLimits.isValidSearchQuery("x"))
        assertTrue(KellvynAgentLimits.isValidSearchQuery("LocalBrain"))
        assertFalse(KellvynAgentLimits.isValidSearchQuery("a".repeat(KellvynAgentLimits.MAX_SEARCH_QUERY_CHARS + 1)))
    }

    @Test
    fun onlyKnownTextAndCodeExtensionsCanBeRead() {
        assertTrue(KellvynAgentLimits.isSupportedTextFile("src/MainActivity.kt"))
        assertTrue(KellvynAgentLimits.isSupportedTextFile("docs/plan.md"))
        assertFalse(KellvynAgentLimits.isSupportedTextFile("build/app.apk"))
        assertFalse(KellvynAgentLimits.isSupportedTextFile("assets/image.png"))
    }
}
