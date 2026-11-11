package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRequestPolicyTest {
    @Test
    fun `explicitly asking the character for a photo starts image generation`() {
        assertTrue(ImageRequestPolicy.isExplicitPhotoRequest("给我发一张你的照片"))
        assertTrue(ImageRequestPolicy.isExplicitPhotoRequest("想看你自拍"))
        assertTrue(ImageRequestPolicy.isExplicitPhotoRequest("给我看看你"))
    }

    @Test
    fun `ordinary text does not start image generation`() {
        assertFalse(ImageRequestPolicy.isExplicitPhotoRequest("今天好累"))
    }

    @Test
    fun `ambiguous retry alone does not start image generation`() {
        assertFalse(ImageRequestPolicy.isExplicitPhotoRequest("再试试嘛"))
    }

    @Test
    fun `ambiguous acceptance only starts image generation after a photo offer`() {
        assertTrue(
            ImageRequestPolicy.isPhotoGenerationRequest(
                "那就试试嘛",
                "我刚拍了张照片，要不要发给你看看？"
            )
        )
        assertFalse(
            ImageRequestPolicy.isPhotoGenerationRequest(
                "那就试试嘛",
                "今晚要不要一起看个电影？"
            )
        )
    }

    @Test
    fun `mentioning a photo without asking for the character is reference only`() {
        // 判定拆成两层：isExplicitPhotoRequest 只看句子里有没有照片请求词（宽松），
        // 真正的排除交给 isPhotoReferenceOnly —— DeepSeekPhotoIntentClassifier 会先问它。
        assertTrue(ImageRequestPolicy.isExplicitPhotoRequest("我看到了你发的照片，挺好看"))
        assertTrue(ImageRequestPolicy.isPhotoReferenceOnly("我看到了你发的照片，挺好看"))
        assertTrue(ImageRequestPolicy.isPhotoReferenceOnly("我看到了你发的照片"))
        assertFalse(ImageRequestPolicy.isPhotoReferenceOnly("照片呢"))
    }

    @Test
    fun `explicit local request wins over a negative semantic classifier result`() {
        assertTrue(ImageRequestPolicy.shouldStartCharacterPhotoGeneration(localRequest = true, semanticIntent = false))
        assertTrue(ImageRequestPolicy.shouldStartCharacterPhotoGeneration(localRequest = false, semanticIntent = true))
        assertFalse(ImageRequestPolicy.shouldStartCharacterPhotoGeneration(localRequest = false, semanticIntent = false))
        assertFalse(ImageRequestPolicy.shouldStartCharacterPhotoGeneration(localRequest = false, semanticIntent = null))
    }
}
