package com.aster.companion.agent

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ComfyIllustriousStyleWorkflowTest {
    @Test
    fun `Illustrious workflow applies the style reference through IPAdapter`() {
        val workflow = Json.parseToJsonElement(
            File("src/main/res/raw/comfy_illustrious_xl_reference.json").readText()
        ).jsonObject

        assertEquals("LoadImage", workflow.getValue("79").jsonObject.getValue("class_type").jsonPrimitive.content)
        assertEquals("IPAdapterUnifiedLoader", workflow.getValue("94").jsonObject.getValue("class_type").jsonPrimitive.content)
        assertEquals("IPAdapterAdvanced", workflow.getValue("95").jsonObject.getValue("class_type").jsonPrimitive.content)
        assertEquals(
            "95",
            workflow.getValue("3").jsonObject.getValue("inputs").jsonObject.getValue("model").jsonArray[0].jsonPrimitive.content
        )
    }
}
