package com.craftflowtechnologies.meetingmind.feature.applock

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** A tappable region announced by TalkBack as a button with [label]. */
internal fun Modifier.clickableWithRole(onClick: () -> Unit, label: String): Modifier =
    this.semantics { contentDescription = label }.clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
