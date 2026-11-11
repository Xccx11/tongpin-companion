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

internal enum class MainTab(val label: String) { Chat("消息"), Moments("动态"), Memory("日记"), Settings("设置") }
private enum class ConversationFilter(val label: String) { All("全部"), Private("私聊"), Group("群聊") }
internal enum class SettingsTab(val label: String) { Models("模型"), Chat("聊天"), System("系统"), Data("数据") }

internal fun GroupAtmosphere.label(): String = when (this) {
    GroupAtmosphere.QUIET -> "安静"
    GroupAtmosphere.DAILY -> "日常"
    GroupAtmosphere.LIVELY -> "热闹"
}

internal val com.aster.ui.theme.AsterColorScheme.accentWash: Color
    get() = if (isDark) Color(0xFF3B242D) else Color(0xFFFFEEF1)

internal val com.aster.ui.theme.AsterColorScheme.accentLine: Color
    get() = if (isDark) Color(0xFF70414F) else Color(0xFFE8BBC3)


@Composable
internal fun RecoveryProtectionDialog(message: String) {
    val colors = AsterTheme.colors
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Data recovery protection", color = colors.text, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = colors.textMuted, fontSize = AsterType.bodyLarge, lineHeight = 21.sp) },
        confirmButton = {}
    )
}

@Composable
internal fun RelationshipHomePage(
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
internal fun privateCharacterIsEnabled(state: com.aster.companion.ui.CompanionHomeState, threadId: String): Boolean =
    state.threadParticipants[threadId]?.firstOrNull() in state.proactiveCharacterIds

internal fun privateCharacterIsPaused(state: com.aster.companion.ui.CompanionHomeState, threadId: String): Boolean =
    state.threadParticipants[threadId]?.firstOrNull() in state.proactivelyPausedCharacterIds

@Composable
internal fun TongpinBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
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
internal fun ThreadCreationDialog(
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
internal fun AddConversationDialog(onDismiss: () -> Unit, onNewCharacter: () -> Unit, onNewGroup: () -> Unit) {
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
internal fun CharacterEditorDialog(
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


internal enum class ConversationManagementAction(
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
internal fun ConversationManagementConfirmation(
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
internal fun MomentCard(
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
internal fun MomentEditorDialog(
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
internal fun ChatListPage(
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
internal fun CustomImageSizeDialog(
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
internal fun CharacterStatusCapsule(
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
internal fun ScenePhotoCard(
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
internal fun PhotoConsentCard(
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
internal fun DeviceActionConsentCard(
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

internal fun createCameraCaptureUri(context: android.content.Context): android.net.Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "tongpin_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

@Composable
internal fun ChatToolButton(
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
internal fun MessageBubble(message: MessageEntity, character: com.aster.companion.data.CharacterEntity?, dimStageDirections: Boolean, onOpenImage: (String) -> Unit, onDeleteMessage: (() -> Unit)? = null, distanceContext: String? = null, animateEntrance: Boolean = true) {
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
internal fun PendingImageBubble(imageUri: String) {
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

internal fun extractLocationUi(activity: String): String? {
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

internal fun extractSubLocationUi(activity: String): String? {
    val trimmed = activity.trim().removePrefix("此刻正在").removePrefix("正在").trim()
    if (!trimmed.startsWith("在")) return null
    val after = trimmed.removePrefix("在")
    val commaIdx = after.indexOfFirst { it == '，' || it == ',' || it == '、' }
    if (commaIdx < 0 || commaIdx + 1 >= after.length) return null
    return after.substring(commaIdx + 1).trim().takeIf { it.isNotBlank() && it.length <= 20 }
}

@Composable
internal fun ImagePreviewDialog(imageUri: String, onDismiss: () -> Unit) {
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
internal fun DiaryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
internal fun relativeTimeLabel(timestamp: Long): String {
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

internal fun diaryDateKey(timestamp: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))

internal fun diaryDateLabel(timestamp: Long): String {
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
internal fun SettingsSection(title: String) {
    val colors = AsterTheme.colors
    Text(title, color = colors.primary, fontSize = AsterType.label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 17.dp, bottom = 9.dp))
}

/**
 * 一个数字输入框弹窗（自定义上下文档位、窗口条数用）。
 *
 * 复用设置页里已有的 OutlinedTextField + AlertDialog 样式；空串或非数字由 [onConfirm] 自己判断。
 */
@Composable
internal fun NumberInputDialog(
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
internal fun parseContextTokens(raw: String): Int? {
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
internal fun LastScenePromptBlock(prompt: String) {
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
internal fun SensingStatusPanel(statuses: List<com.aster.companion.proactive.SensingSourceStatus>) {
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
internal fun SettingsSubpageHeader(section: String, onBack: () -> Unit) {
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
internal fun SettingsMenuRow(title: String, description: String, onClick: () -> Unit, last: Boolean = false, trailing: String = "进入") {
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
internal fun SettingsToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, last: Boolean = false) {
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
internal fun BackupPasswordDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
internal object BackupPasswordHolder {
    private var password: String? = null
    fun set(value: String) { password = value }
    fun consume(): String? = password.also { password = null }
}

/** Holds the migration password only while Android's document picker is open. */
internal object TransferPasswordHolder {
    private var password: String? = null
    fun set(value: String) { password = value }
    fun consume(): String? = password.also { password = null }
}

@Composable
internal fun PermissionControls(context: android.content.Context, onRequestShizukuPermission: () -> Unit) {
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


internal fun imageProtocolLabel(protocol: String): String = when (protocol.trim().uppercase()) {
    "COMFY_GATEWAY_TASK" -> "任务网关"
    "COMFY_UI" -> "ComfyUI"
    "QWEN_DASHSCOPE" -> "DashScope Qwen"
    else -> "标准 OpenAI Images"
}


@Composable
internal fun ImagePromptDebugEditor(
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
internal fun ImageGenerationTaskViewer(
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
internal fun ImageGenerationTaskDetail(
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


