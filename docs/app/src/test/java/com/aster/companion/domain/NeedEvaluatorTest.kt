package com.aster.companion.domain

import com.aster.companion.data.CharacterLifeStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedEvaluatorTest {

    private fun state(
        energy: Int = 80, stress: Int = 40, loneliness: Int = 30, socialNeed: Int = 40
    ) = CharacterLifeStateEntity(
        characterId = "linxi", energy = energy, stress = stress,
        loneliness = loneliness, socialNeed = socialNeed, updatedAt = 0L
    )

    @Test
    fun `high loneliness produces social need above threshold`() {
        val needs = NeedEvaluator.evaluate(state(loneliness = 90))
        val social = needs.firstOrNull { it.type == "social" }
        assertTrue(social != null)
        assertTrue(social!!.level >= NeedEvaluator.NEED_THRESHOLD)
    }

    @Test
    fun `low needs produce no candidates`() {
        val needs = NeedEvaluator.evaluate(state(energy = 90, stress = 10, loneliness = 5))
        assertTrue(needs.isEmpty())
    }

    @Test
    fun `high stress produces emotional need`() {
        val needs = NeedEvaluator.evaluate(state(stress = 95))
        assertTrue(needs.any { it.type == "emotional" })
    }

    @Test
    fun `hourly advance drains energy and grows loneliness when awake`() {
        val advanced = NeedEvaluator.advanceHourly(state(), hours = 3)
        assertTrue(advanced.energy < 80)
        assertTrue(advanced.loneliness > 30)
    }

    @Test
    fun `sleep hours restore energy`() {
        val advanced = NeedEvaluator.advanceHourly(state(energy = 30), hours = 7)
        assertEquals(100, advanced.energy)
    }

    @Test
    fun `relationship distance raises social need`() {
        val near = NeedEvaluator.evaluate(state(loneliness = 40), relationshipDistance = 10)
        val far = NeedEvaluator.evaluate(state(loneliness = 40), relationshipDistance = 90)
        val nearSocial = near.firstOrNull { it.type == "social" }?.level ?: 0
        val farSocial = far.firstOrNull { it.type == "social" }?.level ?: 0
        assertTrue(farSocial > nearSocial)
    }
}
