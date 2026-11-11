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
internal fun ChatDetailPage(
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

