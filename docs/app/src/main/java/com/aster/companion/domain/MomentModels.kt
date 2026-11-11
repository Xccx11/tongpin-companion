package com.aster.companion.domain

object MomentActors {
    const val USER = "USER"
    const val CHARACTER = "CHARACTER"
    const val USER_ID = "user"
    const val PUBLIC = "ALL"
    const val SOURCE_USER = "USER"
    const val SOURCE_PROACTIVE = "PROACTIVE"
    const val SOURCE_GENERATED = "GENERATED"
    const val STATUS_PUBLISHED = "PUBLISHED"
    const val STATUS_GENERATING = "GENERATING"
    const val STATUS_FAILED = "FAILED"
    const val LIKE = "LIKE"
}

enum class MomentReactionDecision { LIKE, COMMENT, BOTH, IGNORE }
