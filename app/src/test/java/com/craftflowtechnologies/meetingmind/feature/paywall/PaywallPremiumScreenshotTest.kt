package com.craftflowtechnologies.meetingmind.feature.paywall

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.billing.FakeBillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.OfferKind
import com.craftflowtechnologies.meetingmind.core.billing.Plan
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The light plans page in the app's light and dark settings (it must draw light in both), the "not in this build"
 * page and the upgrade dialog. The phone is a 360 dp wide, 3-button-height screen, so the pinned button and its
 * links show above the navigation bar. Images: build/outputs/roborazzi/paywall_premium_<name>_<light|dark>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi", sdk = [34])
class PaywallPremiumScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun ComposeContentTestRule.paywallShot(name: String, waitForTag: String? = null, dialog: Boolean = false, content: @Composable () -> Unit) {
        val dark = mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                MeetMindTheme(darkTheme = dark.value) { content() }
            }
        }
        waitForTag?.let { tag -> waitUntil(10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        waitForIdle()
        for (d in listOf(false, true)) {
            dark.value = d
            waitForIdle()
            val path = "build/outputs/roborazzi/paywall_premium_${name}_${if (d) "dark" else "light"}.png"
            // A dialog draws in its own window, which the root node doesn't include.
            if (dialog) onNode(isDialog()).captureRoboImage(path) else onRoot().captureRoboImage(path)
        }
    }

    @Test fun plans() {
        val vm = PaywallViewModel(app, FakeBillingGateway())
        compose.paywallShot("plans", waitForTag = "plan_yearly") {
            PaywallScreen(PaywallTrigger.FIRST_RESULT, onClose = {}, vm = vm)
        }
        // The one button and the way out stay on screen, pinned above the navigation bar.
        compose.onNodeWithTag("paywall_buy").assertIsDisplayed()
        compose.onNodeWithTag("paywall_continue_free").assertIsDisplayed()
        plansInView("plans", "plan_yearly")
    }

    /** Scrolls the plan cards into view and captures them: the first screen shows the hero, not the prices. */
    private fun plansInView(name: String, tag: String) {
        compose.onNodeWithTag(tag).performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/paywall_premium_${name}_scrolled_light.png")
    }

    @Test fun plansAllWithTrial() {
        val offers = listOf(
            PlanOffer("monthly", Plan.PRO, "$12.99", OfferKind.MONTHLY, trialDays = 30),
            PlanOffer("yearly", Plan.PRO, "$99.99", OfferKind.YEARLY),
            PlanOffer("lifetime", Plan.PRO, "$199.99", OfferKind.LIFETIME)
        )
        val vm = PaywallViewModel(app, FakeBillingGateway(offers))
        compose.paywallShot("plans_trial", waitForTag = "plan_monthly") {
            PaywallScreen(PaywallTrigger.FIRST_RESULT, onClose = {}, vm = vm)
        }
        // The monthly plan with its trial may sit behind "View all plans".
        if (compose.onAllNodesWithTag("paywall_view_all").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("plan_monthly").fetchSemanticsNodes().isEmpty()
        ) compose.onNodeWithTag("paywall_view_all").performScrollTo().performClick()
        compose.waitForIdle()
        plansInView("plans_trial", "plan_monthly")
    }

    @Test fun unavailable() {
        val vm = PaywallViewModel(app, FakeBillingGateway(configured = false))
        compose.paywallShot("unavailable", waitForTag = "paywall_unavailable") {
            PaywallScreen(PaywallTrigger.FIRST_RESULT, onClose = {}, vm = vm)
        }
    }

    @Test fun upgradeDialog() = compose.paywallShot("upgrade_dialog", waitForTag = "upgrade_see_plans", dialog = true) {
        Box(Modifier.fillMaxSize().background(MM.colors.background)) {
            UpgradeDialog(
                title = "Autopilot drafts your follow-ups",
                body = "It's part of a plan. Everything you have now stays free.",
                onSeePlans = {}, onNotNow = {}
            )
        }
    }
}
