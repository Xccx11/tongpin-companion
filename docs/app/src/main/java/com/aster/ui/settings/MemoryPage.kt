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
@OptIn(ExperimentalLayoutApi::class)
internal fun MemoryPage(
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
