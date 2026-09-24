package com.example.feature.setup

import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.datastore.UserPreferencesManager
import com.example.core.setup.PartStatus
import com.example.core.setup.SetupGuide
import com.example.core.setup.SetupPart
import com.example.core.setup.SetupState
import com.example.ui.theme.Brand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Setup state for any screen that shows the card, banner or page. */
class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferencesManager(app)
    val state: StateFlow<SetupState?> = SetupGuide.observe(app).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val snoozedUntil: StateFlow<Long> = prefs.preferencesFlow.map { it.setupSnoozedUntil }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val wifiOnly: StateFlow<Boolean> = prefs.preferencesFlow.map { it.wifiOnlyDownload }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setUp(parts: Collection<SetupPart> = SetupPart.entries) = viewModelScope.launch {
        val s = state.value ?: SetupGuide.observe(getApplication()).first()
        SetupGuide.downloadMissing(getApplication(), s, prefs.preferencesFlow.first().wifiOnlyDownload, parts)
    }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch { prefs.setWifiOnlyDownload(value) }

    /** "Later": the card steps aside for a day, then comes back until setup is done. */
    fun snooze() = viewModelScope.launch { prefs.setSetupSnoozedUntil(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
}

fun SetupPart.icon(): ImageVector = when (this) {
    SetupPart.HEAR -> Icons.Filled.GraphicEq
    SetupPart.SPEAKERS -> Icons.Filled.Groups
    SetupPart.THINK -> Icons.Filled.AutoAwesome
}

/**
 * The Home reminder: a navy brand card that stays until the offline pack is in (or Internet mode
 * is set up). One tap starts everything that's missing; it shows each job's progress as it lands.
 */
@Composable
fun SetupCard(state: SetupState, onSetUp: () -> Unit, onDetails: () -> Unit, onLater: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Brand.NavyLift, Brand.Navy)))
            .border(1.dp, Brush.linearGradient(Brand.sweep.map { it.copy(alpha = 0.55f) }), RoundedCornerShape(24.dp))
            .clickable(onClick = onDetails)
            .testTag("setup_card")
    ) {
        // The brand glow in the corner, like the icon's light.
        Box(Modifier.size(160.dp).align(Alignment.TopEnd).background(Brush.radialGradient(listOf(Brand.Violet.copy(alpha = 0.28f), Color.Transparent)), CircleShape))
        Column(Modifier.padding(18.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.thinkingOnly) Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp).padding(end = 2.dp))
                else Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(width = 24.dp, height = 20.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.headline, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Text(state.detail, color = Color.White.copy(alpha = 0.72f), fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.parts.forEach { PartChip(it, Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!state.downloading) {
                    Surface(onClick = onSetUp, shape = RoundedCornerShape(50), color = Color.Transparent, modifier = Modifier.testTag("setup_one_tap")) {
                        Box(Modifier.background(Brush.horizontalGradient(listOf(Brand.Cyan, Brand.Indigo, Brand.Violet))).padding(horizontal = 18.dp, vertical = 11.dp)) {
                            Text("Set up in one tap · ${SetupGuide.formatBytes(state.remainingBytes)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text("Keep using the app — we'll let you know.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
                }
                Spacer(Modifier.weight(1f))
                if (onLater != null && !state.downloading) Text("Later", color = Color.White.copy(alpha = 0.55f), fontSize = 13.5.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLater).padding(8.dp))
            }
        }
    }
}

@Composable
private fun PartChip(p: PartStatus, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(if (p.installed) 1f else p.progress, label = "part")
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (p.installed) 0.12f else 0.06f)).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                p.installed -> Box(Modifier.size(20.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                p.downloading -> CircularProgressIndicator(progress = { progress.coerceAtLeast(0.03f) }, modifier = Modifier.size(20.dp), color = Brand.Cyan, trackColor = Color.White.copy(alpha = 0.15f), strokeWidth = 2.5.dp, strokeCap = StrokeCap.Round)
                else -> Icon(p.part.icon(), contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(p.part.title, color = Color.White.copy(alpha = if (p.installed) 1f else 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The slim version for Record: you can record now; here's what transcription still needs. */
@Composable
fun SetupBanner(state: SetupState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Brand.NavyLift, Brand.Navy)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp).testTag("setup_banner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.downloading) CircularProgressIndicator(progress = { state.progress }, modifier = Modifier.size(18.dp), color = Brand.Cyan, trackColor = Color.White.copy(alpha = 0.15f), strokeWidth = 2.dp)
        else Icon(if (state.thinkingOnly) Icons.Filled.Warning else Icons.Filled.GraphicEq, contentDescription = null, tint = if (state.thinkingOnly) Color(0xFFFBBF24) else Brand.Cyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    state.downloading -> "Getting ready to transcribe… ${(state.progress * 100).toInt()}%"
                    !state.part(SetupPart.HEAR).installed -> "You can record now — transcripts need the speech model"
                    else -> "Add ${state.parts.filterNot { it.installed }.joinToString(" and ") { it.part.title.lowercase() }} for the full picture"
                },
                color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium
            )
            if (!state.downloading) Text("Tap to finish setup · ${SetupGuide.formatBytes(state.remainingBytes)}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

/** The full setup page: each job in plain words, what it costs, and the Internet alternative. */
@Composable
fun SetupScreen(viewModel: SetupViewModel, onBack: () -> Unit, onInternetMode: () -> Unit, onAdvanced: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyLift, Brand.Navy))).testTag("setup_screen")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.padding(6.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Column(Modifier.padding(horizontal = 22.dp)) {
                Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(width = 56.dp, height = 48.dp))
                Text("Set up MeetingMind", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                Text(
                    "Three small jobs make it work fully offline and private. Get all three — one isn't enough.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp)
                )
                val s = state ?: return@Column
                if (s.thinkingOnly) {
                    Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33FBBF24)).padding(12.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("You have a language model, but it can't hear. Recordings need the Hearing model to become transcripts.", color = Color.White, fontSize = 13.5.sp, lineHeight = 19.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                s.parts.forEachIndexed { i, p -> PartRow(i + 1, p) { viewModel.setUp(listOf(p.part)) } }

                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wi-Fi only", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Waits for Wi-Fi before downloading", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                    Switch(checked = wifiOnly, onCheckedChange = viewModel::setWifiOnly)
                }

                if (!s.offlineReady) {
                    Surface(onClick = { viewModel.setUp() }, enabled = !s.downloading, shape = RoundedCornerShape(50), color = Color.Transparent, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("setup_all")) {
                        Box(Modifier.background(Brush.horizontalGradient(listOf(Brand.Cyan, Brand.Indigo, Brand.Violet))).padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
                            Text(if (s.downloading) "Downloading… ${(s.progress * 100).toInt()}%" else "Get everything · ${SetupGuide.formatBytes(s.remainingBytes)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Downloads keep going in the background, even if you close the app.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
                } else {
                    Text("✓ You're all set. Everything runs on this phone.", color = Brand.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp))
                }

                // The other way: no downloads, Gemini does the work.
                Column(Modifier.padding(top = 26.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onInternetMode).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, tint = Brand.Lilac, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.internetReady) "Internet mode is on" else "Or: use Internet mode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "No big downloads: Gemini transcribes and summarises. Needs a connection and your own Gemini API key, and recordings are sent to Google.",
                        color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(if (s.internetReady) "Manage in Settings →" else "Set up in Settings →", color = Brand.Lilac, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                }
                Text("Choose other models (AI Engine) →", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onAdvanced).padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun PartRow(n: Int, p: PartStatus, onGet: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (p.installed) Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.06f)))), contentAlignment = Alignment.Center) {
            if (p.downloading) CircularProgressIndicator(progress = { p.progress.coerceAtLeast(0.03f) }, modifier = Modifier.size(38.dp), color = Brand.Cyan, trackColor = Color.Transparent, strokeWidth = 3.dp)
            Icon(if (p.installed) Icons.Filled.Check else p.part.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$n. ${p.part.title}", color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            Text(p.part.job, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 18.sp)
            Text(
                when {
                    p.installed -> "Ready"
                    p.downloading -> "Downloading ${(p.progress * 100).toInt()}% · ${p.modelNames}"
                    p.failed -> "Download stopped — tap Get to try again"
                    else -> "${p.modelNames} · ${SetupGuide.formatBytes(p.remainingBytes)}"
                },
                color = if (p.failed) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.45f), fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (!p.installed && !p.downloading) {
            Text("Get", color = Brand.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Brand.Cyan.copy(alpha = 0.6f), RoundedCornerShape(50)).clickable(onClick = onGet).padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}
