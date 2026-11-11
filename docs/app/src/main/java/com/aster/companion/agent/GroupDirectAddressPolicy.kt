package com.aster.companion.agent

import com.aster.companion.domain.GroupMemberContext

/** A direct question between members deserves one model response opportunity even if selection JSON is malformed. */
object GroupDirectAddressPolicy {
    fun questionedMemberId(
        body: String,
        previousSpeakerId: String,
        members: List<GroupMemberContext>
    ): String? {
        return members.firstOrNull { member ->
            member.id != previousSpeakerId && namesFor(member.name).any { name -> isDirectAddress(body, name) }
        }?.id
    }

    private fun isDirectAddress(body: String, name: String): Boolean {
        val trimmed = body.trimStart()
        if (trimmed.startsWith(name)) return true
        return Regex("(?:^|[，,、：:\\s])${Regex.escape(name)}(?=$|[，,。！？!?：:、\\s])")
            .containsMatchIn(body)
    }

    private fun namesFor(displayName: String): List<String> = buildList {
        displayName.trim().takeIf(String::isNotBlank)?.let(::add)
        Regex("[\\uFF08(]([^\\uFF08\\uFF09()]+)[\\uFF09)]").findAll(displayName).forEach { match ->
            match.groupValues.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
