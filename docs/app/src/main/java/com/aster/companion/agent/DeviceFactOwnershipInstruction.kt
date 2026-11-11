package com.aster.companion.agent

/** Keeps a companion's fictional inner life separate from the user's real device. */
object DeviceFactOwnershipInstruction {
    fun text(): String =
        "DEVICE-FACT OWNERSHIP: Every sensed life fact belongs only to the user and their Android phone. " +
            "This includes weather, location, battery, app activity, and media. You may notice, care about, or act on these facts, " +
            "but must not call it your own phone, your own battery, your own location, or your own music. " +
            "When referring to one, say the user's phone, the weather around the user, or what the user is listening to."
}
