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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun ConversationManagerDialog(
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

