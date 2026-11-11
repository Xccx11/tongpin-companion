@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aster.ui

import androidx.compose.ui.graphics.graphicsLayer
import com.aster.ui.theme.AsterMetrics
import com.aster.ui.theme.AsterType
import com.aster.ui.theme.asterCard
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.ThreadEntity
import com.aster.companion.data.MomentPostEntity
import com.aster.companion.data.MomentMediaEntity
import com.aster.companion.data.MomentCommentEntity
import com.aster.companion.data.MomentReactionEntity
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.MemoryKind
import com.aster.companion.domain.MomentActors
import com.aster.companion.domain.DiaryPhotoPolicy
import com.aster.companion.domain.PendingImagePreviewPolicy
import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.ui.GroupManagementProjection
import com.aster.companion.ui.GroupManagementSnapshot
import com.aster.companion.proactive.CompanionSystemService
import com.aster.companion.proactive.SystemModeState
import com.aster.companion.system.ShizukuStatusProvider
import com.aster.companion.system.RuntimePermissionConfigurationAction
import com.aster.companion.system.RuntimePermissionConfigurationPolicy
import com.aster.companion.proactive.CompanionNotificationPublisher
import com.aster.companion.ui.CompanionHomeViewModel
import com.aster.companion.agent.ImagePromptDebugConfig
import com.aster.companion.agent.world.WorldInteractionService
import com.aster.ui.theme.AsterTheme
import com.aster.ui.theme.GlassTier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import com.aster.ui.theme.contentSurface
import com.aster.ui.theme.glassSurface
import com.aster.ui.theme.glassCapsule
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var worldInteractionService: WorldInteractionService
    private val openedThreadId = MutableStateFlow<String?>(null)
    private val openedMomentId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val systemMode = SystemModeState(this)
        systemMode.enableOnFirstUse()
        if (systemMode.enabled) CompanionSystemService.start(this)
        openedThreadId.value = intent?.getStringExtra(CompanionNotificationPublisher.EXTRA_THREAD_ID)
        openedMomentId.value = intent?.getStringExtra(CompanionNotificationPublisher.EXTRA_MOMENT_ID)
        val clearDevelopmentTestData = intent?.getBooleanExtra(EXTRA_CLEAR_DEVELOPMENT_TEST_DATA, false) == true
        setContent {
            val threadId by openedThreadId.collectAsStateWithLifecycle()
            val momentId by openedMomentId.collectAsStateWithLifecycle()
            AsterTheme(darkTheme = false) { TongpinApp(threadId, momentId, clearDevelopmentTestData) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedThreadId.value = intent.getStringExtra(CompanionNotificationPublisher.EXTRA_THREAD_ID)
        openedMomentId.value = intent.getStringExtra(CompanionNotificationPublisher.EXTRA_MOMENT_ID)
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            runCatching {
                worldInteractionService.recordCurrentUserNotVisible(lastAction = "离开应用")
            }
        }
    }

    companion object {
        const val EXTRA_CLEAR_DEVELOPMENT_TEST_DATA = "clear_development_test_data"
    }
}

private enum class MainTab(val label: String) { Chat("消息"), Moments("动态"), Memory("日记"), Settings("设置") }
private enum class ConversationFilter(val label: String) { All("全部"), Private("私聊"), Group("群聊") }
private enum class SettingsTab(val label: String) { Models("模型"), Chat("聊天"), System("系统"), Data("数据") }

private fun GroupAtmosphere.label(): String = when (this) {
    GroupAtmosphere.QUIET -> "安静"
    GroupAtmosphere.DAILY -> "日常"
    GroupAtmosphere.LIVELY -> "热闹"
}

private val com.aster.ui.theme.AsterColorScheme.accentWash: Color
    get() = if (isDark) Color(0xFF3B242D) else Color(0xFFFFEEF1)

private val com.aster.ui.theme.AsterColorScheme.accentLine: Color
    get() = if (isDark) Color(0xFF70414F) else Color(0xFFE8BBC3)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TongpinApp(openedThreadId: String?, openedMomentId: String?, clearDevelopmentTestData: Boolean = false, viewModel: CompanionHomeViewModel = hiltViewModel()) {
    var tab by rememberSaveable {
        mutableStateOf(
            when {
                openedThreadId != null -> MainTab.Chat
                openedMomentId != null -> MainTab.Moments
                // 世界页已删除（世界引擎下线，胶囊一个就够）：第一屏就是消息列表。
                else -> MainTab.Chat
            }
        )
    }
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var showNewCharacter by rememberSaveable { mutableStateOf(false) }
    var editingCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
    var showGroupCreator by rememberSaveable { mutableStateOf(false) }
    var manageThreadId by rememberSaveable { mutableStateOf<String?>(null) }
    var relationshipCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedThreadId by viewModel.selectedThread.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val livingStates by viewModel.livingStates.collectAsStateWithLifecycle()
    // 她主动找你时要能收到通知：一进 App 就顺手要一次「通知」权限（Android 13+ 需要运行时授权），
    // 否则这个开关一直藏在高设置里，没人会去点。
    val permissionContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val activity = permissionContext as? android.app.Activity
        if (activity != null &&
            android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                permissionContext, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                9101
            )
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(openedThreadId) {
        if (openedThreadId != null) {
            tab = MainTab.Chat
            viewModel.selectThread(openedThreadId)
        }
    }
    LaunchedEffect(openedMomentId) { if (openedMomentId != null) { tab = MainTab.Moments; viewModel.closeThread() } }
    LaunchedEffect(clearDevelopmentTestData) { if (clearDevelopmentTestData) viewModel.clearDevelopmentTestData() }
    val selectedThread = state.threads.firstOrNull { it.id == selectedThreadId }
    BackHandler(enabled = selectedThread != null) { viewModel.closeThread() }
    BackHandler(enabled = showAddMenu) { showAddMenu = false }
    BackHandler(enabled = showNewCharacter) { showNewCharacter = false }
    BackHandler(enabled = showGroupCreator) { showGroupCreator = false }
    BackHandler(enabled = editingCharacterId != null) { editingCharacterId = null }
    BackHandler(enabled = manageThreadId != null) { manageThreadId = null }
    BackHandler(enabled = relationshipCharacterId != null) { relationshipCharacterId = null }
    androidx.compose.material3.Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = AsterTheme.colors.bg,
        bottomBar = { if (selectedThread == null && !imeVisible) TongpinBottomBar(tab) { tab = it } }
    ) { padding ->
        when (tab) {
            MainTab.Chat -> if (selectedThread == null) {
                ChatListPage(state.threads, state.characters.associateBy { it.id }, state.threadParticipants, state.threadPreviews, state.worldInvitations, viewModel::respondToWorldInvitation, viewModel::selectThread, { showAddMenu = true }, Modifier.padding(padding))
            } else {
                ChatDetailPage(
                    thread = selectedThread,
                    characters = state.characters.associateBy { it.id },
                    messages = messages,
                    onBack = viewModel::closeThread,
                    onManage = { manageThreadId = selectedThread.id },
                    onPeek = { viewModel.peekPhotoGenerate(selectedThread.id) },
                    onPeekFor = { characterId -> viewModel.peekPhotoGenerate(selectedThread.id, characterId) },
                    onSyncPrivate = { characterId ->
                        if (selectedThread.type == ConversationType.PRIVATE) viewModel.syncContextToCharacterState(selectedThread.id, characterId)
                        else viewModel.syncPrivateToGroup(selectedThread.id, characterId)
                    },
                    onRefreshWorld = viewModel::refreshWorldState,
                    onToggleStealth = { viewModel.toggleStealth(selectedThread.id) },
                    stealthOn = viewModel.stealthMode(selectedThread.id).collectAsState().value,
                    familyMode = selectedThread.type == ConversationType.GROUP && state.groupSettings.firstOrNull { it.groupThreadId == selectedThread.id }?.groupType == "FAMILY",
                    livingStates = livingStates,
                    onDeleteMessage = { id, segment -> viewModel.deleteMessage(id, segment) },
                    onDeleteRun = { id -> viewModel.deleteExchangeTurn(selectedThread.id, id) },
                    onSend = viewModel::sendMessage,
                    onSendImage = viewModel::sendImage,
                    onRememberMessage = { body -> viewModel.rememberMessage(selectedThread.id, body) },
                    participants = state.threadParticipants,
                    isReplying = state.replyingThreadId == selectedThread.id,
                    activeReplyCharacterName = state.replyingCharacterId?.let { activeId -> state.characters.firstOrNull { it.id == activeId }?.name },
                    photoGenerationActions = state.imageGenerationActions.filter { it.threadId == selectedThread.id && com.aster.ui.PhotoGenerationChatPresentation.shouldShowInConversation(it) },
                    pendingDeviceActions = state.pendingActions.filter {
                        it.threadId == selectedThread.id && it.capabilityId !in setOf("companion.propose_image", "companion.propose_private_image")
                    },
                    displayPreferences = state.chatDisplayPreferences,
                    scenePhoto = viewModel.scenePhoto.collectAsState().value,
                    onRequestScenePhoto = { intent, characterId -> viewModel.requestScenePhoto(selectedThread.id, intent, characterId) },
                    onUpdateScenePhotoPrompt = viewModel::updateScenePhotoPrompt,
                    onConfirmScenePhoto = viewModel::confirmScenePhoto,
                    onRetryScenePhoto = viewModel::retryScenePhoto,
                    onCancelScenePhoto = viewModel::cancelScenePhoto,
                    onApprovePhoto = viewModel::approveAction,
                    onRejectPhoto = viewModel::rejectAction,
                    modifier = Modifier
                )
            }
            MainTab.Moments -> MomentsPage(
                state = state,
                onPublish = viewModel::createUserMoment,
                onToggleLike = viewModel::toggleMomentLike,
                onComment = viewModel::commentOnMoment,
                onEdit = viewModel::editMoment,
                onDelete = viewModel::deleteMoment,
                onApproveImage = viewModel::approveAction,
                onRejectImage = viewModel::rejectAction,
                scenePhoto = viewModel.scenePhoto.collectAsState().value,
                onRequestMomentScenePhoto = viewModel::requestMomentScenePhoto,
                onUpdateScenePhotoPrompt = viewModel::updateScenePhotoPrompt,
                onConfirmScenePhoto = viewModel::confirmScenePhoto,
                onRetryScenePhoto = viewModel::retryScenePhoto,
                onCancelScenePhoto = viewModel::cancelScenePhoto,
                openedPostId = openedMomentId,
                modifier = Modifier.padding(padding)
            )
            MainTab.Memory -> MemoryPage(state, viewModel::deleteMemory, viewModel::setMemoryBlocked, viewModel::deleteDiaryPhoto, Modifier.padding(padding))
            MainTab.Settings -> SettingsPage(state, viewModel::saveModel, viewModel::saveImageApiPoolEntry, viewModel::deleteImageApiPoolEntry, viewModel::saveImagePromptDebugConfig, viewModel::resetImagePromptDebugConfig, viewModel::setSplitMultilineResponses, viewModel::setDimStageDirections, viewModel::setStreamingOutput, viewModel::setEmotionalOffsetEnabled, viewModel::testModel, viewModel::fetchModels, { enabled, effort -> viewModel.setThinking("conversation", enabled, effort) }, { kind, base, model, protocol -> viewModel.setModelEndpoint(kind, baseUrl = base, modelName = model, imageProtocol = protocol) }, viewModel.imageRuleBaseUrl.collectAsStateWithLifecycle().value, viewModel.imageRuleFormat.collectAsStateWithLifecycle().value, { base, format -> viewModel.setImageRule(base, format) }, viewModel::deleteMemory, viewModel::setMemoryBlocked, viewModel::approveAction, viewModel::rejectAction, viewModel::retryImageAction, viewModel::cancelImageAction, viewModel::regenerateImageAction, viewModel::respondToWorldInvitation, viewModel::requestShizukuPermission, viewModel::setSystemModeEnabled, viewModel::exportBackup, viewModel::importBackup, viewModel::exportDeviceTransfer, viewModel::importDeviceTransfer, viewModel.imageSize.collectAsState().value, viewModel::setImageSize, viewModel.updateState.collectAsState().value, viewModel::checkUpdate, viewModel::installUpdate, viewModel.lastScenePrompt.collectAsState().value, viewModel.backgroundIdleMs.collectAsState().value, viewModel::setBackgroundIdleMs, viewModel.contextTokens.collectAsState().value, viewModel::setContextTokens, viewModel.contextWindowMessages.collectAsState().value, viewModel::setContextWindowMessages, viewModel.proactiveMomentsEnabled.collectAsStateWithLifecycle().value, viewModel.proactivePhotoEnabled.collectAsStateWithLifecycle().value, viewModel::setProactiveMomentsEnabled, viewModel::setProactivePhotoEnabled, viewModel.proactiveUrgeEnabled.collectAsStateWithLifecycle().value, viewModel.proactiveRelaxedContact.collectAsStateWithLifecycle().value, viewModel::setProactiveUrgeEnabled, viewModel::setProactiveRelaxedContact, viewModel.proactiveWorldSeedingEnabled.collectAsStateWithLifecycle().value, viewModel::setProactiveWorldSeeding, Modifier.padding(padding))
        }
    }
    if (showAddMenu) AddConversationDialog({ showAddMenu = false }, { showAddMenu = false; showNewCharacter = true }, { showAddMenu = false; showGroupCreator = true })
    if (showNewCharacter) CharacterEditorDialog(null, { showNewCharacter = false }, viewModel::saveCharacter, viewModel::createCharacterAndPrivate)
    editingCharacterId?.let { characterId -> state.characters.firstOrNull { it.id == characterId }?.let { character ->
        CharacterEditorDialog(character, { editingCharacterId = null }, viewModel::saveCharacter, viewModel::createCharacterAndPrivate)
    } }
    if (showGroupCreator) ThreadCreationDialog(ConversationType.GROUP, state.characters, { showGroupCreator = false }) { ids, title -> viewModel.createGroup(ids, title); showGroupCreator = false }
    manageThreadId?.let { threadId ->
        state.threads.firstOrNull { it.id == threadId }?.let { thread ->
            val memberIds = state.threadParticipants[thread.id].orEmpty()
            val groupSnapshot = GroupManagementProjection.forThread(
                threadId = thread.id,
                memberIds = memberIds.toSet(),
                settings = state.groupSettings,
                facts = state.familyGroupFacts,
                memberStates = state.groupMemberStates
            )
            ConversationManagerDialog(
                thread = thread,
                characters = state.characters,
                memberIds = memberIds,
                groupMemories = state.memories.filter { it.threadId == thread.id && it.kind == MemoryKind.CONVERSATION }.sortedByDescending { it.updatedAt }.take(5),
                groupSnapshot = groupSnapshot,
                relatedGroupTitles = if (thread.type == ConversationType.PRIVATE) {
                    state.threadParticipants[thread.id].orEmpty().firstOrNull()?.let { characterId ->
                        state.threads.filter { candidate ->
                            candidate.type == ConversationType.GROUP && characterId in state.threadParticipants[candidate.id].orEmpty()
                        }.map { it.title }
                    }.orEmpty()
                } else emptyList(),
                onDismiss = { manageThreadId = null },
                onClearHistory = { viewModel.clearThreadHistory(thread.id) },
                onDeleteFriend = { viewModel.deletePrivateFriend(thread.id); manageThreadId = null },
                onDeleteGroup = { viewModel.deleteThread(thread.id); manageThreadId = null },
                onSaveGroup = { title, ids, atmosphere, groupProactiveEnabled -> viewModel.updateGroup(thread.id, title, ids, atmosphere, groupProactiveEnabled); manageThreadId = null },
                onSaveFact = { factId, summary, allowedInGroup -> viewModel.saveFamilyGroupFact(thread.id, factId, summary, allowedInGroup) },
                onDeleteFact = viewModel::deleteFamilyGroupFact,
                onEditCharacter = { character -> manageThreadId = null; editingCharacterId = character.id },
                onOpenRelationship = { character -> manageThreadId = null; relationshipCharacterId = character.id },
                proactiveEnabled = !state.proactiveSelectionConfigured || thread.type == ConversationType.GROUP || privateCharacterIsEnabled(state, thread.id),
                proactivePaused = thread.type == ConversationType.PRIVATE && privateCharacterIsPaused(state, thread.id),
                onSetProactive = viewModel::setCharacterProactive,
                onResumeProactive = viewModel::resumeCharacterProactive,
                onToggleGroupType = { currentType -> viewModel.toggleGroupType(thread.id, currentType) }
            )
        }
    }
    state.recoveryProtectionMessage?.let { message -> RecoveryProtectionDialog(message) }
    relationshipCharacterId?.let { characterId ->
        state.characters.firstOrNull { it.id == characterId }?.let { character ->
            RelationshipHomePage(
                character = character,
                state = state,
                livingState = livingStates.firstOrNull { it.characterId == characterId },
                onBack = { relationshipCharacterId = null },
                onDeleteMemory = viewModel::deleteMemory,
                onToggleMemoryBlocked = viewModel::setMemoryBlocked,
                onDeleteDiaryPhoto = viewModel::deleteDiaryPhoto
            )
        }
    }
}

@Composable
private fun RecoveryProtectionDialog(message: String) {
    val colors = AsterTheme.colors
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Data recovery protection", color = colors.text, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = colors.textMuted, fontSize = AsterType.bodyLarge, lineHeight = 21.sp) },
        confirmButton = {}
    )
}

@Composable
private fun RelationshipHomePage(
    character: com.aster.companion.data.CharacterEntity,
    state: com.aster.companion.ui.CompanionHomeState,
    livingState: com.aster.companion.data.CharacterLivingStateEntity?,
    onBack: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onToggleMemoryBlocked: (String, Boolean) -> Unit,
    /** 长按「她留下的照片」→ 删除（日记页与关系页共用同一个回调）。 */
    onDeleteDiaryPhoto: (String) -> Unit
) {
    val colors = AsterTheme.colors
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    // 长按「她留下的照片」→ 删除（先确认）。
    var pendingPhotoDeleteUri by rememberSaveable(character.id) { mutableStateOf<String?>(null) }
    pendingPhotoDeleteUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingPhotoDeleteUri = null },
            title = { Text("删除这张照片？") },
            text = { Text("会从「她留下的照片」里删掉，聊天记录里的那条消息不受影响。", color = colors.textMuted, fontSize = AsterType.body) },
            confirmButton = {
                TextButton(onClick = { onDeleteDiaryPhoto(uri); pendingPhotoDeleteUri = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingPhotoDeleteUri = null }) { Text("取消", color = colors.textMuted) } }
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val characterFacts = state.memories
        .filter { it.characterId == character.id && it.kind != MemoryKind.CONVERSATION }
        .sortedByDescending { it.updatedAt }
    val conversationMemories = state.memories
        .filter { it.characterId == character.id && it.kind == MemoryKind.CONVERSATION }
        .sortedByDescending { it.updatedAt }
        .take(5)
    val diary = state.memories.filter { it.characterId == character.id && it.kind == MemoryKind.EVENT }.maxByOrNull { it.updatedAt }
    val recentPhoto = state.diaryPhotos.firstOrNull { it.characterId == character.id }
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = colors.text) }
            Text("${character.name} 的资料与记忆", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(colors.accentWash), contentAlignment = Alignment.Center) {
                        if (!character.avatarUri.isNullOrBlank()) AsyncImage(character.avatarUri, "${character.name} 头像", Modifier.fillMaxSize().clip(CircleShape))
                        else Text(character.name.take(1), color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column { Text(character.name, color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("每个角色有各自的记忆", color = colors.textMuted, fontSize = AsterType.body, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
            item {
                val activity = livingState?.currentActivity?.trim().orEmpty()
                val sleepingUntil = livingState?.sleepUntil?.takeIf { it > nowMillis }
                val sleeping = sleepingUntil != null
                val plannedWake = sleepingUntil
                val clock = SimpleDateFormat("HH:mm", Locale.CHINA)
                val headline = when {
                    livingState == null -> "暂时没有她的记录"
                    sleeping -> "正在睡觉"
                    activity.isBlank() -> "安静待着"
                    else -> activity
                }
                val subtitle = when {
                    livingState == null -> "过一会儿再看，她自己会过日子。"
                    sleeping -> plannedWake?.let { "预计 ${clock.format(Date(it))} 醒来" } ?: "睡着中"
                    else -> {
                        val minutes = (nowMillis - livingState.activityStartedAt).coerceAtLeast(0L) / 60_000L
                        "心情 ${livingState.mood.ifBlank { "平静" }}" +
                            if (minutes > 0) " · 已持续 $minutes 分钟" else ""
                    }
                }
                Column(
                    Modifier.fillMaxWidth()
                        .asterCard(colors, radius = 24.dp)
                        .padding(18.dp)
                ) {
                    Text("她现在", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        headline,
                        color = colors.text,
                        fontSize = AsterType.title,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(subtitle, color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 6.dp))
                }
            }
            recentPhoto?.let { photo ->
                item {
                    AsyncImage(
                        photo.imageUri,
                        "${character.name} 留下的照片",
                        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(AsterMetrics.cardRadius))
                            // 长按删除（和日记页同一个回调）。
                            .combinedClickable(onClick = {}, onLongClick = { pendingPhotoDeleteUri = photo.imageUri }),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            // 这页叫「资料与记忆」，但记忆那部分以前只算了变量、没渲染出来（回调也一直是死的）——
            // 现在按角色列出来：删掉就不会再提，屏蔽只是不再主动引用（和日记页里那份口径一致）。
            if (characterFacts.isNotEmpty()) {
                item {
                    Text("她记住的事", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
                    Text("删掉就不会再提；屏蔽只是不再主动引用。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
                }
                items(characterFacts.take(MAX_RELATIONSHIP_MEMORIES), key = { it.id }) { memory ->
                    Column(Modifier.fillMaxWidth().asterCard(colors).padding(AsterMetrics.cardPadding)) {
                        Text(memory.content.take(120), color = colors.text, fontSize = AsterType.body, lineHeight = 19.sp)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(memory.kind.name.lowercase(), color = colors.textMuted, fontSize = AsterType.micro, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onToggleMemoryBlocked(memory.id, !memory.blockedFromMention) }) {
                                Text(if (memory.blockedFromMention) "取消屏蔽" else "屏蔽", color = colors.textSubtle, fontSize = AsterType.label)
                            }
                            TextButton(onClick = { onDeleteMemory(memory.id) }) {
                                Text("删除", color = colors.errorBright, fontSize = AsterType.label)
                            }
                        }
                    }
                }
            }
            if (conversationMemories.isNotEmpty()) {
                item {
                    Text("最近聊过的片段", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
                    conversationMemories.forEach { memory ->
                        Text("· ${memory.content.take(80)}", color = colors.textMuted, fontSize = AsterType.label, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (diary != null) {
                item {
                    Text("最近的一条共同片段", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
                    Text(diary.content.take(200), color = colors.textMuted, fontSize = AsterType.label, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** 单人资料页最多列这么多条记忆（再多也没人翻，且这页每次都要重排）。 */
private const val MAX_RELATIONSHIP_MEMORIES = 20

@Composable
private fun privateCharacterIsEnabled(state: com.aster.companion.ui.CompanionHomeState, threadId: String): Boolean =
    state.threadParticipants[threadId]?.firstOrNull() in state.proactiveCharacterIds

private fun privateCharacterIsPaused(state: com.aster.companion.ui.CompanionHomeState, threadId: String): Boolean =
    state.threadParticipants[threadId]?.firstOrNull() in state.proactivelyPausedCharacterIds

@Composable
private fun TongpinBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface1)
            .border(1.dp, colors.border)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MainTab.entries.filter { tab ->
            // Lite 底部导航只有 消息 / 动态 / 设置（计划书 §7.3）。
            !com.aster.companion.lite.LiteProductConfig.ENABLED ||
                tab.name in com.aster.companion.lite.LiteProductConfig.BOTTOM_TABS
        }.forEach { tab ->
            val isSelected = tab == selected
            val icon = when (tab) {
                MainTab.Chat -> Icons.Outlined.ChatBubbleOutline
                MainTab.Moments -> Icons.Outlined.Public
                MainTab.Memory -> Icons.Outlined.Favorite
                MainTab.Settings -> Icons.Outlined.Settings
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AsterMetrics.cardRadius))
                    .background(if (isSelected) colors.accentWash else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) colors.primary else colors.textMuted,
                    modifier = Modifier.size(21.dp)
                )
                Text(
                    text = tab.label,
                    color = if (isSelected) colors.text else colors.textMuted,
                    fontSize = AsterType.caption,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ThreadCreationDialog(
    type: ConversationType,
    characters: List<com.aster.companion.data.CharacterEntity>,
    onDismiss: () -> Unit,
    onCreate: (List<String>, String) -> Unit
) {
    var title by rememberSaveable(type) { mutableStateOf("") }
    var selectedIds by remember(type) { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == ConversationType.PRIVATE) "新建私聊" else "创建群聊") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("会话名称") })
                characters.forEach { character ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(character.name)
                        Checkbox(
                            checked = character.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = when {
                                    type == ConversationType.PRIVATE && checked -> setOf(character.id)
                                    checked -> selectedIds + character.id
                                    else -> selectedIds - character.id
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            val valid = if (type == ConversationType.PRIVATE) selectedIds.size == 1 else selectedIds.size >= 2
            Button(onClick = { onCreate(selectedIds.toList(), title) }, enabled = valid) { Text("创建") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddConversationDialog(onDismiss: () -> Unit, onNewCharacter: () -> Unit, onNewGroup: () -> Unit) {
    val colors = AsterTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        scrimColor = Color.Black.copy(alpha = .28f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("添加", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            Text("选择要开始的对话", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(bottom = 4.dp))
            AddConversationOption(Icons.Outlined.PersonAdd, "添加角色", "建立一段新的私聊", onNewCharacter)
            if (!com.aster.companion.lite.LiteProductConfig.ENABLED) {
                AddConversationOption(Icons.Outlined.Groups, "创建群聊", "邀请已有角色一起聊天", onNewGroup)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
                Text("取消", color = colors.textSubtle, fontSize = AsterType.bodyLarge)
            }
        }
    }
}

@Composable
private fun AddConversationOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AsterMetrics.cardRadius))
            .background(colors.accentWash)
            .border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(Modifier.size(38.dp).background(colors.surface1, RoundedCornerShape(AsterMetrics.controlRadius)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
            Text(description, color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
        }
        Text("前往", color = colors.primary, fontSize = AsterType.label)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CharacterEditorDialog(
    character: com.aster.companion.data.CharacterEntity?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String?, String?, String?, String) -> Unit,
    onCreate: (String, String, String, String?, String?, String?, String) -> Unit
) {
    var name by rememberSaveable(character?.id) { mutableStateOf(character?.name.orEmpty()) }
    var personality by rememberSaveable(character?.id) { mutableStateOf(character?.personalityPrompt.orEmpty()) }
    var appearance by rememberSaveable(character?.id) { mutableStateOf(character?.appearancePrompt.orEmpty()) }
    var relationship by rememberSaveable(character?.id) { mutableStateOf(character?.relationshipPrompt.orEmpty()) }
    var referenceUri by rememberSaveable(character?.id) { mutableStateOf(character?.referenceImageUri.orEmpty()) }
    var styleReferenceUri by rememberSaveable(character?.id) { mutableStateOf(character?.styleReferenceImageUri.orEmpty()) }
    var avatarUri by rememberSaveable(character?.id) { mutableStateOf(character?.avatarUri.orEmpty()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun persistImageUri(uri: android.net.Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    val pickReferenceImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistImageUri(it); referenceUri = it.toString() }
    }
    val pickStyleReferenceImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistImageUri(it); styleReferenceUri = it.toString() }
    }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistImageUri(it); avatarUri = it.toString() }
    }
    val colors = AsterTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        scrimColor = Color.Black.copy(alpha = .28f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().height(680.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (character == null) "新建角色" else "角色资料", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("人格与外貌信息分别保存", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 3.dp))
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.textSubtle) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(colors.accentWash).border(1.dp, colors.accentLine, CircleShape), contentAlignment = Alignment.Center) {
                            if (avatarUri.isNotBlank()) AsyncImage(model = avatarUri, contentDescription = "角色头像", modifier = Modifier.fillMaxSize().clip(CircleShape))
                            else Text(name.take(1).ifBlank { "?" }, color = colors.primary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { pickAvatar.launch(arrayOf("image/*")) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("设置头像") }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(colors.surface2, RoundedCornerShape(AsterMetrics.cardRadius)).padding(14.dp)) {
                        Text("画风参考图", color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("只控制笔触、上色、光影和整体审美，不会替换人物的脸型、发型或体型。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                        if (styleReferenceUri.isNotBlank()) {
                            AsyncImage(
                                model = styleReferenceUri,
                                contentDescription = "当前画风参考图",
                                modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(AsterMetrics.controlRadius)).border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.controlRadius))
                            )
                            Text("Qwen 会同时接收身份参考图与这张画风参考图。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text("未选择时只按文字画风规则生成。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        OutlinedButton(onClick = { pickStyleReferenceImage.launch(arrayOf("image/*")) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("选择画风参考图") }
                    }
                }
                item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("角色名称") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius)) }
                item {
                    Column {
                        Text("你们的关系", color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("单独描述角色与你的关系、称呼、亲密距离和相处边界；不会进入生图提示词。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp, bottom = 7.dp))
                        OutlinedTextField(relationship, { relationship = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), minLines = 3, maxLines = 6, shape = RoundedCornerShape(AsterMetrics.cardRadius))
                    }
                }
                item {
                    Column {
                        Text("人物设定", color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("人格、关系、称呼、语气与相处边界", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp, bottom = 7.dp))
                        OutlinedTextField(personality, { personality = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), minLines = 6, shape = RoundedCornerShape(AsterMetrics.cardRadius))
                    }
                }
                item {
                    Column {
                        Text("外貌提示词", color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("只用于生成图片，不进入聊天人格", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp, bottom = 7.dp))
                        OutlinedTextField(appearance, { appearance = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), minLines = 5, shape = RoundedCornerShape(AsterMetrics.cardRadius))
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(colors.surface2, RoundedCornerShape(AsterMetrics.cardRadius)).padding(14.dp)) {
                        Text("身份参考图（三视图）", color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (referenceUri.isBlank()) "未选择，无法稳定锁定脸型、发型和体型" else "已选择人物身份参考图", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                        if (referenceUri.isNotBlank()) {
                            AsyncImage(
                                model = referenceUri,
                                contentDescription = "当前身份参考图",
                                modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(AsterMetrics.controlRadius)).border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.controlRadius))
                            )
                            Text("这张图只锁定她是谁：脸型、发型、体型和稳定身份。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 8.dp))
                        }
                        OutlinedButton(onClick = { pickReferenceImage.launch(arrayOf("image/*")) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("选择身份参考图") }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().border(1.dp, colors.border).padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("取消", color = colors.textSubtle) }
                Button(
                    onClick = {
                        if (character == null) onCreate(name, personality, appearance, referenceUri.ifBlank { null }, styleReferenceUri.ifBlank { null }, avatarUri.ifBlank { null }, relationship)
                        else onSave(character.id, name, personality, appearance, referenceUri.ifBlank { null }, styleReferenceUri.ifBlank { null }, avatarUri.ifBlank { null }, relationship)
                        onDismiss()
                    },
                    enabled = name.isNotBlank() && personality.isNotBlank(),
                    shape = RoundedCornerShape(AsterMetrics.cardRadius),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun ConversationManagerDialog(
    thread: ThreadEntity,
    characters: List<com.aster.companion.data.CharacterEntity>,
    memberIds: List<String>,
    groupSnapshot: GroupManagementSnapshot,
    relatedGroupTitles: List<String>,
    onDismiss: () -> Unit,
    onClearHistory: () -> Unit,
    onDeleteFriend: () -> Unit,
    onDeleteGroup: () -> Unit,
    onSaveGroup: (String, List<String>, GroupAtmosphere, Boolean) -> Unit,
    onSaveFact: (String?, String, Boolean) -> Unit,
    onDeleteFact: (String) -> Unit,
    onEditCharacter: (com.aster.companion.data.CharacterEntity) -> Unit,
    onOpenRelationship: (com.aster.companion.data.CharacterEntity) -> Unit,
    proactiveEnabled: Boolean,
    proactivePaused: Boolean,
    onSetProactive: (String, Boolean) -> Unit,
    onResumeProactive: (String) -> Unit,
    onToggleGroupType: (String) -> Unit,
    groupMemories: List<com.aster.companion.data.MemoryEntity>
) {
    val privateCharacter = characters.firstOrNull { it.id in memberIds }
    var title by rememberSaveable(thread.id) { mutableStateOf(thread.title) }
    var selectedIds by remember(thread.id, memberIds) { mutableStateOf(memberIds.toSet()) }
    var confirmation by rememberSaveable(thread.id) { mutableStateOf<ConversationManagementAction?>(null) }
    var atmosphereName by rememberSaveable(thread.id, groupSnapshot.atmosphere.name) { mutableStateOf(groupSnapshot.atmosphere.name) }
    var groupProactiveEnabled by rememberSaveable(thread.id, groupSnapshot.proactiveEnabled) { mutableStateOf(groupSnapshot.proactiveEnabled) }
    var factDraft by rememberSaveable(thread.id) { mutableStateOf("") }
    var editingFactId by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var factAllowedInGroup by rememberSaveable(thread.id) { mutableStateOf(true) }
    val atmosphere = runCatching { GroupAtmosphere.valueOf(atmosphereName) }.getOrDefault(GroupAtmosphere.DAILY)
    var groupTypeName by rememberSaveable(thread.id, groupSnapshot.groupType) { mutableStateOf(groupSnapshot.groupType) }
    val colors = AsterTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        scrimColor = Color.Black.copy(alpha = .28f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (thread.type == ConversationType.GROUP) "群聊管理" else "角色管理", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(if (thread.type == ConversationType.GROUP) "调整成员与群聊名称" else "管理 ${privateCharacter?.name ?: "这个角色"}", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 3.dp))
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.textSubtle) }
            }
            if (thread.type == ConversationType.PRIVATE) {
                privateCharacter?.let { character ->
                    OutlinedButton(onClick = { onEditCharacter(character) }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("编辑角色资料") }
                    // 「资料与记忆」页的**唯一入口**就是这里。这个回调以前一直传着却没人调 ——
                    // 页面还活着但进不去（和"更新按钮只在僵尸页里"是同一类问题）。这里把它接回来。
                    OutlinedButton(onClick = { onOpenRelationship(character) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("${character.name} 的资料与记忆") }
                    if (proactivePaused) {
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp).border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text("主动联系已暂停", color = colors.primary, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
                            Text("你说回来、醒了、到家或忙完等意思相近的话后会自动恢复；就算没说，睡过一夜也会自动恢复。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = { onResumeProactive(character.id) }, modifier = Modifier.padding(top = 4.dp)) {
                                Text("立即恢复主动联系", color = colors.primary, fontSize = AsterType.label)
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { onSetProactive(character.id, !proactiveEnabled) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(AsterMetrics.cardRadius)) {
                            Text(if (proactiveEnabled) "主动联系已开启" else "主动联系已关闭")
                        }
                    }
                }
                OutlinedButton(onClick = { confirmation = ConversationManagementAction.ClearPrivateHistory }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("清空聊天记录") }
                if (relatedGroupTitles.isEmpty()) {
                    TextButton(onClick = { confirmation = ConversationManagementAction.DeleteFriend }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("删除好友", color = colors.error) }
                } else {
                    Text("${privateCharacter?.name ?: "此角色"} 仍在${relatedGroupTitles.joinToString("、")}，请先从群聊中移除后再删除好友。", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 18.dp).keepVisibleWhenKeyboardOpened(), label = { Text("群名称") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius))
                Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    characters.forEach { character ->
                        Row(Modifier.fillMaxWidth().clickable { selectedIds = if (character.id in selectedIds) selectedIds - character.id else selectedIds + character.id }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(character.id in selectedIds, onCheckedChange = null)
                            Column(Modifier.padding(start = 5.dp)) {
                                Text(character.name, color = colors.text, fontSize = AsterType.bodyLarge)
                                if (character.relationshipPrompt.isNotBlank()) {
                                    Text(character.relationshipPrompt.take(64), color = colors.textSubtle, fontSize = AsterType.micro, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
                Text("群氛围", color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp).background(colors.surface2, RoundedCornerShape(AsterMetrics.controlRadius)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GroupAtmosphere.entries.forEach { option ->
                        val selected = atmosphere == option
                        Text(
                            option.label(),
                            color = if (selected) colors.primary else colors.textMuted,
                            fontSize = AsterType.label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(AsterMetrics.microRadius)).background(if (selected) colors.accentWash else Color.Transparent).clickable { atmosphereName = option.name }.padding(vertical = 7.dp)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("允许群成员主动聊天", color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
                        Text("开启后，她们会基于真实群上下文自然续话或开小话题。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
                    }
                    Switch(checked = groupProactiveEnabled, onCheckedChange = { groupProactiveEnabled = it })
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("对话模式", color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
                        Text("群聊 = 远程聊天；家庭 = 真实家庭，面对面说话/喊话，按各自状态开口。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
                    }
                    TextButton(onClick = { onToggleGroupType(groupTypeName) }, modifier = Modifier.padding(start = 8.dp)) {
                        Text(if (groupTypeName == "FAMILY") "当前：家庭模式 · 切回群聊" else "当前：群聊模式 · 切换家庭", color = if (groupTypeName == "FAMILY") colors.primary else colors.textMuted, fontSize = AsterType.label)
                    }
                }
                if (groupSnapshot.memberStates.isNotEmpty()) {
                    Text("群内状态", color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                    groupSnapshot.memberStates.forEach { memberState ->
                        val memberName = characters.firstOrNull { it.id == memberState.characterId }?.name ?: memberState.characterId
                        val intent = memberState.unspokenIntent.trim().ifBlank { "正在观察群里的聊天" }
                        Text("$memberName · $intent", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 5.dp))
                    }
                }
                if (groupMemories.isNotEmpty()) {
                    Text("群聊记忆", color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupMemories.forEach { memory ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(AsterMetrics.microRadius))
                                    .background(colors.surface2)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(memory.content, color = colors.text, fontSize = AsterType.label, lineHeight = 18.sp)
                                Text(
                                    relativeTimeLabel(memory.updatedAt),
                                    color = colors.textMuted, fontSize = AsterType.micro, modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                Text("群共享摘要", color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                Text("只会进入这个群的上下文，不会覆盖任何角色的私聊记忆。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
                OutlinedTextField(
                    value = factDraft,
                    onValueChange = { factDraft = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).keepVisibleWhenKeyboardOpened(),
                    label = { Text(if (editingFactId == null) "添加群共享摘要" else "编辑群共享摘要") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(AsterMetrics.controlRadius)
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = factAllowedInGroup, onCheckedChange = { factAllowedInGroup = it })
                    Text("允许带入群聊上下文", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(start = 5.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onSaveFact(editingFactId, factDraft.trim(), factAllowedInGroup)
                            factDraft = ""
                            editingFactId = null
                            factAllowedInGroup = true
                        },
                        enabled = factDraft.isNotBlank()
                    ) { Text("保存摘要", color = colors.primary) }
                }
                groupSnapshot.facts.take(8).forEach { fact ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            factDraft = fact.summary
                            editingFactId = fact.id
                            factAllowedInGroup = fact.allowedInGroup
                        }) {
                            Text(fact.summary, color = colors.text, fontSize = AsterType.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (fact.allowedInGroup) "已允许带入群聊" else "已禁止带入群聊", color = colors.textSubtle, fontSize = AsterType.micro, modifier = Modifier.padding(top = 2.dp))
                        }
                        IconButton(onClick = { onDeleteFact(fact.id) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除共享摘要", tint = colors.textSubtle, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                    TextButton(onClick = { confirmation = ConversationManagementAction.ClearGroupHistory }) { Text("清空记录", color = colors.textSubtle) }
                    TextButton(onClick = { confirmation = ConversationManagementAction.DeleteGroup }) { Text("删除群聊", color = colors.error) }
                    Button(onClick = { onSaveGroup(title, selectedIds.toList(), atmosphere, groupProactiveEnabled) }, enabled = title.isNotBlank() && selectedIds.size >= 2, shape = RoundedCornerShape(AsterMetrics.cardRadius), modifier = Modifier.weight(1f)) { Text("保存") }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    confirmation?.let { action ->
        ConversationManagementConfirmation(
            action = action,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                when (action) {
                    ConversationManagementAction.ClearPrivateHistory,
                    ConversationManagementAction.ClearGroupHistory -> {
                        onClearHistory()
                        onDismiss()
                    }
                    ConversationManagementAction.DeleteFriend -> onDeleteFriend()
                    ConversationManagementAction.DeleteGroup -> onDeleteGroup()
                }
            }
        )
    }
}

private enum class ConversationManagementAction(
    val title: String,
    val description: String,
    val confirmLabel: String,
    val destructive: Boolean
) {
    ClearPrivateHistory("清空聊天记录？", "会删除这段私聊的全部消息，并把这个角色重置到全新状态——她的记忆、性格推演、自主生活状态、目标、活动轨迹、朋友圈和照片都会一并清空。角色卡和她与其他角色的关系保留。", "清空", false),
    ClearGroupHistory("清空群聊记录？", "只删除群聊消息。群聊名称、成员和所有角色卡都会保留。", "清空", false),
    DeleteFriend("删除好友？", "将删除该角色卡、私聊和此私聊的全部记录。其他角色不会受影响。", "删除好友", true),
    DeleteGroup("删除群聊？", "将删除该群聊和全部群聊记录。群成员的角色卡和私聊都会保留。", "删除群聊", true)
}

@Composable
private fun ConversationManagementConfirmation(
    action: ConversationManagementAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = AsterTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.title, color = colors.text, fontWeight = FontWeight.Bold) },
        text = { Text(action.description, color = colors.textMuted, fontSize = AsterType.bodyLarge) },
        confirmButton = {
            if (action.destructive) {
                TextButton(onClick = onConfirm) { Text(action.confirmLabel, color = colors.error) }
            } else {
                Button(onClick = onConfirm, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text(action.confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = colors.textSubtle) } }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MomentsPage(
    state: com.aster.companion.ui.CompanionHomeState,
    onPublish: (String, List<String>) -> Unit,
    onToggleLike: (String, Boolean) -> Unit,
    onComment: (String, String, String?) -> Unit,
    onEdit: (String, String, List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onApproveImage: (String) -> Unit,
    onRejectImage: (String) -> Unit,
    scenePhoto: com.aster.companion.lite.ScenePhotoRequest?,
    onRequestMomentScenePhoto: (String, String) -> Unit,
    onUpdateScenePhotoPrompt: (String) -> Unit,
    onConfirmScenePhoto: () -> Unit,
    onRetryScenePhoto: () -> Unit,
    onCancelScenePhoto: () -> Unit,
    openedPostId: String?,
    modifier: Modifier
) {
    var body by rememberSaveable { mutableStateOf("") }
    var selectedImages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var commentPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentReplyToId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentReplyToName by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentDraft by rememberSaveable { mutableStateOf("") }
    var previewImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = AsterTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = (selectedImages + uris.map { it.toString() }).distinct().take(9)
    }
    val characters = state.characters.associateBy { it.id }
    val mediaByPost = state.momentMedia.groupBy { it.postId }
    val commentsByPost = state.momentComments.groupBy { it.postId }
    val reactionsByPost = state.momentReactions.groupBy { it.postId }
    val pendingImageActions = state.imageGenerationActions.filter {
        it.capabilityId == "companion.generate_moment_image" && it.state == "PENDING"
    }
    val listState = rememberLazyListState()
    LaunchedEffect(openedPostId, state.momentPosts) {
        val postIndex = openedPostId?.let { id -> state.momentPosts.indexOfFirst { it.id == id } } ?: -1
        if (postIndex >= 0) listState.animateScrollToItem(2 + postIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(colors.bg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("动态", color = colors.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("看看她们此刻想分享什么", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = { body = "" }) {
                    Icon(Icons.Outlined.Add, contentDescription = "发布动态", tint = colors.primary)
                }
            }
        }
        scenePhoto?.takeIf { it.momentPostId != null }?.let { momentScene ->
            item {
                ScenePhotoCard(
                    request = momentScene,
                    characterName = characters[momentScene.characterId]?.name ?: "她",
                    onPromptChange = onUpdateScenePhotoPrompt,
                    onConfirm = onConfirmScenePhoto,
                    onRetry = onRetryScenePhoto,
                    onCancel = onCancelScenePhoto
                )
            }
        }
        if (pendingImageActions.isNotEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius))
                        .border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("有角色想发动态照片", color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                    pendingImageActions.forEach { action ->
                        PhotoConsentCard(
                            action = action,
                            characterNames = characters.mapValues { it.value.name },
                            onApprove = { onApproveImage(action.id) },
                            onReject = { onRejectImage(action.id) }
                        )
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().asterCard(colors, radius = 24.dp).padding(16.dp)
            ) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(2_000) },
                    modifier = Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("分享此刻的心情、照片或一件小事", color = colors.textSubtle) },
                    shape = RoundedCornerShape(AsterMetrics.cardRadius)
                )
                if (selectedImages.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        selectedImages.chunked(3).forEach { rowImages ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                rowImages.forEach { uri ->
                                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                                        AsyncImage(model = uri, contentDescription = "待发布图片", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(AsterMetrics.controlRadius)), contentScale = ContentScale.Crop)
                                        IconButton(onClick = { selectedImages = selectedImages - uri }, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                                            Icon(Icons.Outlined.Close, contentDescription = "移除图片", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = .48f), CircleShape).padding(5.dp))
                                        }
                                    }
                                }
                                repeat(3 - rowImages.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickImages.launch("image/*") }, shape = RoundedCornerShape(AsterMetrics.controlRadius)) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("图片")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            onPublish(body.trim(), selectedImages)
                            body = ""
                            selectedImages = emptyList()
                        },
                        enabled = body.isNotBlank() || selectedImages.isNotEmpty(),
                        shape = RoundedCornerShape(AsterMetrics.cardRadius)
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("发布")
                    }
                }
            }
        }
        if (state.momentPosts.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Public, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(36.dp))
                    Text("还没有动态", color = colors.textMuted, fontSize = AsterType.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    Text("发一条，让她们有机会来回应你", color = colors.textSubtle, fontSize = AsterType.label, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            items(state.momentPosts, key = { it.id }) { post ->
                MomentCard(
                    post = post,
                    character = post.authorCharacterId?.let(characters::get),
                    characters = characters,
                    media = mediaByPost[post.id].orEmpty(),
                    comments = commentsByPost[post.id].orEmpty(),
                    reactions = reactionsByPost[post.id].orEmpty(),
                    onImageClick = { previewImageUri = it },
                    onToggleLike = { onToggleLike(post.id, it) },
                    onComment = { commentPostId = post.id; commentReplyToId = null; commentReplyToName = null; commentDraft = "" },
                    onReply = { comment ->
                        commentPostId = post.id
                        commentReplyToId = comment.id
                        commentReplyToName = if (comment.authorType == MomentActors.USER) "我" else {
                            comment.authorCharacterId?.let { id -> state.characters.firstOrNull { it.id == id }?.name } ?: "角色"
                        }
                        commentDraft = ""
                    },
                    // 别人的动态不给「编辑」：那是角色写的内容，只该由她自己改。
                    onEdit = if (post.authorType == MomentActors.USER) {
                        { editingPostId = post.id }
                    } else null,
                    onDelete = { deletingPostId = post.id },
                    // 配图必须配给这条动态的主人；用户自己的动态没有角色主人，就不给「配图」。
                    onComposeImage = if (com.aster.companion.lite.LiteProductConfig.ENABLED && post.authorCharacterId != null) {
                        { onRequestMomentScenePhoto(post.id, post.authorCharacterId!!) }
                    } else null
                )
            }
        }
    }
    commentPostId?.let { postId ->
        AlertDialog(
            onDismissRequest = { commentPostId = null; commentReplyToId = null; commentReplyToName = null },
            title = { Text(if (commentReplyToId == null) "写一条评论" else "回复 ${commentReplyToName ?: "评论"}") },
            text = {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it.take(2_000) },
                    modifier = Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = { Text(if (commentReplyToId == null) "说点自然的话" else "回复 ${commentReplyToName ?: "这条评论"}") },
                    shape = RoundedCornerShape(AsterMetrics.cardRadius)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (commentDraft.isNotBlank()) onComment(postId, commentDraft.trim(), commentReplyToId)
                    commentPostId = null
                    commentReplyToId = null
                    commentReplyToName = null
                }) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = { commentPostId = null; commentReplyToId = null; commentReplyToName = null }) { Text("取消") } }
        )
    }
    editingPostId?.let { postId ->
        val post = state.momentPosts.firstOrNull { it.id == postId }
        if (post != null) {
            MomentEditorDialog(
                post = post,
                initialMedia = mediaByPost[postId].orEmpty(),
                onDismiss = { editingPostId = null },
                onSave = { editedBody, imageUris ->
                    onEdit(postId, editedBody, imageUris)
                    editingPostId = null
                }
            )
        } else {
            editingPostId = null
        }
    }
    deletingPostId?.let { postId ->
        val post = state.momentPosts.firstOrNull { it.id == postId }
        AlertDialog(
            onDismissRequest = { deletingPostId = null },
            title = { Text("删除动态") },
            text = {
                Text(
                    if (post?.authorType == MomentActors.USER) {
                        "确定删除这条动态吗？图片、点赞和评论会一起删除。"
                    } else {
                        "确定删除这条角色动态吗？图片、点赞和评论会一起删除。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(postId)
                    deletingPostId = null
                }) { Text("删除", color = colors.errorBright) }
            },
            dismissButton = { TextButton(onClick = { deletingPostId = null }) { Text("取消") } }
        )
    }
    previewImageUri?.let { uri -> ImagePreviewDialog(uri) { previewImageUri = null } }
}

@Composable
private fun MomentCard(
    post: MomentPostEntity,
    character: com.aster.companion.data.CharacterEntity?,
    characters: Map<String, com.aster.companion.data.CharacterEntity>,
    media: List<MomentMediaEntity>,
    comments: List<MomentCommentEntity>,
    reactions: List<MomentReactionEntity>,
    onImageClick: (String) -> Unit,
    onToggleLike: (Boolean) -> Unit,
    onComment: () -> Unit,
    onReply: (com.aster.companion.data.MomentCommentEntity) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onComposeImage: (() -> Unit)? = null
) {
    val colors = AsterTheme.colors
    val isUser = post.authorType == MomentActors.USER
    val liked = reactions.any { it.actorType == MomentActors.USER && it.actorId == MomentActors.USER_ID && it.kind == MomentActors.LIKE }
    val name = if (isUser) "我" else character?.name ?: "角色"
    Column(Modifier.fillMaxWidth().asterCard(colors).padding(AsterMetrics.cardPadding)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accentWash), contentAlignment = Alignment.Center) {
                if (!isUser && !character?.avatarUri.isNullOrBlank()) AsyncImage(model = character?.avatarUri, contentDescription = "${name}头像", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                else Text(name.take(1), color = colors.primary, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(name, color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
                Text(momentTime(post.createdAt), color = colors.textSubtle, fontSize = AsterType.caption, modifier = Modifier.padding(top = 2.dp))
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑动态", tint = colors.textSubtle, modifier = Modifier.size(19.dp)) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "删除动态", tint = colors.textSubtle, modifier = Modifier.size(19.dp)) }
        }
        if (post.body.isNotBlank()) Text(post.body, color = colors.text, fontSize = AsterType.title, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp))
        if (post.status == MomentActors.STATUS_GENERATING) Text("正在生成配图…", color = colors.primary, fontSize = AsterType.label, modifier = Modifier.padding(top = 10.dp))
        if (post.status == MomentActors.STATUS_FAILED) Text("配图生成失败，文字动态已保留", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 10.dp))
        if (media.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                media.sortedBy { it.ordinal }.chunked(if (media.size == 1) 1 else 3).forEach { rowImages ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        rowImages.forEach { item ->
                            AsyncImage(
                                model = item.imageUri,
                                contentDescription = "动态图片",
                                modifier = Modifier.weight(1f).then(if (media.size == 1) Modifier.height(250.dp) else Modifier.aspectRatio(1f)).clip(RoundedCornerShape(AsterMetrics.controlRadius)).clickable { onImageClick(item.imageUri) },
                                contentScale = ContentScale.Crop
                            )
                        }
                        repeat((if (media.size == 1) 0 else 3) - rowImages.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${reactions.size} 个赞", color = colors.textMuted, fontSize = AsterType.caption)
            Spacer(Modifier.weight(1f))
            Text("${comments.size} 条评论", color = colors.textMuted, fontSize = AsterType.caption)
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TextButton(onClick = { onToggleLike(liked) }, modifier = Modifier.weight(1f)) {
                Icon(if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null, tint = if (liked) colors.primary else colors.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (liked) "已赞" else "赞", color = if (liked) colors.primary else colors.textMuted)
            }
            TextButton(onClick = onComment, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("评论", color = colors.textMuted)
            }
            if (onComposeImage != null) {
                TextButton(onClick = onComposeImage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("配图", color = colors.textMuted)
                }
            }
        }
        var showAllComments by remember(post.id) { mutableStateOf(false) }
        val visibleComments = if (showAllComments) comments else comments.takeLast(4)
        visibleComments.forEach { comment ->
            val commentName = MomentCommentPresentation.authorLine(comment, comments) { characterId ->
                characters[characterId]?.name
                    ?: character?.takeIf { it.id == characterId }?.name
                    ?: "角色"
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) { append(commentName); append("：") }
                    withStyle(SpanStyle(color = colors.text)) { append(comment.body) }
                }, fontSize = AsterType.label, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onReply(comment) }
                        .defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("回复", color = colors.textSubtle, fontSize = AsterType.caption)
                }
            }
        }
        if (comments.size > 4) {
            Text(
                if (showAllComments) "收起评论" else "查看全部 ${comments.size} 条评论",
                color = colors.primary,
                fontSize = AsterType.caption,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAllComments = !showAllComments }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun MomentEditorDialog(
    post: MomentPostEntity,
    initialMedia: List<MomentMediaEntity>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    var body by rememberSaveable(post.id, post.updatedAt) { mutableStateOf(post.body) }
    var imageUris by rememberSaveable(post.id, post.updatedAt) {
        mutableStateOf(initialMedia.sortedBy { it.ordinal }.map { it.imageUri })
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        imageUris = (imageUris + uris.map { it.toString() }).distinct().take(9)
    }
    val colors = AsterTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑动态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(if (post.authorType == MomentActors.USER) 2_000 else 1_000) },
                    modifier = Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("这一刻想说什么") },
                    shape = RoundedCornerShape(AsterMetrics.cardRadius)
                )
                if (imageUris.isNotEmpty()) {
                    imageUris.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { uri ->
                                Box(Modifier.weight(1f).aspectRatio(1f)) {
                                    AsyncImage(model = uri, contentDescription = "动态图片", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(AsterMetrics.controlRadius)), contentScale = ContentScale.Crop)
                                    IconButton(onClick = { imageUris = imageUris - uri }, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                                        Icon(Icons.Outlined.Close, contentDescription = "移除图片", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = .48f), CircleShape).padding(5.dp))
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = imageUris.size < 9, shape = RoundedCornerShape(AsterMetrics.controlRadius)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageUris.isEmpty()) "添加图片" else "添加图片 (${imageUris.size}/9)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(body.trim(), imageUris) },
                enabled = body.isNotBlank() || imageUris.isNotEmpty(),
                shape = RoundedCornerShape(AsterMetrics.cardRadius)
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = colors.textSubtle) } }
    )
}

private fun momentTime(timestamp: Long): String = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(timestamp))

@Composable
private fun ChatListPage(
    threads: List<ThreadEntity>,
    characters: Map<String, com.aster.companion.data.CharacterEntity>,
    participants: Map<String, List<String>>,
    previews: Map<String, com.aster.companion.ui.ThreadPreview>,
    worldInvitations: List<com.aster.companion.data.WorldTimelineEventEntity>,
    onRespondToWorldInvitation: (String, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier
) {
    val colors = AsterTheme.colors
    var filter by rememberSaveable { mutableStateOf(ConversationFilter.All) }
    val visibleThreads = threads.sortedByDescending { it.updatedAt }.filter { thread ->
        // Lite 只有私聊；历史遗留的群会话不再出现在列表里。
        if (com.aster.companion.lite.LiteProductConfig.ENABLED) {
            thread.type == ConversationType.PRIVATE
        } else when (filter) {
            ConversationFilter.All -> true
            ConversationFilter.Private -> thread.type == ConversationType.PRIVATE
            ConversationFilter.Group -> thread.type == ConversationType.GROUP
        }
    }
    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                // 标题与加号放在同一行，避免「标题偏上、按钮偏下」的错觉。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("同频", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(AsterMetrics.microRadius))
                            .background(colors.primary)
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "添加角色或创建群聊",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text("今天想和谁说话？", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 2.dp))
            }
        }
        // Lite 列表不显示世界邀请（计划书 §7.3）。
        if (!com.aster.companion.lite.LiteProductConfig.ENABLED && worldInvitations.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius))
                    .background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius))
                    .padding(16.dp)
            ) {
                Text("世界活动邀请", color = colors.primary, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                worldInvitations.forEach { invitation ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(invitation.description, color = colors.text, fontSize = AsterType.body)
                        Text(
                            "${invitation.locationId} · ${invitation.durationMinutes} 分钟",
                            color = colors.textMuted,
                            fontSize = AsterType.caption,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                            Button(onClick = { onRespondToWorldInvitation(invitation.id, true) }, shape = RoundedCornerShape(AsterMetrics.controlRadius)) {
                                Text("参加")
                            }
                            TextButton(onClick = { onRespondToWorldInvitation(invitation.id, false) }) {
                                Text("不参加", color = colors.textSubtle)
                            }
                        }
                    }
                }
            }
        }
        // Lite 列表没有筛选条，也没有群聊（计划书 §7.3）。
        if (!com.aster.companion.lite.LiteProductConfig.ENABLED) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).background(colors.surface2, RoundedCornerShape(AsterMetrics.cardRadius)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ConversationFilter.entries.forEach { item ->
                    val active = filter == item
                    Text(
                        item.label,
                        color = if (active) colors.primary else colors.textMuted,
                        fontSize = AsterType.body,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(AsterMetrics.controlRadius)).background(if (active) colors.accentWash else Color.Transparent).clickable { filter = item }.padding(vertical = 8.dp)
                    )
                }
            }
        }
        if (visibleThreads.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("还没有对话", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("从右上角添加角色或创建群聊", color = colors.textMuted, fontSize = AsterType.body, modifier = Modifier.padding(top = 7.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 18.dp)) {
                items(visibleThreads, key = { it.id }) { thread ->
                    val memberNames = participants[thread.id].orEmpty().mapNotNull(characters::get).map { it.name }
                    val avatarUri = participants[thread.id].orEmpty().firstOrNull()?.let(characters::get)?.avatarUri
                    val displayName = if (thread.type == ConversationType.PRIVATE) memberNames.firstOrNull() ?: thread.title else thread.title
                    ThreadListItem(
                        thread = thread,
                        displayName = displayName,
                        subtitle = previews[thread.id]?.lastPreview ?: if (thread.type == ConversationType.GROUP) {
                            memberNames.joinToString("、").ifBlank { "群聊" }
                        } else {
                            ""
                        },
                        avatarUri = avatarUri,
                        unreadCount = previews[thread.id]?.unreadCount ?: 0,
                        onClick = { onSelect(thread.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadListItem(thread: ThreadEntity, displayName: String, subtitle: String, avatarUri: String?, unreadCount: Int, onClick: () -> Unit) {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .asterCard(colors, radius = 24.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(if (thread.type == ConversationType.GROUP) RoundedCornerShape(AsterMetrics.cardRadius) else CircleShape)
                .background(if (thread.type == ConversationType.GROUP) colors.surface3 else colors.accentWash)
                .border(1.dp, if (thread.type == ConversationType.GROUP) colors.borderBright else colors.accentLine, if (thread.type == ConversationType.GROUP) RoundedCornerShape(AsterMetrics.cardRadius) else CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (thread.type == ConversationType.PRIVATE && !avatarUri.isNullOrBlank()) {
                AsyncImage(model = avatarUri, contentDescription = "$displayName 的头像", modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else Text(
                text = if (thread.type == ConversationType.GROUP) "群" else displayName.take(1),
                color = if (thread.type == ConversationType.GROUP) colors.textSubtle else colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(displayName, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = colors.textMuted, fontSize = AsterType.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(shortTime(thread.updatedAt), color = colors.textMuted, fontSize = AsterType.caption)
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 20.dp)
                        .height(20.dp)
                        .background(colors.primary, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (unreadCount > 99) "99+" else unreadCount.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = AsterType.micro, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun shortTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChatDetailPage(
    thread: ThreadEntity,
    characters: Map<String, com.aster.companion.data.CharacterEntity>,
    messages: List<MessageEntity>,
    onBack: () -> Unit,
    onManage: () -> Unit,
    onPeek: () -> Unit,
    onPeekFor: (String) -> Unit,
    onSyncPrivate: (String) -> Unit,
    onRefreshWorld: () -> Unit,
    onToggleStealth: () -> Unit,
    stealthOn: Boolean,
    familyMode: Boolean,
    livingStates: List<com.aster.companion.data.CharacterLivingStateEntity>,
    /** (消息 id, 段序号)：段序号非空表示只删「拆段显示」拆出来的那一段，不删整条回复。 */
    onDeleteMessage: (String, Int?) -> Unit,
    onDeleteRun: (String) -> Unit,
    onRememberMessage: (String) -> Unit,
    onSend: (String) -> Unit,
    onSendImage: (String) -> Unit,
    participants: Map<String, List<String>>,
    isReplying: Boolean,
    activeReplyCharacterName: String?,
    photoGenerationActions: List<com.aster.companion.data.PendingActionEntity>,
    pendingDeviceActions: List<com.aster.companion.data.PendingActionEntity>,
    scenePhoto: com.aster.companion.lite.ScenePhotoRequest?,
    onRequestScenePhoto: (intent: String, characterId: String?) -> Unit,
    onUpdateScenePhotoPrompt: (String) -> Unit,
    onConfirmScenePhoto: () -> Unit,
    onRetryScenePhoto: () -> Unit,
    onCancelScenePhoto: () -> Unit,
    displayPreferences: com.aster.companion.ui.ChatDisplayPreferences,
    onApprovePhoto: (String) -> Unit,
    onRejectPhoto: (String) -> Unit,
    modifier: Modifier
) {
    var showPeekConfirm by rememberSaveable(thread.id) { mutableStateOf(false) }
    var showSyncPicker by rememberSaveable(thread.id) { mutableStateOf(false) }
    var deletingMessageId by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(thread.id) { mutableStateOf("") }
    var cameraImageUri by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var previewImageUri by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var sendingImageUri by remember(thread.id) { mutableStateOf<String?>(null) }
    var messageMenuTarget by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var deletingRunTarget by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val enqueueImage: (String) -> Unit = { uri ->
        sendingImageUri = uri
        onSendImage(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            enqueueImage(it.toString())
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraImageUri?.let(enqueueImage)
    }
    val messageListState = remember(thread.id) { LazyListState() }
    var initiallyPositioned by remember(thread.id) { mutableStateOf(false) }
    var previousMessageCount by remember(thread.id) { mutableStateOf(0) }
    val colors = AsterTheme.colors
    val displayMessages = remember(messages, displayPreferences.splitMultilineResponses) {
        com.aster.companion.ui.ChatMessagePresentation.displayMessages(messages, displayPreferences.splitMultilineResponses)
    }
    // 多气泡投递：**刚到的最后一条**分几段逐个冒出来，中间按字数停一下（像在打字）。
    // 只影响下面渲染用的那份列表——`displayMessages` 本身仍供滚动定位、入场动画与
    // 分段删除使用（那些必须看到全部段落，否则段序号会算错）。
    val lastDisplayedMessage = messages.lastOrNull()
    val lastMessageSegments = remember(displayMessages, lastDisplayedMessage?.id) {
        displayMessages.filter { it.message.id == lastDisplayedMessage?.id }
    }
    val staggerBubbles = com.aster.companion.ui.BubbleRevealPolicy.shouldStagger(
        createdAt = lastDisplayedMessage?.createdAt ?: 0L,
        now = System.currentTimeMillis(),
        streaming = lastDisplayedMessage?.id?.startsWith("stream:") == true,
        segmentCount = lastMessageSegments.size,
        // 流式开着时正文本来就是逐步长出来的（用户已经看全），不该再拆段重放。
        progressiveOutput = displayPreferences.streamingOutput
    )
    var revealedSegments by remember(lastDisplayedMessage?.id, lastMessageSegments.size, staggerBubbles) {
        mutableStateOf(if (staggerBubbles) 1 else Int.MAX_VALUE)
    }
    LaunchedEffect(lastDisplayedMessage?.id, lastMessageSegments.size, staggerBubbles) {
        if (!staggerBubbles) {
            revealedSegments = Int.MAX_VALUE
            return@LaunchedEffect
        }
        revealedSegments = 1
        val schedule = com.aster.companion.ui.BubbleRevealPolicy.revealSchedule(lastMessageSegments.map { it.message.body })
        for (index in 1 until lastMessageSegments.size) {
            kotlinx.coroutines.delay(schedule.getOrElse(index) { 0L })
            revealedSegments = index + 1
        }
    }
    val visibleDisplayMessages = if (staggerBubbles) {
        displayMessages.filterNot { it.message.id == lastDisplayedMessage?.id } + lastMessageSegments.take(revealedSegments)
    } else {
        displayMessages
    }
    val imeVisible = WindowInsets.isImeVisible
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val memberNames = participants[thread.id].orEmpty().mapNotNull(characters::get).map { it.name }
    val characterNames = remember(characters) { characters.mapValues { (_, character) -> character.name } }
    val title = if (thread.type == ConversationType.PRIVATE) memberNames.firstOrNull() ?: thread.title else thread.title
    val replyIndicator = com.aster.companion.ui.ReplyIndicatorPresentation.state(
        isReplying = isReplying,
        hasVisibleStream = messages.any { it.id.startsWith("stream:") && it.body.isNotBlank() }
    )
    val memberLocationMap: Map<String, String> = if (familyMode) {
        participants[thread.id].orEmpty().mapNotNull { cid ->
            val activity = livingStates.firstOrNull { it.characterId == cid }?.currentActivity.orEmpty()
            val location = extractLocationUi(activity)
            if (location != null) {
                val sub = extractSubLocationUi(activity)
                cid to (location + if (sub != null) " · $sub" else "")
            } else null
        }.toMap()
    } else emptyMap()
        val replySpeaker = com.aster.companion.ui.ReplyIndicatorPresentation.speakerLabel(
        isGroup = thread.type == ConversationType.GROUP,
        activeCharacterName = activeReplyCharacterName,
        privateTitle = title
    ) ?: title
    val submitMessage = {
        val body = draft.trim()
        if (body.isNotEmpty()) {
            onSend(body)
            draft = ""
        }
    }
    LaunchedEffect(sendingImageUri, messages) {
        if (!PendingImagePreviewPolicy.shouldShow(sendingImageUri, messages.mapNotNull { it.imageUri }.toSet())) {
            sendingImageUri = null
        }
    }
    // ⚠️ 这个 effect 必须盯**可见**列表（`visibleDisplayMessages`），不能盯 `displayMessages`：
    // 逐段冒泡只改变渲染用的那份列表的长度，全量那份在整个冒泡过程中是不变的——
    // 盯错了的话每冒出一段都不会跟着滚，用户得一段一段手动往下滑
    // （这正是 P3.3 第一版引入的回归）。没在冒泡时两者完全相同，所以旧行为不受影响。
    LaunchedEffect(thread.id, visibleDisplayMessages.size, sendingImageUri, photoGenerationActions.size, pendingDeviceActions.size, isReplying, imeVisible, imeBottom) {
        val lastMessageIndex = ChatDetailScrollTarget.lastItemIndex(
            messageCount = visibleDisplayMessages.size,
            hasPendingImage = sendingImageUri != null,
            pendingPhotoCount = photoGenerationActions.size,
            pendingDeviceCount = pendingDeviceActions.size,
            isReplying = isReplying
        ) ?: return@LaunchedEffect
        if (!initiallyPositioned) {
            withFrameNanos { }
            messageListState.scrollToItem(lastMessageIndex)
            initiallyPositioned = true
        } else if (imeVisible) {
            // OEM IME animations update the inset over several frames. Scroll once now and once after it settles.
            withFrameNanos { }
            withFrameNanos { }
            messageListState.scrollToItem(lastMessageIndex)
            delay(220)
            messageListState.scrollToItem(lastMessageIndex)
        } else if (visibleDisplayMessages.size > previousMessageCount || sendingImageUri != null || isReplying) {
            val lastVisibleIndex = messageListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearBottom = lastVisibleIndex >= messageListState.layoutInfo.totalItemsCount - 3
            if (isNearBottom || sendingImageUri != null) messageListState.animateScrollToItem(lastMessageIndex)
        }
        previousMessageCount = visibleDisplayMessages.size
    }
    // 当前会话正在进行的发照片请求（只认当前会话）。
    val chatScenePhoto = scenePhoto?.takeIf { it.threadId == thread.id }
    // 玻璃的两端必须在同一个 Compose 窗口里：聊天内容当 hazeSource，
    // 弹层当 hazeEffect。用 Dialog / ModalBottomSheet 会各开一个窗口，采不到背景。
    val hazeState = remember { HazeState() }
    // 她此刻的生活状态。私聊才显示——群聊里"她"指谁不明确。
    val herLivingState = if (thread.type == ConversationType.PRIVATE) {
        participants[thread.id]?.firstOrNull()?.let { id -> livingStates.firstOrNull { it.characterId == id } }
    } else {
        null
    }
    val herCharacterId = if (thread.type == ConversationType.PRIVATE) {
        participants[thread.id]?.firstOrNull().orEmpty()
    } else {
        ""
    }
    Box(modifier.fillMaxSize().background(colors.bg)) {
    // **入场动画只给"这一屏停留期间新到的消息"**。消息列表是 LazyColumn：滚出屏幕的条目会被回收，
    // 滚回来时 `remember(message.id)` 会重新建一个从 0 开始的 Animatable —— 于是往上翻旧消息时
    // 每一条都会重新淡入 + 上浮一次，看起来像整屏在闪。
    // 打开会话时已经在列表里的消息直接记成"已出现"，只有之后新到的才动画；换会话时这份记忆跟着清空。
    // 入场动画按**已经显示出来的**气泡算：逐段冒泡时，后面几段还没露面，
    // 所以它们冒出来时才会走一次入场动画（用全量列表初始化的话它们一开始就算"已播过"，会硬生生蹦出来）。
    // ⚠️ 种子必须来自**第一批真正到达的数据**，不能是"首次组合时的列表"：
    // 进入会话的那一瞬间 `displayMessages` 往往还是空的（DAO 还没吐数据），种子就成了空集，
    // 数据一到整屏消息逐条播入场动画 —— 用户看到的就是「每次进对话都要重新出现一个一个的对话」。
    // 用 `displayMessages.isEmpty()` 当 key：空→非空时种子一次（进会话全部记成已见、不播），
    // 之后列表稳定为非空，key 不再变化，新到的那条才会播。
    val entranceAnimated = remember(thread.id) { mutableSetOf<String>() }
    remember(thread.id, displayMessages.isEmpty()) {
        if (displayMessages.isNotEmpty()) entranceAnimated.addAll(displayMessages.map { it.id })
        displayMessages.isEmpty()
    }
    Column(Modifier.fillMaxSize().hazeSource(hazeState).statusBarsPadding()) {
        LazyColumn(
            state = messageListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // 顶部让出顶栏的高度：列表从状态栏下方开始滚，滚到顶栏底下时被毛玻璃糊掉。
            // 顶栏本身是 Box 里的浮层（见 chatScenePhoto 之前那段），不再占 Column 的位置。
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 14.dp,
                // 顶栏 62 + 间距 14，再加上「她此刻的状态」胶囊（只有私聊才有）所占的高度 ——
                // 少算这一截，胶囊就会压在第一条消息上。
                top = 62.dp + 14.dp + if (thread.type == ConversationType.PRIVATE) 44.dp else 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (displayMessages.isNotEmpty()) item { Text("今天", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            items(visibleDisplayMessages, key = { it.id }) { displayMessage ->
                val location = memberLocationMap[displayMessage.message.characterId]
                val distanceLabel = location?.let { loc -> com.aster.companion.domain.CharacterDistancePolicy.distanceLabel(loc.split(" · ").first()) }
                MessageBubble(
                    displayMessage.message,
                    characters[displayMessage.message.characterId],
                    displayPreferences.dimStageDirections,
                    { previewImageUri = it },
                    { messageMenuTarget = displayMessage.id },
                    distanceContext = if (familyMode && distanceLabel != null) "$location · $distanceLabel" else null,
                    // 第一次出现返回 true（要动画）；回收后再回来时 add 返回 false（不再动画）。
                    animateEntrance = remember(displayMessage.id) { entranceAnimated.add(displayMessage.id) }
                )
            }
            sendingImageUri?.let { uri -> item(key = "pending-image-$uri") { PendingImageBubble(uri) } }
            items(photoGenerationActions, key = { it.id }) { action ->
                PhotoConsentCard(action, characterNames, { onApprovePhoto(action.id) }, { onRejectPhoto(action.id) })
            }
            items(pendingDeviceActions, key = { it.id }) { action ->
                DeviceActionConsentCard(action, { onApprovePhoto(action.id) }, { onRejectPhoto(action.id) })
            }
            replyIndicator?.let { indicator -> item {
                Row(
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(Modifier.size(7.dp).background(colors.primary, CircleShape))
                    Text(
                        com.aster.companion.ui.ReplyIndicatorPresentation.label(
                            title,
                            replySpeaker,
                            indicator == com.aster.companion.ui.ReplyIndicatorState.THINKING
                        ),
                        color = colors.textMuted,
                        fontSize = AsterType.label
                    )
                }
            } }
            // 生图确认卡留在对话流里——她要发的图是这条对话的一部分，不该单独弹出来。
            // 注意：卡片在 hazeSource 内部，所以不能传 hazeState（会自引用），
            // 这里用的是「半透明 + 描边」的退化玻璃。
            chatScenePhoto?.let { request ->
                item(key = "scene-photo-card") {
                    ScenePhotoCard(
                        request = request,
                        characterName = characters[request.characterId]?.name ?: "她",
                        onPromptChange = onUpdateScenePhotoPrompt,
                        onConfirm = onConfirmScenePhoto,
                        onRetry = onRetryScenePhoto,
                        onCancel = onCancelScenePhoto
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface1)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 工具收进「+」里：默认只占一个按钮，让输入框拿回宽度。
            var showTools by rememberSaveable(thread.id) { mutableStateOf(false) }
            if (showTools) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (com.aster.companion.lite.LiteProductConfig.ENABLED && thread.type == ConversationType.PRIVATE) {
                    val characterHint = participants[thread.id]?.firstOrNull()
                        ?: displayMessages.firstOrNull { !it.message.fromUser }?.message?.characterId
                    ChatToolButton(Icons.Outlined.AutoAwesome, "发照片", emphasized = true) {
                        showTools = false
                        onRequestScenePhoto(draft.trim(), characterHint)
                    }
                }
                ChatToolButton(Icons.Outlined.PhotoLibrary, "相册") {
                    showTools = false
                    pickImage.launch(arrayOf("image/*"))
                }
                ChatToolButton(Icons.Outlined.CameraAlt, "相机") {
                    showTools = false
                    createCameraCaptureUri(context)?.let { uri ->
                        cameraImageUri = uri.toString()
                        takePhoto.launch(uri)
                    }
                }
                if (familyMode) {
                    ChatToolButton(
                        if (stealthOn) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                        if (stealthOn) "静音" else "有声"
                    ) { onToggleStealth() }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (showTools) colors.accentWash else colors.surface3)
                        .clickable { showTools = !showTools },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (showTools) Icons.Outlined.Close else Icons.Outlined.Add,
                        contentDescription = if (showTools) "收起工具" else "更多工具",
                        tint = if (showTools) colors.primary else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).keepVisibleWhenKeyboardOpened(),
                    placeholder = { Text("说点什么…", color = colors.textMuted, fontSize = AsterType.bodyLarge) },
                    maxLines = 4,
                    shape = RoundedCornerShape(AsterMetrics.cardRadius),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary.copy(alpha = 0.45f),
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surface2,
                        unfocusedContainerColor = colors.surface2,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = AsterType.bodyLarge, lineHeight = 20.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitMessage() })
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (draft.isNotBlank()) colors.primary else colors.surface3)
                        .clickable(enabled = draft.isNotBlank()) { submitMessage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    // 顶栏：内容是要「滚到它底下」的，所以它必须浮在列表之上、而不是占 Column 的位置。
    // 这是 DESIGN.md §3.1 要的那个常驻玻璃，也是「美化到底有没有生效」最直观的一处。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(62.dp)
            .glassSurface(GlassTier.Regular, hazeState, androidx.compose.ui.graphics.RectangleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回聊天列表", tint = colors.text) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (thread.type == ConversationType.GROUP) "${memberNames.size} 位成员" else "私聊", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 1.dp))
        }
        if (!com.aster.companion.lite.LiteProductConfig.ENABLED) {
            IconButton(onClick = { showPeekConfirm = true }) { Icon(Icons.Outlined.Visibility, contentDescription = "偷看角色此刻的样子", tint = colors.text) }
            IconButton(onClick = { showSyncPicker = true }) { Icon(Icons.Outlined.Sync, contentDescription = "同步上下文", tint = colors.text) }
        }
        IconButton(onClick = onManage) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "管理会话", tint = colors.text) }
    }
    // 她此刻在干什么：贴在顶栏下方，随内容滚走 —— 看得到上下文，但不挡聊天。
    CharacterStatusCapsule(
        characterId = herCharacterId,
        livingState = herLivingState,
        hazeState = hazeState,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 62.dp + 8.dp)
    )
    }
    messageMenuTarget?.let { targetDisplayId ->
        // 长按的可能只是「拆段显示」拆出来的一个气泡：菜单里的复制/让她记住/删除都只针对这一段。
        // 兜底：rememberSaveable 里可能存着拆段前的**原始消息 id**（那时它等于显示 id），解析不到就按整条找。
        val targetDisplay = displayMessages.firstOrNull { it.id == targetDisplayId }
            ?: messages.firstOrNull { it.id == targetDisplayId }?.let { com.aster.companion.ui.DisplayMessage(it.id, it) }
        val targetBody = targetDisplay?.message?.body?.takeIf { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { messageMenuTarget = null },
            title = { Text("这条消息", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(targetBody ?: "（图片消息）", color = colors.textMuted, fontSize = AsterType.body)
                    Spacer(Modifier.height(4.dp))
                    // 微信习惯：长按第一条就是复制。
                    if (targetBody != null) {
                        Text(
                            "复制",
                            color = colors.text,
                            fontSize = AsterType.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AsterMetrics.microRadius))
                                .clickable {
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("消息", targetBody))
                                    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                                    messageMenuTarget = null
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                    Text(
                        "让她记住",
                        color = colors.primary,
                        fontSize = AsterType.title,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AsterMetrics.microRadius))
                            .clickable {
                                targetBody?.let(onRememberMessage)
                                messageMenuTarget = null
                            }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        "删除这一整轮（你发的那句 + 她这一串回复）",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = AsterType.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AsterMetrics.microRadius))
                            .clickable {
                                deletingRunTarget = targetDisplayId
                                messageMenuTarget = null
                            }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        if (targetDisplay?.message?.imageUri != null) "删除这张照片" else "删除",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = AsterType.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AsterMetrics.microRadius))
                            .clickable {
                                deletingMessageId = targetDisplayId
                                messageMenuTarget = null
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { messageMenuTarget = null }) { Text("取消", color = colors.textMuted) }
            }
        )
    }
    deletingRunTarget?.let { displayId ->
        AlertDialog(
            onDismissRequest = { deletingRunTarget = null },
            title = { Text("删除这一整轮？", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "会删掉：你说的那一句 + 她接着回的那一串（含拆成多段的气泡）。删除后无法恢复。",
                    color = colors.textMuted,
                    fontSize = AsterType.body
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // **必须先把显示 id 还原成真正的消息 id**：拆段显示时长按拿到的是
                    // `<消息id>:<段号>`，直接拿去库里找是找不到的 —— 那样点删除就会静默什么都不发生。
                    val rawId = com.aster.companion.ui.ChatMessagePresentation.refOf(displayMessages, displayId)?.messageId
                        ?: displayId.substringBefore(':')
                    onDeleteRun(rawId)
                    deletingRunTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { deletingRunTarget = null }) { Text("取消", color = colors.textMuted) } }
        )
    }
    deletingMessageId?.let { displayId ->
        // 拆段气泡：只删这一段，这条回复的其它段落必须留着 —— 所以确认框要说清楚删的是什么。
        // 兜底同上：解析不到就退回按原始消息 id 删整条，绝不能让「删除」按下去没反应。
        val deleteRef = com.aster.companion.ui.ChatMessagePresentation.refOf(displayMessages, displayId)
            ?: messages.firstOrNull { it.id == displayId }?.let { com.aster.companion.ui.DisplayMessageRef(it.id, null, 1) }
        val segmentIndex = deleteRef?.segmentIndex
        val remainingSegments = if (deleteRef != null && segmentIndex != null) deleteRef.segmentCount - 1 else 0
        AlertDialog(
            onDismissRequest = { deletingMessageId = null },
            title = { Text(if (segmentIndex != null) "删除这一段？" else "删除这条消息？", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    when {
                        segmentIndex == null -> "删除后无法恢复。"
                        remainingSegments > 0 -> "只删掉这一段，这条回复剩下的 $remainingSegments 段会保留。"
                        else -> "这是这条回复的最后一段，删掉后整条消息都会消失。"
                    },
                    color = colors.textMuted,
                    fontSize = AsterType.body
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteRef?.let { ref -> onDeleteMessage(ref.messageId, ref.segmentIndex) }
                    deletingMessageId = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { deletingMessageId = null }) { Text("取消", color = colors.textMuted) } }
        )
    }
    if (showSyncPicker) {
        if (thread.type == ConversationType.GROUP) {
            AlertDialog(
                onDismissRequest = { showSyncPicker = false },
                title = { Text("同步 / 更新", color = colors.text, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // 世界引擎已下线：「更新角色状态」是世界推演入口，已移除（胶囊由对话/自主生活维护）。
                        val members = participants[thread.id].orEmpty().mapNotNull { id -> characters[id] }
                        Text("把某位成员的 30 条私聊同步到群里", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp))
                        members.forEach { member ->
                            Text("  · ${member.name}", color = colors.text, fontSize = AsterType.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { onSyncPrivate(member.id); showSyncPicker = false }.padding(vertical = 7.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSyncPicker = false }) { Text("取消", color = colors.textMuted) } }
            )
        } else {
            val privateMember = participants[thread.id].orEmpty().firstOrNull()?.let { id -> characters[id] }
            AlertDialog(
                onDismissRequest = { showSyncPicker = false },
                title = { Text("同步 / 更新", color = colors.text, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // 世界引擎已下线：「更新角色状态」已移除。
                        Text("结合最近 30 条上下文", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { privateMember?.let { onSyncPrivate(it.id) }; showSyncPicker = false }.padding(vertical = 9.dp))
                        Text("  ↳ 把最近对话总结同步到 ${privateMember?.name ?: "角色"} 的状态", color = colors.textMuted, fontSize = AsterType.caption)
                    }
                },
                confirmButton = { TextButton(onClick = { showSyncPicker = false }) { Text("取消", color = colors.textMuted) } }
            )
        }
    }
    if (showPeekConfirm) {
        val peekMembers = participants[thread.id].orEmpty().mapNotNull { id -> characters[id] }
        if (thread.type == ConversationType.GROUP) {
            AlertDialog(
                onDismissRequest = { showPeekConfirm = false },
                title = { Text("偷看谁？", color = colors.text, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("点角色名偷看她一个人，或偷看全家人合照。照片会直接出现在对话里，她们不会知道。", color = colors.textMuted, fontSize = AsterType.label)
                        peekMembers.forEach { member ->
                            val state = livingStates.firstOrNull { it.characterId == member.id }
                            val activity = state?.currentActivity?.takeIf { it.isNotBlank() } ?: "安静待着"
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AsterMetrics.microRadius)).background(colors.surface2).clickable { showPeekConfirm = false; onPeekFor(member.id) }.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text(member.name, color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(activity, color = colors.textMuted, fontSize = AsterType.caption)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPeekConfirm = false; onPeek() }) { Text("全家人合照", color = colors.primary, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { showPeekConfirm = false }) { Text("取消", color = colors.textMuted) } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showPeekConfirm = false },
                title = { Text("偷看" + peekMembers.firstOrNull()?.name.orEmpty(), color = colors.text, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                        peekMembers.forEach { member ->
                            val state = livingStates.firstOrNull { it.characterId == member.id }
                            val activity = state?.currentActivity?.takeIf { it.isNotBlank() } ?: "安静待着"
                            Text(activity, color = colors.text, fontSize = AsterType.bodyLarge)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showPeekConfirm = false; onPeek() }) { Text("偷看", color = colors.primary, fontWeight = FontWeight.SemiBold) } },
                dismissButton = { TextButton(onClick = { showPeekConfirm = false }) { Text("取消", color = colors.textMuted) } }
            )
        }
    }
    previewImageUri?.let { imageUri -> ImagePreviewDialog(imageUri) { previewImageUri = null } }
}

/**
 * 她此刻的状态：贴在聊天顶栏下方的一层薄玻璃。
 *
 * 这是全 App 唯一一处把「她现在在干什么」露出来的地方，数据来自 LifeTicker 维护的
 * `livingState`。点一下展开看心情 / 已经这样多久 / 睡到几点 —— 不展开时不挡聊天。
 *
 * 小圆点统一用主色：不用颜色区分状态，免得视觉变吵。
 */
/**
 * 自定义出图尺寸。
 *
 * 宽高各自必须是 256–1536 的数字；确定时按 64 对齐，并把**实际会用的值**显示出来
 * （填 1000 会看到 1024）—— latent 是 1/8 下采样，不对齐会被 ComfyUI 静默截断。
 * 非法输入只提示、不回退默认值：静默回退会让人以为填的生效了。
 */
@Composable
private fun CustomImageSizeDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = AsterTheme.colors
    val policy = com.aster.companion.agent.ImageGenerationRequestPolicy
    val parts = current.split("x")
    var width by remember {
        mutableStateOf(parts.getOrNull(0)?.takeIf { it.all(Char::isDigit) } ?: "1024")
    }
    var height by remember {
        mutableStateOf(parts.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: "1024")
    }
    val effective = policy.customSizeOrNull(width, height)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义出图尺寸", color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it.filter(Char::isDigit).take(4) },
                        label = { Text("宽") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).keepVisibleWhenKeyboardOpened()
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it.filter(Char::isDigit).take(4) },
                        label = { Text("高") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).keepVisibleWhenKeyboardOpened()
                    )
                }
                Text(
                    text = effective?.let { "实际使用 $it（自动按 ${policy.SIZE_ALIGN} 对齐）" }
                        ?: "宽高各自填 ${policy.MIN_SIZE}–${policy.MAX_SIZE} 之间的数字",
                    color = colors.textMuted,
                    fontSize = AsterType.label
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { effective?.let(onConfirm) }, enabled = effective != null) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CharacterStatusCapsule(
    characterId: String,
    livingState: com.aster.companion.data.CharacterLivingStateEntity?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colors = AsterTheme.colors
    // 胶囊要用 ViewModel 触发"立即同步"：这里取的是 Activity 作用域的那一个实例，
    // 和聊天屏用的是同一个（不新建、不泄漏）。
    val homeViewModel: com.aster.companion.ui.CompanionHomeViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    var expanded by rememberSaveable(characterId) { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val sleepUntil = livingState?.sleepUntil?.takeIf { it > now }
    val activity = livingState?.currentActivity?.trim().orEmpty()
    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)

    val headline = when {
        livingState == null -> "暂时没有她的记录"
        sleepUntil != null -> "正在睡觉"
        activity.isBlank() -> "安静待着"
        else -> activity
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 和顶栏用同一个水平边距 —— 两边不一致看起来就是歪的。
            .padding(horizontal = 8.dp)
            .glassSurface(GlassTier.Thin, hazeState, RoundedCornerShape(AsterMetrics.cardRadius))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        if (expanded) {
            // 「立即同步」：自动流程要等静默满 5 分钟才带着最近这段对话去问一次模型，
            // 所以她说"我去睡了"之后胶囊可能还是旧状态。展开胶囊就能手动对齐。
            TextButton(
                onClick = { homeViewModel.syncLivingState(characterId) },
                enabled = characterId.isNotBlank(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("立即同步她的状态", color = colors.primary)

            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(7.dp).background(colors.primary, CircleShape))
            Text(
                headline,
                color = colors.text,
                fontSize = AsterType.label,
                // 未展开给两行（一句活动描述一行装不下），展开后完整显示、不再截断。
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(if (expanded) "收起" else "详情", color = colors.textMuted, fontSize = AsterType.caption)
        }
        // iOS 风：展开/收起带一点弹性（以前是硬切）。
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "心情 " + (livingState?.mood?.takeIf { it.isNotBlank() } ?: "平静"),
                    color = colors.textMuted,
                    fontSize = AsterType.caption
                )
                if (sleepUntil != null) {
                    Text(
                        "预计 " + clock.format(java.util.Date(sleepUntil)) + " 醒来",
                        color = colors.textMuted,
                        fontSize = AsterType.caption
                    )
                } else {
                    val minutes = livingState?.activityStartedAt
                        ?.let { ((now - it) / 60_000L).coerceAtLeast(0L) }
                        ?: 0L
                    if (minutes > 0) {
                        Text("已经这样 $minutes 分钟了", color = colors.textMuted, fontSize = AsterType.caption)
                    }
                }
                // 依据：这条状态是从哪来的（她的原话 / 自主生活）。空说明是老数据或自主生活写的。
                livingState?.evidence?.takeIf { it.isNotBlank() }?.let { evidence ->
                    Text("依据：$evidence", color = colors.textMuted, fontSize = AsterType.caption)
                }
            }
        }
    }
}

@Composable
private fun ScenePhotoCardBody(
    request: com.aster.companion.lite.ScenePhotoRequest,
    characterName: String,
    onPromptChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = AsterTheme.colors
    var editable by remember(request.stage, request.attempts, request.promptEdited) { mutableStateOf(request.prompt) }
    Text(
        "$characterName · " + com.aster.companion.lite.ScenePhotoStageCopy.label(request.stage),
        color = colors.text,
        fontSize = AsterType.bodyLarge,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        com.aster.companion.lite.ScenePhotoStageCopy.hint(request.stage, request.error),
        color = colors.textMuted,
        fontSize = AsterType.caption,
        modifier = Modifier.padding(top = 4.dp)
    )
    when (request.stage) {
        com.aster.companion.lite.ScenePhotoStage.PLANNING,
        com.aster.companion.lite.ScenePhotoStage.GENERATING -> Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
            TextButton(onClick = onCancel) { Text("取消", color = colors.textMuted) }
        }

        com.aster.companion.lite.ScenePhotoStage.READY -> Column(Modifier.padding(top = 10.dp)) {
            request.shot?.takeIf { it.isNotBlank() }?.let { shot ->
                Text(
                    "她想拍：$shot",
                    color = colors.text,
                    fontSize = AsterType.label,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            request.reason?.takeIf { it.isNotBlank() }?.let { why ->
                Text(
                    "她想让你看：$why",
                    color = colors.textMuted,
                    fontSize = AsterType.label,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text("将要生成的画面标签（可改）", color = colors.textSubtle, fontSize = AsterType.caption)
            OutlinedTextField(
                value = editable,
                onValueChange = { editable = it },
                modifier = Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = AsterType.label),
                maxLines = 6,
                shape = RoundedCornerShape(AsterMetrics.controlRadius),
                // 提示词是正文：可编辑的内容必须落在纯色上，不能压在半透明材质里。
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface1,
                    unfocusedContainerColor = colors.surface1,
                    disabledContainerColor = colors.surface1,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border
                )
            )
            request.notes.filter { it.isNotBlank() }.forEach { note ->
                Text(
                    "提示：$note",
                    color = colors.textMuted,
                    fontSize = AsterType.caption,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (request.promptEdited) {
                Text(
                    "已按你手改的内容出图（不会再自动补人物锚点）",
                    color = colors.textMuted,
                    fontSize = AsterType.caption,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text("点「确认出图」才会真正开始生成", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                Button(onClick = { onPromptChange(editable); onConfirm() }, shape = RoundedCornerShape(AsterMetrics.controlRadius)) { Text("确认出图") }
                TextButton(onClick = onCancel) { Text("取消", color = colors.textSubtle) }
            }
        }

        com.aster.companion.lite.ScenePhotoStage.FAILED -> Column(Modifier.padding(top = 10.dp)) {
            request.error?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(reason, color = colors.textMuted, fontSize = AsterType.caption)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                Button(onClick = onRetry, shape = RoundedCornerShape(AsterMetrics.controlRadius)) { Text("重试") }
                TextButton(onClick = onCancel) { Text("取消", color = colors.textSubtle) }
            }
        }
    }
}

/**
 * 朋友圈的动态出图卡：留在滚动流里的内容卡片。
 *
 * 朋友圈按 DESIGN.md §3.3 不走弹层，所以这里不传 hazeState，材质退化成「半透明 + 描边」。
 */
@Composable
private fun ScenePhotoCard(
    request: com.aster.companion.lite.ScenePhotoRequest,
    characterName: String,
    onPromptChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glassSurface(GlassTier.Thick)
            .padding(14.dp)
    ) {
        ScenePhotoCardBody(request, characterName, onPromptChange, onConfirm, onRetry, onCancel)
    }
}

@Composable
private fun PhotoConsentCard(
    action: com.aster.companion.data.PendingActionEntity,
    characterNames: Map<String, String>,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val colors = AsterTheme.colors
    val contextualReply = com.aster.companion.ui.ImageGenerationActionPresentation.title(action, characterNames)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, top = 2.dp, end = 8.dp)
            .clip(RoundedCornerShape(AsterMetrics.cardRadius))
            .background(colors.surface2)
            .padding(14.dp)
    ) {
        Text(contextualReply, color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(com.aster.companion.ui.ImageGenerationActionPresentation.detail(action), color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 4.dp))
        if (action.state == "PENDING") Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
            Button(onClick = onApprove, shape = RoundedCornerShape(AsterMetrics.controlRadius)) { Text("同意") }
            TextButton(onClick = onReject) { Text("拒绝", color = colors.textSubtle) }
        }
    }
}

@Composable
private fun DeviceActionConsentCard(
    action: com.aster.companion.data.PendingActionEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val colors = AsterTheme.colors
    val title = com.aster.companion.ui.PendingActionPresentation.title(action, emptyMap())
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 50.dp, top = 2.dp)
            .border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius))
            .background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius)).padding(14.dp)
    ) {
        Text(title, color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            com.aster.companion.ui.PendingActionPresentation.transportLabel(action.transport) + "，执行前需要你点一下确认。",
            color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 4.dp)
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
            Button(onClick = onApprove, shape = RoundedCornerShape(AsterMetrics.controlRadius)) { Text("确认") }
            TextButton(onClick = onReject) { Text("取消", color = colors.textSubtle) }
        }
    }
}

private fun createCameraCaptureUri(context: android.content.Context): android.net.Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "tongpin_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

@Composable
private fun ChatToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AsterMetrics.controlRadius))
            .background(if (emphasized) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (emphasized) colors.primary else colors.textSubtle,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            color = if (emphasized) colors.primary else colors.textMuted,
            fontSize = AsterType.label,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, character: com.aster.companion.data.CharacterEntity?, dimStageDirections: Boolean, onOpenImage: (String) -> Unit, onDeleteMessage: (() -> Unit)? = null, distanceContext: String? = null, animateEntrance: Boolean = true) {
    val colors = AsterTheme.colors
    // iOS 风：消息出现时淡入 + 轻微上移（每条只播一次，不影响滚动与重排）。
    val appear = remember(message.id) { androidx.compose.animation.core.Animatable(if (animateEntrance) 0f else 1f) }
    LaunchedEffect(message.id, animateEntrance) {
        // 不给回收后重现的旧消息重放动画（否则往上翻历史会整屏闪）。
        if (animateEntrance) appear.animateTo(1f, androidx.compose.animation.core.tween(durationMillis = 180))
    }
    val characterName = character?.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 24f
            }
            .let { modifier -> if (onDeleteMessage != null) modifier.combinedClickable(onClick = {}, onLongClick = onDeleteMessage) else modifier },
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.fromUser) {
            Box(
                modifier = Modifier.padding(top = 15.dp).size(32.dp).clip(CircleShape).background(colors.surface2),
                contentAlignment = Alignment.Center
            ) {
                if (!character?.avatarUri.isNullOrBlank()) AsyncImage(model = character?.avatarUri, contentDescription = "${characterName ?: "角色"}的头像", modifier = Modifier.fillMaxSize().clip(CircleShape))
                else Text((characterName ?: "岚").take(1), color = colors.primary, fontSize = AsterType.label, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 292.dp)) {
            if (!message.fromUser) {
                Text(characterName ?: "岚", color = colors.textMuted, fontSize = AsterType.micro, modifier = Modifier.padding(bottom = 3.dp))
                if (distanceContext != null) {
                    Text(distanceContext, color = colors.textSubtle, fontSize = 9.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(AsterMetrics.bubbleRadius))
                    .background(
                        if (message.fromUser) colors.primary
                        else colors.surface2
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (message.body.isNotBlank()) MessageBodyText(message.body, if (message.fromUser) MaterialTheme.colorScheme.onPrimary else colors.text, dimStageDirections)
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "聊天图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(top = if (message.body.isBlank()) 0.dp else 8.dp).fillMaxWidth().height(200.dp).clip(RoundedCornerShape(AsterMetrics.controlRadius)).combinedClickable(onClick = { onOpenImage(uri) }, onLongClick = { if (onDeleteMessage != null) onDeleteMessage() })
                    )
                }
            }
            Text(shortTime(message.createdAt), color = colors.textMuted.copy(alpha = 0.65f), fontSize = AsterType.micro, modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp))
        }
    }
}

@Composable
private fun MessageBodyText(body: String, textColor: Color, dimStageDirections: Boolean) {
    val colors = AsterTheme.colors
    if (!dimStageDirections) {
        Text(body, color = textColor, fontSize = AsterType.bodyLarge, lineHeight = 20.sp)
        return
    }
    val styled: AnnotatedString = buildAnnotatedString {
        com.aster.companion.ui.ChatMessagePresentation.parts(body).forEach { part ->
            if (part.isStageDirection) withStyle(SpanStyle(color = colors.textMuted)) { append(part.value) }
            else append(part.value)
        }
    }
    Text(styled, color = textColor, fontSize = AsterType.bodyLarge, lineHeight = 20.sp)
}

@Composable
private fun PendingImageBubble(imageUri: String) {
    val colors = AsterTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 292.dp)) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.primary).padding(4.dp)
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "正在发送的图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(AsterMetrics.controlRadius))
                )
                Row(
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(AsterMetrics.controlRadius)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 1.5.dp)
                    Text("发送中", color = Color.White, fontSize = AsterType.label)
                }
            }
        }
    }
}

private fun extractLocationUi(activity: String): String? {
    val trimmed = activity.trim().removePrefix("此刻正在").removePrefix("正在").trim()
    if (!trimmed.startsWith("在")) return null
    val after = trimmed.removePrefix("在")
    val commaIdx = after.indexOfFirst { it == '，' || it == ',' || it == '、' }
    if (commaIdx > 0) return after.substring(0, commaIdx).trim().takeIf(String::isNotBlank)
    val actionTerms = listOf("做", "煮", "看", "洗", "浇", "擦", "切", "坐", "躺", "站", "听", "拿", "喂", "拖", "扫", "收", "折", "晾", "叠", "铺", "换", "摆", "倒", "烧", "炖", "炒", "淘", "刷", "冲", "晒", "理", "找", "发", "回", "收拾", "整理", "打扫", "翻", "选", "挑", "泡", "喝", "吃", "揉", "拍", "挂", "套", "盖", "掀", "关", "开")
    for (i in after.indices) {
        if (actionTerms.any { after.startsWith(it, i) }) return after.substring(0, i).trim().takeIf(String::isNotBlank)
    }
    return null
}

private fun extractSubLocationUi(activity: String): String? {
    val trimmed = activity.trim().removePrefix("此刻正在").removePrefix("正在").trim()
    if (!trimmed.startsWith("在")) return null
    val after = trimmed.removePrefix("在")
    val commaIdx = after.indexOfFirst { it == '，' || it == ',' || it == '、' }
    if (commaIdx < 0 || commaIdx + 1 >= after.length) return null
    return after.substring(commaIdx + 1).trim().takeIf { it.isNotBlank() && it.length <= 20 }
}

@Composable
private fun ImagePreviewDialog(imageUri: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var saveStatus by remember(imageUri) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(top = 24.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭图片", tint = Color.White) }
                TextButton(onClick = {
                    scope.launch {
                        saveStatus = "正在保存..."
                        saveImageToGallery(context, imageUri).onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); saveStatus = it }
                            .onFailure { saveStatus = "保存失败"; Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                }) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(saveStatus ?: "保存", color = Color.White, modifier = Modifier.padding(start = 5.dp))
                }
            }
            AsyncImage(model = imageUri, contentDescription = "角色照片大图", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

private suspend fun saveImageToGallery(context: android.content.Context, source: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "tongpin_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/同频")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val destination = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) { "无法创建相册文件" }
        try {
            val input = if (source.startsWith("content://") || source.startsWith("file://")) {
                requireNotNull(resolver.openInputStream(Uri.parse(source))) { "无法读取图片" }
            } else URL(source).openConnection().getInputStream()
            input.use { sourceStream -> requireNotNull(resolver.openOutputStream(destination)) { "无法写入图片" }.use { target -> sourceStream.copyTo(target) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(destination, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (failure: Throwable) {
            resolver.delete(destination, null, null)
            throw failure
        }
        "已保存到相册"
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MemoryPage(
    state: com.aster.companion.ui.CompanionHomeState,
    onDeleteMemory: (String) -> Unit,
    onToggleMemoryBlocked: (String, Boolean) -> Unit,
    /** 长按「留在今天的照片」→ 删除。 */
    onDeleteDiaryPhoto: (String) -> Unit,
    modifier: Modifier
) {
    val colors = AsterTheme.colors
    // 日记现在有自己的类型：以前它和「共同活动 / 互动 / 世界事件」共用 EVENT，
    // 所以这一页混着一堆系统流水，真正的日记只占一小部分。现在只认 DIARY。
    val diaryEntries = state.memories.filter { it.kind == MemoryKind.DIARY }
    val diaryCharacterIds = (diaryEntries.mapNotNull { it.characterId } + state.diaryPhotos.map { it.characterId }).distinct()
    var selectedCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeCharacterId = selectedCharacterId?.takeIf { it in diaryCharacterIds } ?: diaryCharacterIds.firstOrNull()
    val entriesForCharacter = diaryEntries.filter { it.characterId == activeCharacterId }
    val photosForCharacter = state.diaryPhotos.filter { it.characterId == activeCharacterId }
    val dateKeys = (entriesForCharacter.map { diaryDateKey(it.updatedAt) } + photosForCharacter.map { diaryDateKey(it.createdAt) }).distinct()
    var selectedDateKey by rememberSaveable(activeCharacterId) { mutableStateOf<String?>(null) }
    val activeDateKey = selectedDateKey?.takeIf { it in dateKeys } ?: dateKeys.firstOrNull()
    val visibleEntries = entriesForCharacter.filter { diaryDateKey(it.updatedAt) == activeDateKey }
    val selectedTimestamp = visibleEntries.maxByOrNull { it.updatedAt }?.updatedAt
        ?: photosForCharacter.firstOrNull { diaryDateKey(it.createdAt) == activeDateKey }?.createdAt
    val visiblePhotos = selectedTimestamp?.let { timestamp -> photosForCharacter.filter { photo ->
        DiaryPhotoPolicy.belongsToDiary(photo.characterId, activeCharacterId, photo.createdAt, timestamp)
    } }.orEmpty()
    var previewImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    // 长按照片 → 先确认再删（照片删了没法恢复，不做成"一按就没"）。
    var pendingPhotoDeleteUri by rememberSaveable { mutableStateOf<String?>(null) }
    pendingPhotoDeleteUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingPhotoDeleteUri = null },
            title = { Text("删除这张照片？") },
            text = { Text("会从「她留下的照片」里删掉，聊天记录里的那条消息不受影响。", color = colors.textMuted, fontSize = AsterType.body) },
            confirmButton = {
                TextButton(onClick = { onDeleteDiaryPhoto(uri); pendingPhotoDeleteUri = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingPhotoDeleteUri = null }) { Text("取消", color = colors.textMuted) } }
        )
    }
    LazyColumn(modifier.fillMaxSize().background(colors.bg).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Text("日记", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            Text("翻看她在一天结束后写下的话。", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 3.dp, bottom = 17.dp))
            if (state.diaryWritingThreadId != null) {
                Column(Modifier.fillMaxWidth().background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius)).padding(15.dp)) {
                    Text("她正在写今天的日记", color = colors.primary, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("整理好后会留在这里。", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Text("谁写的", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 23.dp, bottom = 8.dp))
        }
        if (diaryCharacterIds.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                    diaryCharacterIds.forEach { characterId ->
                        val character = state.characters.firstOrNull { it.id == characterId }
                        DiaryFilterChip(character?.name ?: "未命名角色", activeCharacterId == characterId) {
                            selectedCharacterId = characterId
                            selectedDateKey = null
                        }
                    }
                }
            }
            item {
                Text("翻到哪一天", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                    dateKeys.forEach { dateKey ->
                        val sampleTime = entriesForCharacter.firstOrNull { diaryDateKey(it.updatedAt) == dateKey }?.updatedAt
                            ?: photosForCharacter.first { diaryDateKey(it.createdAt) == dateKey }.createdAt
                        DiaryFilterChip(diaryDateLabel(sampleTime), activeDateKey == dateKey) { selectedDateKey = dateKey }
                    }
                }
            }
            item {
                val selectedCharacter = state.characters.firstOrNull { it.id == activeCharacterId }
                val selectedEntry = visibleEntries.maxByOrNull { it.updatedAt }
                if (selectedEntry != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).asterCard(colors).padding(horizontal = 22.dp, vertical = 24.dp)
                    ) {
                        Text(diaryDateLabel(selectedEntry.updatedAt), color = colors.primary, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
                        Text("${selectedCharacter?.name ?: "她"} 写给今天的你", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp))
                        Spacer(Modifier.height(22.dp))
                        Text(selectedEntry.content, color = colors.text, fontSize = 17.sp, lineHeight = 30.sp)
                        Text("${selectedCharacter?.name ?: "她"}", color = colors.textMuted, fontSize = AsterType.body, modifier = Modifier.fillMaxWidth().padding(top = 30.dp), textAlign = TextAlign.End)
                    }
                }
                if (visiblePhotos.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        Text("留在今天的照片", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(bottom = 8.dp))
                        visiblePhotos.forEach { photo ->
                            AsyncImage(
                                model = photo.imageUri,
                                contentDescription = "日记照片",
                                contentScale = ContentScale.Crop,
                                // 点开看大图；长按 = 删除（先弹确认）。
                                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(AsterMetrics.cardRadius))
                                    .combinedClickable(
                                        onClick = { previewImageUri = photo.imageUri },
                                        onLongClick = { pendingPhotoDeleteUri = photo.imageUri }
                                    )
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
        if (diaryCharacterIds.isEmpty()) item {
            Text("她还没有写下日记。", color = colors.textMuted, fontSize = AsterType.body, modifier = Modifier.fillMaxWidth().padding(top = 32.dp), textAlign = TextAlign.Center)
        }
        item { Spacer(Modifier.height(18.dp)) }
        // 记忆管理：设置页改版时这块入口丢了（回调一直传着却没人用），
        // 结果用户**没法删掉/屏蔽她记住的事**。这里接回来（不含日记，日记在上面单独看）。
        val manageableMemories = state.memories.filter { it.kind != MemoryKind.DIARY }.take(50)
        if (manageableMemories.isNotEmpty()) {
            item {
                Text("她记住的事", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("删掉就不会再提；屏蔽只是不再主动引用。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
            }
            items(manageableMemories, key = { it.id }) { memory ->
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).asterCard(colors).padding(AsterMetrics.cardPadding)
                ) {
                    Text(memory.content.take(120), color = colors.text, fontSize = AsterType.body, lineHeight = 19.sp)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            memory.kind.name.lowercase(),
                            color = colors.textMuted,
                            fontSize = AsterType.micro,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onToggleMemoryBlocked(memory.id, !memory.blockedFromMention) }) {
                            Text(
                                if (memory.blockedFromMention) "取消屏蔽" else "屏蔽",
                                color = colors.textSubtle,
                                fontSize = AsterType.label
                            )
                        }
                        TextButton(onClick = { onDeleteMemory(memory.id) }) {
                            Text("删除", color = MaterialTheme.colorScheme.error, fontSize = AsterType.label)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
    previewImageUri?.let { imageUri -> ImagePreviewDialog(imageUri) { previewImageUri = null } }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DiaryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AsterTheme.colors
    Text(
        label,
        color = if (selected) colors.primary else colors.textMuted,
        fontSize = AsterType.caption,
        modifier = Modifier
            .clip(RoundedCornerShape(AsterMetrics.controlRadius))
            .background(if (selected) colors.accentWash else Color.Transparent)
            .border(1.dp, if (selected) colors.accentLine else colors.border, RoundedCornerShape(AsterMetrics.controlRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    )
}

/** 相对时间：刚刚 / x 分钟前 / x 小时前 / 昨天 / 前天 / MM-dd。 */
private fun relativeTimeLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60_000L) return "刚刚"
    if (diff < 3_600_000L) return "${diff / 60_000L} 分钟前"
    if (diff < 86_400_000L) return "${diff / 3_600_000L} 小时前"
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowCalendar = java.util.Calendar.getInstance()
    val dayDiff = (nowCalendar.get(java.util.Calendar.DAY_OF_YEAR) - calendar.get(java.util.Calendar.DAY_OF_YEAR) + 365) % 365
    return when {
        dayDiff == 1 -> "昨天"
        dayDiff == 2 -> "前天"
        else -> java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

private fun diaryDateKey(timestamp: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))

private fun diaryDateLabel(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val today = formatter.format(Date())
    val target = formatter.format(Date(timestamp))
    return when (target) {
        today -> "今天"
        formatter.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)) -> "昨天"
        else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun SettingsPage(
    state: com.aster.companion.ui.CompanionHomeState,
    onSaveModel: (String, String, String, String, String) -> Unit,
    onSaveImageApiPool: (String, String, String, String) -> Unit,
    onDeleteImageApiPool: (String) -> Unit,
    onSaveImagePromptDebugConfig: (ImagePromptDebugConfig) -> Unit,
    onResetImagePromptDebugConfig: () -> Unit,
    onSetSplitMultilineResponses: (Boolean) -> Unit,
    onSetDimStageDirections: (Boolean) -> Unit,
    onSetStreamingOutput: (Boolean) -> Unit,
    onSetEmotionalOffset: (Boolean) -> Unit,
    onTestModel: (String) -> Unit,
    onFetchModels: (String, String, String) -> Unit,
    onSetThinking: (Boolean, String) -> Unit,
    onSetModelEndpoint: (String, String?, String?, String?) -> Unit,
    imageRuleBaseUrl: String,
    imageRuleFormat: String,
    onSetImageRule: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onToggleMemoryBlocked: (String, Boolean) -> Unit,
    onApproveAction: (String) -> Unit,
    onRejectAction: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onCancelImage: (String) -> Unit,
    onRegenerateImage: (String) -> Unit,
    onRespondToWorldInvitation: (String, Boolean) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onSetSystemModeEnabled: (Boolean) -> Unit,
    onExportBackup: (android.net.Uri, String) -> Unit,
    onImportBackup: (android.net.Uri, String) -> Unit,
    onExportDeviceTransfer: (android.net.Uri, String) -> Unit,
    onImportDeviceTransfer: (android.net.Uri, String) -> Unit,
    imageSize: String,
    onSetImageSize: (String) -> Unit,
    updateState: com.aster.companion.update.UpdateState,
    onCheckUpdate: (String) -> Unit,
    onInstallUpdate: (String) -> Unit,
    lastScenePrompt: String,
    backgroundIdleMs: Long,
    onSetBackgroundIdleMs: (Long) -> Unit,
    contextTokens: Int,
    onSetContextTokens: (Int) -> Unit,
    contextWindowMessages: Int,
    onSetContextWindowMessages: (Int) -> Unit,
    proactiveMomentsEnabled: Boolean,
    proactivePhotoEnabled: Boolean,
    onSetProactiveMoments: (Boolean) -> Unit,
    onSetProactivePhoto: (Boolean) -> Unit,
    proactiveUrgeEnabled: Boolean,
    proactiveRelaxedContact: Boolean,
    onSetProactiveUrge: (Boolean) -> Unit,
    onSetProactiveRelaxedContact: (Boolean) -> Unit,
    proactiveWorldSeedingEnabled: Boolean,
    onSetProactiveWorldSeeding: (Boolean) -> Unit,
    modifier: Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.Models) }
    var selectedModelKind by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImageGenerationPage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImageTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSystemPage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDataPage by rememberSaveable { mutableStateOf<String?>(null) }
    var promptExportPassword by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var promptTransferExportPassword by rememberSaveable { mutableStateOf(false) }
    var pendingTransferImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    val createBackupDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { selectedUri -> BackupPasswordHolder.consume()?.let { onExportBackup(selectedUri, it) } }
    }
    val openBackupDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImportUri = uri?.toString() }
    val createTransferDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { selectedUri -> TransferPasswordHolder.consume()?.let { onExportDeviceTransfer(selectedUri, it) } }
    }
    val openTransferDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingTransferImportUri = uri?.toString() }
    val colors = AsterTheme.colors
    BackHandler(enabled = selectedImageTaskId != null || selectedModelKind != null || selectedImageGenerationPage != null || selectedSystemPage != null || selectedDataPage != null) {
        when {
            selectedImageTaskId != null -> selectedImageTaskId = null
            selectedTab == SettingsTab.Models && selectedImageGenerationPage != null -> selectedImageGenerationPage = null
            selectedTab == SettingsTab.Models -> selectedModelKind = null
            selectedTab == SettingsTab.System -> selectedSystemPage = null
            selectedTab == SettingsTab.Data -> selectedDataPage = null
        }
    }
    // 自更新/远程提示词都放在网关上。取地址的优先级跟 RemotePrompts.baseUrl() 一致：
    // 生图规则地址（用户单独填过）→ 图像模型地址 → 对话模型地址。
    val updateBaseUrl: () -> String = {
        imageRuleBaseUrl.takeIf { it.isNotBlank() }
            ?: state.modelProfiles.firstOrNull { it.kind == "image_generation" }?.let { com.aster.companion.net.EndpointResolver.cachedBaseUrl(it.id, it.baseUrl) }.orEmpty().takeIf { it.isNotBlank() }
            ?: state.modelProfiles.firstOrNull { it.kind == "conversation" }?.let { com.aster.companion.net.EndpointResolver.cachedBaseUrl(it.id, it.baseUrl) }.orEmpty()
    }
    LazyColumn(modifier.fillMaxSize().background(colors.bg).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Text("设置", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            Text("模型、权限与数据管理", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(bottom = 14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.surface2, RoundedCornerShape(AsterMetrics.cardRadius)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsTab.entries.forEach { tab ->
                    val active = selectedTab == tab
                    Text(
                        tab.label,
                        color = if (active) colors.primary else colors.textMuted,
                        fontSize = AsterType.body,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(AsterMetrics.controlRadius)).background(if (active) colors.accentWash else Color.Transparent).clickable {
                            selectedTab = tab
                            selectedModelKind = null
                            selectedImageGenerationPage = null
                            selectedImageTaskId = null
                            selectedSystemPage = null
                            selectedDataPage = null
                        }.padding(vertical = 8.dp)
                    )
                }
            }
        }
        when (selectedTab) {
            SettingsTab.Models -> {
                if (selectedModelKind == null) {                    item { SettingsSection("模型") }
                    item {
                        var ruleBaseUrlDraft by rememberSaveable { mutableStateOf(imageRuleBaseUrl) }
                        var showRuleFormatChoices by rememberSaveable { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "生图规则：规则来源和出图地址分开配，换任何模型或别家官方 API 也能继续用你调好的这套规则。",
                                color = colors.textMuted,
                                fontSize = AsterType.caption
                            )
                            OutlinedTextField(
                                ruleBaseUrlDraft,
                                {
                                    ruleBaseUrlDraft = it
                                    onSetImageRule(it, imageRuleFormat)
                                },
                                Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(),
                                label = { Text("规则地址（留空＝跟随图像模型地址）") },
                                singleLine = true,
                                shape = RoundedCornerShape(AsterMetrics.controlRadius)
                            )
                            Box {
                                TextButton(onClick = { showRuleFormatChoices = true }) {
                                    Text("生图规则集：${com.aster.companion.lite.ImageRuleSettings.labelFor(imageRuleFormat)}")
                                }
                                DropdownMenu(expanded = showRuleFormatChoices, onDismissRequest = { showRuleFormatChoices = false }) {
                                    com.aster.companion.lite.ImageRuleSettings.FORMATS.forEach { format ->
                                        DropdownMenuItem(
                                            text = { Text(com.aster.companion.lite.ImageRuleSettings.labelFor(format)) },
                                            onClick = {
                                                onSetImageRule(ruleBaseUrlDraft, format)
                                                showRuleFormatChoices = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        var sizeMenu by remember { mutableStateOf(false) }
                        var customSizeOpen by remember { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box {
                                TextButton(onClick = { sizeMenu = true }) {
                                    Text(
                                        "图片尺寸：${com.aster.companion.lite.LiteImageSizeStore.labelFor(imageSize)}",
                                        color = colors.text
                                    )
                                }
                                DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                                    // 用统一的档位表（6 档，含横屏/省显存），并保留「自定义…」——
                                    // 设置页合并时曾把这两样丢掉，只留了 4 档硬编码。
                                    com.aster.companion.lite.LiteImageSizeStore.OPTIONS.forEach { (label, value) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { onSetImageSize(value); sizeMenu = false }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(com.aster.companion.lite.LiteImageSizeStore.CUSTOM_LABEL) },
                                        onClick = { customSizeOpen = true; sizeMenu = false }
                                    )
                                }
                            }
                            Text("竖幅（768×1344）才装得下「全身 + 两个人」的构图；正方形只适合单人半身/特写。", color = colors.textMuted, fontSize = AsterType.caption)
                            Text("她主动发的照片、偷看、朋友圈配图都按这个尺寸出；如果她自己指定了尺寸，以她指定的为准。", color = colors.textMuted, fontSize = AsterType.caption)
                        }
                        if (customSizeOpen) {
                            CustomImageSizeDialog(
                                current = imageSize,
                                onDismiss = { customSizeOpen = false },
                                onConfirm = { size -> onSetImageSize(size); customSizeOpen = false }
                            )
                        }
                    }
                    item { SettingsMenuRow("对话模型", "聊天与主动消息", onClick = { selectedModelKind = "conversation" }) }
                    item { SettingsMenuRow("推理规划", "主动判断与关系复盘，未配置时使用对话模型", onClick = { selectedModelKind = "planning" }) }
                    item { SettingsMenuRow("图片提示词", "把聊天总结成出图提示词；未配置时沿用对话模型", onClick = { selectedModelKind = "image_prompt" }) }
                    item { SettingsMenuRow("语义检索", "长期记忆 Embedding 模型", onClick = { selectedModelKind = "embedding" }) }
                    item { SettingsMenuRow("图像生成", "角色照片与图片", onClick = { selectedModelKind = "image_generation" }) }
                    item { SettingsMenuRow("视觉模型", "理解你发来的图片", onClick = { selectedModelKind = "image_understanding" }, last = true) }
                } else {
                    item { SettingsSubpageHeader("模型", onBack = { selectedModelKind = null }) }
                    when (selectedModelKind) {
                        "conversation" -> item { ModelEditor("对话模型", "conversation", "负责聊天与主动消息", state.modelProfiles.firstOrNull { it.kind == "conversation" }, state.availableModels["conversation"].orEmpty(), state.modelFetchStatus["conversation"], onSaveModel, onTestModel, onFetchModels) }
                        "planning" -> item { ModelEditor("推理规划", "planning", "负责主动陪伴决策、照片提议与动作规划", state.modelProfiles.firstOrNull { it.kind == "planning" }, state.availableModels["planning"].orEmpty(), state.modelFetchStatus["planning"], onSaveModel, onTestModel, onFetchModels) }
                        "image_prompt" -> item { ModelEditor("图片提示词", "image_prompt", "把这段对话总结成最终出图提示词（不配则用对话模型）", state.modelProfiles.firstOrNull { it.kind == "image_prompt" }, state.availableModels["image_prompt"].orEmpty(), state.modelFetchStatus["image_prompt"], onSaveModel, onTestModel, onFetchModels) }
                        "embedding" -> item { ModelEditor("语义检索", "embedding", "为长期记忆提供 Embedding 检索", state.modelProfiles.firstOrNull { it.kind == "embedding" }, state.availableModels["embedding"].orEmpty(), state.modelFetchStatus["embedding"], onSaveModel, onTestModel, onFetchModels) }
                        "image_generation" -> if (selectedImageGenerationPage == null) {
                            item { SettingsMenuRow("图像 API 池", "仅通过 OpenAI 兼容图片 API 生成", onClick = { selectedImageGenerationPage = "providers" }) }
                            item { SettingsMenuRow("生成任务", "查看正在生成与失败的图片", onClick = { selectedImageGenerationPage = "tasks" }, last = true) }
                        } else {
                            item { SettingsSubpageHeader("图像生成", onBack = { selectedImageGenerationPage = null }) }
                            when (selectedImageGenerationPage) {
                                "providers" -> {
                                    val imageProfile = state.modelProfiles.firstOrNull { it.kind == "image_generation" }
                                    item { ModelEditor("图像生成", "image_generation", "主配置作为 API 池的后备，图片请求原样发送给 API", imageProfile, state.availableModels["image_generation"].orEmpty(), state.modelFetchStatus["image_generation"], onSaveModel, onTestModel, onFetchModels) }
                                    // 协议选 ComfyUI 时才出现：一键在同一个 Wi-Fi 里找 ComfyUI 并保存地址，
                                    // 省得手动填局域网 IP 和端口。以前这个面板写好了但没人调用（死代码）。
                                    if (imageProfile?.imageProtocol == com.aster.companion.agent.ImageProviderProtocol.COMFY_UI.name) {
                                        item {
                                            ComfyUiEditor(
                                                profile = imageProfile,
                                                onSave = { kind, base, model, key ->
                                                    onSaveModel(kind, base, model, key, imageProfile.imageProtocol)
                                                },
                                                onTest = onTestModel
                                            )
                                        }
                                    }
                                    item { ImageApiPoolEditor(state.imageApiPool, onSaveImageApiPool, onDeleteImageApiPool) }
                                    item { ImagePromptDebugEditor(state.imagePromptDebugConfig, onSaveImagePromptDebugConfig, onResetImagePromptDebugConfig) }
                                }
                                "tasks" -> item {
                                    if (selectedImageTaskId == null) {
                                        ImageGenerationTaskViewer(
                                            actions = state.imageGenerationActions,
                                            characterNames = state.characters.associate { it.id to it.name },
                                            onRetry = onRetryImage,
                                            onCancel = onCancelImage,
                                            onRegenerate = onRegenerateImage,
                                            onOpen = { selectedImageTaskId = it }
                                        )
                                    } else {
                                        ImageGenerationTaskDetail(
                                            action = state.imageGenerationActions.firstOrNull { it.id == selectedImageTaskId },
                                            characterNames = state.characters.associate { it.id to it.name },
                                            onBack = { selectedImageTaskId = null },
                                            onRetry = onRetryImage,
                                            onCancel = onCancelImage,
                                            onRegenerate = onRegenerateImage
                                        )
                                    }
                                }
                            }
                        }
                        "image_understanding" -> item { ModelEditor("视觉模型", "image_understanding", "理解图片与屏幕信息，不用于生成照片", state.modelProfiles.firstOrNull { it.kind == "image_understanding" }, state.availableModels["image_understanding"].orEmpty(), state.modelFetchStatus["image_understanding"], onSaveModel, onTestModel, onFetchModels) }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
            SettingsTab.Chat -> {
                item { SettingsSection("思考") }
                item {
                    val conversationProfile = state.modelProfiles.firstOrNull { it.kind == "conversation" }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("思考模式（先想再回）", color = colors.text, fontSize = AsterType.bodyLarge)
                            Switch(
                                checked = conversationProfile?.thinkingEnabled == true,
                                onCheckedChange = { enabled ->
                                    onSetThinking(
                                        enabled,
                                        conversationProfile?.reasoningEffort
                                            ?: com.aster.companion.domain.ConversationReasoning.DEFAULT_EFFORT
                                    )
                                }
                            )
                            if (conversationProfile?.thinkingEnabled == true) {
                                Text("强度", color = colors.textSubtle, fontSize = AsterType.label)
                                com.aster.companion.domain.ConversationReasoning.EFFORTS.forEach { effort ->
                                    val selected = conversationProfile.reasoningEffort == effort
                                    TextButton(onClick = { onSetThinking(true, effort) }) {
                                        Text(
                                            com.aster.companion.domain.ConversationReasoning.labelFor(effort),
                                            color = if (selected) colors.primary else colors.textMuted,
                                            fontSize = AsterType.body,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "DeepSeek 默认就思考（关闭会显式禁用）；Grok 关不掉思考、关闭时压到最低档。" +
                                "只有在官方 api.deepseek.com 上，带工具调用的那一轮才会临时关掉思考（官方的文档级限制）；" +
                                "自建服务和中转站（LM Studio、vLLM、你的网关）带工具也照你想的来。",
                            color = colors.textMuted,
                            fontSize = AsterType.caption
                        )
                    }
                }
                item { SettingsSection("模型上下文") }
                item {
                    var contextMenu by remember { mutableStateOf(false) }
                    var contextCustom by remember { mutableStateOf(false) }
                    Box {
                        SettingsMenuRow(
                            title = "上下文长度",
                            description = com.aster.companion.agent.ConversationContextWindowPolicy.labelFor(contextTokens) +
                                "：填你正在用的模型能吃多长。App 每轮的人设、记忆状态和最近对话都按它拼，" +
                                "超了会被服务端从中间截断（等于记忆被砍），所以换模型时记得改这里。",
                            onClick = { contextMenu = true },
                            last = false
                        )
                        DropdownMenu(expanded = contextMenu, onDismissRequest = { contextMenu = false }) {
                            com.aster.companion.agent.ConversationContextWindowPolicy.PRESETS.forEach { (label, tokens) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { onSetContextTokens(tokens); contextMenu = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("自定义…（当前 ${com.aster.companion.agent.ConversationContextWindowPolicy.formatTokens(contextTokens)}）") },
                                onClick = { contextMenu = false; contextCustom = true }
                            )
                        }
                    }
                    if (contextCustom) {
                        NumberInputDialog(
                            title = "自定义上下文长度",
                            hint = "填 K 数或 token 数：例如 96 或 98304（范围 " +
                                "${com.aster.companion.agent.ConversationContextWindowPolicy.MIN_CONTEXT_TOKENS / 1024}K~" +
                                "${com.aster.companion.agent.ConversationContextWindowPolicy.MAX_CONTEXT_TOKENS / 1024}K）",
                            initial = com.aster.companion.agent.ConversationContextWindowPolicy.formatTokens(contextTokens),
                            onDismiss = { contextCustom = false },
                            onConfirm = { raw ->
                                parseContextTokens(raw)?.let(onSetContextTokens)
                                contextCustom = false
                            }
                        )
                    }
                }
                item {
                    var windowMenu by remember { mutableStateOf(false) }
                    var windowCustom by remember { mutableStateOf(false) }
                    Box {
                        SettingsMenuRow(
                            title = "对话窗口条数",
                            description = com.aster.companion.agent.ConversationContextWindowPolicy.windowLabelFor(contextWindowMessages) +
                                "：每轮带多少条真实历史。条数越大她记得越多，但**每轮重新预填充的提示词也越大、回复越慢**" +
                                "（实测 128K 档加满整条对话时，每轮 prompt 涨到 2.3 万 token、还在线性上涨）；" +
                                "更早的事由记忆状态和长期记忆承接，所以 20~40 条通常就够。" +
                                "当前档位的推荐值：${com.aster.companion.agent.ConversationContextWindowPolicy.recommendedWindowMessages(contextTokens)} 条。",
                            onClick = { windowMenu = true },
                            last = true
                        )
                        DropdownMenu(expanded = windowMenu, onDismissRequest = { windowMenu = false }) {
                            com.aster.companion.agent.ConversationContextWindowPolicy.WINDOW_PRESETS.forEach { (label, messages) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { onSetContextWindowMessages(messages); windowMenu = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("自定义…（当前 ${contextWindowMessages} 条）") },
                                onClick = { windowMenu = false; windowCustom = true }
                            )
                        }
                    }
                    if (windowCustom) {
                        NumberInputDialog(
                            title = "自定义对话窗口条数",
                            hint = "填条数：例如 30（范围 " +
                                "${com.aster.companion.agent.ConversationContextWindowPolicy.MIN_WINDOW_MESSAGES}~" +
                                "${com.aster.companion.agent.ConversationContextWindowPolicy.MAX_WINDOW_MESSAGES}）",
                            initial = contextWindowMessages.toString(),
                            onDismiss = { windowCustom = false },
                            onConfirm = { raw ->
                                raw.trim().toIntOrNull()?.let(onSetContextWindowMessages)
                                windowCustom = false
                            }
                        )
                    }
                }
                item { SettingsSection("后台推演") }
                item {
                    var idleMenu by remember { mutableStateOf(false) }
                    Box {
                        SettingsMenuRow(
                            title = "空闲让路",
                            description = com.aster.companion.lite.BackgroundIdlePolicy.labelFor(backgroundIdleMs) +
                                "：你在这个时间内还在聊天，她的生活推进和朋友圈决策就先让路，免得跟对话抢同一张卡；闲下来后的下一拍自动照常跑。",
                            onClick = { idleMenu = true },
                            last = true
                        )
                        DropdownMenu(expanded = idleMenu, onDismissRequest = { idleMenu = false }) {
                            com.aster.companion.lite.BackgroundIdlePolicy.OPTIONS.forEach { (label, millis) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { onSetBackgroundIdleMs(millis); idleMenu = false }
                                )
                            }
                        }
                    }
                }
                item { SettingsSection("聊天显示") }
                item {
                    SettingsToggleRow(
                        title = "流式输出",
                        description = "开着（默认）：她的回复逐字显示，像真人正在打字；关掉：等整条写完再一次性出现" +
                            "（弱模型逐字抖动、或网络不稳时流断掉，可以关掉它换取稳定）。",
                        checked = state.chatDisplayPreferences.streamingOutput,
                        onCheckedChange = onSetStreamingOutput
                    )
                }
                item {
                    SettingsToggleRow(
                        title = "情绪底色",
                        description = "开着（默认）：她的语气会跨轮延续——前面几轮顶上去了，就算话题换了也不会立刻" +
                            "回到起点（关掉后不再累积、不再生成，也不会注入提示词，聊天行为回到以前）。",
                        checked = state.chatDisplayPreferences.emotionalOffsetEnabled,
                        onCheckedChange = onSetEmotionalOffset
                    )
                }
                // 「多行文本自动分割」在 Lite 下被 ChatDisplaySettings 强制恒开（关了也不生效），
                // 所以这里**不显示这个假开关**，免得用户以为能关。
                if (!com.aster.companion.lite.LiteProductConfig.ENABLED) {
                    item {
                        SettingsToggleRow(
                            title = "多行文本自动分割",
                            description = "角色多段回复会显示为多条消息；模型请求仍使用合并后的原文上下文。",
                            checked = state.chatDisplayPreferences.splitMultilineResponses,
                            onCheckedChange = onSetSplitMultilineResponses
                        )
                    }
                }
                item {
                    SettingsToggleRow(
                        title = "动作神态灰色化",
                        description = "角色消息中的 ()、（）和 <> 会以灰色辅助文字显示。",
                        checked = state.chatDisplayPreferences.dimStageDirections,
                        onCheckedChange = onSetDimStageDirections,
                        last = true
                    )
                }
                item { SettingsSection("主动陪伴") }
                item {
                    SettingsToggleRow(
                        title = "允许她主动发照片",
                        description = "她会按自己的状态主动拍一张发给你。出图会占显存、和聊天抢同一张卡，忙的时候可以关掉。",
                        checked = proactivePhotoEnabled,
                        onCheckedChange = onSetProactivePhoto
                    )
                }
                item {
                    SettingsToggleRow(
                        title = "允许她主动发朋友圈",
                        description = "她会在自己有空的时候发动态（不配图）。关掉后只保留聊天里的生活状态。",
                        checked = proactiveMomentsEnabled,
                        onCheckedChange = onSetProactiveMoments
                    )
                }
                item {
                    SettingsToggleRow(
                        title = "按她自己的节奏找你",
                        description = "不再「冷却一到就问一次」，而是按最近聊得热不热决定她什么时候想说话：" +
                            "刚聊完很快又会惦记你，冷下来就自己待着。关掉则完全回到原来的固定冷却。",
                        checked = proactiveUrgeEnabled,
                        onCheckedChange = onSetProactiveUrge
                    )
                }
                item {
                    SettingsToggleRow(
                        title = "更主动一点",
                        description = "她的意愿门槛降低、两次主动之间的最小间隔缩到 30 分钟。" +
                            "睡着、在忙、手头有事、以及每天的上限都不受影响。",
                        checked = proactiveRelaxedContact,
                        onCheckedChange = onSetProactiveRelaxedContact
                    )
                }
                item {
                    SettingsToggleRow(
                        title = "让外面也发生点事",
                        description = "她身边会偶尔有些小事（修路、降温、邻居动静），她会自然地知道、也可以提起。" +
                            "这些是已经发生的事，但怎么反应由她决定。关掉之后她的世界只有你们的对话。",
                        checked = proactiveWorldSeedingEnabled,
                        onCheckedChange = onSetProactiveWorldSeeding,
                        last = true
                    )
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
            SettingsTab.System -> if (selectedSystemPage == null) {
                item { SettingsSection("系统") }
                item { SettingsMenuRow("系统模式", "主动感知与 Shizuku 操作", onClick = { selectedSystemPage = "mode" }) }
                item { SettingsMenuRow("待确认动作", "查看并确认角色请求", onClick = { selectedSystemPage = "actions" }) }
                item { SettingsMenuRow("权限管理", "通知、相机、位置与授权", onClick = { selectedSystemPage = "permissions" }, last = true) }
            } else item {
                SettingsSubpageHeader("系统", onBack = { selectedSystemPage = null })
                if (selectedSystemPage == "actions") {
                SettingsSection("待确认动作")
                // 世界引擎已下线：这里不再显示「世界活动邀请」（那是世界推演产物，单人模式没有它）。
                if (state.pendingActions.isEmpty()) {
                    Text("现在没有需要你确认的操作。", color = colors.textMuted, fontSize = AsterType.label)
                }
                state.pendingActions.forEach { action ->
                    // 标题走共用的一份（出图类会显示她的原话/合影模式，手机操作显示人话），
                    // 以前这里直接把 capabilityId 打给用户看。
                    val actionTitle = com.aster.companion.ui.PendingActionPresentation
                        .title(action, state.characters.associate { it.id to it.name })
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp).asterCard(colors).padding(AsterMetrics.cardPadding)) {
                        Text(actionTitle, color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
                        Text(
                            com.aster.companion.ui.PendingActionPresentation.transportLabel(action.transport),
                            color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                            if (action.state == "PENDING") {
                                Button(onClick = { onApproveAction(action.id) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("确认") }
                                TextButton(onClick = { onRejectAction(action.id) }) { Text("拒绝", color = colors.textSubtle) }
                            } else {
                                Text(
                                    when (action.state) {
                                        "RUNNING" -> "正在生成…"
                                        "APPROVED" -> "已完成"
                                        "FAILED" -> "生成失败"
                                        "CANCELLED" -> "已取消"
                                        "REJECTED" -> "已拒绝"
                                        else -> action.state
                                    },
                                    color = colors.textMuted, fontSize = AsterType.label
                                )
                            }
                        }
                    }
                }
                }
                if (selectedSystemPage == "mode") {
                SettingsSection("系统模式")
                Column(Modifier.fillMaxWidth().border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius)).padding(16.dp)) {
                    Text(if (state.systemModeEnabled) "系统模式已开启" else "系统模式未开启", color = colors.primary, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Shizuku Binder ${if (state.shizukuStatus.binderAlive) "已连接" else "未连接"} · 授权 ${if (state.shizukuStatus.authorized) "已授予" else "未授予"}", color = colors.textSubtle, fontSize = AsterType.label, modifier = Modifier.padding(top = 7.dp))
                    Text("Shizuku 操作通道 ${if (state.shizukuStatus.commandChannelAlive) "已连接" else "未连接"}", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 3.dp))
                    Button(onClick = { onSetSystemModeEnabled(!state.systemModeEnabled) }, shape = RoundedCornerShape(AsterMetrics.cardRadius), modifier = Modifier.padding(top = 12.dp)) { Text(if (state.systemModeEnabled) "停止" else "开启") }
                }
                SettingsSection("生活感知")
                SensingStatusPanel(state.sensingStatuses)
                }
                if (selectedSystemPage == "permissions") {
                SettingsSection("权限")
                PermissionControls(context, onRequestShizukuPermission)
                }
                Spacer(Modifier.height(18.dp))
            }
            SettingsTab.Data -> if (selectedDataPage == null) {
                item { SettingsSection("数据") }
                // 记忆同步失败以前是静默的（历史照样被截断，用户完全不知道）。连续失败到阈值就在这里露出来。
                state.memorySyncWarning?.let { warning ->
                    item {
                        // 纯提示行（点不动）：真正的重试由后台自动做，水位没推进所以不会丢消息。
                        SettingsMenuRow(
                            title = "记忆同步异常",
                            description = warning,
                            onClick = {},
                            last = false
                        )
                    }
                }
                item { SettingsMenuRow("加密备份", "导出或导入本地数据", onClick = { selectedDataPage = "backup" }, last = false) }
                // 在线更新提到**顶层列表**里：以前它只在一张「关于」卡片的最底部，
                // 用户翻不到就等于没有（用户反馈"app 怎么没有在线更新了"）。
                item {
                    val update = updateState
                    SettingsMenuRow(
                        title = "检查更新",
                        description = when (update) {
                            is com.aster.companion.update.UpdateState.Checking -> "正在检查…"
                            is com.aster.companion.update.UpdateState.Downloading -> "下载 ${update.percent}%"
                            is com.aster.companion.update.UpdateState.Available ->
                                "发现 ${update.versionName}，点这里下载并安装"
                            is com.aster.companion.update.UpdateState.UpToDate ->
                                "已是最新" + if (update.promptCount > 0) "（远程提示词 ${update.promptCount} 项）" else ""
                            is com.aster.companion.update.UpdateState.Failed -> update.message
                            else -> "从网关拉新版本（下面「关于」里有版本号与按钮）"
                        },
                        onClick = {
                            val available = updateState is com.aster.companion.update.UpdateState.Available
                            if (available) onInstallUpdate(updateBaseUrl()) else onCheckUpdate(updateBaseUrl())
                        },
                        last = true
                    )
                }
                item { SettingsSection("最近一次出图的最终提示词") }
                item { LastScenePromptBlock(lastScenePrompt) }
                // 关于 / 自更新。这段以前只存在于**旧 Lite 设置页**里，而那个页面早就不可达了 ——
                // 于是 App 内既不能查更新也不能装更新（回调一直传着、没人用），
                // 手机又常常连不上 adb，等于只能靠浏览器手点网关地址。这里接回正式设置页。
                item { SettingsSection("关于") }
                item {
                    val versionName = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                            .getOrNull() ?: "-"
                    }
                    Column(Modifier.fillMaxWidth().asterCard(colors).padding(AsterMetrics.cardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("版本", color = colors.text, fontSize = AsterType.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(versionName, color = colors.textMuted, fontSize = AsterType.body)
                            Spacer(Modifier.size(10.dp))
                            when (val update = updateState) {
                                is com.aster.companion.update.UpdateState.Checking ->
                                    Text("检查中…", color = colors.textMuted, fontSize = AsterType.body)
                                is com.aster.companion.update.UpdateState.Downloading ->
                                    Text("下载 ${update.percent}%", color = colors.primary, fontSize = AsterType.body)
                                is com.aster.companion.update.UpdateState.Available ->
                                    TextButton(onClick = { onInstallUpdate(updateBaseUrl()) }) {
                                        Text("下载并安装", fontWeight = FontWeight.SemiBold)
                                    }
                                is com.aster.companion.update.UpdateState.UpToDate ->
                                    Text("已是最新", color = colors.textMuted, fontSize = AsterType.body)
                                else ->
                                    TextButton(onClick = { onCheckUpdate(updateBaseUrl()) }) { Text("检查更新") }
                            }
                        }
                        when (val update = updateState) {
                            is com.aster.companion.update.UpdateState.Available ->
                                Text(
                                    "发现新版本 ${update.versionName}（${"%.1f".format(update.sizeBytes / 1024.0 / 1024.0)} MB）" +
                                        if (update.promptCount > 0) " · 提示词已同步（${update.promptCount} 项）" else "",
                                    color = colors.primary,
                                    fontSize = AsterType.label,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            is com.aster.companion.update.UpdateState.UpToDate ->
                                Text(
                                    "已是最新" + if (update.promptCount > 0) {
                                        " · 提示词已同步（${update.promptCount} 项）"
                                    } else {
                                        " · 网关还没放提示词，这一版用内置默认"
                                    },
                                    color = colors.textMuted,
                                    fontSize = AsterType.label,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            is com.aster.companion.update.UpdateState.Failed ->
                                Text(
                                    update.message,
                                    color = colors.errorBright,
                                    fontSize = AsterType.label,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            else -> Unit
                        }
                    }
                }
            } else item {
                SettingsSubpageHeader("数据", onBack = { selectedDataPage = null })
                SettingsSection("加密备份")
                Text(
                    "包含角色、聊天、日记、朋友圈，以及她此刻的状态、世界状态、长期记忆和会话摘要；不包含 API Key 与模型配置。",
                    color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(bottom = 9.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                    Button(onClick = { promptExportPassword = true }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("导出") }
                    Button(onClick = { openBackupDocument.launch(arrayOf("application/octet-stream", "application/json")) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("导入") }
                }
                state.backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
                SettingsSection("跨设备迁移")
                Text(
                    "包含 API 配置、私有图片和全部角色数据（含她此刻的状态、关系与家庭群设置）；仅用于你的真机与模拟器之间迁移。导入后需要重新授予系统权限和 Shizuku。",
                    color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(bottom = 9.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
                    Button(onClick = { promptTransferExportPassword = true }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("导出迁移包") }
                    Button(onClick = { openTransferDocument.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("导入迁移包") }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
    if (promptExportPassword) BackupPasswordDialog("设置备份密码", { promptExportPassword = false }) { password ->
        BackupPasswordHolder.set(password)
        promptExportPassword = false
        createBackupDocument.launch("tongpin-backup.tpb")
    }
    pendingImportUri?.let { rawUri -> BackupPasswordDialog("输入备份密码", { pendingImportUri = null }) { password ->
        onImportBackup(android.net.Uri.parse(rawUri), password)
        pendingImportUri = null
    } }
    if (promptTransferExportPassword) BackupPasswordDialog("设置迁移密码", { promptTransferExportPassword = false }) { password ->
        TransferPasswordHolder.set(password)
        promptTransferExportPassword = false
        createTransferDocument.launch("tongpin-device-transfer.tptx")
    }
    pendingTransferImportUri?.let { rawUri -> BackupPasswordDialog("输入迁移密码", { pendingTransferImportUri = null }) { password ->
        onImportDeviceTransfer(android.net.Uri.parse(rawUri), password)
        pendingTransferImportUri = null
    } }
}

@Composable
private fun SettingsSection(title: String) {
    val colors = AsterTheme.colors
    Text(title, color = colors.primary, fontSize = AsterType.label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 17.dp, bottom = 9.dp))
}

/**
 * 一个数字输入框弹窗（自定义上下文档位、窗口条数用）。
 *
 * 复用设置页里已有的 OutlinedTextField + AlertDialog 样式；空串或非数字由 [onConfirm] 自己判断。
 */
@Composable
private fun NumberInputDialog(
    title: String,
    hint: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(12) },
                    modifier = Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(),
                    singleLine = true,
                    label = { Text("数值") },
                    shape = RoundedCornerShape(AsterMetrics.cardRadius)
                )
                Text(hint, color = AsterTheme.colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 解析「上下文长度」输入：`96` / `96K` 当 K 数，`98304` 当 token 数原样收。
 * 非数字返回 null（调用方保持原值）。
 */
private fun parseContextTokens(raw: String): Int? {
    val text = raw.trim().lowercase().removeSuffix("k").trim()
    val value = text.toIntOrNull() ?: return null
    if (value <= 0) return null
    return if (value <= 1024) value * 1024 else value
}

/**
 * 设置页「最近一次出图的最终提示词」：展示网关真正送去渲染的那份提示词（只读，可长按复制）。
 * 没出过图时给一句占位文案。这个块放在设置页的 LazyColumn 里，页面本身可滚动。
 */
@Composable
private fun LastScenePromptBlock(prompt: String) {
    val colors = AsterTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.surface1)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            "网关真正送去渲染的提示词（只读，长按可复制）",
            color = colors.textMuted,
            fontSize = AsterType.caption,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (prompt.isBlank()) {
            Text("还没有出过图", color = colors.textMuted, fontSize = AsterType.body)
        } else {
            SelectionContainer {
                Text(prompt, color = colors.text, fontSize = AsterType.label)
            }
        }
    }
}

@Composable
private fun SensingStatusPanel(statuses: List<com.aster.companion.proactive.SensingSourceStatus>) {
    val colors = AsterTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.surface1)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        val labels = mapOf(
            "place" to "当前位置区域",
            "weather" to "天气",
            "battery" to "电量与充电",
            "network" to "网络",
            "calendar" to "日程",
            "foreground" to "当前应用",
            "screen" to "屏幕状态",
            "media" to "媒体播放"
        )
        statuses.forEachIndexed { index, status ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(labels[status.source] ?: status.source, color = colors.text, fontSize = AsterType.body, modifier = Modifier.weight(1f))
                Text(sensingStatusText(status), color = if (status.state.contains("ready") || status.state == "event_recorded") colors.successBright else colors.textMuted, fontSize = AsterType.caption)
            }
            if (index < statuses.lastIndex) Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
    }
}

private fun sensingStatusText(status: com.aster.companion.proactive.SensingSourceStatus): String = when (status.state) {
    "area_ready" -> "已获取区域"
    "location_permission_missing" -> "需要位置授权"
    "location_unavailable" -> "等待定位"
    "area_unavailable" -> "无法识别区域"
    "event_recorded" -> "感知正常"
    "permission_missing" -> "需要日程授权"
    "empty" -> "暂无近期日程"
    "query_failed" -> "读取失败"
    "shizuku" -> "通过 Shizuku"
    "usage_stats" -> "通过使用情况"
    "screen_on", "screen_off", "user_present" -> "感知正常"
    "playing" -> "正在播放"
    "not_playing" -> "暂无播放"
    "not_observed" -> "等待首次感知"
    else -> status.state
}

@Composable
private fun SettingsSubpageHeader(section: String, onBack: () -> Unit) {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回$section", tint = colors.text) }
        Text(section, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsMenuRow(title: String, description: String, onClick: () -> Unit, last: Boolean = false, trailing: String = "进入") {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.surface1).clickable(onClick = onClick)
            .then(if (last) Modifier else Modifier.padding(bottom = 1.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
        }
        Text(trailing, color = colors.primary, fontSize = AsterType.label)
    }
}

@Composable
private fun SettingsToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, last: Boolean = false) {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.surface1)
            .then(if (last) Modifier else Modifier.padding(bottom = 1.dp)).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = AsterType.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BackupPasswordDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by rememberSaveable(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("至少 8 位") }, visualTransformation = PasswordVisualTransformation()) },
        confirmButton = { Button(onClick = { onConfirm(password) }, enabled = password.length >= 8) { Text("继续") } },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } }
    )
}

/** Holds the password only between the confirmation dialog and Android's file picker result. */
private object BackupPasswordHolder {
    private var password: String? = null
    fun set(value: String) { password = value }
    fun consume(): String? = password.also { password = null }
}

/** Holds the migration password only while Android's document picker is open. */
private object TransferPasswordHolder {
    private var password: String? = null
    fun set(value: String) { password = value }
    fun consume(): String? = password.also { password = null }
}

@Composable
private fun PermissionControls(context: android.content.Context, onRequestShizukuPermission: () -> Unit) {
    val colors = AsterTheme.colors
    Column(Modifier.fillMaxWidth().asterCard(colors)) {
        TongpinPermissionRow("通知访问", "接收外卖、快递等系统事件", onClick = { openSystemSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        TongpinPermissionRow("通知显示", "显示主动陪伴通知", onClick = { requestRuntimePermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS)) })
        TongpinPermissionRow("使用情况", "感知当前前台应用", onClick = { openSystemSettings(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        TongpinPermissionRow("相机", "在你确认后使用相机拍照", onClick = { requestRuntimePermissions(context, arrayOf(Manifest.permission.CAMERA)) })
        TongpinPermissionRow("短信与通讯录", "仅在你确认动作时使用", onClick = { requestRuntimePermissions(context, arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) })
        TongpinPermissionRow("天气位置与日程", "用于上下文感知", onClick = { requestRuntimePermissions(context, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_CALENDAR)) })
        TongpinPermissionRow("Shizuku 授权", "优先执行受限系统动作", onClick = onRequestShizukuPermission, last = true)
    }
}

@Composable
private fun TongpinPermissionRow(title: String, description: String, onClick: () -> Unit, last: Boolean = false) {
    val colors = AsterTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).then(if (last) Modifier else Modifier.border(1.dp, colors.border)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 13.dp, vertical = 12.dp)) {
            Text(title, color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
            Text(description, color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
        }
        Text("前往", color = colors.primary, fontSize = AsterType.label, modifier = Modifier.padding(end = 13.dp))
    }
}

private fun requestRuntimePermissions(context: android.content.Context, permissions: Array<String>) {
    val grantedPermissions = permissions.filterTo(mutableSetOf()) { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
    when (RuntimePermissionConfigurationPolicy.resolve(permissions.toList(), grantedPermissions)) {
        RuntimePermissionConfigurationAction.OPEN_APP_DETAILS -> openAppPermissionSettings(context)
        RuntimePermissionConfigurationAction.REQUEST_RUNTIME -> {
            val activity = context as? Activity
            val requested = activity?.let {
                runCatching { ActivityCompat.requestPermissions(it, permissions, 9100) }.isSuccess
            } ?: false
            if (!requested) openAppPermissionSettings(context)
        }
    }
}

private fun openSystemSettings(context: android.content.Context, intent: Intent) {
    val activityIntent = intent.withNewTaskWhenNeeded(context)
    val launched = activityIntent.resolveActivity(context.packageManager) != null &&
        runCatching { context.startActivity(activityIntent) }.isSuccess
    if (!launched) openAppPermissionSettings(context)
}

private fun openAppPermissionSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).withNewTaskWhenNeeded(context)
    val launched = intent.resolveActivity(context.packageManager) != null &&
        runCatching { context.startActivity(intent) }.isSuccess
    if (!launched) Toast.makeText(context, "无法打开系统权限设置", Toast.LENGTH_SHORT).show()
}

private fun Intent.withNewTaskWhenNeeded(context: android.content.Context): Intent = apply {
    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
private fun ModelEditor(
    title: String,
    kind: String,
    purpose: String,
    profile: com.aster.companion.data.ModelProfileEntity?,
    availableModels: List<String>,
    fetchStatus: String?,
    onSave: (String, String, String, String, String) -> Unit,
    onTest: (String) -> Unit,
    onFetchModels: (String, String, String) -> Unit
) {
    var baseUrl by rememberSaveable(kind, profile?.baseUrl) { mutableStateOf(profile?.baseUrl.orEmpty()) }
    var model by rememberSaveable(kind, profile?.modelName) {
        mutableStateOf(
            profile?.modelName ?: if (kind == "conversation") {
                com.aster.companion.agent.ModelSettingsPresentationPolicy.documentedConversationModel
            } else {
                com.aster.companion.agent.ModelSettingsPresentationPolicy.documentedImageModel
            }
        )
    }
    var apiKey by rememberSaveable(kind) { mutableStateOf("") }
    var imageProtocol by rememberSaveable(kind, profile?.imageProtocol) {
        mutableStateOf(profile?.imageProtocol?.takeIf(String::isNotBlank) ?: com.aster.companion.agent.ImageProviderProtocol.OPENAI_IMAGES.name)
    }
    var showImageProtocolChoices by rememberSaveable(kind) { mutableStateOf(false) }
    var showModelChoices by rememberSaveable(kind) { mutableStateOf(false) }
    val colors = AsterTheme.colors
    Column(
        Modifier.fillMaxWidth().asterCard(colors).padding(AsterMetrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
        Text("$purpose。服务根地址会自动使用 /v1 接口", color = colors.textMuted, fontSize = AsterType.caption)
        Text(
            com.aster.companion.agent.ModelSettingsPresentationPolicy.gatewayConnectionNotice,
            color = colors.textMuted,
            fontSize = AsterType.caption
        )
        Text(
            if (kind == "conversation") {
                "建议对话模型：${com.aster.companion.agent.ModelSettingsPresentationPolicy.documentedConversationModel}"
            } else {
                "建议图片模型：${com.aster.companion.agent.ModelSettingsPresentationPolicy.documentedImageModel}；默认尺寸 ${com.aster.companion.agent.ModelSettingsPresentationPolicy.documentedImageSize}"
            },
            color = colors.textMuted,
            fontSize = AsterType.caption
        )
        if (profile?.encryptedApiKey != null) Text("密钥已安全保存", color = colors.primary, fontSize = AsterType.caption, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("服务地址（Base URL）") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius))

        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text(if (profile?.encryptedApiKey == null) "API Key" else "API Key（留空则保留已保存的密钥）") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius), visualTransformation = PasswordVisualTransformation())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
            Text("模型", color = colors.textSubtle, fontSize = AsterType.label)
            TextButton(onClick = { onFetchModels(kind, baseUrl, apiKey); showModelChoices = true }, enabled = baseUrl.isNotBlank()) { Text("获取模型") }
            if (availableModels.isNotEmpty()) {
                TextButton(onClick = { showModelChoices = true }) { Text("选择 (${availableModels.size})") }
            }
        }
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("模型名") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius))
        if (kind == "image_generation") {
            Box {
                TextButton(onClick = { showImageProtocolChoices = true }) {
                    Text("图片协议：${imageProtocolLabel(imageProtocol)}")
                }
                DropdownMenu(
                    expanded = showImageProtocolChoices,
                    onDismissRequest = { showImageProtocolChoices = false }
                ) {
                    listOf(
                        com.aster.companion.agent.ImageProviderProtocol.OPENAI_IMAGES,
                        com.aster.companion.agent.ImageProviderProtocol.COMFY_GATEWAY_TASK,
                        com.aster.companion.agent.ImageProviderProtocol.COMFY_UI,
                        com.aster.companion.agent.ImageProviderProtocol.QWEN_DASHSCOPE
                    ).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(imageProtocolLabel(option.name)) },
                            onClick = { imageProtocol = option.name; showImageProtocolChoices = false }
                        )
                    }
                }
            }
        }
        if (kind == "conversation") {
            Text(
                com.aster.companion.agent.ModelSettingsPresentationPolicy.conversationParametersNotice,
                color = colors.textMuted,
                fontSize = AsterType.caption,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        fetchStatus?.let { Text(it, color = if (it.startsWith("HTTP")) colors.errorBright else colors.textMuted, fontSize = AsterType.caption) }
        profile?.lastTestResult?.let { Text("连接：$it", color = if (it.startsWith("连接成功")) colors.successBright else colors.errorBright, fontSize = AsterType.caption) }
        // 思考开关只在「设置 → 聊天」那一处（唯一入口）。以前这里（对话模型卡）也有一份，
        // 同一个开关两处显示、还紧挨着「保存」按钮，容易让人以为不一致。

        Row(horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
            Button(onClick = { onSave(kind, baseUrl, model, apiKey, if (kind == "image_generation") imageProtocol else "AUTO"); apiKey = "" }, enabled = baseUrl.isNotBlank() && model.isNotBlank() && (apiKey.isNotBlank() || profile?.encryptedApiKey != null), shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("保存") }
            TextButton(onClick = { onTest(kind) }, enabled = profile?.encryptedApiKey != null) { Text("测试连接", color = colors.textSubtle) }
        }
    }
    if (showModelChoices && availableModels.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showModelChoices = false },
            title = { Text("选择 $title") },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
                    items(availableModels) { option ->
                        Text(
                            option,
                            color = colors.text,
                            fontSize = AsterType.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { model = option; showModelChoices = false }.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelChoices = false }) { Text("取消", color = colors.textSubtle) } }
        )
    }
}

private fun imageProtocolLabel(protocol: String): String = when (protocol.trim().uppercase()) {
    "COMFY_GATEWAY_TASK" -> "任务网关"
    "COMFY_UI" -> "ComfyUI"
    "QWEN_DASHSCOPE" -> "DashScope Qwen"
    else -> "标准 OpenAI Images"
}

@Composable
private fun ComfyUiEditor(
    profile: com.aster.companion.data.ModelProfileEntity?,
    onSave: (String, String, String, String) -> Unit,
    onTest: (String) -> Unit
) {
    val isActive = profile?.let(com.aster.companion.agent.ComfyUiProtocol::supports) == true
    val workflow = profile?.takeIf(com.aster.companion.agent.ComfyUiProtocol::supports)
        ?.let(com.aster.companion.agent.ComfyUiProtocol::workflow)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var baseUrl by rememberSaveable(profile?.baseUrl, isActive) {
        mutableStateOf(if (isActive) profile?.baseUrl.orEmpty() else "http://10.0.2.2:8188")
    }
    var saveStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var discovering by rememberSaveable { mutableStateOf(false) }
    var connectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    val hasHttpAddress = com.aster.companion.agent.ComfyUiProtocol.isValidBaseUrl(baseUrl)
    val colors = AsterTheme.colors
    Column(
        Modifier.fillMaxWidth().asterCard(colors).padding(AsterMetrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ComfyUI 生图", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
        Text("Qwen 保留原始工作流；Illustrious XL 使用参考图 img2img 工作流。生图总规则会同步作用于两种模式。", color = colors.textMuted, fontSize = AsterType.caption)
        Text("模拟器请填 http://10.0.2.2:8188；真机可直接填 PC 局域网 IPv4，应用会自动补 http:// 和 :8188。", color = colors.textMuted, fontSize = AsterType.caption)
        TextButton(
            onClick = {
                scope.launch {
                    discovering = true
                    saveStatus = "1/2 正在扫描同一 Wi-Fi 的 ComfyUI…"
                    val result = ComfyUiLanDiscovery.findServer(context)
                    result.onSuccess { address ->
                        val modelName = if (workflow == com.aster.companion.agent.ComfyUiWorkflow.ILLUSTRIOUS_XL_REFERENCE) {
                            com.aster.companion.agent.ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME
                        } else {
                            com.aster.companion.agent.ComfyUiProtocol.QWEN_IMAGE_EDIT_MODEL_NAME
                        }
                        baseUrl = address
                        saveStatus = "2/2 已验证 ComfyUI 服务，正在保存连接…"
                        onSave("image_generation", address, modelName, "local-comfyui")
                        saveStatus = "已连接并保存：$address"
                        connectedAddress = address
                    }.onFailure { error ->
                        saveStatus = "未找到 ComfyUI：${error.message ?: "请确认电脑与手机在同一 Wi-Fi"}"
                    }
                    discovering = false
                }
            },
            enabled = !discovering
        ) { Text(if (discovering) "正在查找…" else "一键连接当前 Wi-Fi 的 ComfyUI", color = colors.primary) }
        OutlinedTextField(baseUrl, { baseUrl = it; saveStatus = null }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("ComfyUI 地址") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius))

        Row(horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
            Button(
                onClick = {
                    onSave("image_generation", com.aster.companion.agent.ComfyUiProtocol.normalizedBaseUrl(baseUrl), com.aster.companion.agent.ComfyUiProtocol.QWEN_IMAGE_EDIT_MODEL_NAME, "local-comfyui")
                    saveStatus = "Qwen 地址已保存"
                },
                enabled = hasHttpAddress,
                shape = RoundedCornerShape(AsterMetrics.cardRadius)
            ) { Text("保存 Qwen") }
            Button(
                onClick = {
                    onSave("image_generation", com.aster.companion.agent.ComfyUiProtocol.normalizedBaseUrl(baseUrl), com.aster.companion.agent.ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME, "local-comfyui")
                    saveStatus = "Illustrious 地址已保存"
                },
                enabled = hasHttpAddress,
                shape = RoundedCornerShape(AsterMetrics.cardRadius)
            ) { Text("保存 Illustrious") }
            TextButton(onClick = { onTest("image_generation") }, enabled = isActive) { Text("测试连接", color = colors.textSubtle) }
        }
        saveStatus?.let { Text(it, color = colors.primary, fontSize = AsterType.caption, fontWeight = FontWeight.SemiBold) }
        if (isActive) Text("当前：${if (workflow == com.aster.companion.agent.ComfyUiWorkflow.ILLUSTRIOUS_XL_REFERENCE) "Illustrious XL" else "Qwen Image Edit"}，图像 API 池会被跳过。", color = colors.primary, fontSize = AsterType.caption, fontWeight = FontWeight.SemiBold)
    }
    connectedAddress?.let { address ->
        AlertDialog(
            onDismissRequest = { connectedAddress = null },
            title = { Text("ComfyUI 已连接") },
            text = { Text("已发现并验证 $address 的 ComfyUI 服务，当前工作流地址已保存。") },
            confirmButton = { TextButton(onClick = { connectedAddress = null }) { Text("完成") } }
        )
    }
}

@Composable
private fun ImagePromptDebugEditor(
    savedConfig: ImagePromptDebugConfig,
    onSave: (ImagePromptDebugConfig) -> Unit,
    onReset: () -> Unit
) {
    var enabled by rememberSaveable(savedConfig.enabled) { mutableStateOf(savedConfig.enabled) }
    var globalInstructions by rememberSaveable(savedConfig.globalInstructions) { mutableStateOf(savedConfig.globalInstructions) }
    var saveStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = AsterTheme.colors
    val currentConfig = ImagePromptDebugConfig(enabled, globalInstructions)
    val hasUnsavedChanges = currentConfig != savedConfig
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            .border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius))
            .background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("生图总规则", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
                Text("控制手机端如何从当前上下文提取本次画面", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp))
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Text("保存后从下一次生图开始生效；网关的最终扩写规则仍由电脑端单独控制。", color = colors.textMuted, fontSize = AsterType.caption)
        Text(
            when {
                hasUnsavedChanges -> "有未保存的修改"
                savedConfig.enabled -> "当前总规则已保存，下次生图会完整使用"
                else -> "当前总规则关闭。保存后打开开关才会生效"
            },
            color = if (hasUnsavedChanges) colors.errorBright else colors.primary,
            fontSize = AsterType.label,
            fontWeight = FontWeight.SemiBold
        )
        saveStatus?.let { Text(it, color = colors.primary, fontSize = AsterType.label, fontWeight = FontWeight.SemiBold) }
        OutlinedTextField(
            value = globalInstructions,
            onValueChange = { globalInstructions = it },
            modifier = Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(),
            label = { Text("你的生图总规则") },
            placeholder = { Text("例如：固定现代国漫柔和薄涂、低饱和暖灰、自然室内漫反射光；每张图必须有正在发生的情绪瞬间，避免正面居中摆拍。") },
            minLines = 8,
            maxLines = 16,
            shape = RoundedCornerShape(AsterMetrics.cardRadius)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AsterMetrics.gap)) {
            Button(
                onClick = {
                    onSave(currentConfig)
                    saveStatus = if (enabled) "已保存并启用：下一次生图开始生效" else "已保存，但调试目前关闭"
                },
                shape = RoundedCornerShape(AsterMetrics.cardRadius)
            ) { Text("保存总规则") }
            TextButton(
                onClick = {
                    onReset()
                    enabled = false
                    globalInstructions = ""
                    saveStatus = "已清空总规则"
                }
            ) { Text("清空", color = colors.textSubtle) }
        }
    }
}

@Composable
private fun ImageApiPoolEditor(
    entries: List<com.aster.companion.data.ImageApiPoolEntity>,
    onSave: (String, String, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var label by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    val colors = AsterTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).border(1.dp, colors.accentLine, RoundedCornerShape(AsterMetrics.cardRadius)).background(colors.accentWash, RoundedCornerShape(AsterMetrics.cardRadius)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("图像 API 池", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
        Text("主图像服务默认使用标准 /v1/images/generations；同一个电脑网关只配置一次即可。只有真正不同的服务才需要加入备用 API。每项密钥独立加密保存。", color = colors.textMuted, fontSize = AsterType.caption)
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("供应商名称") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius))
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("外网服务地址") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius))
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("模型名") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius))
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth().keepVisibleWhenKeyboardOpened(), label = { Text("API Key") }, singleLine = true, shape = RoundedCornerShape(AsterMetrics.cardRadius), visualTransformation = PasswordVisualTransformation())
        Button(
            onClick = {
                onSave(label, baseUrl, model, apiKey)
                label = ""
                baseUrl = ""
                model = ""
                apiKey = ""
            },
            enabled = label.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank(),
            shape = RoundedCornerShape(AsterMetrics.cardRadius)
        ) { Text("加入 API 池") }
        if (entries.isEmpty()) {
            Text("还没有额外 API，当前使用上方主配置。", color = colors.textMuted, fontSize = AsterType.caption)
        } else {
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(colors.surface1, RoundedCornerShape(AsterMetrics.controlRadius)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.label, color = colors.text, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
                        Text("${entry.modelName}  ·  ${entry.baseUrl}", color = colors.textMuted, fontSize = AsterType.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { onDelete(entry.id) }) { Text("删除", color = colors.textSubtle) }
                }
            }
        }
    }
}

@Composable
private fun ImageGenerationTaskViewer(
    actions: List<com.aster.companion.data.PendingActionEntity>,
    characterNames: Map<String, String>,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    val colors = AsterTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text("图片生成记录", color = colors.text, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
        Text("正在生成、已完成和失败的照片任务都会保留在这里。", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
        if (actions.isEmpty()) {
            Text("还没有照片生成任务。", color = colors.textMuted, fontSize = AsterType.label)
        } else {
            actions.take(20).forEach { action ->
                val state = when (action.state) {
                    "PENDING" -> "等待你确认"
                    "RUNNING" -> "正在生成"
                    "APPROVED" -> "已发到对话"
                    "FAILED" -> "生成失败"
                    "REJECTED" -> "已取消"
                    "CANCELLED" -> "已取消"
                    else -> action.state
                }
                val stateColor = when (action.state) {
                    "FAILED" -> colors.errorBright
                    "RUNNING" -> colors.primary
                    else -> colors.textMuted
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 7.dp)
                        .asterCard(colors)
                        .clickable { onOpen(action.id) }
                        .padding(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${com.aster.companion.ui.ImageGenerationActionPresentation.title(action, characterNames)} · $state",
                            color = stateColor,
                            fontSize = AsterType.body,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(com.aster.companion.agent.TimeLabel.of(action.createdAt), color = colors.textSubtle, fontSize = AsterType.micro)
                    }
                    Text(
                        com.aster.companion.ui.ImageGenerationActionPresentation.detail(action),
                        color = colors.textMuted,
                        fontSize = AsterType.caption,
                        modifier = Modifier.padding(top = 5.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (action.state == "FAILED") {
                        TextButton(
                            onClick = { onRetry(action.id) },
                            modifier = Modifier.align(Alignment.End).padding(top = 3.dp)
                        ) { Text("重试（用同一份提示词）", color = colors.primary) }
                    }
                    if (action.state == "RUNNING") {
                        // 「重新生成」和「取消生成」并排：正在跑的时候也能换一版重开。
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { onRegenerate(action.id) }) {
                                Text("重新生成", color = colors.primary)
                            }
                            TextButton(onClick = { onCancel(action.id) }) {
                                Text("取消生成", color = colors.textSubtle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGenerationTaskDetail(
    action: com.aster.companion.data.PendingActionEntity?,
    characterNames: Map<String, String>,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRegenerate: (String) -> Unit
) {
    val colors = AsterTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        SettingsSubpageHeader("图片请求详情", onBack)
        if (action == null) {
            Text("这个图片任务已经不存在了。", color = colors.textMuted, fontSize = AsterType.label, modifier = Modifier.padding(top = 12.dp))
            return@Column
        }
        val args = imageGenerationActionArguments(action)
        val participantIds = args["participant_character_ids"]
            ?.split(',', '|', '\n')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOfNotNull(args["character_id"]) }
        val participants = participantIds.map { characterNames[it] ?: it }.joinToString("、").ifBlank { "未指定" }
        val mode = args["group_image_mode"]?.trim()?.uppercase()?.let {
            when (it) {
                "TOGETHER" -> "合照"
                "SEPARATE" -> "分别生成"
                "SINGLE" -> "单人照"
                else -> it
            }
        } ?: "单人照"
        val state = when (action.state) {
            "PENDING" -> "等待确认"
            "RUNNING" -> "正在生成"
            "APPROVED" -> "已完成"
            "FAILED" -> "生成失败"
            "REJECTED" -> "已拒绝"
            "CANCELLED" -> "已取消"
            else -> action.state
        }
        val request = args["scene"] ?: args["image_prompt"] ?: args["prompt"] ?: "未记录原始请求"
        Text(state, color = if (action.state == "FAILED") colors.errorBright else colors.primary, fontSize = AsterType.title, fontWeight = FontWeight.SemiBold)
        Text("角色：$participants  ·  模式：$mode", color = colors.textMuted, fontSize = AsterType.caption, modifier = Modifier.padding(top = 4.dp))
        ImageTaskDetailBlock("本次画面内容", request, colors)
        // 「重新生成」复用的是上一版**真正送去渲染**的那份提示词（不重写、直接重出同一张）。
        args["rendered_prompt"]?.takeIf(String::isNotBlank)?.let { prompt ->
            ImageTaskDetailBlock("重新生成会原样复用这份提示词", prompt, colors)
        }
        action.resultSummary?.takeIf(String::isNotBlank)?.let { summary ->
            ImageTaskDetailBlock("任务结果", summary, colors)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (action.state == "FAILED") {
                Button(onClick = { onRetry(action.id) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("重试（用同一份提示词）") }
            }
            if (action.state == "RUNNING") {
                Button(onClick = { onRegenerate(action.id) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("重新生成") }
                OutlinedButton(onClick = { onCancel(action.id) }, shape = RoundedCornerShape(AsterMetrics.cardRadius)) { Text("取消生成") }
            }
        }
    }
}

@Composable
private fun ImageTaskDetailBlock(
    title: String,
    content: String,
    colors: com.aster.ui.theme.AsterColorScheme
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .asterCard(colors)
            .padding(14.dp)
    ) {
        Text(title, color = colors.primary, fontSize = AsterType.body, fontWeight = FontWeight.SemiBold)
        Text(content, color = colors.text, fontSize = AsterType.body, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun imageGenerationActionArguments(
    action: com.aster.companion.data.PendingActionEntity
): Map<String, String> = runCatching {
    Json.parseToJsonElement(action.argumentsJson).jsonObject.mapNotNull { (key, value) ->
        value.jsonPrimitive.contentOrNull?.let { key to it }
    }.toMap()
}.getOrDefault(emptyMap())


