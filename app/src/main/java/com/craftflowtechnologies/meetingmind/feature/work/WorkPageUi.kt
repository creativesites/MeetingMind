package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.craftflowtechnologies.meetingmind.core.notes.PagedState
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** One entry of the ＋ or ⋯ menu. [badge] shows a count beside the label when above 0. */
internal data class WorkMenuAction(val label: String, val badge: Int = 0, val onClick: () -> Unit)

/**
 * The Work header (WORK_UX §2): "Work", one context line, and Search, ＋, ⋯. Plain surface, no gradient.
 * The ⋯ icon carries the Inbox count only when it is above 0.
 */
@Composable
internal fun WorkPageHeader(
    contextLine: String,
    addActions: List<WorkMenuAction>,
    moreActions: List<WorkMenuAction>,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val inbox = moreActions.sumOf { it.badge }
    Column(modifier) {
        ScreenHeader(
            "Work",
            leading = onBack?.let { back -> {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.ink) }
            } },
            actions = {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search Work", tint = MM.colors.inkSecondary) }
                MenuButton(Icons.Rounded.Add, "New", addActions, badge = 0)
                MenuButton(Icons.Rounded.MoreHoriz, if (inbox > 0) "More, $inbox in Inbox" else "More", moreActions, badge = inbox)
            }
        )
        Text(contextLine, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(bottom = MM.space.s))
    }
}

@Composable
private fun MenuButton(icon: ImageVector, description: String, actions: List<WorkMenuAction>, badge: Int) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            if (badge > 0) {
                BadgedBox(badge = { Badge(containerColor = MM.colors.accent, contentColor = MM.colors.onAccent) { Text(badge.toString(), style = MM.type.caption) } }) {
                    Icon(icon, description, tint = MM.colors.inkSecondary)
                }
            } else Icon(icon, description, tint = MM.colors.inkSecondary)
        }
        DropdownMenu(open, { open = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.card) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, style = MM.type.body, color = MM.colors.ink)
                            if (a.badge > 0) {
                                Spacer(Modifier.width(MM.space.s))
                                Text(a.badge.toString(), style = MM.type.caption, color = MM.colors.accent)
                            }
                        }
                    },
                    onClick = { open = false; a.onClick() },
                    modifier = Modifier.heightIn(min = MMSize.minTouch)
                )
            }
        }
    }
}

/** A single-line field on an inset panel: Work search and quick-add share it. */
@Composable
internal fun WorkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Search,
    onAction: () -> Unit = {},
    clearLabel: String? = null
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).background(MM.colors.surfaceSunk, MM.radius.small).padding(start = MM.space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(MMSize.iconSmall), tint = MM.colors.inkMuted)
        Spacer(Modifier.width(MM.space.s))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MM.type.body, color = MM.colors.inkMuted)
            BasicTextField(
                value, onValueChange, singleLine = true,
                textStyle = MM.type.body.copy(color = MM.colors.ink), cursorBrush = SolidColor(MM.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onSearch = { onAction() }, onDone = { onAction() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }
            )
        }
        if (value.isNotEmpty() && clearLabel != null) {
            IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, clearLabel, Modifier.size(MMSize.iconSmall), tint = MM.colors.inkMuted) }
        } else Spacer(Modifier.width(MM.space.m))
    }
}

/** A placeholder row while the first page loads. Static: no shimmer, no idle animation. */
@Composable
internal fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).padding(vertical = MM.space.s).semantics { contentDescription = "Loading" }) {
        Box(Modifier.width(MMSize.marker).height(MM.space.xxl + MM.space.m).background(MM.colors.surfaceSunk, MM.radius.pill))
        Spacer(Modifier.width(MM.space.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Box(Modifier.fillMaxWidth(0.55f).height(MM.space.m).background(MM.colors.surfaceSunk, MM.radius.small))
            Box(Modifier.fillMaxWidth(0.9f).height(MM.space.s + MM.space.xs).background(MM.colors.surfaceSunk, MM.radius.small))
            Box(Modifier.fillMaxWidth(0.4f).height(MM.space.s + MM.space.xs).background(MM.colors.surfaceSunk, MM.radius.small))
        }
    }
}

/** The slim end of a paged list: "Loading more…", an inline retry, or "That's everything". Nothing while idle. */
internal fun <T> LazyListScope.pagedFooter(state: PagedState<T>, endLabel: String, onRetry: () -> Unit) {
    if (state.items.isEmpty()) return
    when {
        state.error != null -> item(key = "footer-error") {
            StatusLine(StatusKind.Error, "Couldn't load more.", actionLabel = "Retry", onAction = onRetry)
        }
        state.isAppending -> item(key = "footer-loading") {
            Box(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), contentAlignment = Alignment.Center) {
                Text("Loading more…", style = MM.type.caption, color = MM.colors.inkMuted)
            }
        }
        state.endReached -> item(key = "footer-end") {
            Box(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), contentAlignment = Alignment.Center) {
                Text(endLabel, style = MM.type.caption, color = MM.colors.inkMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Clickable text row with a 48 dp target, for quiet in-list links such as "Earlier today (2)". */
@Composable
internal fun QuietLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MM.type.bodyStrong, color = MM.colors.accent)
    }
}
