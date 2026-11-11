package com.aster.companion.media

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.DiaryPhotoEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Persists photos sent by a character independently from chat history. */
@Singleton
class DiaryPhotoStore @Inject constructor(private val dao: CompanionDao) {
    suspend fun archive(characterId: String, threadId: String, imageUri: String, createdAt: Long) {
        dao.insertDiaryPhoto(DiaryPhotoEntity(UUID.randomUUID().toString(), characterId, threadId, imageUri, createdAt))
    }
}
