package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyUiProtocolTest {
    private val profile = ModelProfileEntity(
        id = "image_generation",
        kind = "image_generation",
        baseUrl = "http://10.0.2.2:8188/",
        modelName = "comfyui-qwen-image-edit-2509",
        enabled = true,
        timeoutSeconds = 120,
        encryptedApiKey = null,
        lastTestResult = null
    )

    @Test
    fun `recognizes the built in ComfyUI Qwen workflow`() {
        assertTrue(ComfyUiProtocol.supports(profile))
        assertEquals("http://10.0.2.2:8188/prompt", ComfyUiProtocol.promptEndpoint(profile))
        assertEquals(ComfyUiWorkflow.QWEN_IMAGE_EDIT, ComfyUiProtocol.workflow(profile))
    }

    @Test
    fun `selects the Illustrious reference workflow only for the Illustrious profile`() {
        val illustrious = profile.copy(modelName = ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME)

        assertTrue(ComfyUiProtocol.supports(illustrious))
        assertEquals(ComfyUiWorkflow.ILLUSTRIOUS_XL_REFERENCE, ComfyUiProtocol.workflow(illustrious))
    }

    @Test
    fun `requires the configured Illustrious checkpoint to be available in ComfyUI`() {
        val illustrious = profile.copy(modelName = ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME)
        val objectInfo = """
            {"CheckpointLoaderSimple":{"input":{"required":{"ckpt_name":[["Illustrious-XL-v2.0.safetensors"],{}]}}}}
        """.trimIndent()

        assertTrue(ComfyUiProtocol.requiredCheckpointIsAvailable(illustrious, objectInfo))
        assertFalse(ComfyUiProtocol.requiredCheckpointIsAvailable(illustrious, """{"CheckpointLoaderSimple":{"input":{"required":{"ckpt_name":[["other.safetensors"],{}]}}}}"""))
    }

    @Test
    fun `requires IPAdapter nodes for the Illustrious style reference workflow`() {
        assertTrue(
            ComfyUiProtocol.illustriousStyleWorkflowNodesAvailable(
                """{"IPAdapterUnifiedLoader":{},"IPAdapterAdvanced":{}}"""
            )
        )
        assertFalse(ComfyUiProtocol.illustriousStyleWorkflowNodesAvailable("""{"IPAdapterUnifiedLoader":{}}"""))
    }

    @Test
    fun `marks only the Illustrious workflow as requiring SDXL prompt syntax`() {
        val illustrious = profile.copy(modelName = ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME)

        assertTrue(ComfyUiProtocol.usesSdXlPromptSyntax(illustrious))
        assertTrue(!ComfyUiProtocol.usesSdXlPromptSyntax(profile))
    }

    @Test
    fun `uses an English SDXL production prompt only for Illustrious`() {
        val illustrious = profile.copy(modelName = ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME)

        assertTrue(ComfyUiPromptSyntax.draftRequirements(illustrious).contains("English"))
        assertTrue(ComfyUiPromptSyntax.draftRequirements(profile).contains("Chinese"))
    }

    @Test
    fun `normalizes the locally saved ComfyUI address`() {
        assertEquals("http://10.0.2.2:8188", ComfyUiProtocol.normalizedBaseUrl(" http://10.0.2.2:8188/ "))
    }

    @Test
    fun `accepts a Wi-Fi IPv4 address and supplies the ComfyUI default port`() {
        assertEquals("http://192.168.2.36:8188", ComfyUiProtocol.normalizedBaseUrl("192.168.2.36"))
        assertTrue(ComfyUiProtocol.isValidBaseUrl("192.168.2.36"))
    }

    @Test
    fun `does not replace a user entered IPv4 host`() {
        assertEquals("http://192.168.2.36:8188", ComfyUiProtocol.normalizedBaseUrl("192.168.2.36"))
    }

    @Test
    fun `builds a private output image URL from Comfy history`() {
        val history = """
            {"prompt-1":{"status":{"status_str":"success"},"outputs":{"60":{"images":[{"filename":"Companion_01.png","subfolder":"","type":"output"}]}}}}
        """.trimIndent()

        assertEquals(
            "http://10.0.2.2:8188/view?filename=Companion_01.png&subfolder=&type=output",
            ComfyUiProtocol.outputImageUrl(profile, "prompt-1", history)
        )
    }
}
