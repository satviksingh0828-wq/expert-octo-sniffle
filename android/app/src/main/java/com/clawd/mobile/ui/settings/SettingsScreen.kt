package com.clawd.mobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.clawd.mobile.R
import com.clawd.mobile.data.PrefsStore
import com.clawd.mobile.orca.OrcaPrivacy
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import com.clawd.mobile.ui.components.ClawdIcons
import com.clawd.mobile.ui.theme.*
import com.clawd.mobile.ws.StreamingClient
import com.clawd.mobile.ws.ConnectionState

@Composable
fun SettingsScreen(
    navController: NavController,
    streamingClient: StreamingClient,
    prefsStore: PrefsStore,
    snackbarHostState: SnackbarHostState? = null
) {
    val connectionState by streamingClient.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.CONNECTED

    Scaffold(
        containerColor = ClawdBackgroundDark,
        topBar = {
            SettingsTopBar(onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Connection status card (always visible)
            ConnectionStatusCard(
                isConnected = isConnected,
                streamingClient = streamingClient,
                onScan = { navController.navigate("scan") },
                onManual = { navController.navigate("manual") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ORCA local screen awareness — two explicit opt-ins are required.
            AccordionSection(
                title = "ORCA screen awareness",
                icon = ClawdIcons.Activity,
                defaultExpanded = false
            ) {
                val context = LocalContext.current
                var monitoring by remember { mutableStateOf(OrcaPrivacy.enabled(context)) }
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow local screen text analysis", modifier = Modifier.weight(1f), color = ClawdTextDark)
                    Switch(checked = monitoring, onCheckedChange = { monitoring = it; OrcaPrivacy.setEnabled(context, it) })
                }
                Text("Requires enabling ORCA under Android Accessibility settings. Screen text stays on-device; password fields are skipped. Pause here anytime.", modifier = Modifier.padding(horizontal = 12.dp), color = ClawdMutedDark)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Open Accessibility settings")
                }
            }

            // Accordion sections
            AccordionSection(
                title = stringResource(R.string.settings_language),
                icon = ClawdIcons.Activity,
                defaultExpanded = false
            ) {
                LanguageSection(prefsStore = prefsStore)
            }

            AccordionSection(
                title = stringResource(R.string.settings_notification),
                icon = ClawdIcons.Bell,
                defaultExpanded = false
            ) {
                NotificationSection(prefsStore = prefsStore)
            }

            AccordionSection(
                title = stringResource(R.string.settings_pet),
                icon = ClawdIcons.Pet,
                defaultExpanded = true
            ) {
                FloatingPetSection(prefsStore = prefsStore, snackbarHostState = snackbarHostState)
            }

            AccordionSection(
                title = stringResource(R.string.settings_relay),
                icon = ClawdIcons.Activity,
                defaultExpanded = false
            ) {
                RelaySettings(prefsStore = prefsStore, streamingClient = streamingClient)
            }

            AccordionSection(
                title = stringResource(R.string.settings_about),
                icon = ClawdIcons.Activity,
                defaultExpanded = false
            ) {
                AboutSection()
            }

            AccordionSection(
                title = stringResource(R.string.settings_debug_log),
                icon = ClawdIcons.Activity,
                defaultExpanded = false
            ) {
                DebugLogSection()
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                ClawdIcons.ChevronRight,
                stringResource(R.string.settings_back),
                tint = ClawdMutedDark,
                modifier = Modifier.size(20.dp).graphicsLayer(rotationZ = 180f)
            )
        }
        Text(
            stringResource(R.string.settings_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClawdTextDark
        )
    }
}
