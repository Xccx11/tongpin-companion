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

