package com.example.feature.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.UserPreferencesManager
import com.example.ui.theme.AccentChoice
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Appearance
import com.example.ui.theme.HomeSection
import com.example.ui.theme.HomeStyle
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineSoft
import com.example.ui.theme.LocalMMColors
import com.example.ui.theme.OnAccent
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.SurfaceSunk
import com.example.ui.theme.TextSize
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppearanceViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferencesManager(app)
    val appearance: StateFlow<Appearance> = prefs.preferencesFlow.map { it.appearance }.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
    val themeMode: StateFlow<ThemeMode> = prefs.preferencesFlow.map { runCatching { ThemeMode.valueOf(it.themeMode) }.getOrDefault(ThemeMode.DARK) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    fun update(change: (Appearance) -> Appearance) = viewModelScope.launch { prefs.setAppearance(change(appearance.value)) }
    fun setMode(m: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(m.name) }
    fun reset() = viewModelScope.launch { prefs.setAppearance(Appearance()) }
}

/** Look and home: colour, text size, light or dark, which home opens, and what Today shows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(vm: AppearanceViewModel, onNavigateBack: () -> Unit) {
    val a by vm.appearance.collectAsState()
    val mode by vm.themeMode.collectAsState()
    Column(Modifier.fillMaxSize().background(SurfaceBase)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
            Text("Look and home", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.weight(1f))
            Text("Reset", fontSize = 14.sp, color = Accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.reset() }.padding(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Preview(a)

            Label("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentChoice.entries.forEach { c ->
                    val color = if (LocalMMColors.current.isDark) c.dark else c.light
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(color).clickable { vm.update { it.copy(accent = c) } }.testTag("accent_${c.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) { if (a.accent == c) Icon(Icons.Filled.Check, c.label, tint = OnAccent, modifier = Modifier.size(22.dp)) }
                }
            }

            Label("Light or dark")
            Segments(ThemeMode.entries, mode, { it.label }) { vm.setMode(it) }

            Label("Text size")
            Segments(TextSize.entries, a.textSize, { it.label }) { s -> vm.update { it.copy(textSize = s) } }

            Label("Home")
            HomeStyle.entries.forEach { h ->
                val on = a.homeStyle == h
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(if (on) AccentWash else SurfaceRaised)
                        .border(1.dp, if (on) Accent else LineSoft, RoundedCornerShape(16.dp)).clickable { vm.update { it.copy(homeStyle = h) } }.padding(16.dp).testTag("home_${h.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(h.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(h.description, fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (on) Icon(Icons.Filled.Check, null, tint = Accent)
                }
            }

            if (a.homeStyle == HomeStyle.TODAY) {
                Label("On the Today home")
                HomeSection.entries.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.label, fontSize = 15.sp, color = Ink)
                            Text(s.description, fontSize = 12.5.sp, color = InkMuted)
                        }
                        Switch(checked = a.shows(s), onCheckedChange = { on -> vm.update { it.copy(hidden = if (on) it.hidden - s else it.hidden + s) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))

@Composable
private fun <T> Segments(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceSunk).padding(3.dp)) {
        items.forEach { item ->
            val on = item == selected
            Text(
                label(item), fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) Ink else InkSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(item) }.padding(vertical = 10.dp)
            )
        }
    }
}

/** A small live sample, so a change is seen before leaving the page. */
@Composable
private fun Preview(a: Appearance) {
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(20.dp)).padding(18.dp)) {
        Text("UP NEXT · 10:30", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        Text("Sunday service", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.padding(top = 6.dp))
        Text("Last time: Abide in the vine", fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(top = 3.dp))
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Record", color = OnAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 18.dp, vertical = 9.dp))
            Spacer(Modifier.size(10.dp))
            Text(a.homeStyle.label + " home · " + a.textSize.label.lowercase() + " text", fontSize = 12.5.sp, color = InkMuted)
        }
    }
}
