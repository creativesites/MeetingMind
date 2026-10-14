package com.craftflowtechnologies.meetingmind.feature.today.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.devotional.DailyDevotional
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalNotes
import com.craftflowtechnologies.meetingmind.feature.stories.StoryKind
import com.craftflowtechnologies.meetingmind.feature.today.StoryRings

/**
 * Builds the faith tiles' data from what Today already loads, the same way Today builds it.
 * Nothing here is invented: the week count comes from the streak, the devotional from the day's reading.
 */
internal fun faithBentoData(weekCount: Int, devotional: DailyDevotional?): BentoData {
    val opened = devotional?.note?.metadata?.get(DevotionalNotes.META_OPENED) != null
    return BentoData(
        weekCount = weekCount,
        devotionalTitle = devotional?.devotional?.title ?: "A word for your day",
        devotionalSub = devotional?.devotional?.scripture?.firstOrNull()?.display() ?: "Scripture, a reflection and a prayer",
        devotionalNew = !opened,
        todayCount = 0,
        nextTitle = "",
        nextWhen = "",
        plan = emptyList()
    )
}

/** The Today faith tiles, reused as they are: the streak tile over the devotional and prayer tiles. */
@Composable
internal fun FaithSection(
    data: BentoData,
    onFaith: () -> Unit,
    onDevotional: () -> Unit,
    onPrayer: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FaithTile(data, onFaith, index = index)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DevotionalTile(data, onDevotional, index = index + 1, modifier = Modifier.weight(1f))
            PrayerTile(onPrayer, index = index + 2, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The faith tiles and the stories rings, for a home that has a faith space. Shown in every mode,
 * so the faith features stay one tap away; a mode only changes where they sit on the page.
 */
@Composable
internal fun FaithAndStories(
    showsFaith: Boolean,
    data: BentoData,
    stories: List<StoryKind>,
    onFaith: () -> Unit,
    onDevotional: () -> Unit,
    onPrayer: () -> Unit,
    onOpenStories: (StoryKind?) -> Unit,
    index: Int,
    modifier: Modifier = Modifier
) {
    if (!showsFaith && stories.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        if (showsFaith) FaithSection(data, onFaith, onDevotional, onPrayer, index = index)
        if (stories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            StoryRings(stories, onOpenStories)
        }
    }
}
