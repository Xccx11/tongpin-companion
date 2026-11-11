package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGenerationResponsePolicyTest {
    @Test
    fun `uses base64 png when the standard gateway returns a null url`() {
        val response = """{"data":[{"b64_json":"aGVsbG8=","url":null}]}"""

        assertEquals(
            "data:image/png;base64,aGVsbG8=",
            ImageGenerationResponsePolicy.imageReference(response)
        )
    }

    @Test
    fun `keeps a usable image url response`() {
        val response = """{"data":[{"url":"https://images.example/result.png"}]}"""

        assertEquals(
            "https://images.example/result.png",
            ImageGenerationResponsePolicy.imageReference(response)
        )
    }
}
