package com.craftflowtechnologies.meetingmind.feature.today.home

import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.FailureKind
import com.craftflowtechnologies.meetingmind.core.circles2.FakeApi
import com.craftflowtechnologies.meetingmind.core.circles2.FakeData
import com.craftflowtechnologies.meetingmind.core.circles2.PushRegistrar
import com.craftflowtechnologies.meetingmind.core.circles2.repoWith
import com.craftflowtechnologies.meetingmind.core.circles2.testCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Home Circles tile: each state it can be in, from the repository's answers. No network, no Firebase. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCirclesTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class RecordingPush : PushRegistrar {
        var activated = 0
        override suspend fun activate() { activated++ }
        override suspend fun deactivate() {}
        override suspend fun onAccountChanged() {}
    }

    // ---------- the mapping: what the repository knows, to what the tile shows ----------

    @Test fun `before the first answer the tile is loading, never empty`() {
        assertEquals(HomeCircles.Loading, HomeCircles.from(CirclesListState.Ready(emptyList()), refreshed = false))
        assertEquals(HomeCircles.Loading, HomeCircles.from(CirclesListState.Loading, refreshed = true))
    }

    @Test fun `an answer with no circles is the empty state`() {
        assertEquals(HomeCircles.Empty, HomeCircles.from(CirclesListState.Ready(emptyList()), refreshed = true))
    }

    @Test fun `a failure with nothing loaded is shown as that failure`() {
        val offline = CirclesFailure.offline
        assertEquals(HomeCircles.Failed(offline), HomeCircles.from(CirclesListState.Ready(emptyList(), offline), refreshed = true))
    }

    @Test fun `circles that loaded are the loaded state, and a later failure rides along as a note`() {
        val circles = listOf(testCircle())
        assertEquals(HomeCircles.Loaded(circles), HomeCircles.from(CirclesListState.Ready(circles), refreshed = false))
        val note = CirclesFailure.offline
        assertEquals(HomeCircles.Loaded(circles, note), HomeCircles.from(CirclesListState.Ready(circles, note), refreshed = true))
    }

    @Test fun `a build with no Circles service says so`() {
        assertEquals(HomeCircles.NotConnected, HomeCircles.from(CirclesListState.NotConnected, refreshed = false))
    }

    // ---------- the view model, against the fakes ----------

    @Test fun `the tile goes from loading to empty once the Worker has answered`() = runTest {
        val vm = HomeCirclesViewModel(repoWith(FakeApi(), FakeData()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertEquals(HomeCircles.Empty, vm.state.value)
    }

    @Test fun `a person in a circle sees it on Home`() = runTest {
        val api = FakeApi().apply { myCirclesResult = listOf("c1") }
        val data = FakeData().apply { circle.value = DataState.Ready(testCircle()) }
        val vm = HomeCirclesViewModel(repoWith(api, data))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        val loaded = vm.state.value as HomeCircles.Loaded
        assertEquals(listOf("c1"), loaded.circles.map { it.id })
        assertEquals("Tuesday Night", loaded.circles.single().name)
        assertEquals(null, loaded.note)
    }

    @Test fun `a failed Worker call is one plain line, and retry asks again`() = runTest {
        val api = FakeApi().apply { nextFailure = CirclesFailure.offline }
        val data = FakeData()
        val vm = HomeCirclesViewModel(repoWith(api, data))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertEquals(HomeCircles.Failed(CirclesFailure.offline), vm.state.value)

        api.myCirclesResult = listOf("c1")
        data.circle.value = DataState.Ready(testCircle())
        vm.retry()
        assertTrue(vm.state.value is HomeCircles.Loaded)
    }

    @Test fun `a circle whose listener fails is reported, not left on loading`() = runTest {
        val api = FakeApi().apply { myCirclesResult = listOf("c1") }
        val failure = CirclesFailure(FailureKind.Server, "Something went wrong. Please try again.")
        val data = FakeData().apply { circle.value = DataState.Failed(failure) }
        val vm = HomeCirclesViewModel(repoWith(api, data))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertEquals(HomeCircles.Failed(failure), vm.state.value)
    }

    @Test fun `a circle the person is no longer in is dropped, and the tile is empty again`() = runTest {
        val api = FakeApi().apply { myCirclesResult = listOf("c1") }
        val gone = CirclesFailure(FailureKind.Rejected, "You don't have access to this circle.", code = "permission_denied")
        val data = FakeData().apply { circle.value = DataState.Failed(gone) }
        val vm = HomeCirclesViewModel(repoWith(api, data))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertEquals(HomeCircles.Empty, vm.state.value)
    }

    @Test fun `opening Home does not switch pushes on for someone with no circle`() = runTest {
        val push = RecordingPush()
        val vm = HomeCirclesViewModel(repoWith(FakeApi(), FakeData(), push = push))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertEquals(0, push.activated)
    }

    @Test fun `opening Home for someone already in a circle keeps their pushes on`() = runTest {
        val push = RecordingPush()
        val api = FakeApi().apply { myCirclesResult = listOf("c1") }
        val data = FakeData().apply { circle.value = DataState.Ready(testCircle()) }
        val vm = HomeCirclesViewModel(repoWith(api, data, push = push))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertEquals(1, push.activated)
    }
}
