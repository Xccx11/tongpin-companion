package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSendIntentPolicyTest {

    @Test
    fun `说的话里有发照片意图时命中`() {
        val replies = listOf(
            "我给你发一张照片",
            "我给你发一张关于窗外的树的照片",
            "发张自拍给你看看",
            "等我一下，我拍张照片给你",
            "这就发照片，你先等着",
            "（举起手机）给你看看我现在这样",
            "照片我发过去了，看到了吗",
            "我拍好了，发给你",
            "我挑了半天，这张照片给你",
            "我现在坐在窗边，发张给你",
            "刚拍完，这就传给你",
            "等我整理好就发你",
            "（对着镜头笑）我拍一张给你",
            "我给你发张照片，照片里有我新买的书",
            "发个自拍，等我一下",
            "我这就拍给你看",
            "等我一下，我发一张给你",
            "拍张给你嘛，就一张",
            "等我收拾完，给你发一张我现在的样子",
            "拍张我自己给你看看"
        )
        replies.forEach { reply ->
            assertTrue("应当判定为发照片：$reply", PhotoSendIntentPolicy.matches(reply))
        }
    }

    /**
     * 宽松口径：她话里提到别的话题（作业/资料/文件/地址）不能把发照片的意图整句否掉，
     * 更松的口语（我拍一张 / 刚拍了张 / 我现在去拍照）也要能弹卡。
     */
    @Test
    fun `顺带提到别的事或口语省略时仍然命中`() {
        val replies = listOf(
            "拍好了，这就发给你，作业写完了",
            "我把资料整理好就发你照片",
            "我发张照片给你，顺便说下文件的事",
            "我把地址发你，照片一起给你",
            "我拍一张",
            "等我拍完",
            "我这就拍",
            "刚拍了张好看的",
            "我现在去拍照",
            "发你看看",
            "给你看看我",
            "我拿起手机",
            "拍完啦，马上给你",
            "刚刚拍了张，你看看"
        )
        replies.forEach { reply ->
            assertTrue("宽松口径下应当判定为发照片：$reply", PhotoSendIntentPolicy.matches(reply))
        }
    }

    @Test
    fun `拒绝发图或非人物照不命中`() {
        val replies = listOf(
            "我今天不想发照片了",
            "我懒得拍自拍，别催了",
            "我发张截图给你看代码",
            "文件我发你邮箱了",
            "链接我发你了",
            "二维码扫一下",
            "图书馆那边挺安静的",
            "好呀，那我们晚上一起吃饭",
            "今天天气不错，我在阳台晒太阳",
            "明天记得叫我起床",
            "嗯",
            "好的呢"
        )
        replies.forEach { reply ->
            assertFalse("不该判定为发照片：$reply", PhotoSendIntentPolicy.matches(reply))
        }
    }
}
