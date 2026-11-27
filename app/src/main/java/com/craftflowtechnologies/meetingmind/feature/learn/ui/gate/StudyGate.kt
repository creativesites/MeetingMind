package com.craftflowtechnologies.meetingmind.feature.learn.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * Study's paid extras, asked through [FeatureGate] like every other paid feature. Core Learning stays free.
 * When there is nothing to buy (billing not configured) everything is open, as it is today.
 */
object StudyGate {
    fun open(capability: Capability, nowMs: Long = System.currentTimeMillis()): Boolean =
        !Billing.gateway.configured || FeatureGate.can(capability, nowMs)
}

/**
 * The calm card that stands where a paid extra would be: one line on what it does, and a "See plans" button that
 * opens the paywall. Never a dead end; the free parts of the screen stay usable around it.
 */
@Composable
fun PaidPlanCard(line: String, onSeePlans: () -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    MMCard(modifier = modifier.testTag("paid_plan_card"), contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Text("Part of the paid plans", style = MM.type.bodyStrong, color = c.ink)
            Text(line, style = MM.type.caption, color = c.inkSecondary)
            SecondaryButton(text = "See plans", onClick = onSeePlans, modifier = Modifier.testTag("see_plans"))
        }
    }
}
