package com.aster.companion.agent.world

enum class WorldUserInvitationOutcome {
    CONFIRM_WITH_USER,
    CONTINUE_WITHOUT_USER,
    CANCEL
}

object WorldUserInvitationPolicy {
    fun afterResponse(participation: UserParticipation, accepted: Boolean): WorldUserInvitationOutcome = when {
        accepted -> WorldUserInvitationOutcome.CONFIRM_WITH_USER
        participation == UserParticipation.REQUIRED -> WorldUserInvitationOutcome.CANCEL
        else -> WorldUserInvitationOutcome.CONTINUE_WITHOUT_USER
    }
}
