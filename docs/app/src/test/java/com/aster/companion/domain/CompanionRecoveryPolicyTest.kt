package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRecoveryPolicyTest {
    @Test fun `an initialized empty store suppresses automatic defaults without entering permanent recovery`() {
        assertTrue(CompanionRecoveryPolicy.shouldSuppressAutomaticSeeding(companionWasInitialized = true, hasCharacters = false))
        assertFalse(CompanionRecoveryPolicy.shouldSuppressAutomaticSeeding(companionWasInitialized = false, hasCharacters = false))
        assertFalse(CompanionRecoveryPolicy.shouldSuppressAutomaticSeeding(companionWasInitialized = true, hasCharacters = true))
    }

    @Test
    fun `protects an existing database when its encryption key record is unavailable`() {
        val decision = CompanionRecoveryPolicy.decide(
            databaseExists = true,
            keyRecordExists = false,
            companionWasInitialized = true
        )

        assertEquals(CompanionRecoveryDecision.Protected(CompanionRecoveryReason.MissingKeyForExistingDatabase), decision)
    }

    @Test
    fun `protects a previously initialized companion when its database is missing`() {
        val decision = CompanionRecoveryPolicy.decide(
            databaseExists = false,
            keyRecordExists = false,
            companionWasInitialized = true
        )

        assertEquals(CompanionRecoveryDecision.Protected(CompanionRecoveryReason.MissingDatabaseAfterInitialization), decision)
    }

    @Test
    fun `allows first launch to initialize a companion store`() {
        val decision = CompanionRecoveryPolicy.decide(
            databaseExists = false,
            keyRecordExists = false,
            companionWasInitialized = false
        )

        assertEquals(CompanionRecoveryDecision.OpenPrimary, decision)
    }

    @Test
    fun `retains recent snapshots and the latest upgrade recovery point`() {
        val now = 1_000_000_000L
        val snapshots = listOf(
            SnapshotRetentionCandidate("today", now - 1_000L, SnapshotReason.Daily),
            SnapshotRetentionCandidate("recent-clear", now - 6 * CompanionSnapshotRetentionPolicy.DayMillis, SnapshotReason.BeforeClearConversation),
            SnapshotRetentionCandidate("expired", now - 8 * CompanionSnapshotRetentionPolicy.DayMillis, SnapshotReason.Daily),
            SnapshotRetentionCandidate("old-upgrade", now - 30 * CompanionSnapshotRetentionPolicy.DayMillis, SnapshotReason.BeforeUpgrade),
            SnapshotRetentionCandidate("latest-upgrade", now - 20 * CompanionSnapshotRetentionPolicy.DayMillis, SnapshotReason.BeforeUpgrade)
        )

        val retained = CompanionSnapshotRetentionPolicy.retainedIds(snapshots, now)

        assertTrue(retained.containsAll(listOf("today", "recent-clear", "latest-upgrade")))
        assertEquals(setOf("today", "recent-clear", "latest-upgrade"), retained)
    }
}
