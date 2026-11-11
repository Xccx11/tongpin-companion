package com.aster.companion.net

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.data.WorldStateEntity
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@javax.inject.Singleton
class EndpointRegistry @Inject constructor(
    private val cache: EndpointCache = EndpointCache(),
) {


    /** world_states 里的 scope，独立命名空间，不会和剧情/记忆那些 scope 撞。 */
    val SCOPE = "NETWORK"

    private val MODE_ID = "net:mode"
    private val LAN_PREFIX = "net:lan:"

    /** 探测超时：手机切网时不能让它卡住出图/发消息。 */
    private val PROBE_TIMEOUT_MS = 700

    /** 探测结果缓存时长：30 秒内不重复探测。 */
    private val PROBE_TTL_MS = 30_000L



    // ---------------------------------------------------------------- 读

    suspend fun mode(dao: CompanionDao): NetworkMode {
        val row = netRows(dao).firstOrNull { it.id == MODE_ID }
        return NetworkMode.of(row?.content)
    }

    suspend fun lanOverride(dao: CompanionDao, profileId: String): String? {
        val row = netRows(dao).firstOrNull { it.id == LAN_PREFIX + profileId }
        return row?.content?.trim()?.takeIf { it.isNotBlank() }
    }

    /** 所有 profileId -> 内网地址（设置界面用）。 */
    suspend fun allLanOverrides(dao: CompanionDao): Map<String, String> =
        netRows(dao)
            .filter { it.id.startsWith(LAN_PREFIX) && it.content.isNotBlank() }
            .associate { it.id.removePrefix(LAN_PREFIX) to it.content.trim() }

    // ---------------------------------------------------------------- 写

    suspend fun setMode(dao: CompanionDao, mode: NetworkMode) {
        dao.upsertWorldState(row(MODE_ID, mode.id))
    }

    /** 传空/空白 = 删掉这条内网地址（该 profile 永远走外网）。 */
    suspend fun setLanOverride(dao: CompanionDao, profileId: String, url: String?) {
        val v = url?.trim().orEmpty()
        if (v.isBlank()) {
            dao.deleteWorldStateById(LAN_PREFIX + profileId)
        } else {
            dao.upsertWorldState(row(LAN_PREFIX + profileId, v))
        }
    }

    // ---------------------------------------------------------------- 解析

    /** 该 profile 这次实际要用的 baseUrl。 */
    suspend fun baseUrl(dao: CompanionDao, profile: ModelProfileEntity): String {
        val wan = profile.baseUrl.trim()
        val lan = lanOverride(dao, profile.id)
        return when (mode(dao)) {
            NetworkMode.WAN -> wan
            NetworkMode.LAN -> lan ?: wan
            NetworkMode.AUTO -> if (lan != null && reachable(lan)) lan else wan
        }
    }

    /**
     * 把 profile 换成"这次该用的地址"的副本 —— **调用点只改这一行**。
     *
     * 只改 baseUrl，其它字段原样；`ModelProfileEntity` 是 data class，copy 很便宜。
     */
    suspend fun resolve(dao: CompanionDao, profile: ModelProfileEntity): ModelProfileEntity {
        val url = baseUrl(dao, profile)
        cache.putBaseUrl(profile.id, url)   // 原子刷新快照，给非 suspend 的拼装用
        return if (url == profile.baseUrl) profile else profile.copy(baseUrl = url)
    }

    /**
     * **进程内快照**：给那些非 suspend、只负责拼 URL 的函数用（例如
     * `OpenAiCompatibleClient.endpoint()` —— 它拿不到 suspend 上下文）。
     *
     * 快照由 [resolve] 每次调用时刷新，而 [resolve] 在每条链路开头都会被调到
     * （拉规则、图片提示词模型…），所以这里基本总是新鲜的；
     * 拿不到就回退 profile 自己的 baseUrl —— 行为和不接解析器时完全一样。
     */
    // A5：缓存收进 EndpointCache（内部已是原子不可变状态）；下一步把它变成注入的构造参数即可去全局。

    /**
     * 清空所有进程级缓存（探测结果 + baseUrl 快照）。
     *
     * 存在的意义：这些缓存是**进程级全局**的，测试之间会互相污染；同时给"切换网络环境"
     * 这类操作一个明确的失效入口。A5 的目标是把它们挪进可注入的实例，届时这个方法会随之消失。
     */
    @androidx.annotation.VisibleForTesting
    internal fun clearCaches() {
        cache.clearProbe()
        cache.clearBaseUrls()
    }

    fun cachedBaseUrl(profileId: String, fallback: String): String =
        cache.baseUrlOf(profileId)?.takeIf { it.isNotBlank() } ?: fallback

    /** 给界面显示用：当前实际生效的是内网还是外网。 */
    suspend fun describe(dao: CompanionDao): String {
        val m = mode(dao)
        val overrides = allLanOverrides(dao)
        return when (m) {
            NetworkMode.LAN -> "内网" + (overrides.values.firstOrNull()?.let { " · ${hostOf(it)}" } ?: "")
            NetworkMode.WAN -> "外网"
            NetworkMode.AUTO -> {
                val lan = overrides.values.firstOrNull()
                if (lan != null && reachable(lan)) "自动 · 正在用内网 ${hostOf(lan)}" else "自动 · 正在用外网"
            }
        }
    }

    // ---------------------------------------------------------------- 内部

    private fun row(id: String, content: String) = WorldStateEntity(
        id = id,
        characterId = null,
        threadId = null,
        scope = SCOPE,
        content = content,
        updatedAt = System.currentTimeMillis(),
    )

    private suspend fun netRows(dao: CompanionDao): List<WorldStateEntity> =
        runCatching { dao.worldStatesByScope(SCOPE) }.getOrDefault(emptyList())

    /** 只做一次 TCP 连接：够判断"家里那台机器在不在"，又不会拖慢请求。 */
    private suspend fun reachable(url: String): Boolean {
        val now = System.currentTimeMillis()
        val cached = cache.probeState()
        if (cached.url == url && now - cached.at < PROBE_TTL_MS) return cached.ok

        val ok = withContext(Dispatchers.IO) {
            val uri = runCatching { URI(url) }.getOrNull() ?: return@withContext false
            val host = uri.host ?: return@withContext false
            val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
            runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
                }
                true
            }.getOrDefault(false)
        }
        cache.setProbe(url, ok, now)
        return ok
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host + ":" + URI(url).port }.getOrDefault(url)

    /** 测试用：清掉探测缓存。 */
    internal fun resetProbeCache() {
        cache.clearProbe()
    }
}
