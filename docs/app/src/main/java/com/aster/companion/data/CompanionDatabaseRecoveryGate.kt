package com.aster.companion.data

import android.content.Context
import com.aster.companion.domain.CompanionRecoveryDecision
import com.aster.companion.domain.CompanionRecoveryPolicy
import com.aster.companion.domain.CompanionRecoveryReason
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects an isolated empty store when the primary encrypted store cannot be
 * safely opened. The primary database is never replaced by this fallback.
 */
@Singleton
class CompanionDatabaseRecoveryGate @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lifecycle = context.getSharedPreferences(LIFECYCLE_PREFERENCES, Context.MODE_PRIVATE)

    init {
        // Older builds latched an intentionally empty store into recovery mode forever.
        lifecycle.edit().remove(KEY_RUNTIME_REASON).apply()
    }

    val decision: CompanionRecoveryDecision by lazy {
        CompanionRecoveryPolicy.decide(
            databaseExists = context.getDatabasePath(PRIMARY_DATABASE_NAME).exists(),
            keyRecordExists = DatabaseKeyProvider.hasStoredPassphrase(context),
            companionWasInitialized = lifecycle.getBoolean(KEY_EVER_INITIALIZED, false)
        )
    }

    val isProtected: Boolean get() = decision is CompanionRecoveryDecision.Protected
    val databaseName: String get() = if (isProtected) RECOVERY_DATABASE_NAME else PRIMARY_DATABASE_NAME
    val keyPreferenceName: String get() = if (isProtected) RECOVERY_KEY_PREFERENCE else DatabaseKeyProvider.DEFAULT_KEY_PREFERENCE
    val recoveryReason: CompanionRecoveryReason? get() =
        (decision as? CompanionRecoveryDecision.Protected)?.reason

    fun markInitialized() {
        if (!isProtected) lifecycle.edit().putBoolean(KEY_EVER_INITIALIZED, true).apply()
    }

    fun suppressAutomaticSeedingForInitializedEmptyStore(hasCharacters: Boolean): Boolean =
        CompanionRecoveryPolicy.shouldSuppressAutomaticSeeding(
            lifecycle.getBoolean(KEY_EVER_INITIALIZED, false),
            hasCharacters
        )

    companion object {
        const val PRIMARY_DATABASE_NAME = "tongpin_companion.db"
        private const val RECOVERY_DATABASE_NAME = "tongpin_companion_recovery_shell.db"
        private const val RECOVERY_KEY_PREFERENCE = "encrypted_companion_recovery_shell_key"
        private const val LIFECYCLE_PREFERENCES = "tongpin_companion_lifecycle"
        private const val KEY_EVER_INITIALIZED = "companion_ever_initialized"
        private const val KEY_RUNTIME_REASON = "runtime_recovery_reason"
    }
}
