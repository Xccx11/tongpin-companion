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

