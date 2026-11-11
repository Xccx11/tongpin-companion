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

