package com.aster.companion.system

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionRequestValidatorTest {
    @Test fun `rejects capabilities outside the fixed schema`() {
        assertEquals("Unsupported capability", ActionRequestValidator.errorFor("device.free_shell", emptyMap()))
    }

    @Test fun `requires a valid phone number before a dial action can be queued`() {
        assertNull(ActionRequestValidator.errorFor("phone.dial", mapOf("phone_number" to "+8613812345678")))
        assertNotNull(ActionRequestValidator.errorFor("phone.dial", emptyMap()))
        assertNotNull(ActionRequestValidator.errorFor("phone.dial", mapOf("phone_number" to "bad;value")))
    }

    @Test fun `requires a safe package name before an app launch can be queued`() {
        assertNull(ActionRequestValidator.errorFor("app.open_known", mapOf("package_name" to "com.example.calendar")))
        assertNotNull(ActionRequestValidator.errorFor("app.open_known", mapOf("package_name" to "com.bank.wallet")))
    }

    @Test fun `requires an actual clock time before an alarm can be queued`() {
        assertNull(ActionRequestValidator.errorFor("device.set_alarm", mapOf("hour" to "7", "minute" to "30")))
        assertNotNull(ActionRequestValidator.errorFor("device.set_alarm", mapOf("hour" to "24", "minute" to "30")))
        assertNotNull(ActionRequestValidator.errorFor("device.set_alarm", mapOf("hour" to "7", "minute" to "60")))
    }

    @Test fun `allows schema owned device reads without invented arguments`() {
        assertNull(ActionRequestValidator.errorFor("device.read_battery", emptyMap()))
        assertNull(ActionRequestValidator.errorFor("device.read_foreground_app", emptyMap()))
    }

    @Test fun `allows only complete explicit cross conversation messages`() {
        assertNull(ActionRequestValidator.errorFor("companion.send_private_message", mapOf("character_id" to "xia", "message" to "I will message you there.")))
        assertNull(ActionRequestValidator.errorFor("companion.send_group_message", mapOf("character_id" to "xia", "group_thread_id" to "family", "message" to "I am here.")))
        assertNotNull(ActionRequestValidator.errorFor("companion.send_private_message", mapOf("character_id" to "xia")))
        assertNotNull(ActionRequestValidator.errorFor("companion.send_group_message", mapOf("character_id" to "xia", "message" to "missing destination")))
    }

    @Test fun `allows a complete moment image action but never a blank scene`() {
        assertNull(ActionRequestValidator.errorFor("companion.generate_moment_image", mapOf(
            "character_id" to "lan",
            "post_id" to "post-1",
            "scene" to "one young woman, black hair, reading beside a warm window, cinematic light"
        )))
        assertNotNull(ActionRequestValidator.errorFor("companion.generate_moment_image", mapOf(
            "character_id" to "lan",
            "post_id" to "post-1",
            "scene" to ""
        )))
    }

    @Test fun `image actions require a final plain English ComfyUI prompt`() {
        val validPrompt = "masterpiece, best quality, one young woman, silver hair, red eyes, moonlit garden"
        listOf(
            "companion.generate_image",
            "companion.propose_image",
            "companion.propose_private_image"
        ).forEach { capabilityId ->
            assertNull(ActionRequestValidator.errorFor(capabilityId, mapOf("scene" to validPrompt)))
            // Lite 解除了语言限制：中文 scene 同样合法（最终英文由 Scene 细则保证）。
            assertNull(ActionRequestValidator.errorFor(capabilityId, mapOf("scene" to "银发女孩站在月光花园里")))
            assertNotNull(ActionRequestValidator.errorFor(capabilityId, mapOf("scene" to "```json\n{\"scene\":\"portrait\"}\n```")))
        }

        assertNull(ActionRequestValidator.errorFor("companion.generate_moment_image", mapOf(
            "character_id" to "lan",
            "post_id" to "post-1",
            "scene" to "角色坐在窗边"
        )))
        assertNull(ActionRequestValidator.errorFor("companion.share_life_moment", mapOf(
            "character_id" to "lan",
            "scene" to validPrompt
        )))
    }
}
