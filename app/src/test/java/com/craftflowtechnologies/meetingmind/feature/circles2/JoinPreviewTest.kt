package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.CirclesApi
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult
import com.craftflowtechnologies.meetingmind.core.circles2.FailureKind
import com.craftflowtechnologies.meetingmind.core.circles2.FakeApi
import com.craftflowtechnologies.meetingmind.core.circles2.FakeAuth
import com.craftflowtechnologies.meetingmind.core.circles2.FakeData
import com.craftflowtechnologies.meetingmind.core.circles2.InMemoryLocalCircleStore
import com.craftflowtechnologies.meetingmind.core.circles2.InvitePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The join preview: debounced lookup, friendly errors, and that a failed preview never blocks Join. */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinPreviewTest {
    @After fun tearDown() = Dispatchers.resetMain()

    private val preview = InvitePreview("c1", "Client delivery", "work", "client_engagement", 4, null, "Ruth")

    /** Answers previews with [reply] and counts the lookups. Everything else comes from the shared fake. */
    private class PreviewApi(var reply: CirclesResult<InvitePreview>) : CirclesApi by FakeApi() {
        var calls = 0
        override suspend fun previewInvite(code: String): CirclesResult<InvitePreview> {
            calls++
            return reply
        }
    }

    private fun repo(api: CirclesApi) = CirclesRepository(api, FakeData(), FakeAuth(), InMemoryLocalCircleStore(), clock = { 1L })

    @Test fun `a pasted code shows the circle once the typing pauses`() {
        val d = StandardTestDispatcher()
        Dispatchers.setMain(d)
        runTest(d) {
            val api = PreviewApi(CirclesResult.Ok(preview))
            val vm = CirclesListViewModel(repo(api))
            vm.onPaste("GRACE-7K2Q")
            assertNull(vm.join.value.preview)
            advanceTimeBy(PREVIEW_DEBOUNCE_MS - 1)
            runCurrent()
            assertEquals(0, api.calls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, api.calls)
            assertEquals("Client delivery", vm.join.value.preview?.name)
            assertNull(vm.join.value.previewError)
        }
    }

    @Test fun `text with no code never asks the Worker, and an old lookup is dropped`() {
        val d = StandardTestDispatcher()
        Dispatchers.setMain(d)
        runTest(d) {
            val api = PreviewApi(CirclesResult.Ok(preview))
            val vm = CirclesListViewModel(repo(api))
            vm.onPaste("GRACE-7K2Q")
            advanceTimeBy(100)
            vm.onPaste("nothing here")
            advanceTimeBy(PREVIEW_DEBOUNCE_MS * 3)
            runCurrent()
            assertEquals(0, api.calls)
            assertNull(vm.join.value.preview)
        }
    }

    @Test fun `an expired code gets a friendly line, and Join is still possible`() {
        val d = StandardTestDispatcher()
        Dispatchers.setMain(d)
        runTest(d) {
            val api = PreviewApi(CirclesResult.Err(CirclesFailure(FailureKind.Rejected, "expired", "expired", null, 410)))
            val vm = CirclesListViewModel(repo(api))
            vm.onPaste("GRACE-7K2Q")
            advanceTimeBy(PREVIEW_DEBOUNCE_MS)
            runCurrent()
            assertNull(vm.join.value.preview)
            assertEquals("That code has expired. Ask for a new one.", vm.join.value.previewError)
            assertTrue(vm.join.value.canJoin)
        }
    }

    @Test fun `an offline preview says nothing and does not block Join`() {
        val d = StandardTestDispatcher()
        Dispatchers.setMain(d)
        runTest(d) {
            val api = PreviewApi(CirclesResult.Err(CirclesFailure(FailureKind.Offline, "offline")))
            val vm = CirclesListViewModel(repo(api))
            vm.onPaste("GRACE-7K2Q")
            advanceTimeBy(PREVIEW_DEBOUNCE_MS)
            runCurrent()
            assertNull(vm.join.value.previewError)
            assertTrue(vm.join.value.canJoin)
        }
    }

    @Test fun `each refusal from the Worker has its own plain line`() {
        assertEquals("We couldn't find a circle for that code. Check it and try again.", previewErrorLine(CirclesFailure(FailureKind.NotFound, "x", "not_found")))
        assertEquals("That code was turned off. Ask for a new one.", previewErrorLine(CirclesFailure(FailureKind.Rejected, "x", "revoked")))
        assertEquals("That code has reached its limit. Ask for a new one.", previewErrorLine(CirclesFailure(FailureKind.Rejected, "x", "full")))
        assertEquals("You're checking codes too fast. Wait a bit and try again.", previewErrorLine(CirclesFailure(FailureKind.RateLimited, "x", "rate_limited")))
        assertNull(previewErrorLine(CirclesFailure(FailureKind.Server, "x", "internal")))
    }

    @Test fun `the card names the space as people read it`() {
        assertEquals("Work", spaceLabel("work"))
        assertEquals("Learning", spaceLabel("learning"))
        assertEquals("General", spaceLabel("general"))
        assertEquals("Faith", spaceLabel("faith"))
    }
}
