package com.craftflowtechnologies.meetingmind.core.navigation

import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle

/** What the bar's last two places can hold. */
enum class BarSlot { WORK, LEARN, FAITH, PERSONAL, TASKS, SEARCH }

/**
 * The bottom bar follows the spaces a person chose (design/work-vertical-design-spec.md R2):
 * Today · Notes · New, then two places. The person's verticals take those places first, the one
 * their Home leads with ahead of the rest, then Work, Learning, Faith, Personal; free places go
 * to Tasks, then Search. A space that is turned off never appears in the bar.
 *
 * Settings is not in the bar: it lives in the app menu (avatar), with everything else.
 * Search stays in every home's header. Pure, so it is unit-tested on the JVM.
 */
object BarConfig {

    /** How many places the bar has after Today, Notes and New. More would not fit a 360 dp phone. */
    const val PLACES = 2

    /** The vertical the person's Home leads with, if any. */
    fun lead(home: HomeStyle, identity: AppIdentity): NotebookSpace? = when {
        home == HomeStyle.PROFESSIONAL -> NotebookSpace.WORK
        home == HomeStyle.LEARNING -> NotebookSpace.LEARNING
        home == HomeStyle.PERSONAL -> NotebookSpace.PERSONAL
        identity.faithFirst -> NotebookSpace.FAITH
        else -> null
    }

    /** The verticals the person uses, in the order they claim places (and are listed on homes). */
    fun verticals(spaces: Set<NotebookSpace>, lead: NotebookSpace? = null): List<NotebookSpace> =
        (listOfNotNull(lead) + listOf(NotebookSpace.WORK, NotebookSpace.LEARNING, NotebookSpace.FAITH, NotebookSpace.PERSONAL))
            .distinct()
            .filter { it in spaces }

    fun slots(spaces: Set<NotebookSpace>, lead: NotebookSpace? = null): List<BarSlot> =
        (verticals(spaces, lead).map(::slotFor) + BarSlot.TASKS + BarSlot.SEARCH).distinct().take(PLACES)

    fun slots(identity: AppIdentity, home: HomeStyle): List<BarSlot> = slots(identity.spaces, lead(home, identity))

    fun slotFor(space: NotebookSpace): BarSlot = when (space) {
        NotebookSpace.WORK -> BarSlot.WORK
        NotebookSpace.LEARNING -> BarSlot.LEARN
        NotebookSpace.FAITH -> BarSlot.FAITH
        NotebookSpace.PERSONAL -> BarSlot.PERSONAL
    }
}
