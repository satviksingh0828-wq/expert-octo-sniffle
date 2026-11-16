package com.clawd.mobile.orca

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * An Ollama-style live model library, constrained to LiteRT-LM-compatible files.
 * Native Android AI inference stays entirely within Calico.
 */
@Composable
fun CalicoModelLibraryDialog(
    selectedId: String,
    installed: List<CalicoInstalledModel>,
    repositories: List<CalicoModelRepo>,
    variants: List<CalicoModelVariant>,
    inspectingRepo: CalicoModelRepo?,
    searching: Boolean,
    inspecting: Boolean,
    downloadId: String?,
    progress: Int,
    error: String,
    onSearch: (String) -> Unit,
    onInspect: (CalicoModelRepo) -> Unit,
    onInstall: (CalicoModelVariant) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            color = colors.surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Calico Model Library", fontWeight = FontWeight.Bold, fontSize = 22.sp,
                            color = colors.primary)
                        Text("Discover, download and run AI on this device",
                            color = colors.onSurfaceVariant, fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it.take(70) },
                    singleLine = true,
                    label = { Text("Search models") },
                    placeholder = { Text("Qwen, Gemma, Llama, Phi…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) })
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSearch(query) }, enabled = !searching) {
                        Text(if (searching) "Searching…" else "Search / Refresh")
                    }
                    TextButton(onClick = { query = ""; onSearch("") }, enabled = !searching) {
                        Text("All models")
                    }
                }
                if (downloadId != null) {
                    Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Downloading: " + downloadId.substringAfterLast("::"),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSecondaryContainer)
                            Text(progress.toString() + "% • resumes if interrupted",
                                color = colors.onSecondaryContainer, fontSize = 12.sp)
                            TextButton(onClick = onCancel) { Text("Pause download") }
                        }
                    }
                }
                if (error.isNotBlank()) {
                    Text(error, color = colors.error, fontSize = 12.sp, maxLines = 3)
                }
                HorizontalDivider()
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text("INSTALLED ON YOUR DEVICE", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("🐾 Built-in Calico pet brain", fontWeight = FontWeight.SemiBold)
                                Text("No download • instant reactions and basic replies",
                                    fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                            TextButton(onClick = { onSelect("pet") }) {
                                Text(if (selectedId == "pet") "✓ Active" else "Use")
                            }
                        }
                    }
                    items(installed, key = { "installed-" + it.id }) { model ->
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.title, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(humanSize(model.sizeBytes) + " • LiteRT-LM",
                                        fontSize = 11.sp, color = colors.onSurfaceVariant)
                                }
                                TextButton(onClick = { onSelect(model.id) }, enabled = downloadId == null) {
                                    Text(if (selectedId == model.id) "✓ Active" else "Use")
                                }
                            }
                            if (selectedId != model.id) {
                                TextButton(onClick = { onDelete(model.id) },
                                    enabled = downloadId == null) {
                                    Text("Remove local file", color = colors.error, fontSize = 11.sp)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("ONLINE MODEL CATALOG", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                        Text("Live public Hugging Face LiteRT repository search. Models marked for " +
                            "specialized chips may not work on this Android CPU runtime.",
                            fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                    if (searching) {
                        item { Text("Refreshing available models…", color = colors.primary) }
                    }
                    if (inspectingRepo != null) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("DOWNLOADABLE FILES: " + inspectingRepo.title,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (inspecting) {
                                Text("Checking formats, sizes, and checksums…",
                                    color = colors.onSurfaceVariant)
                            } else if (variants.isEmpty()) {
                                Text("No supported public .litertlm weights found in this repository.",
                                    color = colors.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        items(variants, key = { "variant-" + it.id }) { variant ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(variant.title, fontWeight = FontWeight.SemiBold,
                                    maxLines = 3)
                                Text(humanSize(variant.sizeBytes) + " • .litertlm" +
                                    if (variant.hardwareSpecific) " • specialized hardware" else " • CPU not yet tested",
                                    color = colors.onSurfaceVariant, fontSize = 11.sp)
                                if (variant.sha256.isBlank()) {
                                    Text("No published SHA-256 checksum; download disabled",
                                        color = colors.error, fontSize = 11.sp)
                                } else {
                                    val available = installed.any { it.id == variant.id }
                                    TextButton(
                                        enabled = !available && downloadId == null &&
                                            !variant.hardwareSpecific,
                                        onClick = { onInstall(variant) }
                                    ) {
                                        Text(if (available) "✓ Installed"
                                            else if (variant.hardwareSpecific) "Requires matching device"
                                            else "↓ Download & verify")
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    items(repositories, key = { "repo-" + it.id }) { repo ->
                        Surface(color = colors.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.fillMaxWidth()
                                .clickable { onInspect(repo) }.padding(10.dp)) {
                                Text(repo.title, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(repo.id, color = colors.onSurfaceVariant,
                                    fontSize = 11.sp, maxLines = 2)
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Downloads " + repo.downloads,
                                        color = colors.onSurfaceVariant, fontSize = 11.sp)
                                    Text("View model files", color = colors.primary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("GGUF / Ollama formats require a separate llama.cpp runtime; " +
                            "they cannot be installed into this LiteRT-LM engine. " +
                            "Search and downloads are free, but some upstream models are gated.",
                            fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format("%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format("%.0f MB", bytes / 1_000_000.0)
    else -> bytes.toString() + " bytes"
}
