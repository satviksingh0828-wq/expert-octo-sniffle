package com.clawd.mobile.orca

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.clawd.mobile.data.PrefsStore
import com.clawd.mobile.overlay.FloatingPetService
import kotlinx.coroutines.launch
import java.util.UUID

/** Calico: private local chat, animated companion, and explicit Android permission onboarding. */
class OrcaHomeActivity : ComponentActivity() {
    private var chats by mutableStateOf<List<CalicoChatThread>>(emptyList())
    private var activeId by mutableStateOf("")
    private var draft by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var running by mutableStateOf(false)
    private var mood by mutableStateOf("calico-idle.apng")
    private var status by mutableStateOf("")
    private var selectedModel by mutableStateOf("pet")
    private var modelReady by mutableStateOf(false)
    private var modelDownloading by mutableStateOf(false)
    private var modelProgress by mutableIntStateOf(0)
    private var showWelcome by mutableStateOf(false)
    private var showPermissions by mutableStateOf(false)
    private var overlayPending = false

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPending = false
        if (Settings.canDrawOverlays(this)) startPet()
        else status = "Overlay permission not granted. Chat still works without the floating pet."
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Notifications allowed." else "Notifications remain optional."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chats = CalicoChatStore.load(this)
        modelReady = CalicoModelManager.ready(this)
        selectedModel = CalicoModelChoice.get(this)
        if (selectedModel == "qwen" && !modelReady) selectedModel = "pet"
        if (chats.isNotEmpty()) activeId = chats.first().id
        showWelcome = !getSharedPreferences("calico_onboarding", MODE_PRIVATE)
            .getBoolean("welcome_seen_v1", false)
        running = FloatingPetService.isRunning
        showPermissions = !showWelcome && missingCompanionPermissions()

        setContent {
            CalicoTheme {
                CalicoChatScreen(
                    threads = chats,
                    activeId = activeId,
                    draft = draft,
                    generating = busy,
                    mood = mood,
                    petRunning = running,
                    selectedModel = selectedModel,
                    modelReady = modelReady,
                    modelDownloading = modelDownloading,
                    modelProgress = modelProgress,
                    status = status,
                    onDraftChange = { draft = it.take(6000) },
                    onSend = ::send,
                    onNewChat = ::newChat,
                    onSelect = { activeId = it },
                    onDelete = ::deleteChat,
                    onPermissions = { showPermissions = true },
                    onTogglePet = { if (running) stopPet() else requestPet() },
                    onInstallModel = ::installModel,
                    onSelectModel = ::selectModel,
                    onCalendar = ::openCalendar,
                    onSearch = ::openSearch,
                    onMaps = ::openMaps,
                    onEmail = ::openEmail,
                    onFiles = ::openFiles,
                    onClearAll = {
                        CalicoChatStore.clear(this)
                        chats = emptyList()
                        activeId = ""
                        status = "All stored chats deleted from this device."
                    }
                )
                if (showWelcome) {
                    AlertDialog(
                        onDismissRequest = { dismissWelcome() },
                        title = { Text("Welcome to Calico 🐈") },
                        text = {
                            Text(
                                "Calico is your cream-and-orange cat assistant. It runs an AI model inside this app " +
                                "after a one-time model download. Android requires special overlay and Accessibility " +
                                "permissions for the floating cat and browser-tab reactions. Each permission must be " +
                                "granted in Android settings before full companion mode starts."
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                dismissWelcome()
                                showPermissions = true
                            }) { Text("Set up permissions") }
                        },
                        dismissButton = {
                            TextButton(onClick = ::dismissWelcome) { Text("Chat first") }
                        }
                    )
                }
                if (showPermissions) PermissionsDialog(
                    overlayGranted = Settings.canDrawOverlays(this@OrcaHomeActivity),
                    accessibilityGranted = OrcaPrivacy.accessibilityServiceEnabled(this@OrcaHomeActivity),
                    onOverlay = {
                        showPermissions = false
                        requestPet()
                    },
                    onAccessibility = {
                        showPermissions = false
                        openSystemSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onNotifications = {
                        showPermissions = false
                        openSystemSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onPostNotifications = {
                        showPermissions = false
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onClose = { showPermissions = false }
                )
            }
        }
        if (!showWelcome && !missingCompanionPermissions() &&
            !isPetManuallyStopped()) startPet()
    }

    override fun onResume() {
        super.onResume()
        running = FloatingPetService.isRunning
        modelReady = CalicoModelManager.ready(this)
        if (!showWelcome && missingCompanionPermissions()) {
            // Re-check every return from Android Settings and each new launch.
            showPermissions = true
        } else if (!showWelcome && !isPetManuallyStopped() && !running) {
            startPet()
        }
        setHomePetVisibility(true)
    }

    override fun onPause() {
        setHomePetVisibility(false)
        super.onPause()
    }

    private fun missingCompanionPermissions(): Boolean =
        !Settings.canDrawOverlays(this) ||
            !OrcaPrivacy.accessibilityServiceEnabled(this)

    private fun isPetManuallyStopped(): Boolean =
        getSharedPreferences("calico_onboarding", MODE_PRIVATE)
            .getBoolean("pet_manually_stopped", false)

    private fun setHomePetVisibility(visible: Boolean) {
        if (!FloatingPetService.isRunning) return
        try {
            startService(Intent(this, FloatingPetService::class.java).apply {
                action = FloatingPetService.ACTION_CALICO_HOME_VISIBLE
                putExtra(FloatingPetService.EXTRA_CALICO_HOME_VISIBLE, visible)
            })
        } catch (_: Exception) { /* Keep in-app chat usable without overlay. */ }
    }

    private fun dismissWelcome() {
        showWelcome = false
        getSharedPreferences("calico_onboarding", MODE_PRIVATE).edit()
            .putBoolean("welcome_seen_v1", true).apply()
        if (missingCompanionPermissions()) showPermissions = true
        else if (!isPetManuallyStopped()) startPet()
    }

    private fun selectModel(choice: String) {
        if (choice == "qwen" && !modelReady) {
            status = "Download Qwen3 first to select its language model."
            return
        }
        selectedModel = if (choice == "qwen") "qwen" else "pet"
        CalicoModelChoice.set(this, selectedModel)
        status = if (selectedModel == "qwen") {
            "Qwen selected: AI chat and richer pet decisions run in Calico."
        } else {
            "Built-in pet brain selected: basic chat and instant animations work with no download."
        }
    }

    private fun installModel() {
        if (modelDownloading || modelReady) return
        modelDownloading = true
        modelProgress = 0
        status = "Downloading Qwen3-0.6B into Calico's private storage; keep the app open."
        lifecycleScope.launch {
            try {
                CalicoModelManager.install(this@OrcaHomeActivity) { pct ->
                    modelProgress = pct
                    status = "Installing local AI: " + pct + "%"
                }
                modelReady = CalicoModelManager.ready(this@OrcaHomeActivity)
                if (modelReady) selectModel("qwen")
                status = if (modelReady) "Qwen3 installed. Chat now runs entirely in Calico."
                    else "Local model could not be verified."
            } catch (error: Exception) {
                modelReady = false
                status = "Model download failed: " + (error.message ?: "Try again on Wi-Fi.").take(130)
            } finally {
                modelDownloading = false
            }
        }
    }

    private fun persist(value: List<CalicoChatThread>) {
        chats = value.take(24)
        CalicoChatStore.save(this, chats)
    }

    private fun newChat() {
        val thread = CalicoChatThread(UUID.randomUUID().toString(), "New chat", emptyList())
        persist(listOf(thread) + chats)
        activeId = thread.id
        mood = "calico-mini-idle.apng"
        status = ""
    }

    private fun deleteChat(id: String) {
        persist(chats.filterNot { it.id == id })
        if (activeId == id) activeId = chats.firstOrNull()?.id.orEmpty()
    }

    private fun replaceMessages(id: String, list: List<CalicoChatMessage>) {
        persist(chats.map { if (it.id == id) it.copy(messages = list) else it })
    }

    private fun send() {
        val prompt = draft.trim()
        if (busy || prompt.isEmpty()) return
        if (selectedModel == "qwen" && !modelReady) {
            selectedModel = "pet"
            CalicoModelChoice.set(this, "pet")
        }
        val current = chats.find { it.id == activeId }
            ?: CalicoChatThread(UUID.randomUUID().toString(), "New chat", emptyList()).also {
                persist(listOf(it) + chats)
                activeId = it.id
            }
        val id = current.id
        val updated = current.messages +
            CalicoChatMessage("user", prompt) + CalicoChatMessage("assistant", "")
        val title = if (current.messages.isEmpty()) prompt.take(38) else current.title
        persist(chats.map { if (it.id == id) it.copy(title = title, messages = updated) else it })
        draft = ""
        busy = selectedModel == "qwen"
        mood = CalicoChatEngine.animationFor(prompt)
        OrcaPetController.react(this, mood)
        if (selectedModel == "pet") {
            val response = CalicoBuiltInChat.reply(prompt)
            val thread = chats.find { it.id == id }
            if (thread != null) {
                replaceMessages(id, thread.messages.dropLast(1) +
                    CalicoChatMessage("assistant", response))
            }
            mood = CalicoChatEngine.animationFor(response)
            OrcaPetController.react(this, mood)
            status = "Built-in Calico pet brain · instant offline response"
            return
        }
        status = "Qwen is responding inside Calico on this Android device…"
        lifecycleScope.launch {
            val response = StringBuilder()
            try {
                CalicoChatEngine.respond(this@OrcaHomeActivity, updated.dropLast(1)) { delta ->
                    response.append(delta)
                    val thread = chats.find { it.id == id } ?: return@respond
                    replaceMessages(id, thread.messages.dropLast(1) +
                        CalicoChatMessage("assistant", response.toString()))
                }
                mood = CalicoChatEngine.animationFor(response.toString())
                OrcaPetController.react(this@OrcaHomeActivity, mood)
                status = "Answered locally • no API key"
            } catch (error: Exception) {
                val thread = chats.find { it.id == id }
                if (thread != null) {
                    val explanation = if (response.isEmpty()) CalicoChatEngine.OFFLINE_HELP
                        else response.toString() + "\n\n(Local generation interrupted.)"
                    replaceMessages(id, thread.messages.dropLast(1) +
                        CalicoChatMessage("assistant", explanation))
                }
                mood = "calico-error.apng"
                status = "Local model unavailable: " + (error.message ?: "Connection failed").take(110)
            } finally {
                busy = false
            }
        }
    }

    private fun requestPet() {
        if (!Settings.canDrawOverlays(this)) {
            if (!overlayPending) {
                overlayPending = true
                overlayPermission.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                )
            }
        } else startPet()
    }

    private fun startPet() {
        getSharedPreferences("calico_onboarding", MODE_PRIVATE).edit()
            .putBoolean("pet_manually_stopped", false).apply()
        PrefsStore.getInstance(this).apply {
            setPetCharacter("calico")
            setFloatingPetEnabled(true)
        }
        OrcaPrivacy.setEnabled(this, true)
        try {
            ContextCompat.startForegroundService(this, Intent(this, FloatingPetService::class.java))
            running = true
            setHomePetVisibility(true)
            status = "Calico pet started; browser-tab awareness needs Accessibility permission."
        } catch (error: Exception) {
            running = false
            PrefsStore.getInstance(this).setFloatingPetEnabled(false)
            OrcaPrivacy.setEnabled(this, false)
            status = "Unable to start pet: " + (error.message ?: "Android restriction")
        }
    }

    private fun stopPet() {
        getSharedPreferences("calico_onboarding", MODE_PRIVATE).edit()
            .putBoolean("pet_manually_stopped", true).apply()
        OrcaPrivacy.setEnabled(this, false)
        OrcaContextBrain.stop()
        PrefsStore.getInstance(this).setFloatingPetEnabled(false)
        stopService(Intent(this, FloatingPetService::class.java))
        running = false
        status = "Floating pet stopped; local chat is still available."
    }

    private fun openSystemSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: Exception) { status = "Android settings are unavailable on this device." }
    }

    private fun openCalendar() {
        try {
            startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI))
        } catch (_: Exception) {
            status = "Install a calendar app to add events. No Google API key is required."
        }
    }

    private fun openMaps() {
        try {
            val query = draft.trim().ifBlank { "nearby places" }
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode(query))))
        } catch (_: Exception) {
            status = "Install or enable a maps app to open places."
        }
    }

    private fun openEmail() {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
        } catch (_: Exception) {
            status = "No email app is installed."
        }
    }

    private fun openFiles() {
        try {
            startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            })
        } catch (_: Exception) {
            status = "Android's document picker is unavailable."
        }
    }

    private fun openSearch() {
        try {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(
                SearchManager.QUERY, draft.trim().ifBlank { "Calico search" }))
        } catch (_: Exception) {
            status = "No Android search app is available. Circle to Search is not publicly launchable."
        }
    }
}

@Composable
private fun PermissionsDialog(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    onOverlay: () -> Unit,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onPostNotifications: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Calico permissions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Only enable features you want. Android asks you to approve each separately.")
                OutlinedButton(onClick = onOverlay) {
                    Text(if (overlayGranted) "✓ Floating pet overlay" else "Enable floating pet overlay")
                }
                OutlinedButton(onClick = onAccessibility) {
                    Text(if (accessibilityGranted) "✓ Screen awareness" else "Enable screen awareness (optional)")
                }
                OutlinedButton(onClick = onNotifications) {
                    Text("Enable media / notification reactions (optional)")
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    OutlinedButton(onClick = onPostNotifications) {
                        Text("Allow notification popups (optional)")
                    }
                }
                Text("Calico uses local coarse activity signals; it does not upload screen text.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}

@Composable
private fun CalicoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (dark) darkColorScheme(
        primary = Color(0xFFE9A455),
        onPrimary = Color(0xFF372016),
        primaryContainer = Color(0xFF75402D),
        secondaryContainer = Color(0xFF553C2D),
        onSecondaryContainer = Color(0xFFFFE8CD),
        background = Color(0xFF1D1816),
        surface = Color(0xFF2C2421),
        onBackground = Color(0xFFFFF3E7),
        onSurface = Color(0xFFFFF3E7),
        onSurfaceVariant = Color(0xFFCDBAB0),
        outlineVariant = Color(0xFF4F3E34)
    ) else lightColorScheme(
        primary = Color(0xFF7D3D28),
        onPrimary = Color(0xFFFFFAF3),
        primaryContainer = Color(0xFFF5D7AF),
        secondaryContainer = Color(0xFFFFE8CB),
        onSecondaryContainer = Color(0xFF563523),
        background = Color(0xFFFFF9F1),
        surface = Color(0xFFFFFAF3),
        onBackground = Color(0xFF39241C),
        onSurface = Color(0xFF39241C),
        onSurfaceVariant = Color(0xFF7E675B),
        outlineVariant = Color(0xFFEDD6BD)
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
