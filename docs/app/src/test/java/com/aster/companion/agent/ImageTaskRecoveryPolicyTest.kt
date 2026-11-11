package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTaskRecoveryPolicyTest {
    @Test
    fun `a known provider task is polled after a timeout instead of submitted again`() {
        assertEquals(
            ImageTaskRecoveryDecision.POLL_EXISTING,
            ImageTaskRecoveryPolicy.afterTimeout("provider-task-42")
        )
        assertFalse(ImageTaskRecoveryPolicy.maySubmitNewTask("provider-task-42"))
    }

    @Test
    fun `a task without a provider id may be submitted once`() {
        assertEquals(
            ImageTaskRecoveryDecision.SUBMIT_NEW,
            ImageTaskRecoveryPolicy.afterTimeout(null)
        )
        assertTrue(ImageTaskRecoveryPolicy.maySubmitNewTask(null))
    }

    @Test
    fun `existing provider task skips prompt planning`() {
        assertFalse(ImageTaskRecoveryPolicy.shouldPlanPrompt("provider-task-42"))
        assertTrue(ImageTaskRecoveryPolicy.shouldPlanPrompt(null))
    }
}
