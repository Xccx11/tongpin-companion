package com.aster.companion.data

import android.content.Context
import com.aster.companion.domain.CompanionSnapshotRetentionPolicy
import com.aster.companion.domain.SnapshotReason
import com.aster.companion.domain.UpgradeSnapshotPolicy
import com.aster.companion.domain.SnapshotRetentionCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps encrypted SQLCipher database copies inside app-private storage. They are
 * intentionally not exports: opening them still requires this app's Keystore key.
 */
@Singleton
class CompanionSnapshotManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CompanionDatabase,
    private val dao: CompanionDao,
    private val recoveryGate: CompanionDatabaseRecoveryGate
) {
    suspend fun create(reason: SnapshotReason): SnapshotManifestEntity = withContext(Dispatchers.IO) {
        check(!recoveryGate.isProtected) { "Recovery protection is active; the primary store will not be modified" }
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val source = context.getDatabasePath(CompanionDatabaseRecoveryGate.PRIMARY_DATABASE_NAME)
        check(source.exists() && source.length() > 0L) { "Companion database is unavailable for a recovery snapshot" }

        val createdAt = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val destination = snapshotDirectory().resolve("$createdAt-$id.db")
        source.copyTo(destination, overwrite = false)
        val manifest = SnapshotManifestEntity(
            id = id,
            reason = reason.name,
            createdAt = createdAt,
            databaseFileName = destination.name,
            byteSize = destination.length(),
            state = "READY"
        )
        dao.upsertSnapshotManifest(manifest)
        prune()
        manifest
    }

    suspend fun createDailyIfDue() = withContext(Dispatchers.IO) {
        if (recoveryGate.isProtected) return@withContext null
        val lastDaily = dao.allSnapshotManifests()
            .firstOrNull { it.reason == SnapshotReason.Daily.name }
        if (lastDaily != null && System.currentTimeMillis() - lastDaily.createdAt < CompanionSnapshotRetentionPolicy.DayMillis) return@withContext lastDaily
        create(SnapshotReason.Daily)
    }

    suspend fun createUpgradeSafetySnapshotIfDue() = withContext(Dispatchers.IO) {
        if (recoveryGate.isProtected) return@withContext null
        val now = System.currentTimeMillis()
        val lastUpgrade = dao.allSnapshotManifests()
            .firstOrNull { it.reason == SnapshotReason.BeforeUpgrade.name }
        if (!UpgradeSnapshotPolicy.isDue(lastUpgrade?.createdAt, now)) return@withContext lastUpgrade
        create(SnapshotReason.BeforeUpgrade)
    }

    suspend fun recoveryCandidates(): List<SnapshotManifestEntity> = withContext(Dispatchers.IO) {
        dao.allSnapshotManifests().filter { snapshotDirectory().resolve(it.databaseFileName).isFile }
    }

    private suspend fun prune() {
        val manifests = dao.allSnapshotManifests()
        val retained = CompanionSnapshotRetentionPolicy.retainedIds(
            manifests.mapNotNull { manifest ->
                runCatching { SnapshotReason.valueOf(manifest.reason) }.getOrNull()?.let { reason ->
                    SnapshotRetentionCandidate(manifest.id, manifest.createdAt, reason)
                }
            },
            System.currentTimeMillis()
        )
        manifests.filterNot { it.id in retained }.forEach { manifest ->
            snapshotDirectory().resolve(manifest.databaseFileName).delete()
            dao.deleteSnapshotManifest(manifest.id)
        }
    }

    private fun snapshotDirectory(): File = File(context.filesDir, SNAPSHOT_DIRECTORY).apply { mkdirs() }

    companion object {
        const val SNAPSHOT_DIRECTORY = "companion_snapshots"
    }
}
