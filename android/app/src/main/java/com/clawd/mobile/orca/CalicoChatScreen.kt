package com.clawd.mobile.orca

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.launch

/** Native Android Compose interpretation of EvanBacon/chat-template's conversation/sidebar/composer. */
@Composable
fun CalicoChatScreen(
    threads: List<CalicoChatThread>,
    activeId: String,
    draft: String,
    generating: Boolean,
    mood: String,
    petRunning: Boolean,
    modelReady: Boolean,
    modelDownloading: Boolean,
    modelProgress: Int,
    status: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPermissions: () -> Unit,
    onTogglePet: () -> Unit,
    onInstallModel: () -> Unit,
    onCalendar: () -> Unit,
    onSearch: () -> Unit,
    onMaps: () -> Unit,
    onEmail: () -> Unit,
    onFiles: () -> Unit,
    onClearAll: () -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val current = threads.find { it.id == activeId }
    val messages = current?.messages.orEmpty()
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(activeId, messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colors.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CALICO", color = colors.primary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("Your private little AI companion", color = colors.onSurfaceVariant)
                    Button(onClick = {
                        onNewChat()
                        scope.launch { drawer.close() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("+  New conversation") }
                    HorizontalDivider()
                    Text("Conversations", fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(threads, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.title,
                                modifier = Modifier.weight(1f).clickable {
                                    onSelect(item.id)
                                    scope.launch { drawer.close() }
                                }.padding(12.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (item.id == activeId) colors.primary else colors.onSurface
                            )
                            TextButton(onClick = { onDelete(item.id) }) { Text("×") }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = {
                    onPermissions()
                    scope.launch { drawer.close() }
                }, modifier = Modifier.fillMaxWidth()) { Text("Permissions & privacy") }
                TextButton(onClick = {
                    onClearAll()
                    scope.launch { drawer.close() }
                }, modifier = Modifier.fillMaxWidth()) { Text("Delete all local chats") }
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
            Row(
                Modifier.fillMaxWidth().background(colors.surface)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { scope.launch { drawer.open() } }) {
                    Text("☰", fontSize = 24.sp, color = colors.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Calico", fontWeight = FontWeight.Bold, color = colors.onSurface, fontSize = 21.sp)
                    Text(if (modelReady) "QWEN3 LOCAL AI · READY" else "OFFLINE AI · SETUP NEEDED", color = colors.primary, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onPermissions) { Text("⚙", fontSize = 22.sp) }
                TextButton(onClick = onNewChat) { Text("+", fontSize = 26.sp) }
            }
            HorizontalDivider(color = colors.outlineVariant)
            if (messages.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Hi, I'm Calico!", color = colors.onBackground, fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    Text("Your cozy offline cat companion. Ask me anything, explore your apps, or just say hello.",
                        color = colors.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = colors.secondaryContainer) {
                        Text(if (modelReady) "Qwen3-0.6B is ready for fully offline conversation."
                             else "Install the free local AI model once (~614 MB). It runs entirely inside Calico after download.",
                            modifier = Modifier.padding(12.dp), color = colors.onSecondaryContainer,
                            fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(messages) { _, message ->
                        CalicoMessageBubble(message)
                    }
                    if (generating) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🐈", fontSize = 22.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Calico is thinking…", color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // A permanently reserved bottom-right dock keeps the animated cat
            // visible while typing, without floating over chat messages.
            Row(
                Modifier.fillMaxWidth().height(105.dp).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🐾 Calico is with you", color = colors.primary,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (!modelReady) {
                        if (modelDownloading) {
                            Text("Installing local AI: $modelProgress%", fontSize = 12.sp,
                                color = colors.onSurfaceVariant)
                        } else {
                            Button(onClick = onInstallModel,
                                shape = RoundedCornerShape(14.dp)) {
                                Text("↓ Download offline AI (~614 MB)", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text("Chat and pet reactions run on this device",
                            color = colors.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
                CalicoAnimatedAvatar(mood, modifier = Modifier.size(100.dp))
            }
            if (status.isNotBlank()) {
                Text(status, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onTogglePet) { Text(if (petRunning) "🐾 Pet on" else "🐾 Start pet") }
                TextButton(onClick = onCalendar) { Text("Calendar") }
                TextButton(onClick = onSearch) { Text("Search") }
                TextButton(onClick = onMaps) { Text("Maps") }
                TextButton(onClick = onEmail) { Text("Email") }
                TextButton(onClick = onFiles) { Text("Files") }
            }
            Row(
                Modifier.fillMaxWidth().background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft, onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    enabled = !generating,
                    placeholder = { Text("Message Calico…") },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Button(
                    onClick = onSend,
                    enabled = modelReady && !generating && draft.isNotBlank(),
                    modifier = Modifier.padding(bottom = 4.dp).height(52.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) { Text("➤", fontSize = 18.sp) }
            }
            Text(
                if (modelReady) "Model: Qwen3-0.6B · private local inference · no API key"
                else "One-time model download required · no Termux or API key",
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                color = colors.onSurfaceVariant, fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalicoMessageBubble(message: CalicoChatMessage) {
    val user = message.role == "user"
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        if (!user) {
            Text("🐱", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp, top = 8.dp))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomEnd = if (user) 5.dp else 18.dp,
                bottomStart = if (user) 18.dp else 5.dp
            ),
            color = if (user) colors.primaryContainer else colors.surface,
            tonalElevation = 2.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (user) "You" else "Calico", color = colors.primary, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(message.content.ifBlank { "…" }, color = colors.onSurface, fontSize = 15.sp)
            }
        }
    }
}

/** Reuses the *actual* packaged Calico animation inside the chat, without network access. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CalicoAnimatedAvatar(animation: String, modifier: Modifier = Modifier) {
    val safe = if (animation in OrcaPetController.animations) animation else "calico-idle.apng"
    key(safe) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val loader = WebViewAssetLoader.Builder()
                    .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(ctx.applicationContext))
                    .build()
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.allowContentAccess = false
                    settings.allowFileAccess = false
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?, request: WebResourceRequest?
                        ): WebResourceResponse? =
                            request?.url?.let { loader.shouldInterceptRequest(it) }
                    }
                    val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<style>*{margin:0;padding:0}body{background:transparent;height:100vh;" +
                        "display:flex;align-items:center;justify-content:center}" +
                        "img{width:100%;height:100%;object-fit:contain}</style></head>" +
                        "<body><img src='/svg/calico/" + safe + "'></body></html>"
                    loadDataWithBaseURL(
                        "https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null
                    )
                }
            }
        )
    }
}
