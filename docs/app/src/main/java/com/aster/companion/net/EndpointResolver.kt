package com.aster.companion.net

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.data.WorldStateEntity
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * **内网 / 外网双配置**的解析器 —— 全 App 取地址的唯一入口。
 *
 * ## 为什么要有它
 * 以前每个模型 profile 只有一个 `baseUrl`：在家要走 `http://192.168.0.100:8088`（快），
 * 出门得换成 ngrok 的公网地址（慢但能用），**每次都得手改**。
 *
 * ## 怎么存（零迁移）
 * 不动 `companion_model_profiles` 表结构，也不加 Room 版本：复用**现成的**
 * `companion_world_states`（项目里本来就用它存各种跨会话状态），用独立 scope：
 *
 * | id                  | content                          | 含义                     |
 * |---------------------|----------------------------------|--------------------------|
 * | `net:mode`          | `AUTO` / `LAN` / `WAN`           | 全局网络模式             |
 * | `net:lan:<profileId>` | `http://192.168.0.100:8088/v1` | 该 profile 的内网地址    |
 *
 * 外网地址就是 profile 原来的 `baseUrl`（不动它，向后兼容、老数据无需迁移）。
 *
 * ## 怎么用（调用点只改一行）
 * ```kotlin
 * val p = EndpointResolver.resolve(dao, profile)   // 拿到"该用哪个地址"的副本
 * // 之后所有用 p.baseUrl 的代码一行都不用改
 * ```
 * 自动模式下会**探测内网是否可达**（纯 TCP 连接，700ms 超时），结果缓存 30 秒：
 * 在家走内网、出门自动回退外网，用户不用管。
 */

/**
 * **兼容层**：实现已搬进可注入的 [EndpointRegistry]，这里保持同样签名，
 * 让尚未迁移的 18 处调用点继续编译。迁完后删除本文件。
 */
object EndpointResolver {

    private val registry = EndpointRegistry()

    suspend fun mode(dao: CompanionDao): NetworkMode = registry.mode(dao)
    suspend fun lanOverride(dao: CompanionDao, profileId: String): String? = registry.lanOverride(dao, profileId)
    suspend fun setMode(dao: CompanionDao, mode: NetworkMode) = registry.setMode(dao, mode)
    suspend fun setLanOverride(dao: CompanionDao, profileId: String, url: String?) =
        registry.setLanOverride(dao, profileId, url)
    suspend fun resolve(dao: CompanionDao, profile: ModelProfileEntity): ModelProfileEntity =
        registry.resolve(dao, profile)
    fun cachedBaseUrl(profileId: String, fallback: String): String = registry.cachedBaseUrl(profileId, fallback)
    suspend fun describe(dao: CompanionDao): String = registry.describe(dao)

    @androidx.annotation.VisibleForTesting
    internal fun clearCaches() = registry.clearCaches()
}
