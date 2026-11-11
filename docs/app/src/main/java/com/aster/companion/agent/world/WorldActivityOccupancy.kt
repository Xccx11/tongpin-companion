package com.aster.companion.agent.world

import com.aster.companion.agent.ActivityParticipantCodec

/** Extracts V3 confirmed-activity participants for state-update exclusion. */
object WorldActivityOccupancy {
    fun characterIds(activities: List<Pair<String, String>>): Set<String> = activities
        .asSequence()
        .filter { (activityId, _) -> activityId.startsWith(WORLD_ACTIVITY_PREFIX) }
        .flatMap { (_, participantsJson) -> ActivityParticipantCodec.decode(participantsJson).characterIds.asSequence() }
        .toSet()

    const val WORLD_ACTIVITY_PREFIX = "world-activity:"
}
