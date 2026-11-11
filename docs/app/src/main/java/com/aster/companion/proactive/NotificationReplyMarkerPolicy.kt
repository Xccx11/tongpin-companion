package com.aster.companion.proactive

import java.security.MessageDigest

/** Creates a durable, content-free key for a notification that already received a reply. */
object NotificationReplyMarkerPolicy {
    fun key(source: String, summary: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$source\n$summary".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
