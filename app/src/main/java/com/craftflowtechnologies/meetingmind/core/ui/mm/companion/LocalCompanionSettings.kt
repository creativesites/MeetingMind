package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.runtime.staticCompositionLocalOf
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings

/**
 * The user's companion choice (form, name, presence, quiet sermons) as screens read it.
 *
 * The default is the spec's default ([CompanionSettings]: Zuri, around). Wire the real store by
 * providing this once near the app root, for example
 * `CompositionLocalProvider(LocalCompanionSettings provides store.settings.collectAsState().value) { … }`.
 * Screens never read a store directly, so wiring it needs no screen change.
 */
val LocalCompanionSettings = staticCompositionLocalOf { CompanionSettings() }
