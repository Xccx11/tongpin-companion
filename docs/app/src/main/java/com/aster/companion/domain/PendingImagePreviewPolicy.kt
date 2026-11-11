package com.aster.companion.domain

object PendingImagePreviewPolicy {
    fun shouldShow(imageUri: String?, persistedImageUris: Set<String>): Boolean =
        !imageUri.isNullOrBlank() && imageUri !in persistedImageUris
}
