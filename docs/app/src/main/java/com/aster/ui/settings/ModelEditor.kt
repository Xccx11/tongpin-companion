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
internal fun ModelEditor(
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

