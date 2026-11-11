package com.aster.companion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionRepository
import com.aster.companion.data.CompanionBackupManager
import com.aster.companion.data.DeviceTransferManager
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.CompanionTimelineRecorder
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.MemoryEntity
import com.aster.companion.data.PendingActionEntity
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.data.ImageApiPoolEntity
import com.aster.companion.data.ThreadEntity
import com.aster.companion.data.ThreadSummaryEntity
import com.aster.companion.data.ThreadParticipantEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.data.WorldTimelineEventEntity
import com.aster.companion.data.MomentPostEntity
import com.aster.companion.data.MomentMediaEntity
import com.aster.companion.data.MomentCommentEntity
import com.aster.companion.data.MomentReactionEntity
import com.aster.companion.data.FamilyGroupFactEntity
import com.aster.companion.data.GroupMemberStateEntity
import com.aster.companion.data.GroupSettingsEntity
import com.aster.companion.agent.ConversationTurnCoordinator
import com.aster.companion.agent.CompanionReplyPlanParser
import com.aster.companion.agent.ComfyTagPromptPolicy
import com.aster.companion.agent.DiaryGenerationCoordinator
import com.aster.companion.agent.ImageUnderstandingCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.aster.companion.domain.MemoryKind
import com.aster.companion.domain.CompanionAvailabilityParser
import com.aster.companion.domain.CompanionAvailabilitySignal
import com.aster.companion.domain.CompanionAvailabilityTargetPolicy
import com.aster.companion.domain.ImageConversationFailurePolicy
import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.system.CompanionActionCoordinator
import com.aster.companion.system.ShizukuFixedActionRouter
import com.aster.companion.system.ShizukuStatus
import com.aster.companion.system.ShizukuStatusProvider
import com.aster.companion.system.CompanionTaskRecoveryCoordinator
import com.aster.companion.data.CompanionSnapshotManager
import com.aster.companion.data.CompanionDatabaseRecoveryGate
import com.aster.companion.data.CharacterLivingStateEntity
import android.util.Log
import com.aster.companion.proactive.SystemModeState
import com.aster.companion.proactive.CompanionSystemService
import com.aster.companion.proactive.ProactiveEventStore
import com.aster.companion.proactive.SensingDiagnosticStore
import com.aster.companion.proactive.SensingSourceStatus
import com.aster.companion.agent.OpenAiCompatibleClient
import com.aster.companion.agent.ImagePromptDebugConfig
import com.aster.companion.agent.ImagePromptDebugSettings
import com.aster.companion.agent.ImageGenerationPromptPolicy
import com.aster.companion.agent.MomentsAgentCoordinator
import com.aster.companion.agent.ScenePromptCoordinator
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.ConfirmationRequirement
import com.aster.companion.lite.LiteProductConfig
import com.aster.companion.lite.ScenePhotoRequest
import com.aster.companion.lite.ScenePhotoStage
import com.aster.companion.agent.world.WorldStateRepository
import com.aster.companion.ui.ThreadPreview
import com.aster.companion.ui.ThreadPreviewBuilder
import javax.inject.Inject
import java.util.UUID

data class CompanionHomeState(
    val characters: List<CharacterEntity> = emptyList(),
    val threads: List<ThreadEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val diaryPhotos: List<com.aster.companion.data.DiaryPhotoEntity> = emptyList(),
    val worldStates: List<WorldStateEntity> = emptyList(),
    val worldInvitations: List<WorldTimelineEventEntity> = emptyList(),
    val threadSummaries: List<com.aster.companion.data.ThreadSummaryEntity> = emptyList(),
    val pendingActions: List<PendingActionEntity> = emptyList(),
    val modelProfiles: List<ModelProfileEntity> = emptyList(),
    val imageApiPool: List<ImageApiPoolEntity> = emptyList(),
    val imageGenerationActions: List<PendingActionEntity> = emptyList(),
    val imagePromptDebugConfig: ImagePromptDebugConfig = ImagePromptDebugConfig(),
    val chatDisplayPreferences: ChatDisplayPreferences = ChatDisplayPreferences(),
    val threadParticipants: Map<String, List<String>> = emptyMap(),
    val familyGroupFacts: List<FamilyGroupFactEntity> = emptyList(),
    val groupMemberStates: List<GroupMemberStateEntity> = emptyList(),
    val groupSettings: List<GroupSettingsEntity> = emptyList(),
    val systemModeEnabled: Boolean = false,
    val proactiveCharacterIds: Set<String> = emptySet(),
    val proactiveSelectionConfigured: Boolean = false,
    val proactivelyPausedCharacterIds: Set<String> = emptySet(),
    val sensingStatuses: List<SensingSourceStatus> = emptyList(),
    val shizukuStatus: ShizukuStatus = ShizukuStatus(false, false, false)
    , val backupStatus: String? = null,
    val availableModels: Map<String, List<String>> = emptyMap(),
    val modelFetchStatus: Map<String, String> = emptyMap(),
    val replyingThreadId: String? = null,
    val replyingCharacterId: String? = null,
    val threadPreviews: Map<String, ThreadPreview> = emptyMap(),
    val diaryWritingThreadId: String? = null,
    val recoveryProtectionMessage: String? = null
    , val momentPosts: List<MomentPostEntity> = emptyList()
    , val momentMedia: List<MomentMediaEntity> = emptyList()
    , val momentComments: List<MomentCommentEntity> = emptyList()
    , val momentReactions: List<MomentReactionEntity> = emptyList()
) {
    /**
     * 记忆同步的健康提示（null = 正常）。
     *
     * 会话状态更新失败以前是 `runCatching` 静默吞掉：历史继续被截断，用户完全不知道记忆在丢。
     * 现在连续失败到 [com.aster.companion.agent.ConversationStateUpdater.ALERT_FAILURES] 次就在设置页露出来。
     */
    val memorySyncWarning: String?
        get() {
            val worst = threadSummaries
                .filter { it.failureCount >= com.aster.companion.agent.ConversationStateUpdater.ALERT_FAILURES }
                .maxByOrNull { it.failureCount }
                ?: return null
            val reason = worst.lastError?.take(60).orEmpty()
            return "记忆同步已连续失败 ${worst.failureCount} 次：$reason（旧状态保留着，会继续重试）"
        }
}

private data class SystemUiState(
    val enabled: Boolean,
    val proactiveCharacterIds: Set<String>,
    val selectionConfigured: Boolean,
    val pausedCharacterIds: Set<String>,
    val sensingStatuses: List<SensingSourceStatus> = emptyList()
)

private data class CompanionRuntime(
    val diaryPhotos: List<com.aster.companion.data.DiaryPhotoEntity>,
    val worldStates: List<WorldStateEntity>,
    val pendingActions: List<PendingActionEntity>,
    val profiles: List<ModelProfileEntity>,
    val imageApiPool: List<ImageApiPoolEntity>,
    val imageGenerationActions: List<PendingActionEntity>,
    val summaries: List<ThreadSummaryEntity>
    , val worldInvitations: List<WorldTimelineEventEntity>
)

private data class MomentRuntime(
    val posts: List<MomentPostEntity>,
    val media: List<MomentMediaEntity>,
    val comments: List<MomentCommentEntity>,
    val reactions: List<MomentReactionEntity>
)

private data class GroupRuntime(
    val facts: List<FamilyGroupFactEntity>,
    val memberStates: List<GroupMemberStateEntity>,
    val settings: List<GroupSettingsEntity>
)

private data class ModelUiState(
    val backupStatus: String?,
    val availableModels: Map<String, List<String>>,
    val modelFetchStatus: Map<String, String>,
    val imagePromptDebugConfig: ImagePromptDebugConfig,
    val chatDisplayPreferences: ChatDisplayPreferences
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionHomeViewModel @Inject constructor(
    /** 时间胶囊的自动写者；这里只用来支持"立即同步"这个手动入口。 */
    private val lifeTicker: com.aster.companion.agent.life.LifeTicker,
    private val repository: CompanionRepository,
    private val dao: CompanionDao,
    private val interruptManager: com.aster.companion.agent.world.InterruptManager,
    /** 「他正在这个会话里回复」：主动陪伴据此让路（见 [com.aster.companion.proactive.ThreadActivitySignal]）。 */
    private val threadActivitySignal: com.aster.companion.proactive.ThreadActivitySignal,
    private val worldScheduler: com.aster.companion.agent.world.WorldScheduler,
    private val worldStateRepository: WorldStateRepository,
    private val timeline: CompanionTimelineRecorder,
    private val conversationTurnCoordinator: ConversationTurnCoordinator,
    private val imageUnderstandingCoordinator: ImageUnderstandingCoordinator,
    private val diaryGenerationCoordinator: DiaryGenerationCoordinator,
    private val actionCoordinator: CompanionActionCoordinator,
    private val client: OpenAiCompatibleClient,
    private val secretCipher: CompanionSecretCipher,
    private val systemModeState: SystemModeState,
    private val sensingDiagnostics: SensingDiagnosticStore,
    private val imagePromptDebugSettings: ImagePromptDebugSettings,
    private val imageRuleSettings: com.aster.companion.lite.ImageRuleSettings,
    private val renderPromptBuilder: com.aster.companion.agent.CharacterRenderPromptBuilder,
    private val chatDisplaySettings: ChatDisplaySettings,
    private val shizukuStatusProvider: ShizukuStatusProvider,
    private val shizukuRouter: ShizukuFixedActionRouter,
    private val backupManager: CompanionBackupManager,
    private val deviceTransferManager: DeviceTransferManager,
    private val snapshotManager: CompanionSnapshotManager,
    private val taskRecovery: CompanionTaskRecoveryCoordinator,
    private val momentsAgent: MomentsAgentCoordinator,
    private val recoveryGate: CompanionDatabaseRecoveryGate,
    private val proactiveEvents: ProactiveEventStore,
    private val peekPhotoCoordinator: com.aster.companion.agent.PeekPhotoCoordinator,
    private val conversationStateUpdater: com.aster.companion.agent.ConversationStateUpdater,
    private val worldSimulation: com.aster.companion.agent.WorldBatchSimulationCoordinator,
    private val scenePromptCoordinator: ScenePromptCoordinator,
    private val proactiveCoordinator: com.aster.companion.lite.proactive.LiteProactiveCoordinator,
    private val liteScenePhotoBus: com.aster.companion.lite.LiteScenePhotoBus,
    private val imageSizeStore: com.aster.companion.lite.LiteImageSizeStore,
    private val memoryRollup: com.aster.companion.lite.memory.MemoryRollupCoordinator,
    private val appUpdate: com.aster.companion.update.AppUpdateCoordinator,
    private val remotePrompts: com.aster.companion.update.RemotePrompts,
    private val backgroundIdle: com.aster.companion.lite.BackgroundIdleStore,
    private val proactiveToggles: com.aster.companion.lite.proactive.LiteProactiveToggles,
    /** 用户选的模型上下文长度（16K/32K/64K）：决定请求能拼多大。 */
    private val contextWindow: com.aster.companion.lite.ContextWindowStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    /** Lite：私聊「发照片」的 Scene 预览状态；null 表示当前没有预览卡（计划书 §5.4）。 */
    private val _scenePhoto = MutableStateFlow<ScenePhotoRequest?>(null)

    /** 聊天页预览卡的数据源：PLANNING → READY（可改标签）→ GENERATING / FAILED。 */
    val scenePhoto: StateFlow<ScenePhotoRequest?> = _scenePhoto

    val imageSize: StateFlow<String> = imageSizeStore.size

    /**
     * 设置页「后台推演」：后台 LLM 工作的空闲阈值。0 = 不限制（旧行为）。
     * 阈值之内你还在聊，世界推演 / 朋友圈决策 / 日记都让路，等闲下来再补跑。
     */
    val backgroundIdleMs: StateFlow<Long> = backgroundIdle.idleMs

    fun setBackgroundIdleMs(idleMs: Long) = backgroundIdle.set(idleMs)

    /**
     * 设置页「模型上下文」：我用的模型能吃多长（16K / 32K / 64K / 96K / 128K，可自定义）。
     * 请求的人设、状态与预算大小都按它算，避免超上下文被服务端从中间截断。
     */
    val contextTokens: StateFlow<Int> = contextWindow.contextTokens

    fun setContextTokens(tokens: Int) = contextWindow.set(tokens)

    /**
     * 设置页「对话窗口条数」：每轮最多带几条真实历史（默认 40，可自定义）。
     * 调大 = 记得更多、但每轮预填充更久；调小 = 更快更省，更早的事靠状态/长期记忆承接。
     */
    val contextWindowMessages: StateFlow<Int> = contextWindow.windowMessages

    fun setContextWindowMessages(messages: Int) = contextWindow.setWindowMessages(messages)

    /**
     * 最近一次出图的**最终提示词**：优先是网关回填的 revised_prompt（真正送去渲染的那份），
     * 落库在 LAST_SCENE；没有记录时为空串。设置页「最近一次出图的最终提示词」读它。
     */
    val lastScenePrompt: StateFlow<String> = dao.observeWorldStates()
        .map { states ->
            states.filter { it.scope == "LAST_SCENE" }
                .maxByOrNull { state -> state.updatedAt }
                ?.content
                .orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** App 自更新状态，设置页「检查更新」按它显示按钮/进度/结果。 */
    private val _updateState = MutableStateFlow<com.aster.companion.update.UpdateState>(
        com.aster.companion.update.UpdateState.Idle
    )
    val updateState: StateFlow<com.aster.companion.update.UpdateState> = _updateState

    private var pendingRelease: com.aster.companion.update.AppUpdateCoordinator.Release? = null

    /** 已安装的版本名（设置页版本行显示）。 */
    val installedVersionName: String get() = appUpdate.currentVersionName()

    /** 去网关查有没有新版本。 */
    fun checkUpdate(baseUrl: String) = viewModelScope.launch(crashGuard) {
        _updateState.value = com.aster.companion.update.UpdateState.Checking
        // 提示词是文本，不该为了改一句话重新装包：点这里顺手强制同步一次远程提示词。
        val promptCount = runCatching { remotePrompts.refreshNow() }
            .getOrDefault(com.aster.companion.update.RemotePromptStore.size())
        appUpdate.check(baseUrl)
            .onSuccess { release ->
                pendingRelease = release
                _updateState.value = if (release.versionCode > appUpdate.currentVersionCode()) {
                    com.aster.companion.update.UpdateState.Available(
                        release.versionName,
                        release.versionCode,
                        release.sizeBytes,
                        promptCount
                    )
                } else {
                    com.aster.companion.update.UpdateState.UpToDate(promptCount)
                }
            }
            .onFailure { error ->
                _updateState.value =
                    com.aster.companion.update.UpdateState.Failed(error.message ?: "检查更新失败")
            }
    }

    /** 下载已发现的新版本并调起系统安装。 */
    fun installUpdate(baseUrl: String) = viewModelScope.launch(crashGuard) {
        val release = pendingRelease ?: return@launch
        _updateState.value = com.aster.companion.update.UpdateState.Downloading(0)
        appUpdate.download(baseUrl, release) { percent ->
            _updateState.value = com.aster.companion.update.UpdateState.Downloading(percent)
        }
            .onSuccess { apk ->
                if (appUpdate.install(apk)) {
                    _updateState.value = com.aster.companion.update.UpdateState.Idle
                } else {
                    appUpdate.openInstallPermissionSettings()
                    _updateState.value = com.aster.companion.update.UpdateState.Failed(
                        "需要先允许「安装未知应用」，已为你打开设置"
                    )
                }
            }
            .onFailure { error ->
                _updateState.value =
                    com.aster.companion.update.UpdateState.Failed(error.message ?: "下载失败")
            }
    }

    fun setImageSize(size: String) {
        imageSizeStore.set(size)
    }

    /** 她能不能主动发朋友圈 / 主动发照片（设置页两个开关，默认都开）。 */
    val proactiveMomentsEnabled: StateFlow<Boolean> = proactiveToggles.momentsEnabled
    val proactivePhotoEnabled: StateFlow<Boolean> = proactiveToggles.photoEnabled
    fun setProactiveMomentsEnabled(enabled: Boolean) = proactiveToggles.setMomentsEnabled(enabled)

    /** 主动性节律：按她自己的节奏找你（默认关）。见 [com.aster.companion.domain.UrgePolicy]。 */
    val proactiveUrgeEnabled: StateFlow<Boolean> = proactiveToggles.urgeEnabled

    /** 联系温度：更主动一点（意愿门槛降低、最小间隔 30 分钟）。默认关 = 严格档。 */
    val proactiveRelaxedContact: StateFlow<Boolean> = proactiveToggles.relaxedContact

    fun setProactiveUrgeEnabled(enabled: Boolean) = proactiveToggles.setUrgeEnabled(enabled)

    fun setProactiveRelaxedContact(enabled: Boolean) = proactiveToggles.setRelaxedContact(enabled)

    /** 世界事件播种：她"外面"偶尔发生的小事（默认关）。见 [com.aster.companion.domain.WorldSeedingPolicy]。 */
    val proactiveWorldSeedingEnabled: StateFlow<Boolean> = proactiveToggles.worldSeeding

    fun setProactiveWorldSeeding(enabled: Boolean) = proactiveToggles.setWorldSeeding(enabled)
    fun setProactivePhotoEnabled(enabled: Boolean) = proactiveToggles.setPhotoEnabled(enabled)

    private val companionContent = combine(dao.observeCharacters(), dao.observeThreads(), dao.observeMemories()) { characters, threads, memories ->
        Triple(characters, threads, memories)
    }
    @Suppress("UNCHECKED_CAST")
    private val companionRuntime = combine(
        dao.observeDiaryPhotos(),
        dao.observeWorldStates(),
        dao.observePendingActions(),
        dao.observeModelProfiles(),
        dao.observeImageApiPool(),
        dao.observeThreadSummaries(),
        dao.observeWorldInvitations()
    ) { array ->
        CompanionRuntime(
            diaryPhotos = array[0] as List<com.aster.companion.data.DiaryPhotoEntity>,
            worldStates = array[1] as List<WorldStateEntity>,
            pendingActions = array[2] as List<PendingActionEntity>,
            profiles = array[3] as List<ModelProfileEntity>,
            imageApiPool = array[4] as List<ImageApiPoolEntity>,
            imageGenerationActions = emptyList(),
            summaries = array[5] as List<ThreadSummaryEntity>,
            worldInvitations = array[6] as List<WorldTimelineEventEntity>
        )
    }
    private val companionRuntimeWithImageActions = combine(companionRuntime, dao.observeImageGenerationActions()) { runtime, imageGenerationActions ->
        runtime.copy(imageGenerationActions = imageGenerationActions)
    }
    private val groupRuntime = combine(dao.observeFamilyGroupFacts(), dao.observeGroupMemberStates(), dao.observeGroupSettings()) { facts, memberStates, settings ->
        GroupRuntime(facts, memberStates, settings)
    }
    // 世界页删除后，「场景投影」就没有消费者了：不再把它并进 UI state。
    // 这一步顺带省掉一路 5 源 combine（角色 / 用户状态 / 地点 / 角色 / 此刻状态），
    // 以前每次这些表写一行都会重算一遍场景文案。
    private val baseDataState = combine(companionContent, companionRuntimeWithImageActions, groupRuntime) { content, runtime, groups ->
        CompanionHomeState(
            characters = content.first,
            threads = content.second,
            memories = content.third,
            diaryPhotos = runtime.diaryPhotos,
            worldStates = runtime.worldStates,
            pendingActions = runtime.pendingActions,
            modelProfiles = runtime.profiles,
            imageApiPool = runtime.imageApiPool,
            imageGenerationActions = runtime.imageGenerationActions,
            worldInvitations = runtime.worldInvitations,
            // 「记忆同步异常」提示只读 threadSummaries（见 CompanionHomeState.memorySyncWarning）。
            // 这一路以前是断的：summaries 采集了却从没进过 UI state，于是 threadSummaries 恒为空、
            // 连续失败提示永远不会出现——记忆在丢，用户完全看不到。补上这一次传递。
            threadSummaries = runtime.summaries,
            familyGroupFacts = groups.facts,
            groupMemberStates = groups.memberStates,
            groupSettings = groups.settings
        )
    }
    private val momentState = combine(dao.observeMomentPosts(), dao.observeMomentMedia(), dao.observeMomentComments(), dao.observeMomentReactions()) { posts, media, comments, reactions ->
        MomentRuntime(posts, media, comments, reactions)
    }
    private val dataWithMoments = combine(baseDataState, momentState) { data, moments ->
        data.copy(
            momentPosts = moments.posts,
            momentMedia = moments.media,
            momentComments = moments.comments,
            momentReactions = moments.reactions,
            imageGenerationActions = ImageGenerationTaskPresentation.merge(data.imageGenerationActions, momentImageTasks(moments.posts, moments.media))
        )
    }
    private val dataWithPreviews = combine(dataWithMoments, dao.observeAllMessages()) { data, messages ->
        data.copy(threadPreviews = ThreadPreviewBuilder.build(messages))
    }
    private val dataState = combine(dataWithPreviews, dao.observeParticipants()) { data, participants ->
        data.copy(threadParticipants = participants.groupBy(ThreadParticipantEntity::threadId) { it.characterId })
    }
    private val rawSystemState = combine(systemModeState.enabledFlow, systemModeState.proactiveCharacterIdsFlow, systemModeState.proactiveSelectionConfiguredFlow, systemModeState.pausedCharacterIdsFlow, shizukuStatusProvider.revision) { enabled, proactiveCharacterIds, configured, pausedCharacterIds, _ ->
        SystemUiState(enabled, proactiveCharacterIds, configured, pausedCharacterIds)
    }
    private val systemState = combine(rawSystemState, sensingDiagnostics.revision) { system, _ ->
        system.copy(sensingStatuses = sensingDiagnostics.statuses())
    }
    private val backupStatus = MutableStateFlow<String?>(null)
    private val availableModels = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val modelFetchStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    private val replyingThreadId = MutableStateFlow<String?>(null)
    private val replyingCharacterId = MutableStateFlow<String?>(null)
    private val diaryWritingThreadId = MutableStateFlow<String?>(null)
    private val modelUiState = combine(backupStatus, availableModels, modelFetchStatus, imagePromptDebugSettings.configFlow, chatDisplaySettings.preferencesFlow) { backupStatus, models, fetchStatus, imagePromptDebugConfig, chatDisplayPreferences ->
        ModelUiState(backupStatus, models, fetchStatus, imagePromptDebugConfig, chatDisplayPreferences)
    }
    private val baseState: StateFlow<CompanionHomeState> = combine(dataState, systemState, modelUiState) { data, system, modelUi ->
        data.copy(
            systemModeEnabled = system.enabled,
            proactiveCharacterIds = system.proactiveCharacterIds,
            proactiveSelectionConfigured = system.selectionConfigured,
            proactivelyPausedCharacterIds = system.pausedCharacterIds,
            sensingStatuses = system.sensingStatuses,
            shizukuStatus = shizukuStatusProvider.current(shizukuRouter.isCommandChannelAlive),
            backupStatus = modelUi.backupStatus,
            availableModels = modelUi.availableModels,
            modelFetchStatus = modelUi.modelFetchStatus,
            imagePromptDebugConfig = modelUi.imagePromptDebugConfig,
            chatDisplayPreferences = modelUi.chatDisplayPreferences,
            recoveryProtectionMessage = recoveryGate.recoveryReason?.let { reason ->
                when (reason) {
                    com.aster.companion.domain.CompanionRecoveryReason.MissingKeyForExistingDatabase -> "Detected encrypted companion data without its key record. Recovery protection is active; the primary database has not been overwritten."
                    com.aster.companion.domain.CompanionRecoveryReason.MissingDatabaseAfterInitialization -> "Detected a missing initialized companion database. Recovery protection is active and no default character will be created."
                    com.aster.companion.domain.CompanionRecoveryReason.EmptyDatabaseAfterInitialization -> "Detected an empty initialized companion database. Recovery protection is active and no new data will be written."
                }
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanionHomeState())
    val state: StateFlow<CompanionHomeState> = combine(baseState, replyingThreadId, diaryWritingThreadId, replyingCharacterId) { current, threadId, diaryThreadId, characterId ->
        current.copy(
            replyingThreadId = threadId,
            replyingCharacterId = characterId,
            diaryWritingThreadId = diaryThreadId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanionHomeState())

    init {
        // Lite 主动消息（分册 §4）：私聊停止后约 5 分钟的余韵探活。
        proactiveCoordinator.start()
        // 聊天层推来的生图草稿 → 预览卡（必须用户确认才出图）。
        viewModelScope.launch(crashGuard) {
            liteScenePhotoBus.request.collect { incoming ->
                if (incoming != null) {
                    _scenePhoto.value = incoming
                    liteScenePhotoBus.clear()
                }
            }
        }
        viewModelScope.launch(crashGuard) {
            repository.seedDefaults()
            // 世界引擎已下线：不再做世界恢复与离线补算。
            //  - `recoverOnAppStart` 会跑一次世界调度（胶囊的第二个写者）
            //  - `catchUpOffline` 用"按小时推进"覆盖自主生活 tick 按真实流逝算出的需求数值（双写冲突）
            // 但**地点目录仍然要初始化**：她换事做时要把 locationId 翻成地点名写进活动文本。
            runCatching { worldStateRepository.initialize() }
            // **启动就同步一次远程提示词**：她的说话方式（`chat.liteStyle`）和生图规则都在网关上，
            // 以前只有"出图"或手动点「检查更新」才会拉 —— 于是全新安装/清过数据的头几轮聊天
            // 用的是 App 内置兜底文案，跟你在网关上调好的那套不一样。这里补上（内部有三分钟节流，很轻）。
            runCatching { remotePrompts.refreshIfStale() }
            // 「他说了晚安」按下的主动联系：越过一夜就自动放开（服务里每拍也会清，这里保证开 App 就生效）。
            runCatching { systemModeState.resumeExpiredPauses() }
            snapshotManager.createDailyIfDue()
            snapshotManager.createUpgradeSafetySnapshotIfDue()
            taskRecovery.recoverOnAppStart()
            dao.removeMalformedStreamTokens()
            dao.allMessages()
                .filter { message -> !message.fromUser && message.body.contains("\"tool_calls\"") && message.body.contains("\"decision\"") }
                .forEach { message ->
                    val repaired = CompanionReplyPlanParser.visibleReply(message.body).ifBlank { "这条回复未能正常显示。" }
                    dao.updateMessageBody(message.id, repaired)
                }
        }
    }

    private val selectedThreadId = MutableStateFlow<String?>(null)
    val livingStates: StateFlow<List<CharacterLivingStateEntity>> = dao.observeAllLivingStates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val messages: StateFlow<List<MessageEntity>> = selectedThreadId.flatMapLatest { threadId ->
        if (threadId == null) flowOf(emptyList()) else dao.observeMessages(threadId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedThread: StateFlow<String?> = selectedThreadId

    fun createCharacterAndPrivate(name: String, personality: String, appearance: String, referenceUri: String?, styleReferenceUri: String?, avatarUri: String?, relationshipPrompt: String = "") = viewModelScope.launch(crashGuard) {
        val characterId = repository.saveCharacter(null, name, avatarUri, personality, appearance, referenceUri, styleReferenceUri, relationshipPrompt)
        selectedThreadId.value = repository.createPrivateThread(characterId, "和${name.trim()}的新对话")
    }

    fun createGroup() = viewModelScope.launch(crashGuard) {
        val characters = state.value.characters.take(2)
        if (characters.size == 2) selectedThreadId.value = repository.createGroupThread(characters.map { it.id }, "新的群聊")
    }

    fun createGroup(characterIds: List<String>, title: String, groupType: String = "GENERAL") = viewModelScope.launch(crashGuard) {
        selectedThreadId.value = repository.createGroupThread(characterIds, title.ifBlank { "新的群聊" }, groupType = groupType)
    }

    fun selectThread(threadId: String) {
        selectedThreadId.value = threadId
        viewModelScope.launch(crashGuard) { dao.markCompanionMessagesRead(threadId, System.currentTimeMillis()) }
    }
    fun closeThread() {
        val threadId = selectedThreadId.value
        selectedThreadId.value = null
        threadId?.let { id -> viewModelScope.launch(crashGuard) { dao.markCompanionMessagesRead(id, System.currentTimeMillis()) } }
    }

    fun deleteThread(threadId: String) = viewModelScope.launch(crashGuard) {
        repository.deleteThread(threadId)
        if (selectedThreadId.value == threadId) selectedThreadId.value = null
    }

    fun clearThreadHistory(threadId: String) = viewModelScope.launch(crashGuard) {
        repository.clearThreadHistory(threadId)
        memoryRollup.resetForThread(threadId)
    }

    fun deletePrivateFriend(threadId: String) = viewModelScope.launch(crashGuard) {
        if (repository.deletePrivateFriend(threadId) == com.aster.companion.domain.FriendDeletionEligibility.Allowed && selectedThreadId.value == threadId) {
            selectedThreadId.value = null
        }
    }

    fun clearDevelopmentTestData() = viewModelScope.launch(crashGuard) {
        repository.clearDevelopmentTestData()
        selectedThreadId.value = null
    }

    fun updateGroup(threadId: String, title: String, characterIds: List<String>, atmosphere: GroupAtmosphere = GroupAtmosphere.DAILY, proactiveEnabled: Boolean = true) = viewModelScope.launch(crashGuard) {
        repository.updateGroupThread(threadId, title, characterIds, atmosphere, proactiveEnabled)
    }

    fun saveFamilyGroupFact(threadId: String, factId: String?, summary: String, allowedInGroup: Boolean) = viewModelScope.launch(crashGuard) {
        val existing = factId?.let { id -> dao.allFamilyGroupFacts().firstOrNull { it.id == id } }
        repository.saveFamilyGroupFact(
            id = factId,
            groupThreadId = threadId,
            ownerCharacterId = existing?.ownerCharacterId,
            summary = summary,
            sourceThreadId = existing?.sourceThreadId ?: threadId,
            sourceKind = existing?.sourceKind ?: "USER_EDITED",
            expiresAt = existing?.expiresAt,
            sensitivity = existing?.sensitivity ?: "NORMAL",
            allowedInGroup = allowedInGroup
        )
    }

    fun deleteFamilyGroupFact(factId: String) = viewModelScope.launch(crashGuard) {
        repository.deleteFamilyGroupFact(factId)
    }

    /** 偷看：生成角色此刻的照片放进对话，角色不知情。 */
    fun peekPhotoGenerate(threadId: String, characterId: String? = null) = viewModelScope.launch(crashGuard) {
        peekPhotoCoordinator.peek(threadId, characterId)
            .onFailure { error ->
                Log.w("Peek", "Peek photo failed: ${error.message}")
                // 以前失败是静默的（点了"偷看照片"什么都不发生）；现在给一条明确提示。
                android.widget.Toast.makeText(
                    appContext,
                    "偷看没成功：${error.message?.take(60) ?: "未知错误"}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
    }

    /** 同步私聊到群聊：把指定角色最近的 30 条私聊强制写入群聊上下文（所有群成员都知道）。 */
    fun syncPrivateToGroup(groupThreadId: String, characterId: String) = viewModelScope.launch(crashGuard) {
        val character = dao.character(characterId)
        val privateThread = character?.let { dao.latestPrivateThread(it.id) }
        val messages = privateThread?.let { dao.recentMessages(it.id, 30).asReversed() }.orEmpty()
        if (character == null || privateThread == null || messages.isEmpty()) {
            android.widget.Toast.makeText(
                appContext,
                "同步失败：${character?.name ?: "角色"} 没有可同步的私聊记录",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return@launch
        }
        val content = buildString {
            appendLine("【私聊同步】${character.name} 与用户的最近私聊（已同步到群里，大家自然知道）：")
            messages.forEach { message ->
                val speaker = if (message.fromUser) "用户" else character.name
                appendLine("$speaker：${message.body.take(80)}")
            }
        }.trim()
        dao.upsertWorldState(
            com.aster.companion.data.WorldStateEntity(
                id = "group-sync:$groupThreadId:$characterId",
                characterId = characterId,
                threadId = groupThreadId,
                scope = "GROUP_SYNCED_PRIVATE",
                content = content,
                updatedAt = System.currentTimeMillis()
            )
        )
        android.util.Log.i("SyncPrivate", "synchronized ${character.name} ${messages.size} msgs to group $groupThreadId")
        android.widget.Toast.makeText(appContext, "已把 ${character.name} 的最近私聊同步到群聊", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 隐身模式：家庭模式下开启后，用户的所有行为动作都是偷偷的（角色不知道是你干的，只感觉有动静）。 */
    fun toggleStealth(threadId: String) = viewModelScope.launch(crashGuard) {
        val current = dao.worldStatesByThread(threadId, "STEALTH_MODE").firstOrNull()
        val newOn = current?.content != "ON"
        dao.upsertWorldState(
            com.aster.companion.data.WorldStateEntity(
                id = "stealth:$threadId",
                characterId = null,
                threadId = threadId,
                scope = "STEALTH_MODE",
                content = if (newOn) "ON" else "OFF",
                updatedAt = System.currentTimeMillis()
            )
        )
        android.widget.Toast.makeText(appContext, if (newOn) "隐身模式已开启（你的行为都是偷偷的）" else "隐身模式已关闭", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 隐身模式状态（按线程，缓存 flow 避免每次重组重置）。 */
    private val stealthFlows = mutableMapOf<String, kotlinx.coroutines.flow.StateFlow<Boolean>>()
    fun stealthMode(threadId: String): kotlinx.coroutines.flow.StateFlow<Boolean> = stealthFlows.getOrPut(threadId) {
        dao.observeStealthStates(threadId, "STEALTH_MODE")
            .map { states -> states.firstOrNull()?.content == "ON" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    }

    /** 位置更新：强制世界引擎立即推演，刷新角色状态（刷新后才有「在{大地点}，{小地点}」格式，距离/地点才能显示）。 */
    /**
     * 「立即同步」：手动把"她此刻的状态"（时间胶囊）跟最近的对话对齐。
     *
     * 自动流程要等静默满 [com.aster.companion.agent.life.LifeTicker.CONVERSATION_SETTLE_MS]
     * 才更新，所以她说"我去睡了"之后胶囊可能还显示旧状态；这个入口跳过那道等待。
     *
     * **是一次模型调用，必须走后台闸**：不然会跟聊天抢本机那几张并发槽。
     */
    // ------------------------------------------------------------ 内网 / 外网 双配置
    fun syncLivingState(characterId: String) = viewModelScope.launch(crashGuard) {
        val synced = com.aster.companion.lite.LiteBackgroundLlmGate.withPermit {
            lifeTicker.syncNow(characterId)
        }
        android.widget.Toast.makeText(
            appContext,
            if (synced) "她的状态已同步" else "同步失败：请检查对话模型配置或网络",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun refreshWorldState() = viewModelScope.launch(crashGuard) {
        runCatching { worldSimulation.forceSimulate() }
        android.widget.Toast.makeText(appContext, "已更新角色位置状态", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 切换群聊模式（群聊 ↔ 家庭）：家庭模式=真实家庭，面对面/喊话。切换后注入隐形引导词，教角色家庭模式行为。 */
    fun toggleGroupType(threadId: String, currentType: String) = viewModelScope.launch(crashGuard) {
        val newType = if (currentType == "FAMILY") "GENERAL" else "FAMILY"
        repository.setGroupType(threadId, newType)
        if (newType == "FAMILY") {
            val guide = buildString {
                appendLine("【家庭模式已开启】你们现在住在同一个家里——这不是远程群聊，而是真实家庭的面对面生活。")
                appendLine("距离感知：你知道家里人在哪个房间（同一个屋檐下的自然感知），但不知道她们具体在做什么；说话像在同一个屋檐下——近的就面对面说，远的就喊一声或走过去。")
                appendLine("私密：你可以小声对某位家人单独说话（用「（小声对XX说）内容」）或偷偷做点什么（用「（偷偷）...」）——只有当事人知道内容/原因，其他人只察觉有动静。看到带「（小声对XX说）」的消息是别人的悄悄话，你只能感觉有动静、听不清内容。用户也可以这样对你。")
                appendLine("从现在起，言行都按这个家庭的真实方式。")
            }.toString()
            dao.upsertWorldState(
                com.aster.companion.data.WorldStateEntity(
                    id = "family-guide:$threadId",
                    characterId = null,
                    threadId = threadId,
                    scope = "FAMILY_GUIDE",
                    content = guide,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        android.widget.Toast.makeText(appContext, if (newType == "FAMILY") "已切换为家庭模式（面对面/喊话）" else "已切换为普通群聊模式", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 同步上下文到角色状态：把最近 30 条私聊总结，作为角色的状态上下文（SYNCED_CONTEXT）注入对话。 */
    fun syncContextToCharacterState(threadId: String, characterId: String) = viewModelScope.launch(crashGuard) {
        val character = dao.character(characterId)
        val messages = dao.recentMessages(threadId, 30).asReversed()
        if (character == null || messages.isEmpty()) {
            android.widget.Toast.makeText(appContext, "同步失败：没有可总结的对话", android.widget.Toast.LENGTH_SHORT).show()
            return@launch
        }
        val transcript = messages.joinToString("\n") { message ->
            val speaker = if (message.fromUser) "用户" else character.name
            "$speaker：${message.body.take(80)}"
        }
        val profile = dao.modelProfile(com.aster.companion.agent.AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
        if (profile == null) {
            android.widget.Toast.makeText(appContext, "同步失败：对话模型未配置", android.widget.Toast.LENGTH_SHORT).show()
            return@launch
        }
        val state = dao.livingState(characterId)
        // ⚠️ **全仓唯一一处"用第二个模型调用产出可推导视图"的地方**（上游把这条列为硬纪律：
        // 派生视图必须可重建、不许再调模型写摘要，否则就会出现"摘要的摘要"层层有损）。
        // 这里容忍它，只因为它是**用户显式按下「同步上下文」**才发生的：
        // 失败还会弹「总结生成失败」，调用次数由用户决定，不存在后台悄悄烧钱的问题。
        // **新增代码请不要照抄这个模式**——结构化状态（ConversationStateFormat）与记忆都已经
        // 由各自的后台路径维护，能推导的东西就用推导的。
        val summary = conversationStateUpdater.summarizeTranscript(transcript, state?.currentActivity)
        if (summary.isBlank()) {
            android.widget.Toast.makeText(appContext, "同步失败：总结生成失败", android.widget.Toast.LENGTH_SHORT).show()
            return@launch
        }
        dao.upsertWorldState(
            com.aster.companion.data.WorldStateEntity(
                id = "synced-context:$characterId",
                characterId = characterId,
                threadId = threadId,
                scope = "SYNCED_CONTEXT",
                content = summary,
                updatedAt = System.currentTimeMillis()
            )
        )
        android.util.Log.i("SyncContext", "synced ${character.name} context (${messages.size} msgs): $summary")
        android.widget.Toast.makeText(appContext, "已把最近对话总结同步到 ${character.name} 的状态", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 删除一个生成任务记录（设置→生成任务里删除）；正在生成的任务先取消 ComfyUI 生成再删记录。 */
    fun deletePendingAction(actionId: String) = viewModelScope.launch(crashGuard) {
        val action = dao.pendingAction(actionId)
        if (action != null && action.state in setOf("RUNNING", "RETRYING")) {
            runCatching { actionCoordinator.cancelImage(actionId) }
        }
        dao.deletePendingAction(actionId)
    }

    /**
     * 删除一条消息（含照片消息）；照片消息连带删除对应的生成任务记录。
     *
     * [segmentIndex] 不为空时表示用户长按的是「拆段显示」拆出来的**一个气泡**：只把这一段
     * 从正文里去掉，这条回复剩下的段落原样保留。一个气泡不等于一条消息——删一段绝不能把
     * 整条回复（用户眼里的「一整段对话」）删掉。
     */
    /**
     * 删除**一整轮问答**：这一轮里你说的那句 + 她连着回的那串（拆段气泡也一起删）。
     *
     * 边界按"说话人切换"划：往前找到最近一条用户消息当这一轮的开头，往后直到下一条用户消息为止
     * （她主动开的话头，前面没有用户消息时就从线程第一条开算）。
     * `stream:` 占位不删 —— 那是正在生成中的回复，删了会把在飞的流弄断。
     */
    fun deleteExchangeTurn(threadId: String, messageId: String) = viewModelScope.launch(crashGuard) {
        val ordered = dao.allMessagesForThread(threadId)
        // 兼容两种 id：真正的主键，以及拆段显示的 `<主键>:<段号>`（UI 层已经还原过一次，这里再兜一层）。
        val index = ordered.indexOfFirst { it.id == messageId }
            .takeIf { it >= 0 }
            ?: ordered.indexOfFirst { it.id == messageId.substringBefore(':') }
        if (index < 0) return@launch
        var start = index
        while (start > 0 && !ordered[start].fromUser) start--
        if (!ordered[start].fromUser) start = 0
        var end = start
        while (end + 1 < ordered.size && !ordered[end + 1].fromUser) end++
        ordered.subList(start, end + 1)
            .filterNot { it.id.startsWith("stream:") }
            .forEach { dao.deleteMessage(it.id) }
    }

    fun deleteMessage(messageId: String, segmentIndex: Int? = null) = viewModelScope.launch(crashGuard) {
        val message = dao.message(messageId) ?: return@launch
        if (segmentIndex != null && message.imageUri == null && !message.fromUser) {
            val remaining = ChatMessagePresentation.removeSegment(message.body, segmentIndex)
            if (remaining != null) {
                dao.updateMessageBody(messageId, remaining)
                return@launch
            }
        }
        if (message.imageUri != null) {
            dao.pendingActionsByResult(message.imageUri).forEach { action -> dao.deletePendingAction(action.id) }
            // 删图就是真删：日记/照片页的归档也一并删除。
            dao.deleteDiaryPhotosByUri(message.imageUri)
        }
        dao.deleteMessage(messageId)
    }

    fun sendMessage(body: String) = viewModelScope.launch(crashGuard) {
        val threadId = selectedThreadId.value ?: return@launch
        val participants = dao.participantIds(threadId)
        val invitationResponse = com.aster.companion.agent.world.WorldUserInvitationResponseParser.parse(body)
        if (invitationResponse != com.aster.companion.agent.world.WorldUserInvitationResponse.NONE) {
            state.value.worldInvitations.firstOrNull()?.let { invitation ->
                runCatching {
                    worldScheduler.respondToUserInvitation(
                        eventId = invitation.id,
                        accepted = invitationResponse == com.aster.companion.agent.world.WorldUserInvitationResponse.ACCEPT
                    )
                }
            }
        }
        // V3 only re-evaluates candidate events directly involving this conversation's characters.
        runCatching { interruptManager.onUserAction(body.take(40), participants.toSet()) }
        val availability = CompanionAvailabilityParser.parse(body)
        val availabilityTargets = CompanionAvailabilityTargetPolicy.resolve(
            dao.thread(threadId)?.type,
            participants
        )
        when (availability) {
            CompanionAvailabilitySignal.PAUSE, CompanionAvailabilitySignal.PAUSE_AND_WRITE_DIARY -> {
                systemModeState.pauseProactiveFor(availabilityTargets)
                // A pause is a boundary, not a delay. Old prompts must not surface on resume.
                if (availabilityTargets.isNotEmpty()) {
                    dao.consumePendingRecipientsForThread(threadId, System.currentTimeMillis())
                }
            }
            CompanionAvailabilitySignal.RESUME -> systemModeState.resumeProactiveFor(availabilityTargets)
            CompanionAvailabilitySignal.NONE -> Unit
        }
        if (availability != CompanionAvailabilitySignal.NONE && availabilityTargets.isNotEmpty()) {
            Log.i(
                "TongpinAvailability",
                "Availability signal=$availability for ${availabilityTargets.size} private character(s)"
            )
        }
        replyingThreadId.value = threadId
        // 告诉后台「他正在这个会话里回复」：她的回复要经过一次完整模型调用（可能几十秒），
        // 这段时间里主动陪伴不该插进来——这正是 active 那道闸的原本意图，而它以前恒为 false。
        threadActivitySignal.markReplying(threadId)
        try {
            val result = conversationTurnCoordinator.send(threadId, body) { activeCharacterId ->
                replyingCharacterId.value = activeCharacterId
            }
            // 用户消息进入世界引擎事件流，让角色状态对本次互动跟进。
            if (result.isSuccess) {
                recordUserMessageEvent(threadId, body)
            }
            if (result.isFailure) {
                val characterId = dao.participantIds(threadId).firstOrNull()
                val detail = result.exceptionOrNull()?.message?.take(160).orEmpty()
                dao.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        threadId = threadId,
                        characterId = characterId,
                        body = if (detail.isBlank()) "我现在没能把消息发出去，稍后再试一次。" else "我现在没能回复：$detail",
                        imageUri = null,
                        fromUser = false,
                        createdAt = System.currentTimeMillis()
                    )
                )
                dao.touchThread(threadId, System.currentTimeMillis())
                val fallbackMessage = dao.recentMessages(threadId, 1).firstOrNull()
                if (fallbackMessage != null && !fallbackMessage.fromUser) {
                    timeline.recordMessage(fallbackMessage)
                }
            }
            // 只有**私聊**才写日记（日记是"她和你"的关系日记）。在群里说「我去睡了」按下的主动
            // 一样算数，但那条路进 `writeAtBedtime` 会被 `require(thread.type == PRIVATE)` 拒掉 ——
            // 那样日记页会先闪一下「她正在写今天的日记」再消失。这里先判类型，不留这个假动作。
            val privateThreadForDiary = dao.thread(threadId)?.type == com.aster.companion.domain.ConversationType.PRIVATE
            if (availability == CompanionAvailabilitySignal.PAUSE_AND_WRITE_DIARY &&
                availabilityTargets.isNotEmpty() && result.isSuccess && privateThreadForDiary
            ) {
                // 日记就在「主动联系按条件关闭」这一刻开始写：上面已经 pauseProactiveFor + 清掉待发队列，
                // 条件收住了就立刻落笔。
                // **绝对不要挪到「空闲让路」后面去**：那会让日记延后，而且待办只存在内存里，
                // 进程一退就丢，日记就永远不写了（这个错误已经犯过一次）。
                launch {
                    diaryWritingThreadId.value = threadId
                    try {
                        // 写日记失败以前是**完全静默**的：说了晚安、日记页却一直「她还没有写下日记」，
                        // 用户只能猜。这里把原因说出来（模型没配/返回空/上下文取不到都会带 message）。
                        diaryGenerationCoordinator.writeAtBedtime(threadId).onFailure { error ->
                            android.widget.Toast.makeText(
                                appContext,
                                "这次日记没写成：${error.message ?: "未知原因"}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        if (diaryWritingThreadId.value == threadId) diaryWritingThreadId.value = null
                    }
                }
            }
        } finally {
            replyingCharacterId.value = null
            replyingThreadId.value = null
            // 成功、失败、取消都要放开；否则这个会话会被永远静音（比不挡更糟）。
            threadActivitySignal.clearReplying(threadId)
        }
    }

    /** 用户消息事件进入世界引擎事件流（source=user_message），失败不影响发送结果。 */
    private suspend fun recordUserMessageEvent(threadId: String, body: String) {
        val summary = body.take(USER_MESSAGE_EVENT_SUMMARY_LENGTH).ifBlank { "用户发送了一条消息" }
        runCatching {
            proactiveEvents.recordSystemEvent(
                source = "user_message",
                summary = summary,
                dedupeKey = "user_message:$threadId:${System.currentTimeMillis() / USER_MESSAGE_EVENT_BUCKET_MS}",
                occurredAt = System.currentTimeMillis()
            )
        }.onFailure { error -> Log.w("TongpinUserMessage", "记录用户消息事件失败", error) }
    }

    // ---- Lite「发照片」：Scene 预览 → 确认 → 出图（计划书 §5.4）----

    /** 私聊里点「发照片」：可选一句意图，由 Scene 流水线产出可编辑的英文 tag 串。 */
    fun requestScenePhoto(
        threadId: String,
        intent: String = "",
        characterIdHint: String? = null
    ) = viewModelScope.launch(crashGuard) {
        val characterId = characterIdHint?.takeIf { it.isNotBlank() }
            ?: runCatching { repository.resolvePrivateCharacterId(threadId) }.getOrNull()
            ?: dao.participantIds(threadId).firstOrNull()
            ?: dao.recentMessages(threadId, 80)
                .firstOrNull { !it.fromUser && !it.characterId.isNullOrBlank() }
                ?.characterId
            ?: run {
                android.widget.Toast.makeText(appContext, "找不到这个对话的角色，请稍后再试", android.widget.Toast.LENGTH_SHORT).show()
                android.util.Log.w("TongpinScenePhoto", "no character for thread=$threadId hint=$characterIdHint")
                return@launch
            }
        // 尽量把参与者补回去，下次不必再猜。
        runCatching {
            if (dao.participantIds(threadId).none { it == characterId }) {
                dao.upsertParticipant(
                    com.aster.companion.data.ThreadParticipantEntity(threadId, characterId, 0)
                )
            }
        }
        val effectiveIntent = intent.trim().ifBlank { "角色此刻想给用户看的一张照片" }
        // 「确认这张照片」这张卡接手了这次出图：把她之前那条还挂着「同意/拒绝」的图片动作清掉，
        // 否则同一件事会在聊天里挂两张确认卡。
        runCatching {
            dao.pendingActionsForThread(threadId)
                .filter { action ->
                    action.state == "PENDING" &&
                        (action.capabilityId == "companion.propose_image" || action.capabilityId == "companion.generate_image")
                }
                .forEach { dao.deletePendingAction(it.id) }
        }
        android.util.Log.i("TongpinScenePhoto", "request thread=$threadId character=$characterId intent=$effectiveIntent")
        android.widget.Toast.makeText(appContext, "正在整理画面…", android.widget.Toast.LENGTH_SHORT).show()
        _scenePhoto.value = ScenePhotoRequest(
            threadId = threadId,
            characterId = characterId,
            intent = effectiveIntent,
            stage = ScenePhotoStage.PLANNING
        )
        // 操作入口只取一次：角色行、对话模型 profile、提示词形态；下游不再各自回查数据库。
        val character = dao.character(characterId)
        val format = renderPromptBuilder.format()
        val inputs = character?.let {
            ScenePromptCoordinator.ScenePromptInputs(
                character = it,
                // ⚠️ 必须走 `ImagePromptModel.resolve`，不能写死对话模型。
                // 这两条（偷看照片 / 朋友圈配图）以前一直是写死的，所以**用户在设置里配的
                // 「图片提示词」模型从来没被用过**——提示词请求全打到对话模型那个地址上。
                // 没配 / 停用 / 没 key 时 `resolve` 会回退到对话模型，升级行为不变。
                conversationProfile = com.aster.companion.agent.ImagePromptModel.resolve(dao),
                format = format
            )
        }
        scenePromptCoordinator.buildTagPromptForThread(
            threadId = threadId,
            characterId = characterId,
            intent = effectiveIntent,
            useBackgroundGate = false,
            inputs = inputs
        )
            .onSuccess { draft ->
                val appearance = character?.appearancePrompt.orEmpty()
                // 预览里就带上外貌原文——预览与真正出图必须是同一份（否则「预览有、出图没有」）。
                val fullPrompt = renderPromptBuilder.compose(draft.prompt, format, appearance)
                android.util.Log.i("TongpinScenePhoto", "ready prompt_len=${fullPrompt.length} appearance_len=${appearance.length}")
                _scenePhoto.value = ScenePhotoRequest(
                    threadId = threadId,
                    characterId = characterId,
                    intent = effectiveIntent,
                    sceneText = draft.prompt,
                    prompt = fullPrompt,
                    stage = ScenePhotoStage.READY,
                    attempts = draft.attempts,
                    notes = draft.notes + listOfNotNull(
                        ImageGenerationPromptPolicy.anchorWarning(appearance, format),
                        com.aster.companion.agent.CharacterIdentityPromptPolicy.identityNote(appearance)
                    )
                )
            }
            .onFailure { error ->
                android.util.Log.w("TongpinScenePhoto", "failed", error)
                _scenePhoto.value = ScenePhotoRequest(
                    threadId = threadId,
                    characterId = characterId,
                    intent = effectiveIntent,
                    stage = ScenePhotoStage.FAILED,
                    error = error.message ?: "画面没整理好"
                )
            }
    }

    /** 用户在预览卡里改提示词：标记为手改，出图时按用户写的发，不再自动补人物锚点。 */
    fun updateScenePhotoPrompt(prompt: String) {
        _scenePhoto.value = _scenePhoto.value?.copy(prompt = prompt, promptEdited = true)
    }

    /**
     * 朋友圈配图（计划书 §6）：同一条 Scene 流水线，人物锚点 + 动态意图 → tag 串；
     * 确认后走 companion.generate_moment_image，出图全局串行由图片动作链路保证。
     */
    fun requestMomentScenePhoto(postId: String, characterId: String, intent: String = "") = viewModelScope.launch(crashGuard) {
        fun snapshot(stage: ScenePhotoStage, prompt: String = "", attempts: Int = 0, error: String? = null) =
            ScenePhotoRequest(
                threadId = "",
                characterId = characterId,
                momentPostId = postId,
                intent = intent,
                prompt = prompt,
                stage = stage,
                attempts = attempts,
                error = error
            )
        _scenePhoto.value = snapshot(ScenePhotoStage.PLANNING)
        // 操作入口只取一次：角色行、对话模型 profile、提示词形态；下游不再各自回查数据库。
        val character = dao.character(characterId)
        val format = renderPromptBuilder.format()
        val inputs = character?.let {
            ScenePromptCoordinator.ScenePromptInputs(
                character = it,
                // ⚠️ 必须走 `ImagePromptModel.resolve`，不能写死对话模型。
                // 这两条（偷看照片 / 朋友圈配图）以前一直是写死的，所以**用户在设置里配的
                // 「图片提示词」模型从来没被用过**——提示词请求全打到对话模型那个地址上。
                // 没配 / 停用 / 没 key 时 `resolve` 会回退到对话模型，升级行为不变。
                conversationProfile = com.aster.companion.agent.ImagePromptModel.resolve(dao),
                format = format
            )
        }
        val effectiveIntent = intent.trim().ifBlank {
            val name = character?.name ?: "角色"
            "${name}想分享的此刻画面"
        }
        runCatching {
            scenePromptCoordinator.buildTagPrompt(
                characterId = characterId,
                intent = effectiveIntent,
                recent = emptyList(),
                useBackgroundGate = false,
                inputs = inputs
            )
        }
            .onSuccess { draft ->
                val appearance = character?.appearancePrompt.orEmpty()
                // 同私聊那条路：外貌由代码接上，模型规则里明确不许它复述长相。
                val fullPrompt = renderPromptBuilder.compose(draft.prompt, format, appearance)
                _scenePhoto.value = snapshot(
                    stage = ScenePhotoStage.READY,
                    prompt = fullPrompt,
                    attempts = draft.attempts,
                    error = null
                ).copy(
                    intent = effectiveIntent,
                    sceneText = draft.prompt,
                    notes = draft.notes + listOfNotNull(
                        ImageGenerationPromptPolicy.anchorWarning(appearance, format),
                        com.aster.companion.agent.CharacterIdentityPromptPolicy.identityNote(appearance)
                    )
                )
            }
            .onFailure { error ->
                _scenePhoto.value = snapshot(
                    stage = ScenePhotoStage.FAILED,
                    error = error.message ?: "画面没整理好"
                )
            }
    }

    fun retryScenePhoto() {
        val current = _scenePhoto.value ?: return
        val postId = current.momentPostId
        if (postId != null) requestMomentScenePhoto(postId, current.characterId, current.intent)
        else requestScenePhoto(current.threadId, current.intent, current.characterId)
    }

    fun cancelScenePhoto() {
        _scenePhoto.value = null
    }

    /** 确认后交给既有图片动作链路出图；预览卡收起，进度由照片任务卡接管。 */
    fun confirmScenePhoto() = viewModelScope.launch(crashGuard) {
        val current = _scenePhoto.value ?: return@launch
        if (!current.hasPrompt) return@launch
        val character = dao.character(current.characterId)
        val appearance = character?.appearancePrompt.orEmpty()
        val validation = scenePromptCoordinator.validateEditedPrompt(current.prompt, appearance, character?.name.orEmpty())
        if (!validation.accepted) {
            _scenePhoto.value = current.copy(
                stage = ScenePhotoStage.FAILED,
                error = "这串标签还出不了图：" + validation.reasons.joinToString("、")
            )
            return@launch
        }
        _scenePhoto.value = current.copy(stage = ScenePhotoStage.GENERATING, error = null)
        // 预览时已经拼好的那份就是最终提示词（用户手改过就是改后的那份）：直接用它，不再拼第二遍。
        val finalPrompt = current.prompt
        android.util.Log.i(
            "TongpinScenePhoto",
            "confirm appearance_len=${appearance.length} prompt_len=${finalPrompt.length} head=${finalPrompt.take(80)}"
        )
        val postId = current.momentPostId
        actionCoordinator.submit(
            eventId = null,
            threadId = current.threadId.takeIf { it.isNotBlank() },
            request = AgentActionRequest(
                capabilityId = if (postId == null) "companion.generate_image" else "companion.generate_moment_image",
                transport = ActionTransport.LOCAL,
                confirmation = ConfirmationRequirement.NONE,
                arguments = buildMap {
                    put("character_id", current.characterId)
                    put("scene", finalPrompt.trim())
                    put("size", imageSizeStore.size.value)
                    if (postId != null) put("post_id", postId)
                }
            )
        )
        _scenePhoto.value = null
    }

    // ---- 记忆分册 §6：手动记忆 ----

    /** 长按一条消息 →「让她记住」：清理后的内容作为角色级 L2 写入。 */
    fun rememberMessage(threadId: String, messageBody: String) = viewModelScope.launch(crashGuard) {
        val characterId = dao.participantIds(threadId).firstOrNull() ?: return@launch
        val draft = com.aster.companion.lite.memory.MemoryManualPolicy.fromMessage(messageBody) ?: return@launch
        repository.saveMemory(
            id = null,
            characterId = characterId,
            threadId = null,
            kind = draft.kind,
            content = draft.content,
            blocked = false
        )
    }

    /** 「不再提及」：只改标记，不删内容（记忆分册 §6）。 */
    fun setMemoryBlocked(memoryId: String, blocked: Boolean) = viewModelScope.launch(crashGuard) {
        val memory = dao.memory(memoryId) ?: return@launch
        repository.saveMemory(
            id = memoryId,
            characterId = memory.characterId,
            threadId = memory.threadId,
            kind = memory.kind,
            content = memory.content,
            blocked = blocked
        )
    }

    fun sendImage(imageUri: String) = viewModelScope.launch(crashGuard) {
        val threadId = selectedThreadId.value ?: return@launch
        replyingThreadId.value = threadId
        // 告诉后台「他正在这个会话里回复」：她的回复要经过一次完整模型调用（可能几十秒），
        // 这段时间里主动陪伴不该插进来——这正是 active 那道闸的原本意图，而它以前恒为 false。
        threadActivitySignal.markReplying(threadId)
        try {
            val now = System.currentTimeMillis()
            val userImage = MessageEntity(UUID.randomUUID().toString(), threadId, null, "", imageUri, true, now)
            dao.insertMessage(userImage)
            timeline.recordMessage(userImage)
            dao.touchThread(threadId, now)
            val description = imageUnderstandingCoordinator.describe(imageUri, "Describe the image faithfully. Focus on visible people, setting, actions, text, and mood.").getOrThrow()
            conversationTurnCoordinator.respondToVisualContext(
                threadId,
                imageUri,
                description,
                persistUserImage = false,
                onActiveCharacterChanged = { activeCharacterId -> replyingCharacterId.value = activeCharacterId }
            ).getOrThrow()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w("TongpinImage", "Image understanding failed", error)
            val characterId = dao.participantIds(threadId).firstOrNull()
            val now = System.currentTimeMillis()
            val message = MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                characterId = characterId,
                body = ImageConversationFailurePolicy.reply(error),
                imageUri = null,
                fromUser = false,
                createdAt = now
            )
            dao.insertMessage(message)
            timeline.recordMessage(message)
            dao.touchThread(threadId, now)
        } finally {
            replyingCharacterId.value = null
            replyingThreadId.value = null
            // 成功、失败、取消都要放开；否则这个会话会被永远静音（比不挡更糟）。
            threadActivitySignal.clearReplying(threadId)
        }
    }

    // 这里原来有两个没人调的函数，已删除：
    //  - `createPrivate(character)`：给"已有角色"单开一个私聊。App 里角色和私聊是同时创建的
    //    （`createCharacterAndPrivate`），删好友时两者一起删，所以不存在"有角色没私聊"的状态。
    //  - `saveConversationModel(baseUrl, model, key)`：老的一键保存对话模型。它直接调
    //    `saveModelProfile` 且不带思考开关/温度/top_p —— 一旦被别处调用，就会把用户的
    //    「思考模式」和采样参数打回默认（这个坑真的踩过）。要保存模型请走 `saveModel`。

    fun saveModel(kind: String, baseUrl: String, model: String, key: String, imageProtocol: String = "AUTO") = viewModelScope.launch(crashGuard) {
        val existing = dao.modelProfile(kind)
        val effectiveKey = key.trim().ifBlank {
            existing?.encryptedApiKey?.let(secretCipher::decrypt).orEmpty()
        }
        // `saveModelProfile` 是**整行替换**：界面上没编辑过的字段必须带现值，
        // 否则保存一次地址就会把思考开关/强度、温度、top_p、惩罚项全部打回默认
        //（用户现象：「思考模式」打开后一点保存就自己弹回关闭）。
        repository.saveModelProfile(
            kind = kind,
            baseUrl = baseUrl,
            modelName = model,
            apiKey = effectiveKey,
            enabled = existing?.enabled ?: true,
            // 新配置的默认超时给到 180 秒：场景/出图提示词是长请求，60 秒在网关忙时不够。
            // 已有配置保持原值（可在设置里改），存储上限见 `saveModelProfile` 的 coerceIn(5, 600)。
            timeoutSeconds = existing?.timeoutSeconds ?: 180,
            cipher = secretCipher,
            temperature = existing?.temperature ?: com.aster.companion.domain.ConversationTemperature.DEFAULT,
            thinkingEnabled = existing?.thinkingEnabled ?: false,
            reasoningEffort = existing?.reasoningEffort
                ?: com.aster.companion.domain.ConversationReasoning.DEFAULT_EFFORT,
            topP = existing?.topP ?: 0.85,
            frequencyPenalty = existing?.frequencyPenalty ?: 0.30,
            presencePenalty = existing?.presencePenalty ?: 0.20,
            imageProtocol = imageProtocol.takeIf { it.isNotBlank() && it != "AUTO" }
                ?: existing?.imageProtocol
                ?: "AUTO"
        )
    }

    /**
     * 思考开关 + 强度档位：只改这两项。
     *
     * 注意 [CompanionRepository.saveModelProfile] 是**整行替换**，所以其余参数必须带现值，
     * 否则保存一次开关就会把温度、top_p、频率惩罚这些打回默认。
     */
    fun setThinking(kind: String, enabled: Boolean, effort: String) = viewModelScope.launch(crashGuard) {
        val existing = dao.modelProfile(kind) ?: return@launch
        val key = existing.encryptedApiKey?.let(secretCipher::decrypt).orEmpty()
        if (key.isBlank()) return@launch
        repository.saveModelProfile(
            kind = kind,
            baseUrl = existing.baseUrl,
            modelName = existing.modelName,
            apiKey = key,
            enabled = existing.enabled,
            timeoutSeconds = existing.timeoutSeconds,
            cipher = secretCipher,
            temperature = existing.temperature,
            thinkingEnabled = enabled,
            reasoningEffort = effort,
            topP = existing.topP,
            frequencyPenalty = existing.frequencyPenalty,
            presencePenalty = existing.presencePenalty,
            imageProtocol = existing.imageProtocol
        )
    }

    /**
     * **端点级保存**：设置页「服务」组里改地址 / 密钥 / 模型名 / 协议，改一次存一次。
     *
     * 传 null 的项保持现值（密钥留空也算"不改"）。其余字段（温度、top_p、惩罚项、思考开关……）
     * 一律带现值——`saveModelProfile` 是整行替换，漏一个就把用户原来的配置打回默认。
     */
    fun setModelEndpoint(
        kind: String,
        baseUrl: String? = null,
        modelName: String? = null,
        apiKey: String? = null,
        imageProtocol: String? = null
    ) = viewModelScope.launch(crashGuard) {
        val existing = dao.modelProfile(kind) ?: return@launch
        val newBaseUrl = baseUrl?.trim()?.takeIf { it.isNotBlank() } ?: existing.baseUrl
        val newModel = modelName?.trim()?.takeIf { it.isNotBlank() } ?: existing.modelName
        val newKey = apiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: existing.encryptedApiKey?.let(secretCipher::decrypt).orEmpty()
        if (newKey.isBlank()) return@launch
        repository.saveModelProfile(
            kind = kind,
            baseUrl = newBaseUrl,
            modelName = newModel,
            apiKey = newKey,
            enabled = existing.enabled,
            timeoutSeconds = existing.timeoutSeconds,
            cipher = secretCipher,
            temperature = existing.temperature,
            thinkingEnabled = existing.thinkingEnabled,
            reasoningEffort = existing.reasoningEffort,
            topP = existing.topP,
            frequencyPenalty = existing.frequencyPenalty,
            presencePenalty = existing.presencePenalty,
            imageProtocol = imageProtocol?.takeIf { it.isNotBlank() } ?: existing.imageProtocol
        )
    }

    /**
     * 「生图规则从哪来 / 用哪一套」：方案 A——把规则来源从出图地址里解耦出来。
     *
     * 规则地址留空 = 跟随图像模型地址（老行为）；规则集 auto = 按模型名判断（老行为）。
     */
    private val _imageRuleBaseUrl = MutableStateFlow(imageRuleSettings.ruleBaseUrl)
    val imageRuleBaseUrl: StateFlow<String> = _imageRuleBaseUrl

    private val _imageRuleFormat = MutableStateFlow(imageRuleSettings.formatOverride)
    val imageRuleFormat: StateFlow<String> = _imageRuleFormat

    fun setImageRule(baseUrl: String, format: String) {
        imageRuleSettings.setRuleBaseUrl(baseUrl)
        imageRuleSettings.setFormatOverride(format)
        _imageRuleBaseUrl.value = imageRuleSettings.ruleBaseUrl
        _imageRuleFormat.value = imageRuleSettings.formatOverride
    }

    fun saveImageApiPoolEntry(label: String, baseUrl: String, model: String, key: String) = viewModelScope.launch(crashGuard) {
        repository.saveImageApiPoolEntry(label, baseUrl, model, key, secretCipher)
    }

    fun deleteImageApiPoolEntry(entryId: String) = viewModelScope.launch(crashGuard) {
        repository.deleteImageApiPoolEntry(entryId)
    }

    fun saveImagePromptDebugConfig(config: ImagePromptDebugConfig) {
        imagePromptDebugSettings.save(config)
    }

    fun resetImagePromptDebugConfig() {
        imagePromptDebugSettings.reset()
    }

    fun setSplitMultilineResponses(enabled: Boolean) {
        chatDisplaySettings.setSplitMultilineResponses(enabled)
    }

    fun setDimStageDirections(enabled: Boolean) {
        chatDisplaySettings.setDimStageDirections(enabled)
    }

    /** 设置 → 聊天 → 流式输出：默认开（逐字显示），关掉后整条回复一次性出现。 */
    fun setStreamingOutput(enabled: Boolean) {
        chatDisplaySettings.setStreamingOutput(enabled)
    }

    /** 情绪底色（氛围惯性）开关：见 [ChatDisplayPreferences.emotionalOffsetEnabled]。 */
    fun setEmotionalOffsetEnabled(enabled: Boolean) {
        chatDisplaySettings.setEmotionalOffsetEnabled(enabled)
    }

    /**
     * 长按「她留下的照片」→ 删除。
     *
     * 删的是 `companion_diary_photos` 里那一条（日记页和关系页都读它，删完两边都不再显示）。
     * **不动聊天里那条消息**：照片在聊天气泡里是另一份引用，用户在聊天里长按气泡还能单独删消息。
     * 也不去删磁盘上的图片文件——同一张图可能还被聊天消息引用着，删文件会变成裂图。
     */
    fun deleteDiaryPhoto(imageUri: String) = viewModelScope.launch(crashGuard) {
        if (imageUri.isBlank()) return@launch
        runCatching { dao.deleteDiaryPhotosByUri(imageUri) }
            .onSuccess {
                android.widget.Toast.makeText(appContext, "已删除这张照片", android.widget.Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                android.widget.Toast.makeText(appContext, "删除失败：${it.message ?: "未知原因"}", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    fun saveCharacter(id: String?, name: String, personality: String, appearance: String, referenceUri: String?, styleReferenceUri: String?, avatarUri: String?, relationshipPrompt: String = "") = viewModelScope.launch(crashGuard) {
        repository.saveCharacter(id, name, avatarUri, personality, appearance, referenceUri, styleReferenceUri, relationshipPrompt)
    }

    fun saveMemory(id: String?, characterId: String?, threadId: String?, kind: MemoryKind, content: String, blocked: Boolean) = viewModelScope.launch(crashGuard) {
        repository.saveMemory(id, characterId, threadId, kind, content, blocked)
    }

    fun deleteMemory(memoryId: String) = viewModelScope.launch(crashGuard) { repository.deleteMemory(memoryId) }

    fun createUserMoment(body: String, imageUris: List<String>) = viewModelScope.launch(crashGuard) {
        val postId = repository.createUserMoment(body, imageUris)
        launch { momentsAgent.handleUserPost(postId) }
    }

    fun toggleMomentLike(postId: String, liked: Boolean) = viewModelScope.launch(crashGuard) {
        repository.toggleMomentLike(postId, "USER", "user")
    }

    fun commentOnMoment(postId: String, body: String, replyToCommentId: String? = null) = viewModelScope.launch(crashGuard) {
        val commentId = repository.addMomentComment(postId, "USER", null, body, replyToCommentId)
        launch { momentsAgent.handleUserComment(postId, commentId) }
    }

    fun deleteMoment(postId: String) = viewModelScope.launch(crashGuard) { repository.deleteMoment(postId) }

    fun editMoment(postId: String, body: String, imageUris: List<String>) = viewModelScope.launch(crashGuard) {
        repository.editMoment(postId, body, imageUris)
    }

    fun setProactiveCharacters(characterIds: Set<String>) = systemModeState.setProactiveCharacterIds(characterIds)

    fun setCharacterProactive(characterId: String, enabled: Boolean) {
        val current = if (systemModeState.proactiveSelectionConfigured) systemModeState.proactiveCharacterIds else state.value.characters.map { it.id }.toSet()
        systemModeState.setProactiveCharacterIds(if (enabled) current + characterId else current - characterId)
    }

    fun resumeCharacterProactive(characterId: String) = systemModeState.resumeProactiveFor(listOf(characterId))

    fun setSystemModeEnabled(enabled: Boolean) {
        systemModeState.setEnabled(enabled)
        if (enabled) {
            appContext.startForegroundService(Intent(appContext, CompanionSystemService::class.java))
        } else {
            appContext.stopService(Intent(appContext, CompanionSystemService::class.java))
        }
    }

    fun exportBackup(uri: Uri, password: String) = viewModelScope.launch(crashGuard) {
        backupStatus.value = "正在导出加密备份"
        backupStatus.value = runCatching {
            requireNotNull(appContext.contentResolver.openOutputStream(uri)) { "无法写入所选文件" }.use { backupManager.export(it, password) }
            "备份已加密导出"
        }.getOrElse { "备份导出失败：${it.message ?: "未知错误"}" }
    }

    fun importBackup(uri: Uri, password: String) = viewModelScope.launch(crashGuard) {
        backupStatus.value = "正在导入加密备份"
        backupStatus.value = runCatching {
            requireNotNull(appContext.contentResolver.openInputStream(uri)) { "无法读取所选文件" }.use { backupManager.import(it, password) }
            "备份已导入，模型密钥未包含在文件中"
        }.getOrElse { "备份导入失败：${it.message ?: "密码或文件不正确"}" }
    }

    fun exportDeviceTransfer(uri: Uri, password: String) = viewModelScope.launch(crashGuard) {
        backupStatus.value = "正在创建跨设备迁移包"
        backupStatus.value = runCatching {
            val result = requireNotNull(appContext.contentResolver.openOutputStream(uri)) { "无法写入所选文件" }
                .use { deviceTransferManager.export(it, password) }
            "迁移包已加密导出：${result.characters} 个角色、${result.messages} 条消息、${result.images} 个图片文件"
        }.getOrElse { "迁移包导出失败：${it.message ?: "未知错误"}" }
    }

    fun importDeviceTransfer(uri: Uri, password: String) = viewModelScope.launch(crashGuard) {
        backupStatus.value = "正在导入跨设备迁移包"
        backupStatus.value = runCatching {
            val result = requireNotNull(appContext.contentResolver.openInputStream(uri)) { "无法读取所选文件" }
                .use { deviceTransferManager.import(it, password) }
            "迁移完成：${result.characters} 个角色、${result.messages} 条消息、${result.images} 个图片文件。请重新授予系统权限和 Shizuku。"
        }.getOrElse { "迁移包导入失败，现有数据未被清空：${it.message ?: "密码或文件不正确"}" }
    }

    fun approveAction(actionId: String) = viewModelScope.launch(crashGuard) { runCatching { actionCoordinator.approve(actionId) } }
    fun rejectAction(actionId: String) = viewModelScope.launch(crashGuard) { runCatching { actionCoordinator.reject(actionId) } }
    fun retryImageAction(actionId: String) = viewModelScope.launch(crashGuard) { runCatching { actionCoordinator.retryImage(actionId) } }

    /**
     * 「重新生成」：重新生成**照片**，沿用同一份提示词——先拉取那一张
     * （provider 任务还在就收它；网关按同提示词缓存命中就秒回），拉不到才用同一份提示词重新提交。
     */
    fun regenerateImageAction(actionId: String) = viewModelScope.launch(crashGuard) {
        val failure = runCatching { actionCoordinator.regenerateImage(actionId) }.exceptionOrNull()
        val message = when {
            failure != null -> "重新生成没能启动：${failure.message ?: "未知错误"}"
            dao.pendingAction(actionId)?.state == "RUNNING" -> "正在用同一份提示词重新生成这张照片…"
            else -> "这个任务现在不能重新生成"
        }
        android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    fun cancelImageAction(actionId: String) = viewModelScope.launch(crashGuard) { runCatching { actionCoordinator.cancelImage(actionId) } }
    fun respondToWorldInvitation(eventId: String, accepted: Boolean) = viewModelScope.launch(crashGuard) {
        runCatching { worldScheduler.respondToUserInvitation(eventId, accepted) }
    }

    // 世界页（`MainTab.World`）已删除，所以原来只给它用的 performWorldAction / moveUserTo /
    // actionLabel / toInteractionType 一并删掉：没有入口的交互记录写进库只会变成噪音。
    // 邀请回应（respondToWorldInvitation）保留 —— 聊天列表和设置页还在用它。

    fun requestShizukuPermission() = runCatching { shizukuStatusProvider.requestPermission(9101) }

    fun testModel(kind: String) = viewModelScope.launch(crashGuard) {
        val profile = dao.modelProfile(kind) ?: return@launch
        val key = profile.encryptedApiKey ?: return@launch
        val apiKey = secretCipher.decrypt(key)
        val result = if (kind == "embedding") {
            client.embed(profile, apiKey, "同频语义检索连接测试").map { "连接成功，Embedding 可用" }
        } else {
            client.test(profile, apiKey)
        }
        val status = result.fold(
            onSuccess = { connected ->
                if (kind == "image_generation") "$connected；尚未验证图片生成能力" else connected
            },
            onFailure = { it.message ?: "连接失败" }
        )
        dao.upsertModel(profile.copy(lastTestResult = status))
    }

    fun fetchModels(kind: String, baseUrl: String, enteredKey: String) = viewModelScope.launch(crashGuard) {
        val saved = dao.modelProfile(kind)
        val apiKey = enteredKey.trim().ifBlank {
            saved?.encryptedApiKey?.let(secretCipher::decrypt).orEmpty()
        }
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            modelFetchStatus.value = modelFetchStatus.value + (kind to "先填写服务地址和 API Key，再获取模型")
            return@launch
        }
        modelFetchStatus.value = modelFetchStatus.value + (kind to "正在获取可用模型...")
        val temporaryProfile = (saved ?: ModelProfileEntity(kind, kind, baseUrl, "", true, 60, null, null)).copy(baseUrl = baseUrl.trimEnd('/'))
        val result = client.listModels(temporaryProfile, apiKey)
        result.onSuccess { models ->
            availableModels.value = availableModels.value + (kind to models)
            modelFetchStatus.value = modelFetchStatus.value + (kind to if (models.isEmpty()) "服务未返回模型列表" else "已获取 ${models.size} 个模型")
        }.onFailure { failure ->
            modelFetchStatus.value = modelFetchStatus.value + (kind to (failure.message ?: "获取模型失败"))
        }
    }
}

private fun momentImageTasks(
    posts: List<MomentPostEntity>,
    media: List<MomentMediaEntity>
): List<PendingActionEntity> {
    val generatedPostIds = media.filter { it.source == com.aster.companion.domain.MomentActors.SOURCE_GENERATED }.mapTo(hashSetOf()) { it.postId }
    return posts.asSequence()
        .filter { it.authorType == com.aster.companion.domain.MomentActors.CHARACTER }
        .filter { it.source == com.aster.companion.domain.MomentActors.SOURCE_PROACTIVE }
        .filter { it.status == com.aster.companion.domain.MomentActors.STATUS_GENERATING || it.status == com.aster.companion.domain.MomentActors.STATUS_FAILED || it.id in generatedPostIds }
        .map { post ->
            val state = when (post.status) {
                com.aster.companion.domain.MomentActors.STATUS_GENERATING -> "RUNNING"
                com.aster.companion.domain.MomentActors.STATUS_FAILED -> "FAILED"
                else -> "APPROVED"
            }
            PendingActionEntity(
                id = "moment-image:${post.id}",
                eventId = null,
                threadId = null,
                capabilityId = "companion.generate_moment_image",
                transport = "LOCAL",
                argumentsJson = "{}",
                state = state,
                createdAt = post.createdAt,
                resolvedAt = post.updatedAt.takeIf { state != "RUNNING" },
                resultSummary = when (state) {
                    "RUNNING" -> "正在为角色动态准备配图"
                    "FAILED" -> "角色动态配图生成失败"
                    else -> "角色动态配图已发布"
                }
            )
        }
        .toList()
}

/** 用户消息事件摘要长度上限：取消息前 60 字。 */
private const val USER_MESSAGE_EVENT_SUMMARY_LENGTH = 60

/** 用户消息事件去重窗口：同一会话每 30 秒最多记一条，避免高频消息刷爆推演。 */
private const val USER_MESSAGE_EVENT_BUCKET_MS = 30_000L
