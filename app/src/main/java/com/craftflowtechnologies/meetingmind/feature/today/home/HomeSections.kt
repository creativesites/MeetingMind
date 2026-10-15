package com.craftflowtechnologies.meetingmind.feature.today.home

import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.feature.today.TodayFocus

/**
 * Which sections of the Today home a person sees, from the spaces they chose (work, faith,
 * personal: any one, two or all of them). Pure, so the mapping is unit-tested on the JVM.
 *
 * - All three: the full home (faith, work and personal).
 * - Work and personal: no faith tiles, so no devotional, prayer or stories-from-faith.
 * - Personal only: no work and no faith tiles.
 * - Other pairs and singles use the same sections, with the missing ones left out.
 */
data class HomeSections(val faith: Boolean, val work: Boolean, val personal: Boolean) {

    /** The home layout this combination gets. */
    val layout: HomeLayout
        get() = when {
            faith -> HomeLayout.ALL
            work && personal -> HomeLayout.WORK_PERSONAL
            personal -> HomeLayout.PERSONAL
            else -> HomeLayout.WORK
        }

    val isEmpty: Boolean get() = !faith && !work && !personal

    companion object {
        /** The sections for [identity], narrowed by the Today focus chip (Everything, Faith, Work). */
        fun of(identity: AppIdentity, focus: TodayFocus): HomeSections {
            val everything = HomeSections(
                faith = identity.showsFaith,
                work = NotebookSpace.WORK in identity.spaces || NotebookSpace.LEARNING in identity.spaces,
                personal = NotebookSpace.PERSONAL in identity.spaces
            )
            val narrowed = when (focus) {
                TodayFocus.ALL -> everything
                TodayFocus.FAITH -> everything.copy(work = false, personal = false)
                TodayFocus.WORK -> everything.copy(faith = false, personal = false)
            }
            // A focus that leaves nothing to show falls back to everything rather than a blank home.
            return if (narrowed.isEmpty) everything else narrowed
        }
    }
}

/** The three designed homes, plus the work-only fallback. */
enum class HomeLayout { ALL, WORK_PERSONAL, PERSONAL, WORK }
