package com.aster.companion.lite

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话层把「要出图」的 Scene 草稿推到 UI 的通道。
 * 保证所有生图（按钮 / 聊天里明确要图）都经过预览确认，而不是静默生成。
 */
@Singleton
class LiteScenePhotoBus @Inject constructor() {
    private val _request = MutableStateFlow<ScenePhotoRequest?>(null)
    val request: StateFlow<ScenePhotoRequest?> = _request.asStateFlow()

    fun publish(request: ScenePhotoRequest) {
        _request.value = request
    }

    fun clear() {
        _request.value = null
    }
}
