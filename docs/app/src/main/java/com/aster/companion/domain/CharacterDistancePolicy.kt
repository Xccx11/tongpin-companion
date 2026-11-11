package com.aster.companion.domain

/** 家庭模式：根据角色地点推断与用户的距离标注（像真实家庭：谁在附近/几步远/有点远）。 */
object CharacterDistancePolicy {
    fun distanceLabel(location: String?): String? {
        if (location.isNullOrBlank()) return null
        val loc = location.trim()
        return when {
            loc.contains("客厅") || loc.contains("沙发") || loc.contains("阳台") ||
                loc.contains("身边") || loc.contains("餐厅") || loc.contains("餐桌") ||
                loc.contains("院子") || loc.contains("门口") -> "就在附近"
            loc.contains("厨房") || loc.contains("书房") || loc.contains("房间") ||
                loc.contains("卧室") || loc.contains("走廊") || loc.contains("门边") ||
                loc.contains("洗手间") || loc.contains("浴室") || loc.contains("卫生间") ||
                loc.contains("书房") -> "几步远"
            loc.contains("楼上") || loc.contains("楼下") || loc.contains("阁楼") ||
                loc.contains("外面") || loc.contains("超市") || loc.contains("公园") ||
                loc.contains("街上") || loc.contains("车库") -> "有点远"
            else -> null
        }
    }
}
