package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSettingsPresentationPolicyTest {
    @Test
    fun `conversation editor leaves sampling to the service and owns the thinking switch`() {
        assertFalse(ModelSettingsPresentationPolicy.showConversationSamplingControls)
        val notice = ModelSettingsPresentationPolicy.conversationParametersNotice
        assertTrue(notice.contains("采样参数"))
        assertTrue(notice.contains("模型服务"))
        assertTrue("思考模式必须是 App 控制的", notice.contains("思考模式") && notice.contains("App 控制"))
    }

    @Test
    fun `documented gateway defaults remain configurable instead of hardcoding the ngrok host`() {
        assertEquals("qwen35-nothink:latest", ModelSettingsPresentationPolicy.documentedConversationModel)
        assertEquals("wai-nsfw-illustrious", ModelSettingsPresentationPolicy.documentedImageModel)
        assertEquals("768x768", ModelSettingsPresentationPolicy.documentedImageSize)
        assertTrue(ModelSettingsPresentationPolicy.gatewayConnectionNotice.contains("Base URL"))
        assertTrue(ModelSettingsPresentationPolicy.gatewayConnectionNotice.contains("任意非空"))
    }
}
