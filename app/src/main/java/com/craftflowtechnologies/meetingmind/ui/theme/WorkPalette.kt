package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Work vertical colours that are content rather than theme roles (docs/DESIGN_SYSTEM.md §2).
 * Theme roles (ink, surface, accent, ...) still come from `MM.colors`; the fixed Briefing gradient
 * is in [Briefing]. Literal colours live here only, because `ColorLiteralGuardTest` allows `ui/theme`.
 */
object WorkPalette {
    /**
     * Eight stable project colours as (light, dark) pairs. The dark value is lifted so a colour bar
     * stays visible on Midnight Graphite. Picked by `projectColor(id, dark)` in core/ui/work.
     */
    val Projects: List<Pair<Color, Color>> = listOf(
        Color(0xFF5B5BD6) to Color(0xFF9B9CF6), // Indigo
        Color(0xFF0E7490) to Color(0xFF5CC8DD), // Ocean
        Color(0xFF15803D) to Color(0xFF5FD08A), // Forest
        Color(0xFF996515) to Color(0xFFE0B25A), // Gold
        Color(0xFFBE185D) to Color(0xFFF27AAE), // Rose
        Color(0xFF7C3AED) to Color(0xFFC39AF0), // Violet
        Color(0xFF0F766E) to Color(0xFF4FD1A5), // Teal
        Color(0xFF3F3F46) to Color(0xFFC9CDD6)  // Graphite
    )

    /**
     * Meeting blocks on the day ribbon when they sit on a dark surface (the Briefing card, or any
     * dark theme). Fixed, so white text on them keeps its contrast whatever accent is chosen.
     */
    val BlockStart: Color = Color(0xFF6E6EF0)
    val BlockEnd: Color = Color(0xFF4F46E5)
}

/** Shapes used by the Work components. Radii the design system does not name are defined here. */
object WorkShape {
    /** The Briefing card (Work "Your day" and Pulse hero). */
    val brief = RoundedCornerShape(26.dp)
    /** The sky hero. */
    val sky = RoundedCornerShape(30.dp)
    /** A meeting block on the day ribbon. */
    val block = RoundedCornerShape(9.dp)
}
