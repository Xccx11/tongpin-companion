package com.aster.companion.system

/** Maps approved capability IDs to fixed command vectors. No model-generated shell text is accepted. */
object FixedShizukuCommand {
    fun forAction(capabilityId: String, argument: String?): List<String> = when (capabilityId) {
        "device.read_battery" -> listOf("dumpsys", "battery")
        "device.read_foreground_app" -> listOf("dumpsys", "activity", "activities")
        "device.set_alarm" -> {
            val parts = requireNotNull(argument).split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()
            val minute = parts.getOrNull(1)?.toIntOrNull()
            require(parts.size == 2 && hour != null && hour in 0..23 && minute != null && minute in 0..59) { "Invalid alarm time" }
            listOf("am", "start", "-a", "android.intent.action.SET_ALARM", "--ei", "android.intent.extra.alarm.HOUR", hour.toString(), "--ei", "android.intent.extra.alarm.MINUTES", minute.toString(), "--ez", "android.intent.extra.alarm.SKIP_UI", "true")
        }
        "app.open_known" -> {
            require(!argument.isNullOrBlank() && PackageNamePolicy.isAllowed(argument)) { "Blocked target package" }
            listOf("monkey", "-p", argument, "1")
        }
        "phone.dial" -> {
            require(!argument.isNullOrBlank() && PhoneNumberPolicy.isAllowed(argument)) { "Invalid dial target" }
            listOf("am", "start", "-a", "android.intent.action.DIAL", "-d", "tel:$argument")
        }
        "sms.compose" -> {
            require(!argument.isNullOrBlank() && PhoneNumberPolicy.isAllowed(argument)) { "Invalid SMS target" }
            listOf("am", "start", "-a", "android.intent.action.SENDTO", "-d", "smsto:$argument")
        }
        else -> throw IllegalArgumentException("Unsupported capability")
    }
}
