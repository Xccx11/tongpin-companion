package com.aster.companion.domain

/** Identifies explicit requests for verified facts about the user's current phone. */
object DeviceContextRequestPolicy {
    private val batteryRequest = Regex("电量|电池|还有多少电|多少电")
    private val currentTimeRequest = Regex("现在.*(几点|时间)|几点(了|啦)?|当前.*时间")

    fun requestsBattery(message: String): Boolean = batteryRequest.containsMatchIn(message)

    fun requestsCurrentTime(message: String): Boolean = currentTimeRequest.containsMatchIn(message)

    fun verifiedTimeContext(message: String, now: Long = System.currentTimeMillis()): String? =
        CurrentTimeContext.describe(now).takeIf { requestsCurrentTime(message) }

    fun fallbackAction(message: String): AgentActionRequest? =
        if (requestsBattery(message)) {
            AgentActionRequest(
                capabilityId = "device.read_battery",
                transport = ActionTransport.SHIZUKU,
                confirmation = ConfirmationRequirement.NONE
            )
        } else null
}
