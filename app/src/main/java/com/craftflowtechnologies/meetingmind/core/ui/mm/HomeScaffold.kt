package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * One scaffold for every home (DESIGN_SYSTEM §9): 16 dp gutter, 24 dp between sections, scrolling
 * and optional pull-to-refresh. Homes differ in [hero] and [sections], never in spacing.
 *
 * [sections] is a lazy list scope: emit one `item { }` per section, and emit nothing for an empty one.
 * Pass [onRefresh] to enable pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    hero: (@Composable () -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    sections: LazyListScope.() -> Unit
) {
    val list: @Composable () -> Unit = {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(
                start = MM.space.l, end = MM.space.l,
                top = contentPadding.calculateTopPadding() + MM.space.l,
                bottom = contentPadding.calculateBottomPadding() + MM.space.xl
            ),
            verticalArrangement = Arrangement.spacedBy(MM.space.xl)
        ) {
            item(key = "header") { header() }
            if (hero != null) item(key = "hero") { hero() }
            sections()
        }
    }
    if (onRefresh != null) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier, content = { list() })
    } else {
        Box(modifier) { list() }
    }
}
