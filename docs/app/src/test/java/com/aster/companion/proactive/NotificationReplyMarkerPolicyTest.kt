package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationReplyMarkerPolicyTest {
    @Test fun `same notification content always produces the same private marker key`() {
        assertEquals(
            NotificationReplyMarkerPolicy.key("notification:parcel", "Parcel delivered"),
            NotificationReplyMarkerPolicy.key("notification:parcel", "Parcel delivered")
        )
    }

    @Test fun `changed notification content produces a different marker key`() {
        assertNotEquals(
            NotificationReplyMarkerPolicy.key("notification:parcel", "Parcel delivered"),
            NotificationReplyMarkerPolicy.key("notification:parcel", "Parcel placed in locker")
        )
    }
}
