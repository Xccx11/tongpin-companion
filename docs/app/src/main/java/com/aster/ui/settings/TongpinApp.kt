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

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun TongpinApp(openedThreadId: String?, openedMomentId: String?, clearDevelopmentTestData: Boolean = false, viewModel: CompanionHomeViewModel = hiltViewModel()) {
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

