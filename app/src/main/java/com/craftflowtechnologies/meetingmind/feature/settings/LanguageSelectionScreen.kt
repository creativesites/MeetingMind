package com.craftflowtechnologies.meetingmind.feature.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.i18n.AppLanguage
import com.craftflowtechnologies.meetingmind.core.i18n.AppLocaleManager
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LanguageSelectionViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferencesManager(app)

    /** Null indicates "Follow system default" */
    val currentLanguageTag: StateFlow<String?> = prefs.preferencesFlow
        .map { it.appLanguage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectLanguage(tag: String?) {
        viewModelScope.launch {
            // Save first: applying can recreate the activity, which must then read the new value.
            prefs.setAppLanguage(tag)
            AppLocaleManager.applyLocale(getApplication(), tag)
        }
    }
}

/**
 * Screen allowing users to select the application's interface language.
 *
 * NOTE: App UI language is completely independent of speech recognition (ASR)
 * language, AI prompts, and transcript translations.
 */
@Composable
fun LanguageSelectionScreen(
    vm: LanguageSelectionViewModel,
    onNavigateBack: () -> Unit
) {
    val currentTag by vm.currentLanguageTag.collectAsState()
    val isSystemDefault = currentTag.isNullOrBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceBase)
    ) {
        // Top Header
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Ink
                )
            }
            Text(
                text = stringResource(R.string.settings_language_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Explanation
            Text(
                text = stringResource(R.string.settings_language_description),
                fontSize = 14.sp,
                color = InkSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // System Default Card
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceRaised)
                    .border(1.dp, LineSoft, RoundedCornerShape(16.dp))
                    .clickable { vm.selectLanguage(null) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_language_follow_system),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Ink
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_language_follow_system_summary),
                            fontSize = 13.sp,
                            color = InkMuted
                        )
                    }
                    RadioButton(
                        selected = isSystemDefault,
                        onClick = { vm.selectLanguage(null) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Accent,
                            unselectedColor = LineSoft
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // All Languages Section Label
            Text(
                text = stringResource(R.string.settings_language_all_header),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Languages Card List
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceRaised)
                    .border(1.dp, LineSoft, RoundedCornerShape(16.dp))
            ) {
                AppLanguage.SUPPORTED.forEachIndexed { index, lang ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = LineSoft,
                            thickness = 0.8.dp
                        )
                    }

                    val isSelected = !isSystemDefault && AppLocaleManager.areLocalesEquivalent(currentTag, lang.tag)

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectLanguage(lang.tag) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = lang.nativeName,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = Ink
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = lang.englishName,
                                fontSize = 13.sp,
                                color = InkMuted
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { vm.selectLanguage(lang.tag) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Accent,
                                unselectedColor = LineSoft
                            )
                        )
                    }
                }
            }
        }
    }
}
