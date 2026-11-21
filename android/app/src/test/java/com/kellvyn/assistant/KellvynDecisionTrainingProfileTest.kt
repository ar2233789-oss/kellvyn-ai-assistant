package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynDecisionTrainingProfileTest {
    @Test
    fun defaultDecisionProfileRaisesCapacityWithinTheDeclaredMemoryBudget() {
        val config = KellvynDecisionTrainingProfile.requireWithinBudget()

        assertEquals(1_024, config.contextLength)
        assertEquals(128, config.embeddingSize)
        assertEquals(4, config.headCount)
        assertEquals(512, config.feedForwardSize)
        assertEquals(2, config.depth)
        assertEquals(768, config.maxMerges)
        assertTrue(
            KellvynDecisionTrainingProfile.estimatedWorkingSetBytes() <=
                KellvynDecisionTrainingProfile.MAX_WORKING_SET_BYTES
        )
    }
}
