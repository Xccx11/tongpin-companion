package com.aster.companion.update

import android.content.Context
import com.aster.companion.agent.AgentTurnCoordinator
import com.aster.companion.agent.ImageGenerationCoordinator
import com.aster.companion.data.CompanionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

/** 从用户填的地址里取出 scheme://host[:port]，忽略 /v1 这类路径。 */
internal object GatewayOrigin {
    fun of(raw: String): String? {
        val text = raw.trim().trimEnd('/')
        if (text.isEmpty()) return null
        val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host ?: return@runCatching text
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "${uri.scheme ?: "https"}://$host$port"
        }.getOrNull()
    }
}

/**
 * 全局可见的远程提示词快照。
 *
 * 提示词散落在 object / companion object 里（[com.aster.companion.agent.ScenePromptBuilder] 等），
 * 那些地方注入不进 Hilt，所以刷新结果统一放这里读。
 */
object RemotePromptStore {
    @Volatile
    private var values: Map<String, String> = emptyMap()

    fun put(next: Map<String, String>) {
        values = next
    }

    /** 命中返回远程值，没有则返回 null（调用方回退内置默认）。 */
    fun get(key: String): String? = values[key]?.trim()?.takeIf { it.isNotEmpty() }

    /** 当前生效的远程提示词条数（0 = 还没同步到，全用内置默认）。 */
    fun size(): Int = values.size
}

/**
 * 网关上的「远程提示词」：一份 prompts.json，App 按间隔拉取并缓存。
 *
 * 目的是不再为改一句提示词重新编译安装整个 App。拉不到就用内置默认值，行为不变；
 * 拉到的值优先于内置默认，但仍然让位于用户自己在设置里写的规则。
 */
@Singleton
class RemotePrompts @Inject constructor(
    private val http: OkHttpClient,
    private val dao: CompanionDao,
    private val imageRuleSettings: com.aster.companion.lite.ImageRuleSettings,
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lastAttemptAt = AtomicLong(0L)
    private val refreshing = AtomicBoolean(false)

    init {
        RemotePromptStore.put(decode(prefs.getString(KEY_VALUES, null)))
    }

    fun get(key: String): String? = RemotePromptStore.get(key)

    /** 上次成功刷新的时间戳（0 表示从没成功过）。 */
    fun updatedAt(): Long = prefs.getLong(KEY_AT, 0L)

    /** 立刻同步一次（设置页点「检查更新」时用），返回这次生效的提示词条数。 */
    suspend fun refreshNow(): Int {
        refreshIfStale(force = true)
        return RemotePromptStore.size()
    }

    /**
     * 按间隔刷新（默认 3 分钟），[force] 忽略间隔。
     * 失败静默并保留旧值——提示词拉不到不该影响出图。
     */
    suspend fun refreshIfStale(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAttemptAt.get() < INTERVAL_MS) return
        if (!refreshing.compareAndSet(false, true)) return
        lastAttemptAt.set(now)
        try {
            val base = GatewayOrigin.of(baseUrl()) ?: return
            val request = Request.Builder()
                .url("$base/app/prompts.json")
                .header("ngrok-skip-browser-warning", "1")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string().orEmpty()
                    val parsed = decode(body)
                    if (parsed.isEmpty()) return@use
                    RemotePromptStore.put(parsed)
                    prefs.edit()
                        .putLong(KEY_AT, System.currentTimeMillis())
                        .putString(KEY_VALUES, encode(parsed))
                        .apply()
                }
            }
        } catch (_: Exception) {
            // 网络问题不该影响出图：用上一次成功拉到的值（可能是空的，那就是内置默认）。
        } finally {
            refreshing.set(false)
        }
    }

    /**
     * 规则网关地址优先（设置页可单独填）；留空才跟随图像模型地址，最后才用对话模型地址。
     *
     * 2026-10-13：profile 那两条分支**走 EndpointResolver**，也就是跟随「自动 / 内网 / 外网」设置
     * —— 在家自动用 192.168.0.100（快），出门自动回退 ngrok 的公网地址。
     * 单独填的 `ruleBaseUrl` 保持原样：那是用户显式指定的，不替他改。
     */
    private suspend fun baseUrl(): String =
        imageRuleSettings.ruleBaseUrl.takeIf { it.isNotBlank() }
            ?: runCatching {
                dao.modelProfile(ImageGenerationCoordinator.IMAGE_GENERATION_MODEL)
                    ?.let { com.aster.companion.net.EndpointResolver.resolve(dao, it).baseUrl }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching {
                dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
                    ?.let { com.aster.companion.net.EndpointResolver.resolve(dao, it).baseUrl }
            }.getOrNull().orEmpty()

    private fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject
                .mapNotNull { (key, value) ->
                    if (key.startsWith("_")) return@mapNotNull null
                    value.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { key to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun encode(values: Map<String, String>): String =
        buildJsonObject { values.forEach { (key, value) -> put(key, value) } }.toString()

    private companion object {
        const val PREFS = "tongpin_remote_prompts"
        const val KEY_VALUES = "values"
        const val KEY_AT = "updated_at"

        /** 拉取间隔：改完提示词后最多等这么久就生效。 */
        const val INTERVAL_MS = 3 * 60 * 1000L
    }
}
