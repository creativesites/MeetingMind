package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive

import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRendererSelectorTest {
    private val ready = RendererInputs(sizeDp = 96f, durationScale = 1f, assetBundled = true, hasBudgetSlot = true)

    private fun reason(i: RendererInputs) = CompanionRendererSelector.select(i).reason

    @Test fun `rive when every condition holds`() {
        assertEquals(RendererChoice(CompanionRenderer.RIVE, RendererReason.RIVE_READY), CompanionRendererSelector.select(ready))
        assertEquals(CompanionRenderer.RIVE, CompanionRendererSelector.select(ready.copy(sizeDp = 48f)).renderer)
        assertEquals(CompanionRenderer.RIVE, CompanionRendererSelector.select(ready.copy(durationScale = 0.5f)).renderer)
    }

    @Test fun `canvas for each failed condition`() {
        assertEquals(RendererReason.TOO_SMALL, reason(ready.copy(sizeDp = 47.9f)))
        assertEquals(RendererReason.TOO_SMALL, reason(ready.copy(sizeDp = 24f)))
        assertEquals(RendererReason.REDUCED_MOTION, reason(ready.copy(durationScale = 0f)))
        assertEquals(RendererReason.NO_ASSET, reason(ready.copy(assetBundled = false)))
        assertEquals(RendererReason.OVER_BUDGET, reason(ready.copy(hasBudgetSlot = false)))
        assertEquals(RendererReason.DISABLED, reason(ready.copy(riveEnabled = false)))
        assertEquals(RendererReason.FORCED_CANVAS, reason(ready.copy(forceCanvas = true)))
        assertEquals(RendererReason.RIVE_FAILED, reason(ready.copy(riveFailed = true)))
    }

    @Test fun `wanting rive ignores only the budget`() {
        assertTrue(CompanionRendererSelector.wantsRive(ready.copy(hasBudgetSlot = false)))
        assertFalse(CompanionRendererSelector.wantsRive(ready.copy(assetBundled = false)))
        assertFalse(CompanionRendererSelector.wantsRive(ready.copy(sizeDp = 24f)))
    }

    @Test fun `the budget allows two live instances by default`() {
        val b = CompanionRiveBudget()
        assertEquals(2, b.max)
        assertTrue(b.tryAcquire()); assertTrue(b.tryAcquire()); assertFalse(b.tryAcquire())
        assertEquals(2, b.live)
        b.release()
        assertTrue(b.tryAcquire())
        b.release(); b.release(); b.release()
        assertEquals(0, b.live)
    }

    @Test fun `input values`() {
        assertEquals(0f, CompanionRiveContract.stateValue(CompanionState.IDLE))
        assertEquals(3f, CompanionRiveContract.stateValue(CompanionState.CELEBRATING))
        assertEquals(8f, CompanionRiveContract.stateValue(CompanionState.READING))
        assertEquals(0f, CompanionRiveContract.modeValue(CompanionState.WORRIED))
        assertEquals(1f, CompanionRiveContract.modeValue(CreateMode.PRAYERFUL))
        assertEquals(6f, CompanionRiveContract.modeValue(CreateMode.CELEBRATORY))
        assertEquals(0f, CompanionRiveContract.stateValue(CreateMode.JOYFUL))
        assertTrue(CompanionRiveContract.calmValue(CompanionVariant.Quiet))
        assertFalse(CompanionRiveContract.calmValue(CompanionVariant.Nod))
        assertEquals(55f, CompanionRiveContract.levelValue(0.55f), 1e-4f)
        assertEquals(100f, CompanionRiveContract.levelValue(3f), 0f)
    }
}
