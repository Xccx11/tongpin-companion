package com.aster.companion.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps a companion photo with the diary page for the character and day it was sent. */
object DiaryPhotoPolicy {
    fun belongsToDiary(
        photoCharacterId: String,
        diaryCharacterId: String?,
        photoTimestamp: Long,
        diaryTimestamp: Long
    ): Boolean = photoCharacterId == diaryCharacterId && dateKey(photoTimestamp) == dateKey(diaryTimestamp)

    fun dateKey(timestamp: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))
}
