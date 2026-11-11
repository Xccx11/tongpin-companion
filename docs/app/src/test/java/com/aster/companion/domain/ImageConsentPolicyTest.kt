package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageConsentPolicyTest {
    @Test
    fun `an image the companion initiates requires approval even when the model chooses it`() {
        assertEquals(
            ConfirmationRequirement.REQUIRED,
            ImageConsentPolicy.requirementForModelChoice(isDirectUserPhotoRequest = false)
        )
    }

    @Test
    fun `a direct user photo request also requires confirmation`() {
        assertEquals(
            ConfirmationRequirement.REQUIRED,
            ImageConsentPolicy.requirementForModelChoice(isDirectUserPhotoRequest = true)
        )
    }
}
