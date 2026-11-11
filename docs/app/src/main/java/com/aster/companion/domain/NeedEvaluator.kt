package com.aster.companion.domain

import com.aster.companion.data.CharacterLifeStateEntity

/**
 * v2.1 需求计算系统：角色先产生需求，再产生行为（DeepSeek 只从候选行为选，规则由代码定）。
 * 需求类型：娱乐 entertainment / 社交 social / 情绪 emotional / 家庭 family。
 * 公式（项目书 §五）：social_need = loneliness*0.5 + relationship_distance*0.3 + free_time*0.2
 * 超过阈值（默认 60）的需求才进入行为候选。
 */
data class CharacterNeed(
    val type: String,          // entertainment / social / emotional / family
    val level: Int,            // 0-100
    val reason: String
)

object NeedEvaluator {
    /** 需求阈值：超过才产生行为候选。 */
    const val NEED_THRESHOLD = 60
    /** 基础需求衰减/恢复速率（每 tick 的分值变化，代码控制数值）。 */
    const val ENERGY_DRAIN_PER_HOUR = 4
    const val LONELINESS_GROW_PER_HOUR = 3
    const val STRESS_DECAY_PER_HOUR = 2

    /**
     * 计算角色当前需求。relationshipDistance 为对目标角色的关系距离（0 亲近 ~ 100 疏远）；
     * freeTime 是否空闲（不在重要任务/睡眠中）。
     */
    fun evaluate(
        state: CharacterLifeStateEntity,
        relationshipDistance: Int = 40,
        freeTime: Boolean = true,
        familyNearby: Boolean = false
    ): List<CharacterNeed> {
        val needs = mutableListOf<CharacterNeed>()

        // 社交需求（项目书公式）
        val social = (
            state.loneliness * 0.5 +
                relationshipDistance * 0.3 +
                if (freeTime) 20 else 0
            ).toInt().coerceIn(0, 100)
        if (social >= NEED_THRESHOLD) {
            needs += CharacterNeed("social", social, "今天和对方交流较少，有点想找人说话")
        }

        // 娱乐需求：能量低 + 压力高 → 想放松
        val entertainment = ((100 - state.energy) / 2 + state.stress / 3).toInt().coerceIn(0, 100)
        if (entertainment >= NEED_THRESHOLD) {
            needs += CharacterNeed("entertainment", entertainment, "有点累/压力大，想放松一下")
        }

        // 情绪需求：压力高（≥80）→ 想倾诉/被安慰
        val emotional = (state.stress / 2 + 30).toInt().coerceIn(0, 100)
        if (emotional >= NEED_THRESHOLD) {
            needs += CharacterNeed("emotional", emotional, "心里压着事，想找人说一说")
        }

        // 家庭需求：家人在附近 → 想一起做点什么
        if (familyNearby) {
            val family = (60 + (100 - state.loneliness) / 4).toInt().coerceIn(0, 100)
            if (family >= NEED_THRESHOLD) {
                needs += CharacterNeed("family", family, "家人在身边，想一起待一会儿")
            }
        }

        return needs.sortedByDescending { it.level }
    }

    /** 时间推进：每小时更新一次生命数值（代码负责数值变化，DeepSeek 只决策）。 */
    fun advanceHourly(state: CharacterLifeStateEntity, hours: Int): CharacterLifeStateEntity {
        val sleeping = hours >= 6
        val energyDelta = if (sleeping) 12 * hours else -ENERGY_DRAIN_PER_HOUR * hours
        return state.copy(
            energy = (state.energy + energyDelta).coerceIn(5, 100),
            stress = (state.stress - STRESS_DECAY_PER_HOUR * hours).coerceIn(0, 100),
            loneliness = if (sleeping) state.loneliness else (state.loneliness + LONELINESS_GROW_PER_HOUR * hours).coerceIn(0, 100),
            socialNeed = (state.socialNeed + if (sleeping) 0 else 8 * hours).coerceIn(0, 100),
            updatedAt = System.currentTimeMillis()
        )
    }
}
