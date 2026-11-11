package com.aster.companion.domain

object ImageConsentPolicy {
    fun requirementForModelChoice(isDirectUserPhotoRequest: Boolean): ConfirmationRequirement =
        ConfirmationRequirement.REQUIRED
}
