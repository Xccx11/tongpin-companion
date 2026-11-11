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
internal fun MomentsPage(
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

