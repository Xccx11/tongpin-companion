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
internal fun SettingsPage(
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

