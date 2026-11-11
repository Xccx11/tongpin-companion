package com.aster.companion.system

/** User-facing action outcomes intentionally exclude provider, command, and transport details. */
object CompanionActionMessagePolicy {
    fun failure(capabilityId: String): String = when (capabilityId) {
        "device.set_alarm" -> "刚才没能把闹钟设好，我们晚点再试一次。"
        "app.open_known" -> "刚才没能帮你打开它，我们晚点再试一次。"
        "phone.dial" -> "刚才没能打开拨号界面，我们晚点再试一次。"
        "sms.compose" -> "刚才没能打开短信界面，我们晚点再试一次。"
        "companion.generate_image", "companion.propose_image" -> "这次照片没拍成，我晚点再试给你。"
        else -> "刚才这件事没办成，我们晚点再试一次。"
    }
}
