package com.clawd.mobile.orca

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clawd.mobile.data.PrefsStore
import com.clawd.mobile.overlay.FloatingPetService

/** The standalone ORCA launcher: no server pairing or WebSocket client is required. */
class OrcaHomeActivity : ComponentActivity() {
    private var message by mutableStateOf("")
    private var monitoring by mutableStateOf(false)
    private var petRunning by mutableStateOf(false)
    private var captureRunning by mutableStateOf(false)
    private var modelReady by mutableStateOf(false)
    private var chatQuestion by mutableStateOf("")
    private var chatAnswer by mutableStateOf("")
    private var chatPending by mutableStateOf(false)

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = "Copying GGUF into ORCA's private storage…"
            OrcaModel.importModel(applicationContext, uri) { success ->
                runOnUiThread {
                    modelReady = OrcaModel.installed(this)
                    message = if (success) "Model imported. AI will run locally." else "Import failed. Existing model is unchanged."
                }
            }
        }
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) startPet() else message = "Overlay permission is needed for the floating cat."
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCapturePermission()
        else message = "Allow ORCA notifications to keep the screen-capture Stop control visible."
    }
    private val capturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val consent = result.data
        if (result.resultCode == Activity.RESULT_OK && consent != null) {
            val start = Intent(this, OrcaScreenCaptureService::class.java).apply {
                action = OrcaScreenCaptureService.START
                putExtra(OrcaScreenCaptureService.RESULT_CODE, result.resultCode)
                putExtra(OrcaScreenCaptureService.RESULT_DATA, consent)
            }
            try {
                ContextCompat.startForegroundService(this, start)
                captureRunning = true
                message = "Screen capture requested. ORCA analyzes frames on-device."
            } catch (ex: Exception) { message = "Screen capture failed: ${ex.javaClass.simpleName}" }
        } else message = "Screen capture was not authorized."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ORCA uses Calico without forcing a desktop pairing.
        val prefs = PrefsStore.getInstance(this)
        if (prefs.getPetCharacter() != "calico") prefs.setPetCharacter("calico")
        refresh()
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text("ORCA", style = MaterialTheme.typography.headlineLarge)
                    Text("Your offline AI pet • Calico", style = MaterialTheme.typography.titleMedium)
                    Text("One app. No Termux, account, cloud API or desktop connection needed to run local pet reactions.")
                    HorizontalDivider()
                    Text("1. Floating companion", style = MaterialTheme.typography.titleLarge)
                    Text(if (petRunning) "Calico overlay is running" else "Calico overlay is stopped")
                    Button(onClick = { if (petRunning) stopPet() else enablePet() }) {
                        Text(if (petRunning) "Stop floating Calico" else "Start floating Calico")
                    }
                    Text("Android requires permission to display the pet over other apps.")
                    OutlinedButton(enabled = petRunning, onClick = {
                        OrcaPetController.react(applicationContext, "calico-happy.apng")
                        message = "Requested Calico happy animation."
                    }) { Text("Test happy animation") }
                    HorizontalDivider()
                    Text("2. Local Qwen AI", style = MaterialTheme.typography.titleLarge)
                    Text(if (modelReady) "GGUF model installed in ORCA private storage" else "No GGUF model installed. Rule-based reactions remain available.")
                    Button(onClick = {
                        message = if (OrcaModel.downloadOverWifi(this@OrcaHomeActivity)) "Qwen download started in Android Download Manager. Check system notifications." else "Download could not start; use Import instead."
                    }) { Text("Download Qwen 4B (Wi-Fi, 2.5 GB)") }
                    Text(OrcaModel.downloadStatus(this@OrcaHomeActivity))
                    Text(if (OrcaModel.nativeAvailable()) "Native llama.cpp runtime is packaged" else "Native llama.cpp runtime is missing from this build")
                    OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }) { Text("Import existing Qwen GGUF") }
                    Text("Select a compatible local .gguf file (about 2.5 GB). It is copied once; inference runs offline. Allow enough storage.")
                    HorizontalDivider()
                    Text("Talk to ORCA", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = chatQuestion, onValueChange = { chatQuestion = it.take(1250) },
                        label = { Text("Ask your offline AI pet") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(enabled = !chatPending && chatQuestion.isNotBlank(), onClick = {
                        chatPending = true
                        chatAnswer = ""
                        OrcaModel.chat(applicationContext, chatQuestion) { response ->
                            runOnUiThread { chatAnswer = response; chatPending = false }
                        }
                    }) { Text(if (chatPending) "Qwen is thinking…" else "Ask ORCA") }
                    if (chatAnswer.isNotEmpty()) Text(chatAnswer)
                    HorizontalDivider()
                    Text("3. Visible screen text", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow ORCA to read accessible screen text", Modifier.weight(1f))
                        Switch(checked = monitoring, onCheckedChange = { value ->
                            OrcaPrivacy.setEnabled(this@OrcaHomeActivity, value)
                            monitoring = value
                        })
                    }
                    Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility permission") }
                    Text("Two permissions are needed: the switch here and ORCA's Accessibility service in Android Settings. You may turn either off anytime. Password fields and sensitive apps are excluded.")
                    HorizontalDivider()
                    Text("4. Optional visual screen capture", style = MaterialTheme.typography.titleLarge)
                    Text("Uses Android's screen-sharing prompt and on-device OCR and image labels; stops when permission is revoked. Captured images are not saved or uploaded.")
                    Button(onClick = { if (captureRunning) stopCapture() else requestCapture() }) {
                        Text(if (captureRunning) "Stop screen capture" else "Allow screen capture")
                    }
                    Text("Screen sharing needs fresh Android approval for every session. This feature is not full visual reasoning by Qwen; Qwen receives the extracted text and labels.")
                    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { refresh() }) { Text("Refresh status") }
                    OutlinedButton(onClick = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }) { Text("Android app and battery settings") }
                    Text("Privacy: This permission can expose private chats and codes. ORCA avoids sensitive app packages and redacts common codes, but the exclusions are not exhaustive. No screenshot or screen text is saved.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    override fun onResume() { super.onResume(); refresh() }
    private fun refresh() {
        monitoring = OrcaPrivacy.enabled(this)
        petRunning = FloatingPetService.isRunning
        captureRunning = OrcaScreenCaptureService.isRunning
        modelReady = OrcaModel.installed(this)
    }
    private fun enablePet() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else startPet()
    }
    private fun startPet() {
        val prefs = PrefsStore.getInstance(this)
        prefs.setPetCharacter("calico")
        prefs.setFloatingPetEnabled(true)
        try {
            ContextCompat.startForegroundService(this, Intent(this, FloatingPetService::class.java))
            petRunning = true
            message = "Calico is running."
        } catch (ex: Exception) { message = "Pet failed to start: ${ex.javaClass.simpleName}" }
    }
    private fun stopPet() {
        PrefsStore.getInstance(this).setFloatingPetEnabled(false)
        stopService(Intent(this, FloatingPetService::class.java))
        petRunning = false
    }
    private fun requestCapture() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else launchCapturePermission()
    }
    private fun launchCapturePermission() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        capturePermission.launch(manager.createScreenCaptureIntent())
    }
    private fun stopCapture() {
        startService(Intent(this, OrcaScreenCaptureService::class.java).setAction(OrcaScreenCaptureService.STOP))
        captureRunning = false
        message = "Screen capture stopped."
    }
}
