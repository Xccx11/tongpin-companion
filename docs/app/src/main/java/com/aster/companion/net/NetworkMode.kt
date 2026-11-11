package com.aster.companion.net

/** 网络模式：自动 / 只走内网 / 只走外网。 */
enum class NetworkMode(val id: String, val label: String) {
    AUTO("AUTO", "自动"),
    LAN("LAN", "内网"),
    WAN("WAN", "外网");

    companion object {
        fun of(raw: String?): NetworkMode = entries.firstOrNull { it.id == raw?.trim()?.uppercase() } ?: AUTO
    }
}
