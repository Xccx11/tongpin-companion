package com.aster.companion.agent

object PlannerMessagePolicy {
    fun select(planned: String, rendered: String?): String = rendered?.trim().takeUnless { it.isNullOrBlank() } ?: planned.trim()
}
