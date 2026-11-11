package com.aster.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

object KeyboardVisibleFieldPolicy {
    fun shouldBringIntoView(isFocused: Boolean, isImeVisible: Boolean): Boolean = isFocused && isImeVisible
}

/** Keeps the focused input visible above the keyboard in scrollable forms and dialogs. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun Modifier.keepVisibleWhenKeyboardOpened(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isFocused, isImeVisible) {
        if (KeyboardVisibleFieldPolicy.shouldBringIntoView(isFocused, isImeVisible)) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    this
        .bringIntoViewRequester(requester)
        .onFocusChanged { isFocused = it.isFocused }
}
