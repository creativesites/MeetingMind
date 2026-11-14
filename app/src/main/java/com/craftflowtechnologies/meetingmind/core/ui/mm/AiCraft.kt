package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay

/** Opens AI / model settings from any AI sheet or card without threading callbacks through every layer. */
val LocalAiOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Whether an AI failure looks like missing key / configuration / offline mode rather than a transient error. */
fun looksUnconfiguredAiError(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val m = message.lowercase()
    return "settings" in m || "api key" in m || "gemini key" in m || "internet mode" in m ||
        "no model" in m || "not configured" in m || "backup ai" in m
}

/** True when the list is already near the bottom, so new streaming/answer items can auto-scroll without yanking reading position. */
fun LazyListState.isNearBottom(threshold: Int = 2): Boolean {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return true
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisible >= (total - 1 - threshold)
}

/** Formats inline `[1]` markers as small accent superscripts so citation keys read cleanly across all AI answers. */
fun citedAnnotatedString(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    Regex("\\[(\\d{1,2})]").findAll(text).forEach { m ->
        append(text.substring(i, m.range.first))
        pushStyle(
            SpanStyle(
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                baselineShift = BaselineShift.Superscript
            )
        )
        append(m.groupValues[1])
        pop()
        i = m.range.last + 1
    }
    append(text.substring(i))
}

/**
 * Copy action with immediate visual confirmation (`Check` + `"Copied"`) and a 48 dp touch target.
 * Use [compact] = true inside card headers/footers and [compact] = false as a primary/pill button.
 */
@Composable
fun AiCopyButton(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    label: String = "Copy",
    onCopied: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val c = MM.colors
    var copied by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }
    val copyAction: () -> Unit = {
        clipboard.setText(AnnotatedString(text))
        copied = true
        onCopied?.invoke()
    }
    val tint by animateColorAsState(if (copied) c.success else c.inkMuted, MM.motion.quick(), label = "copyTint")

    if (compact) {
        Row(
            modifier = modifier.heightIn(min = MMSize.minTouch),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = copyAction,
                modifier = Modifier
                    .sizeIn(minWidth = MMSize.minTouch, minHeight = MMSize.minTouch)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (copied) "Copied to clipboard" else label
                    }
            ) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (copied) {
                Text(
                    "Copied",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.success,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = copyAction,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (copied) c.success else c.line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (copied) c.success else c.ink),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = modifier.heightIn(min = MMSize.minTouch)
        ) {
            Icon(
                imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (copied) "Copied" else label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Pulsing skeleton container for in-flight AI work with screen-reader `LiveRegionMode.Polite`
 * and an optional Stop action to cancel slow requests in place.
 */
@Composable
fun AiThinkingSkeleton(
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    lines: List<Float> = listOf(0.92f, 0.74f, 0.52f),
    onStop: (() -> Unit)? = null
) {
    val c = MM.colors
    val t = rememberInfiniteTransition(label = "aiThinking")
    val pulse by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.accentWash)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AiMark, contentDescription = null, tint = c.accent.copy(alpha = pulse), modifier = Modifier.size(18.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            if (onStop != null) {
                TextButton(
                    onClick = onStop,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!sublabel.isNullOrBlank()) {
            Text(
                text = sublabel,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = c.inkMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        lines.forEach { w ->
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(w)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(c.accent.copy(alpha = 0.14f * pulse))
            )
        }
    }
}

/**
 * Actionable error banner for AI failures: announces itself politely, offers "Try again" when retryable,
 * and surfaces "AI settings" whenever an unconfigured key/tier error is detected.
 */
@Composable
fun AiRecoveryBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "Close"
) {
    val c = MM.colors
    val openSettings = LocalAiOpenSettings.current
    val showSettings = openSettings != null && looksUnconfiguredAiError(message)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceSunk)
            .border(1.dp, c.danger.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Text(message, fontSize = 13.5.sp, lineHeight = 20.sp, color = c.inkSecondary, modifier = Modifier.weight(1f))
        }
        if (onRetry != null || showSettings || onDismiss != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel, color = c.inkMuted) }
                }
                if (showSettings && openSettings != null) {
                    TextButton(onClick = openSettings) {
                        Text("AI settings", color = c.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (onRetry != null) {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Text("Try again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
