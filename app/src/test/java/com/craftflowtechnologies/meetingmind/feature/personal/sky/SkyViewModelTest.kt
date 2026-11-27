package com.craftflowtechnologies.meetingmind.feature.personal.sky

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.billing.TieringMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * The sky's view model with fakes for the reflections, the model and the access rule. The paid read
 * is refused without access and allowed in closed testing; no billing or model is touched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkyViewModelTest {

    private val zone = ZoneId.of("UTC")
    private val november = YearMonth.of(2026, 11)
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun at(day: Int, hour: Int): Long = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val entries = listOf(
        ReflectionEntry("a", "Inbox was loud", at(3, 19)),
        ReflectionEntry("b", "Cleared the inbox", at(4, 19)),
        ReflectionEntry("c", "Mornings were calm", at(5, 8)),
        ReflectionEntry("d", "Mornings again", at(6, 8))
    )

    private class FakeReader : SkyReader {
        var reads = 0
        var lastEntries: List<String> = emptyList()

        override suspend fun route(): SkyRoute = SkyRoute.ON_DEVICE

        override suspend fun read(name: String, entries: List<String>): SkyReadOutcome {
            reads++
            lastEntries = entries
            return SkyReadOutcome.Written("You kept returning to $name.")
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(reader: SkyReader, mode: TieringMode, canDeepSynthesis: Boolean = false) = SkyViewModel(
        app,
        source = { flowOf(entries) },
        reader = reader,
        access = SkyAccess { SkyAccess.allows(canDeepSynthesis, mode) },
        zone = zone,
        initialMonth = november
    )

    @Test fun `without access the paid read is refused and no model is called`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeReader()
        val vm = viewModel(reader, TieringMode.PRODUCTION)

        assertFalse(vm.requestZuri())

        assertEquals(0, reader.reads)
        assertFalse(vm.state.value.zuriAllowed)
        assertEquals(ZuriPhase.IDLE, vm.state.value.zuri.phase)
    }

    @Test fun `in closed testing the paid read is allowed and writes the read`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeReader()
        val vm = viewModel(reader, TieringMode.CLOSED_TESTING)

        assertTrue(vm.state.value.zuriAllowed)
        assertTrue(vm.requestZuri())

        assertEquals(1, reader.reads)
        val zuri = vm.state.value.zuri
        assertEquals(ZuriPhase.WRITTEN, zuri.phase)
        assertEquals("You kept returning to Mornings.", zuri.text)
    }

    @Test fun `with no on-device model the read asks for Setup instead of sending anything`() = runTest(UnconfinedTestDispatcher()) {
        val reader = object : SkyReader {
            var reads = 0
            override suspend fun route() = SkyRoute.NONE
            override suspend fun read(name: String, entries: List<String>): SkyReadOutcome {
                reads++
                return SkyReadOutcome.NoModel
            }
        }
        val vm = viewModel(reader, TieringMode.CLOSED_TESTING)

        assertTrue(vm.requestZuri())

        assertEquals(1, reader.reads)
        assertEquals(SkyRoute.NONE, vm.state.value.route)
        assertEquals(ZuriPhase.NEEDS_SETUP, vm.state.value.zuri.phase)
    }

    @Test fun `a paid entitlement allows the read without closed testing`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeReader()
        val vm = viewModel(reader, TieringMode.PRODUCTION, canDeepSynthesis = true)
        assertTrue(vm.requestZuri())
        assertEquals(1, reader.reads)
    }

    @Test fun `only the selected constellation's reflections are sent, newest first`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeReader()
        val vm = viewModel(reader, TieringMode.CLOSED_TESTING)

        // Both constellations have two reflections; Mornings is the more recent, so it is selected first.
        assertEquals("Mornings", vm.state.value.selected?.name)
        vm.requestZuri()
        assertEquals(listOf("Mornings again", "Mornings were calm"), reader.lastEntries)
    }

    @Test fun `choosing a constellation shows its pattern and its own read`() = runTest(UnconfinedTestDispatcher()) {
        val reader = FakeReader()
        val vm = viewModel(reader, TieringMode.CLOSED_TESTING)

        vm.select("Inbox")

        val state = vm.state.value
        assertEquals("Inbox", state.selected?.name)
        assertEquals("You've mentioned inbox in 2 reflections since 3 November.", state.patternLines.first())
        assertEquals(ZuriPhase.IDLE, state.zuri.phase)
    }

    @Test fun `moving to a month with no reflections shows no constellations`() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(FakeReader(), TieringMode.CLOSED_TESTING)

        vm.nextMonth()

        val state = vm.state.value
        assertEquals(YearMonth.of(2026, 12), state.month)
        assertTrue(state.entries.isEmpty())
        assertTrue(state.constellations.isEmpty())
        assertEquals(null, state.selected)
    }
}
