package com.clawd.mobile.orca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clawd.mobile.data.PrefsStore
import com.clawd.mobile.overlay.FloatingPetService
import kotlinx.coroutines.delay

/** Minimal ORCA launcher: the pet and local screen-description controller run automatically. */
class OrcaHomeActivity : ComponentActivity() {
    private var running by mutableStateOf(false)
    private var overlayPermissionPending = false
    private var accessibilityPromptShown = false
    private var logVersion by mutableIntStateOf(0)

    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayPermissionPending = false
        if (Settings.canDrawOverlays(this)) startOrca() else log("Overlay permission was not granted.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
        setContent {
            var liveTick by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    liveTick++
                    delay(1000)
                }
            }
            val logs = remember(logVersion, liveTick) { OrcaLogStore.snapshot(this@OrcaHomeActivity) }
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ORCA", style = MaterialTheme.typography.headlineLarge)
                    Text("Offline automatic screen-aware pet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (running) "Running: the pet reads permitted screen descriptions and chooses animations locally."
                        else "Stopped: no screen descriptions are processed."
                    )
                    Button(onClick = { if (running) stopOrca() else requestStart() }) {
                        Text(if (running) "Stop" else "Start")
                    }
                    if (!running && !OrcaPrivacy.accessibilityServiceEnabled(this@OrcaHomeActivity)) {
                        Text("Press Start, then open Android Settings > Accessibility > Installed apps > ORCA and turn on Use ORCA. Return here after enabling it.")
                    }
                    OutlinedButton(onClick = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text("Enable notification reactions")
                    }
                    Text("Notification Access is optional. ORCA reacts to notification arrival without reading message text.")
                    HorizontalDivider()
                    Text("Test animations", style = MaterialTheme.typography.titleLarge)
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            OrcaPetController.reactEvent(this@OrcaHomeActivity, "typing")
                        }) { Text("Typing") }
                        OutlinedButton(onClick = {
                            OrcaPetController.reactEvent(this@OrcaHomeActivity, "notification")
                        }) { Text("Alert") }
                        OutlinedButton(onClick = {
                            OrcaPetController.reactEvent(this@OrcaHomeActivity, "success")
                        }) { Text("Happy") }
                    }
                    HorizontalDivider()
                    Text("Test logs", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { OrcaLogStore.clear(this@OrcaHomeActivity); logVersion++ }) { Text("Clear logs") }
                    if (logs.isEmpty()) {
                        Text("No logs yet. Press Start to begin automatic testing.")
                    } else {
                        Text(logs.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (PrefsStore.getInstance(this).isFloatingPetEnabled()) requestStart(auto = true)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        running = FloatingPetService.isRunning
    }

    private fun requestStart(auto: Boolean = false) {
        if (!Settings.canDrawOverlays(this)) {
            if (!overlayPermissionPending) {
                overlayPermissionPending = true
                log(if (auto) "Automatic start needs overlay permission." else "Opening overlay permission for Start.")
                overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            return
        }
        startOrca()
        if (!OrcaPrivacy.accessibilityServiceEnabled(this)) {
            if (!accessibilityPromptShown) {
                accessibilityPromptShown = true
                log("ACTION NEEDED: Android Settings > Accessibility > Installed apps > ORCA > turn on Use ORCA, then return to ORCA.")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        } else {
            log("Accessibility service is enabled; automatic screen descriptions are active.")
        }
    }

    private fun startOrca() {
        PrefsStore.getInstance(this).apply {
            setPetCharacter("calico")
            setFloatingPetEnabled(true)
        }
        OrcaPrivacy.setEnabled(this, true)
        try {
            ContextCompat.startForegroundService(this, Intent(this, FloatingPetService::class.java))
            running = true
            log("Started automatic Calico pet and built-in offline controller.")
        } catch (error: Exception) {
            log("Start failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}")
        }
    }

    private fun stopOrca() {
        OrcaPrivacy.setEnabled(this, false)
        PrefsStore.getInstance(this).setFloatingPetEnabled(false)
        stopService(Intent(this, FloatingPetService::class.java))
        running = false
        log("Stopped pet and screen-description monitoring.")
    }

    private fun log(message: String) {
        OrcaLogStore.append(this, message)
        logVersion++
    }
}
