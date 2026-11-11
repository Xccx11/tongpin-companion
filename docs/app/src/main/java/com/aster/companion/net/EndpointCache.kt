package com.aster.companion.net

import java.util.concurrent.atomic.AtomicReference

/**
 * [EndpointResolver] 的**进程内缓存**：探测结果 + baseUrl 快照。
 *
 * 抽成独立类的目的不是"拆文件"，而是让 A5 的最后一步变得很小：
 * 现在它是 `EndpointResolver` 内部的 `private val cache`，之后只要把它变成
 * 构造参数（由 Hilt 注入），隐式全局状态就没了。
 *
 * 两个状态都用 `AtomicReference` 装**不可变值**：
 * - 探测三字段必须**一起**更新，否则并发下会出现"url 是新的、ok 是旧的"撕裂；
 * - baseUrl 快照的 `snapshot + (id to url)` 是读-改-写，必须原子，否则丢更新。
 */
class EndpointCache {

    data class ProbeState(val url: String?, val ok: Boolean, val at: Long)

    private val probe = AtomicReference(ProbeState(null, false, 0L))
    private val baseUrls = AtomicReference<Map<String, String>>(emptyMap())

    fun probeState(): ProbeState = probe.get()

    fun setProbe(url: String?, ok: Boolean, at: Long) = probe.set(ProbeState(url, ok, at))

    fun clearProbe() = probe.set(ProbeState(null, false, 0L))

    fun baseUrlOf(profileId: String): String? = baseUrls.get()[profileId]

    fun putBaseUrl(profileId: String, url: String) {
        baseUrls.updateAndGet { it + (profileId to url) }
    }

    fun clearBaseUrls() = baseUrls.set(emptyMap())

    fun clearAll() {
        clearProbe()
        clearBaseUrls()
    }
}