package com.aster.companion.agent

/** Gives the role a concrete action vocabulary; local code only validates a requested action. */
object CharacterCapabilityPrompt {
    fun render(
        characterId: String,
        capabilityIds: Set<String>,
        availableGroupTargets: List<String>,
        isGroupChat: Boolean
    ): String = buildString {
        appendLine("ROLE ACTIONS: These are things you can genuinely do inside Tongpin right now. Treat them as your own available ways of acting, not as fictional promises.")
        appendLine("Your character_id is $characterId. Use this exact id whenever an action needs character_id.")
        appendLine("When the user clearly asks you to do an available action, EXECUTE THE ACTION in this response. do not merely promise, explain a limitation you do not have, say you will do it later, or replace the action with a vague chat reply.")
        appendLine("Only use an action when it genuinely fits the user's request. Never invent a completed result: the app will report the real result after execution.")

        if ("companion.send_group_message" in capabilityIds && availableGroupTargets.isNotEmpty()) {
            appendLine("GROUP MESSAGE: You can speak in one of your existing groups from this private chat with companion.send_group_message.")
            appendLine("When the current visible group context gives you a real reason to contribute there, you may decide to call companion.send_group_message yourself without waiting for the user. Use it only for a new, relevant in-character group message; ordinary private-chat small talk is not a reason. When the user explicitly asks you to ask, say, reply, pass something on, or speak in a group, call it immediately. Set character_id=$characterId, select exactly one group_thread_id from the allowed list below, and write the exact natural in-character message that belongs in that group. Do not merely promise that you will go there.")
            appendLine("Allowed group destinations. Do not use any other id:")
            availableGroupTargets.forEach(::appendLine)
        }

        if (isGroupChat && "companion.send_private_message" in capabilityIds) {
            appendLine("PRIVATE MESSAGE: From this group, you can privately message the user with companion.send_private_message. When the user clearly asks you to private-message them, call it with character_id=$characterId and the natural private message. Do not only agree in the group.")
        }
        if ("companion.generate_image" in capabilityIds || "companion.propose_image" in capabilityIds) {
            appendLine("IMAGE: You can create a photo through companion.generate_image when the user explicitly asks to see you, or companion.propose_image when you want to offer one first. For your own initiative, call companion.propose_image, ask the user before generating, and let the confirmation card decide whether the image starts. Put the complete current visual moment in scene. Preserve the user's concrete request and use only yourself unless the group context explicitly calls for other named members.")
        }
        if ("device.set_alarm" in capabilityIds) appendLine("ALARM: You can prepare an Android alarm with device.set_alarm. It requires user confirmation before it is written.")
        if ("device.read_battery" in capabilityIds) appendLine("BATTERY: You can read the user's current device battery with device.read_battery when it is relevant.")
        if ("device.read_foreground_app" in capabilityIds) appendLine("FOREGROUND APP: You can read the user's current foreground app with device.read_foreground_app when it is relevant.")
        if ("app.open_known" in capabilityIds) appendLine("OPEN APP: You can prepare opening an allowed installed app with app.open_known after confirmation.")
        if ("phone.dial" in capabilityIds) appendLine("CALL: You can prepare a dial action with phone.dial after confirmation.")
        if ("sms.compose" in capabilityIds) appendLine("SMS: You can prepare an SMS composer with sms.compose after confirmation.")

        appendLine("ACTION OUTPUT: Prefer the provider's native function call. If native function calling is unavailable, output only this JSON object for an action: {\"decision\":\"tool_calls\",\"message\":\"your natural reply\",\"tool_calls\":[{\"capability_id\":\"exact capability id\",\"transport\":\"local or shizuku\",\"confirmation\":\"none or required\",\"arguments\":{}}]}. Do not wrap it in Markdown. If no action is needed, respond naturally as yourself.")
    }
}
