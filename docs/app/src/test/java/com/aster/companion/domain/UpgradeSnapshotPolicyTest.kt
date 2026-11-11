package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeSnapshotPolicyTest {
    @Test fun `requires a safe upgrade snapshot when none exists or it is stale`() {
        assertTrue(UpgradeSnapshotPolicy.isDue(lastCreatedAt = null, now = 1_000_000L))
        assertTrue(UpgradeSnapshotPolicy.isDue(lastCreatedAt = 1_000L, now = 30_000_000L))
    }

    @Test fun `does not churn snapshots while a recent safe point exists`() {
        assertFalse(UpgradeSnapshotPolicy.isDue(lastCreatedAt = 999_000L, now = 1_000_000L))
    }
}
