package com.aster.companion.domain

sealed interface CompanionRecoveryDecision {
    data object OpenPrimary : CompanionRecoveryDecision
    data class Protected(val reason: CompanionRecoveryReason) : CompanionRecoveryDecision
}

enum class CompanionRecoveryReason {
    MissingKeyForExistingDatabase,
    MissingDatabaseAfterInitialization,
    EmptyDatabaseAfterInitialization
}

object CompanionRecoveryPolicy {
    fun shouldSuppressAutomaticSeeding(companionWasInitialized: Boolean, hasCharacters: Boolean): Boolean =
        companionWasInitialized && !hasCharacters

    fun decide(
        databaseExists: Boolean,
        keyRecordExists: Boolean,
        companionWasInitialized: Boolean
    ): CompanionRecoveryDecision = when {
        databaseExists && !keyRecordExists -> CompanionRecoveryDecision.Protected(CompanionRecoveryReason.MissingKeyForExistingDatabase)
        !databaseExists && companionWasInitialized -> CompanionRecoveryDecision.Protected(CompanionRecoveryReason.MissingDatabaseAfterInitialization)
        else -> CompanionRecoveryDecision.OpenPrimary
    }
}

enum class SnapshotReason {
    Daily,
    BeforeUpgrade,
    BeforeClearConversation,
    BeforeDeleteCharacter
}

data class SnapshotRetentionCandidate(
    val id: String,
    val createdAt: Long,
    val reason: SnapshotReason
)

object CompanionSnapshotRetentionPolicy {
    const val DayMillis = 24L * 60L * 60L * 1_000L

    fun retainedIds(snapshots: List<SnapshotRetentionCandidate>, now: Long): Set<String> {
        val recentCutoff = now - 7L * DayMillis
        val recent = snapshots.filter { it.createdAt >= recentCutoff }.mapTo(linkedSetOf()) { it.id }
        snapshots
            .filter { it.reason == SnapshotReason.BeforeUpgrade }
            .maxByOrNull { it.createdAt }
            ?.let { recent += it.id }
        return recent
    }
}
