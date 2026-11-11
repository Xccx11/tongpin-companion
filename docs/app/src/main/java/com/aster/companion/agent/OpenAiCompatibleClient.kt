package com.aster.companion.agent

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.Capability
import com.aster.companion.domain.ConfirmationRequirement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class ToolChatResponse(val content: String?, val actions: List<AgentActionRequest>)

sealed interface ToolChatStreamEvent {
    data class Delta(val text: String) : ToolChatStreamEvent
    data class Complete(val response: ToolChatResponse) : ToolChatStreamEvent
}

/**
 * 一次出图的结果：图片本体（url 或 data:base64）+ 供应商/网关回填的**最终提示词**。
 *
 * [revisedPrompt] 即 OpenAI 标准的 revised_prompt：本地网关会把（补过结尾咒语、并做过一次提示词
 * 整理之后）**真正送去渲染**的那份放进来；供应商没返回这个字段时为空串。
 */
internal data class GeneratedImage(val image: String, val revisedPrompt: String = "")

@Singleton
class OpenAiCompatibleClient @Inject constructor(
    private val http: OkHttpClient,
    @ApplicationContext private val context: Context,
    /** 按「自动 / 内网 / 外网」解析 profile 的 baseUrl（见 EndpointResolver）。 */
    private val dao: com.aster.companion.data.CompanionDao
) {
    /**
     * 这次该用哪个地址：自动模式下在家走内网、出门走外网。
     * 解析失败一律退回原 profile —— 绝不因为解析把出图/对话弄挂。
     */
    private suspend fun resolved(profile: ModelProfileEntity): ModelProfileEntity =
        runCatching { com.aster.companion.net.EndpointResolver.resolve(dao, profile) }
            .getOrDefault(profile)

    /** Avoid paying for a failed stream attempt on every group participant for non-SSE providers. */
    private val unsupportedToolStreamingProfiles = ConcurrentHashMap.newKeySet<String>()

    suspend fun test(profile: ModelProfileEntity, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint(profile, "/models"))
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { responseError(response.code, body) }
                val count = runCatching { Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.size }.getOrNull()
                if (count == null) "连接成功" else "连接成功，发现 $count 个模型"
            }
        }
    }

    suspend fun listModels(profile: ModelProfileEntity, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint(profile, "/models"))
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { responseError(response.code, body) }
                Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                    ?.mapNotNull { item -> item.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                    ?.distinct()
                    ?.sorted()
                    .orEmpty()
            }
        }
    }

    suspend fun chat(
        profile: ModelProfileEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        longRunning: Boolean = false,
        history: List<ConversationRequestPolicy.Message> = emptyList(),
        /** 仅状态记录器使用：压温度、限输出长度（普通对话不发采样参数）。 */
        temperature: Double? = null,
        maxTokens: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = ConversationRequestPolicy.payload(
                profile = profile,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                history = history,
                stream = false,
                temperature = temperature,
                maxTokens = maxTokens
            )
            val request = Request.Builder()
                .url(endpoint(profile, "/chat/completions"))
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody(JSON))
                .build()
            val client = if (longRunning) planningHttp(profile) else http
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                check(response.isSuccessful) { responseError(response.code, responseBody) }
                check(responseBody.isNotBlank()) { "Empty model response" }
                val root = Json.parseToJsonElement(responseBody).jsonObject
                val message = root["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject
                val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                val reasoning = message["reasoning_content"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                // Prefer visible content; if thinking model hid the reply, take reasoning tail.
                val text = content.ifBlank { reasoning }
                check(text.isNotBlank()) { "Empty model response" }
                text
            }
        }
    }

    /** Uses the provider's native function calling when available. Callers may fall back to plain chat. */
    suspend fun chatWithTools(
        profile: ModelProfileEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        capabilities: Set<Capability>,
        history: List<ConversationRequestPolicy.Message> = emptyList()
    ): Result<ToolChatResponse> = withContext(Dispatchers.IO) {
        val effective = ToolThinkingFallbackPolicy.profileForToolRequest(profile)
        runCatching {
            requestToolChat(effective, apiKey, systemPrompt, userPrompt, capabilities, history)
        }.recoverCatching { error ->
            // 服务端因为"思考 + 工具"拒了这次请求：记下来（以后直接不带思考），并**当场用不带思考的
            // profile 重试一次** —— 用户不该因为一个参数组合就看到"模型调用失败"。
            if (!profile.thinkingEnabled || !ToolThinkingFallbackPolicy.shouldRetryWithoutThinking(error.message)) throw error
            ToolThinkingFallbackPolicy.markUnsupported(profile)
            requestToolChat(profile.copy(thinkingEnabled = false), apiKey, systemPrompt, userPrompt, capabilities, history)
        }
    }

    private fun requestToolChat(
        profile: ModelProfileEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        capabilities: Set<Capability>,
        history: List<ConversationRequestPolicy.Message>
    ): ToolChatResponse {
        val actionable = capabilities
        val payload = buildJsonObject {
            ConversationRequestPolicy.putBase(
                builder = this,
                profile = profile,
                messages = ConversationRequestPolicy.conversationMessages(systemPrompt, userPrompt, history),
                stream = false,
                withTools = true
            )
            put("tool_choice", "auto")
            put("tools", buildJsonArray { actionable.forEach { add(toolDefinition(it)) } })
        }.toString()
        val request = Request.Builder().url(endpoint(profile, "/chat/completions"))
            .header("Authorization", "Bearer $apiKey").post(payload.toRequestBody(JSON)).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { responseError(response.code, body) }
            val message = Json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject
            val content = message["content"]?.jsonPrimitive?.contentOrNull
            val byName = actionable.associateBy { functionName(it.id) }
            val actions = message["tool_calls"]?.jsonArray.orEmpty().mapNotNull { call ->
                val function = call.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
                val capability = byName[function["name"]?.jsonPrimitive?.contentOrNull] ?: return@mapNotNull null
                val rawArguments = function["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val arguments = parseStringArguments(rawArguments)
                AgentActionRequest(capability.id, capability.transport, capability.confirmation, arguments)
            }
            return ToolChatResponse(content, actions)
        }
    }

    /** Streams visible reply text while accumulating native function calls until the final SSE frame. */
    fun chatWithToolsStream(
        profile: ModelProfileEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        capabilities: Set<Capability>,
        history: List<ConversationRequestPolicy.Message> = emptyList()
    ): Flow<ToolChatStreamEvent> = flow {
        val streamProfileKey = "${com.aster.companion.net.EndpointResolver.cachedBaseUrl(profile.id, profile.baseUrl).trimEnd('/')}/${profile.modelName}"
        check(streamProfileKey !in unsupportedToolStreamingProfiles) {
            "Provider does not support tool-call streaming"
        }
        // 这家服务已经证明过"思考 + 工具"会被拒：之后的带 tools 请求直接不带思考。
        // （流式这条路不好整段重试，所以这里只做"记住"；非流式的 chatWithTools 会当场重试一次。）
        val effective = ToolThinkingFallbackPolicy.profileForToolRequest(profile)
        val actionable = capabilities
        val byName = actionable.associateBy { functionName(it.id) }
        val toolCalls = mutableMapOf<Int, StreamToolCall>()
        val content = StringBuilder()
        var receivedSseFrame = false
        val payload = buildJsonObject {
            ConversationRequestPolicy.putBase(
                builder = this,
                profile = effective,
                messages = ConversationRequestPolicy.conversationMessages(systemPrompt, userPrompt, history),
                stream = true,
                withTools = true
            )
            put("tool_choice", "auto")
            put("tools", buildJsonArray { actionable.forEach { add(toolDefinition(it)) } })
        }.toString()
        val request = Request.Builder()
            .url(endpoint(effective, "/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val failureBody = response.body?.string().orEmpty()
                // 因为"思考 + 工具"被拒：记下来，下一次这类请求就自动不带思考。
                if (profile.thinkingEnabled && ToolThinkingFallbackPolicy.shouldRetryWithoutThinking(failureBody)) {
                    ToolThinkingFallbackPolicy.markUnsupported(profile)
                }
                check(false) { responseError(response.code, failureBody) }
            }
            val source = requireNotNull(response.body).source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val choice = runCatching {
                    Json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                }.getOrNull() ?: continue
                receivedSseFrame = true
                val delta = choice["delta"]?.jsonObject ?: continue
                delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let { text ->
                    content.append(text)
                    emit(ToolChatStreamEvent.Delta(text))
                }
                delta["tool_calls"]?.jsonArray.orEmpty().forEach { item ->
                    val call = item.jsonObject
                    val index = call["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@forEach
                    val state = toolCalls.getOrPut(index) { StreamToolCall() }
                    call["function"]?.jsonObject?.let { function ->
                        function["name"]?.jsonPrimitive?.contentOrNull?.let { state.name = it }
                        function["arguments"]?.jsonPrimitive?.contentOrNull?.let(state.arguments::append)
                    }
                }
            }
        }
        if (!receivedSseFrame) {
            unsupportedToolStreamingProfiles += streamProfileKey
            error("Provider does not support tool-call streaming")
        }
        val actions = toolCalls.toSortedMap().values.mapNotNull { call ->
            val capability = call.name?.let(byName::get) ?: return@mapNotNull null
            AgentActionRequest(capability.id, capability.transport, capability.confirmation, parseStringArguments(call.arguments.toString()))
        }
        emit(ToolChatStreamEvent.Complete(ToolChatResponse(content.toString().ifBlank { null }, actions)))
    }.flowOn(Dispatchers.IO)

    fun chatStream(
        profile: ModelProfileEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        history: List<ConversationRequestPolicy.Message> = emptyList()
    ): Flow<String> = flow {
        val payload = buildJsonObject {
            ConversationRequestPolicy.putBase(
                builder = this,
                profile = profile,
                messages = ConversationRequestPolicy.conversationMessages(systemPrompt, userPrompt, history),
                stream = true
            )
        }.toString()
        val request = Request.Builder()
            .url(endpoint(profile, "/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { responseError(response.code, response.body?.string().orEmpty()) }
            val source = requireNotNull(response.body).source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val delta = runCatching {
                    val contentEl = Json.parseToJsonElement(data)
                        .jsonObject["choices"]!!.jsonArray.first()
                        .jsonObject["delta"]!!.jsonObject["content"]
                    // JSON null / missing content must not become the literal "null" string.
                    if (contentEl == null || contentEl is JsonNull) null
                    else contentEl.jsonPrimitive.contentOrNull
                }.getOrNull()
                if (!delta.isNullOrEmpty() && delta != "null") emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 出图提交闸：key -> 这次提交。同一条出图请求只允许有一次真正提交。 */
    private class ImageSubmission(val at: Long, val result: CompletableDeferred<Result<GeneratedImage>>)

    private val imageSubmissions = ConcurrentHashMap<String, ImageSubmission>()

    /**
     * 出图入口。**同一条请求只提交一次**：以 (模型, 尺寸, 提示词) 为 key ——
     * 正在出的同一张图直接等它、不再提交第二次；刚出好的同一张（[IMAGE_SUBMISSION_WINDOW_MS] 内）直接复用。
     * 这样无论上层从哪条路径重复触发，网关都只会收到一次出图请求。
     */
    internal suspend fun generateImage(
        profile: ModelProfileEntity,
        apiKey: String,
        prompt: String,
        gatewayContext: ComfyGatewayPromptContext? = null,
        existingTaskId: String? = null,
        onTaskCreated: suspend (String) -> Unit = {},
        shouldCancel: suspend () -> Boolean = { false },
        size: String = ImageGenerationRequestPolicy.DEFAULT_SIZE
    ): Result<GeneratedImage> {
        // key 里**不含模型名**：图像 API 池轮换供应商时模型名会变，含进去就换了 key、闸门失效。
        val key = listOf(
            com.aster.companion.net.EndpointResolver.cachedBaseUrl(profile.id, profile.baseUrl).trim(),
            ImageGenerationRequestPolicy.normalizeSize(size),
            prompt.trim()
        ).joinToString("|")
        return submitImageOnce(key) {
            generateImageOnce(profile, apiKey, prompt, gatewayContext, existingTaskId, onTaskCreated, shouldCancel, size)
        }
    }

    private suspend fun submitImageOnce(key: String, submit: suspend () -> Result<GeneratedImage>): Result<GeneratedImage> {
        while (true) {
            val now = System.currentTimeMillis()
            imageSubmissions[key]?.let { existing ->
                val finished = existing.result.isCompleted
                val stale = finished && now - existing.at > IMAGE_SUBMISSION_WINDOW_MS
                if (!stale) {
                    Log.i(TAG, "stage=image_submit reused inflight=${!finished} key=${key.take(50)}")
                    return existing.result.await()
                }
                imageSubmissions.remove(key, existing)
            }
            val fresh = ImageSubmission(now, CompletableDeferred())
            if (imageSubmissions.putIfAbsent(key, fresh) != null) continue
            val result = try {
                submit()
            } catch (error: Throwable) {
                Result.failure(error)
            }
            fresh.result.complete(result)
            // 失败**不留在缓存里**：用户点「重试」时必须真的重新提交一次（重试就是重试）。
            if (result.isFailure) imageSubmissions.remove(key, fresh)
            return result
        }
    }

    private suspend fun generateImageOnce(
        profile: ModelProfileEntity,
        apiKey: String,
        prompt: String,
        gatewayContext: ComfyGatewayPromptContext? = null,
        existingTaskId: String? = null,
        onTaskCreated: suspend (String) -> Unit = {},
        shouldCancel: suspend () -> Boolean = { false },
        size: String = ImageGenerationRequestPolicy.DEFAULT_SIZE
    ): Result<GeneratedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedSize = ImageGenerationRequestPolicy.normalizeSize(size)
            if (QwenImageApi.supports(profile)) {
                return@runCatching GeneratedImage(
                    generateQwenImage(profile, apiKey, prompt, null, existingTaskId, onTaskCreated)
                )
            }
            if (ComfyGatewayTaskProtocol.supports(profile)) {
                return@runCatching GeneratedImage(
                    generateComfyGatewayImage(profile, apiKey, prompt, gatewayContext, existingTaskId, onTaskCreated, shouldCancel, normalizedSize)
                )
            }
            val payload = ImageGenerationRequestPolicy.payload(profile.modelName, prompt, normalizedSize)
            val request = Request.Builder()
                .url(endpoint(profile, "/images/generations"))
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody(JSON))
                .build()
            imageHttp(profile).newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { responseError(response.code, body) }
                // 网关会把补完结尾咒语、并整理过之后**真正送去渲染**的提示词回填在 revised_prompt 里。
                GeneratedImage(
                    image = imageUrlFromResponse(body),
                    revisedPrompt = revisedPromptFromResponse(body)
                )
            }
        }
    }

    /**
     * The local Comfy gateway acknowledges work quickly, then exposes a short polling endpoint.
     * Keeping the expensive render off the original phone request avoids tunnel/read timeouts.
     */
    private suspend fun generateComfyGatewayImage(
        profile: ModelProfileEntity,
        apiKey: String,
        prompt: String,
        gatewayContext: ComfyGatewayPromptContext?,
        existingTaskId: String?,
        onTaskCreated: suspend (String) -> Unit,
        shouldCancel: suspend () -> Boolean,
        size: String
    ): String {
        val taskId = if (ImageTaskRecoveryPolicy.maySubmitNewTask(existingTaskId)) {
            submitComfyGatewayTask(profile, apiKey, prompt, gatewayContext, size).id.also { onTaskCreated(it) }
        } else {
            requireNotNull(existingTaskId)
        }
        // The gateway owns a durable task and ComfyUI may legitimately spend longer
        // than a fixed client deadline in the queue. Poll until the provider reports a
        // terminal state; process restart recovery continues with the same task id.
        var transientFailures = 0
        while (true) {
            if (shouldCancel()) {
                cancelComfyGatewayTask(profile, apiKey, taskId)
                throw ImageTaskCancelledException(taskId)
            }
            val task = try {
                pollComfyGatewayTask(profile, apiKey, taskId).also {
                    transientFailures = 0
                }
            } catch (error: Throwable) {
                if (!error.isTransientImagePollFailure()) throw error
                transientFailures += 1
                Log.w(TAG, "stage=gateway_poll_retry task_id=$taskId attempt=$transientFailures", error)
                delay(ImagePollingRetryPolicy.retryDelayMillis(transientFailures - 1))
                continue
            }
            when (task.status) {
                ComfyGatewayTaskStatus.Queued, ComfyGatewayTaskStatus.Running -> {
                    check(ImageTaskPollingPolicy.shouldContinue(task.status))
                    delay(COMFY_GATEWAY_TASK_POLL_INTERVAL_MS)
                }
                ComfyGatewayTaskStatus.Succeeded -> return requireNotNull(task.imageUrl)
                ComfyGatewayTaskStatus.Failed -> throw ImageProviderTaskException(
                    providerTaskId = task.id,
                    terminal = true,
                    message = task.error ?: "Image gateway task failed"
                )
                ComfyGatewayTaskStatus.Cancelled -> throw ImageTaskCancelledException(task.id)
            }
        }
    }

    suspend fun cancelImageTask(
        profile: ModelProfileEntity,
        apiKey: String,
        taskId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (ComfyGatewayTaskProtocol.supports(profile)) {
                cancelComfyGatewayTask(profile, apiKey, taskId)
            }
        }
    }

    private fun cancelComfyGatewayTask(profile: ModelProfileEntity, apiKey: String, taskId: String) {
        val request = Request.Builder()
            .url(endpoint(profile, "/images/tasks/$taskId"))
            .header("Authorization", "Bearer $apiKey")
            .delete()
            .build()
        imageHttp(profile).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { responseError(response.code, body) }
        }
    }

    private fun submitComfyGatewayTask(
        profile: ModelProfileEntity,
        apiKey: String,
        prompt: String,
        gatewayContext: ComfyGatewayPromptContext?,
        size: String
    ): ComfyGatewayTask {
        val payload = buildJsonObject {
            put("model", profile.modelName)
            put("prompt", prompt)
            put("size", size)
            put("response_format", ImageGenerationRequestPolicy.RESPONSE_FORMAT)
            put("background", true)
                gatewayContext?.let { context ->
                    put("tongpin_context", buildJsonObject {
                        // 网关侧同样只吃单行提示词。
                        put("character_appearance", ImageGenerationPromptPolicy.singleLine(context.characterAppearance))
                        put("visual_brief", ImageGenerationPromptPolicy.singleLine(context.visualBrief))
                        put("required_character_count", context.requiredCharacterCount.coerceAtLeast(1))
                    })
                }
        }.toString()
        val request = Request.Builder()
            .url(endpoint(profile, "/images/generations"))
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(JSON))
            .build()
        return imageHttp(profile).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { responseError(response.code, body) }
            ComfyGatewayTaskProtocol.parseStatus(body)
        }
    }

    private fun pollComfyGatewayTask(profile: ModelProfileEntity, apiKey: String, taskId: String): ComfyGatewayTask {
        val request = Request.Builder()
            .url(endpoint(profile, "/images/tasks/$taskId"))
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return pollingHttp().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { responseError(response.code, body) }
            ComfyGatewayTaskProtocol.parseStatus(body)
        }
    }

    suspend fun editImage(
        profile: ModelProfileEntity,
        apiKey: String,
        prompt: String,
        referenceUri: String,
        existingTaskId: String? = null,
        onTaskCreated: suspend (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ImageEditRoutePolicy.usesReferenceEditEndpoint(profile)) {
                throw ImageEditUnsupportedException()
            }
            if (QwenImageApi.supports(profile)) {
                return@runCatching generateQwenImage(
                    profile,
                    apiKey,
                    prompt,
                    referenceDataUrl(referenceUri),
                    existingTaskId,
                    onTaskCreated
                )
            }
            val imageBytes = openReferenceInput(referenceUri).use { it.readBytes() }
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", profile.modelName)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("size", ImageGenerationRequestPolicy.DEFAULT_SIZE)
                .addFormDataPart("response_format", ImageGenerationRequestPolicy.RESPONSE_FORMAT)
                .addFormDataPart("image", "reference.png", imageBytes.toRequestBody("image/png".toMediaType()))
                .build()
            val request = Request.Builder()
                .url(endpoint(profile, "/images/edits"))
                .header("Authorization", "Bearer $apiKey")
                .post(multipart)
                .build()
            imageHttp(profile).newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { responseError(response.code, body) }
                imageUrlFromResponse(body)
            }
        }
    }

    /**
     * 把本地图片转成视觉模型能吃的 data URL。
     *
     * ## 为什么不能把 MIME 写成通配符
     *
     * 以前这里拼的是 `data:image/` + 通配符 `*` + `;base64,…`。**通配符不是合法 MIME**，
     * 服务端从它判断不出图片格式，DeepSeek 官方直接回 400：
     *
     * > You have uploaded an unsupported image. Please make sure your image is valid and has one of
     * > the following formats: webp, png, jpeg, and gif.
     *
     * （原文见 2026-10-10 的真机日志，就是这一行写错造成的。）
     *
     * ## 为什么统一转成 JPEG
     *
     * - 手机相机常见 HEIC，而主流服务端普遍只收 webp/png/jpeg/gif —— **只改 MIME 对 HEIC 仍然没用**；
     * - 原图直接 base64 出去每次请求 **4 MB 多**（实测 4.29/4.31/4.45 MB），又慢又容易撞上中转限流。
     *   长边缩到 1568 + JPEG q85 之后通常只剩几百 KB。
     *
     * 解不出图时**不假装成功**：原样发送，但 MIME 一定按文件头给出真值。
     */
    private fun visionDataUrl(bytes: ByteArray): String {
        val maxEdge = 1568
        val quality = 85
        val jpeg = runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = android.graphics.BitmapFactory.Options().apply {
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxEdge || bounds.outHeight / (sample * 2) >= maxEdge) sample *= 2
                inSampleSize = sample
            }
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return@runCatching null
            val longest = maxOf(decoded.width, decoded.height)
            val scaled = if (longest <= maxEdge) {
                decoded
            } else {
                val ratio = maxEdge.toFloat() / longest.toFloat()
                android.graphics.Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            }
            java.io.ByteArrayOutputStream().use { out ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        }.getOrNull()
        val payload = jpeg ?: bytes
        val mime = if (jpeg != null) "image/jpeg" else sniffImageMime(bytes)
        return "data:$mime;base64," + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** 按文件头判断真实图片格式——`content://` 的 type 有时为空，扩展名也不可信。 */
    private fun sniffImageMime(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
        bytes.size >= 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() -> "image/gif"
        bytes.size >= 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "image/webp"
        bytes.size >= 12 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte() -> "image/heic"
        else -> "image/jpeg"
    }

    suspend fun analyzeImage(profile: ModelProfileEntity, apiKey: String, imageUrl: String, instruction: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedImageUrl = when {
                imageUrl.startsWith("content://") -> {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(Uri.parse(imageUrl))) { "Image is unavailable" }.use { it.readBytes() }
                    visionDataUrl(bytes)
                }
                imageUrl.startsWith("file://") -> {
                    val uri = Uri.parse(imageUrl)
                    val file = runCatching { File(URI(uri.toString())) }
                        .getOrElse { File(requireNotNull(uri.path)) }
                    visionDataUrl(file.readBytes())
                }
                else -> imageUrl
            }
            val payload = buildJsonObject {
                put("model", profile.modelName)
                put("stream", false)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", instruction) })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject { put("url", resolvedImageUrl) })
                            })
                        })
                    })
                })
            }.toString()
            val request = Request.Builder()
                .url(endpoint(profile, "/chat/completions"))
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) {
                    // 人话版给用户看，**原始响应必须进日志**——排查只能靠原文。
                    // 实测教训：DeepSeek 官方对带图请求回 400，映射后只剩一句"不支持这次请求"，
                    // 真正的理由（模型不吃图？字段不对？）就在这三几百字节里。
                    android.util.Log.w("TongpinImage", "analyzeImage HTTP ${response.code} body=$body")
                    responseError(response.code, body)
                }
                Json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
            }
        }
    }

    suspend fun embed(profile: ModelProfileEntity, apiKey: String, input: String): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("model", profile.modelName); put("input", input.take(8_000)) }.toString()
            val request = Request.Builder().url(endpoint(profile, "/embeddings"))
                .header("Authorization", "Bearer $apiKey").post(payload.toRequestBody(JSON)).build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { responseError(response.code, body) }
                Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.first().jsonObject["embedding"]!!
                    .jsonArray.map { it.jsonPrimitive.content.toFloat() }.toFloatArray()
            }
        }
    }

    private fun endpoint(profile: ModelProfileEntity, path: String): String {
        val root = com.aster.companion.net.EndpointResolver.cachedBaseUrl(profile.id, profile.baseUrl).trim().trimEnd('/')
        val versionedRoot = if (root.endsWith("/v1", ignoreCase = true)) root else "$root/v1"
        return versionedRoot + path
    }

    private suspend fun generateQwenImage(
        profile: ModelProfileEntity,
        apiKey: String,
        prompt: String,
        referenceDataUrl: String? = null,
        existingTaskId: String? = null,
        onTaskCreated: suspend (String) -> Unit = {}
    ): String {
        val taskId = if (ImageTaskRecoveryPolicy.maySubmitNewTask(existingTaskId)) {
            val request = Request.Builder()
                .url(QwenImageApi.generationEndpoint(profile))
                .header("Authorization", "Bearer $apiKey")
                .header("X-DashScope-Async", "enable")
                .post(QwenImageApi.requestBody(profile.modelName, prompt, referenceDataUrl).toRequestBody(JSON))
                .build()
            val firstResponse = imageHttp(profile).newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { responseError(response.code, body) }
                body
            }
            val submittedTaskId = QwenImageApi.taskId(firstResponse)
            if (submittedTaskId == null) return QwenImageApi.completedImageUrl(firstResponse)
            onTaskCreated(submittedTaskId)
            submittedTaskId
        } else {
            requireNotNull(existingTaskId)
        }

        var transientFailures = 0
        while (true) {
            delay(QWEN_TASK_POLL_INTERVAL_MS)
            val poll = Request.Builder()
                .url(QwenImageApi.taskEndpoint(profile, taskId))
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val taskResponse = try {
                pollingHttp().newCall(poll).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    check(response.isSuccessful) { responseError(response.code, body) }
                    body
                }.also { transientFailures = 0 }
            } catch (error: Throwable) {
                if (!error.isTransientImagePollFailure()) throw error
                transientFailures += 1
                Log.w(TAG, "stage=qwen_poll_retry task_id=$taskId attempt=$transientFailures", error)
                delay(ImagePollingRetryPolicy.retryDelayMillis(transientFailures - 1))
                continue
            }
            when (QwenImageApi.taskStatus(taskResponse)?.uppercase()) {
                "SUCCEEDED" -> return QwenImageApi.completedImageUrl(taskResponse)
                "FAILED", "CANCELED", "CANCELLED", "UNKNOWN" ->
                    throw ImageProviderTaskException(taskId, terminal = true, "Qwen image task did not complete successfully")
            }
        }
    }

    private fun referenceDataUrl(referenceUri: String): String {
        if (referenceUri.startsWith("data:image/", ignoreCase = true)) return referenceUri
        val uri = Uri.parse(referenceUri)
        val bytes = openReferenceInput(referenceUri).use { it.readBytes() }
        val mimeType = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/png"
        return "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun openReferenceInput(referenceUri: String): java.io.InputStream {
        val uri = Uri.parse(referenceUri)
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            runCatching { File(URI(uri.toString())).inputStream() }
                .getOrElse { File(requireNotNull(uri.path)).inputStream() }
        } else {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "Reference image is unavailable" }
        }
    }

    private fun imageHttp(profile: ModelProfileEntity): OkHttpClient {
        // 出图要等网关在显存里来回换模型，本机实测一张可能 1～3 分钟，忙起来更久。
        // 这里刻意放宽到 30 分钟：网关端等多久都行，App 不能先放弃（先放弃就会重发 → 重复出图）。
        val timeout = profile.timeoutSeconds.coerceIn(1800, 3600).toLong()
        return http.newBuilder()
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            // 不自动重发：连接断了就直接失败，由用户手动点「重试」——重试才算重试。
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun pollingHttp(): OkHttpClient = http.newBuilder()
        .connectTimeout(POLL_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(POLL_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(POLL_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private fun planningHttp(profile: ModelProfileEntity): OkHttpClient {
        val timeout = profile.timeoutSeconds.coerceIn(180, 300).toLong()
        return http.newBuilder()
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    private fun Throwable.isTransientImagePollFailure(): Boolean {
        return this is IOException || ImagePollingRetryPolicy.isTransientMessage(message)
    }

    private fun functionName(capabilityId: String): String = capabilityId.replace('.', '_')

    private class StreamToolCall(
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )

    private fun toolDefinition(capability: Capability): JsonElement = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", functionName(capability.id))
            put("description", when (capability.id) {
                "device.set_alarm" -> "Set an Android alarm through Shizuku after the user confirms."
                "device.read_battery" -> "Read the current Android battery state through Shizuku."
                "device.read_foreground_app" -> "Read the current foreground Android app through Shizuku."
                "app.open_known" -> "Open an allowed Android app through Shizuku after confirmation."
                "phone.dial" -> "Open the phone dialer for a number through Shizuku after confirmation."
                "sms.compose" -> "Open a new SMS for a number through Shizuku after confirmation."
                "companion.generate_image" -> "Generate a companion image immediately."
                "companion.propose_image" -> "Ask the user before generating a companion image in the current conversation."
                "companion.propose_private_image" -> "Ask the user before generating this character's image in that character's private chat. Use only from a group when the user explicitly asks for a private photo."
                "companion.send_private_message" -> "Send this character's natural message to their private chat when the user explicitly asks in a group."
                "companion.send_group_message" -> "Send this character's new, relevant natural message to one allowed group when the current group context gives a real reason, including an autonomous decision during a private turn; do not use it for ordinary private small talk."
                else -> capability.id
            })
            put("parameters", functionParameters(capability.id))
        })
    }

    private fun functionParameters(capabilityId: String): JsonElement {
        val fields = when (capabilityId) {
            "device.set_alarm" -> listOf("hour" to "integer hour from 0 to 23", "minute" to "integer minute from 0 to 59")
            "app.open_known" -> listOf("package_name" to "allowed Android package name")
            "phone.dial", "sms.compose" -> listOf("phone_number" to "phone number")
            "companion.generate_image", "companion.propose_image" -> listOf("scene" to "final self-contained English ComfyUI production prompt summarized by the character from the complete current context; visual content only")
            "companion.propose_private_image" -> listOf("character_id" to "current group member id", "scene" to "final self-contained English ComfyUI production prompt summarized by that character from the complete current context; visual content only")
            "companion.send_private_message" -> listOf("character_id" to "current group character id", "message" to "natural private message")
            "companion.send_group_message" -> listOf("character_id" to "current private-chat character id", "group_thread_id" to "target group thread id", "message" to "natural group message")
            else -> emptyList()
        }
        val optionalFields = buildList {
            if (capabilityId == "companion.generate_image" || capabilityId == "companion.propose_image") {
                add("participant_character_ids" to "comma-separated current group character ids; required only for a group image")
                add("group_image_mode" to "AI-selected group image arrangement: SINGLE, SEPARATE, or TOGETHER")
            }
            if (capabilityId == "companion.generate_image" || capabilityId == "companion.propose_image" ||
                capabilityId == "companion.propose_private_image" || capabilityId == "companion.generate_moment_image"
            ) {
                add("size" to "optional OpenAI Images size override such as 512x768; defaults to 768x768")
            }
        }
        return buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                (fields + optionalFields).forEach { (name, description) ->
                    put(name, buildJsonObject { put("type", "string"); put("description", description) })
                }
            })
            put("required", buildJsonArray { fields.forEach { (name, _) -> add(kotlinx.serialization.json.JsonPrimitive(name)) } })
            put("additionalProperties", false)
        }
    }

    private fun parseStringArguments(raw: String): Map<String, String> = runCatching {
        Json.parseToJsonElement(raw).jsonObject.mapValues { (_, value) -> value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    /**
     * 用户点「取消」时调用：按**提示词内容**通知网关把这张图真的掐掉。
     * 纯 OpenAI 路径没有 provider task id，只能按内容找正在跑/排队的那条。
     */
    suspend fun cancelImageByPrompt(
        profile: ModelProfileEntity,
        apiKey: String,
        prompt: String,
        size: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("prompt", prompt)
                put("size", ImageGenerationRequestPolicy.normalizeSize(size))
            }.toString()
            val request = Request.Builder()
                .url(endpoint(profile, "/images/cancel"))
                .header("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody(JSON))
                .build()
            imageHttp(profile).newCall(request).execute().use { response ->
                response.body?.string().orEmpty()
            }
        }
    }

    private fun imageUrlFromResponse(body: String): String {
        return ImageGenerationResponsePolicy.imageReference(body)
    }

    /**
     * 网关回填的**最终提示词**（OpenAI 标准 revised_prompt）。
     * 拿不到、是 null、或 JSON 解不出来都当空串——缺这个字段不能让整次出图失败。
     */
    private fun revisedPromptFromResponse(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("revised_prompt")?.jsonPrimitive?.contentOrNull?.trim()
    }.getOrNull().orEmpty()

    private fun responseError(code: Int, body: String): String = when (code) {
        400 -> "HTTP 400：图像模型或供应商接口不支持这次请求。"
        401 -> "HTTP 401：身份验证失败。请确认 API Key、服务地址和账户状态。"
        403 -> "HTTP 403：该 Key 没有访问此模型的权限。"
        404 -> "HTTP 404：服务地址或接口不兼容。请填写服务根地址，程序会自动使用 /v1。"
        408, 504 -> "HTTP $code：服务响应超时；如果持续超过 10 分钟，通常是显存不足或后端模型卡住，请重试。"
        502, 503 -> "HTTP $code：电脑端网关或后端 Ollama/ComfyUI 暂时离线，请确认服务已启动后重试。"
        429 -> "HTTP 429：请求过于频繁或账户额度不足。"
        else -> "HTTP $code：${body.take(180).replace(Regex("sk-[A-Za-z0-9_-]+"), "[已隐藏]")}" 
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val QWEN_TASK_POLL_INTERVAL_MS = 1_500L
        const val COMFY_GATEWAY_TASK_POLL_INTERVAL_MS = 2_000L
        /** 同一条出图请求的复用窗口：这段时间内同样的图不再重复提交。 */
        const val IMAGE_SUBMISSION_WINDOW_MS = 10 * 60 * 1000L
        const val POLL_CONNECT_TIMEOUT_SECONDS = 8L
        const val POLL_READ_TIMEOUT_SECONDS = 10L
        const val TAG = "TongpinImageTiming"
    }
}
