package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.circles2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

/** "Keep your circles on any phone": link, conflict -> switch -> myCircles. All fakes. */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AccountLinkingTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `an anonymous account starts unlinked and linking keeps the circles`() = runTest {
        val auth = FakeAuth()
        val vm = AccountViewModel(repoWith(auth = auth))
        assertFalse(vm.state.value.linked)
        vm.link(context)
        assertTrue(vm.state.value.linked)
        assertEquals("me@example.com", vm.state.value.email)
        assertEquals(listOf("link"), auth.authCalls)
    }

    @Test fun `an account that is already Google starts linked`() = runTest {
        val auth = FakeAuth(anonymous = false).apply { linkedEmail = "ann@example.com" }
        assertTrue(AccountViewModel(repoWith(auth = auth)).state.value.linked)
    }

    @Test fun `credential already in use offers a switch, and switching asks the Worker for that account's circles`() = runTest {
        val auth = FakeAuth().apply { linkResult = LinkResult.AlreadyInUse("ann@example.com") }
        val api = FakeApi().apply { myCirclesResult = listOf("c7") }
        val store = InMemoryLocalCircleStore().apply { addCircle("guest-circle") }
        val vm = AccountViewModel(repoWith(api, auth = auth, store = store))
        vm.link(context)
        assertTrue(vm.state.value.conflict)
        assertEquals("ann@example.com", vm.state.value.conflictEmail)
        assertFalse(vm.state.value.linked)
        assertTrue("nothing switched yet", "myCircles" !in api.calls)

        vm.switchAccount()
        assertEquals(listOf("link", "switch"), auth.authCalls)
        assertTrue("myCircles" in api.calls)
        assertEquals(listOf("c7"), store.circleIds())          // this phone's guest list is replaced by the account's
        assertTrue(vm.state.value.linked)
        assertFalse(vm.state.value.conflict)
    }

    @Test fun `declining the switch changes nothing`() = runTest {
        val auth = FakeAuth().apply { linkResult = LinkResult.AlreadyInUse("ann@example.com") }
        val vm = AccountViewModel(repoWith(auth = auth))
        vm.link(context); vm.dismissConflict()
        assertFalse(vm.state.value.conflict)
        assertEquals(listOf("link"), auth.authCalls)
    }

    @Test fun `cancelled, unavailable and failed attempts end quietly with one plain line`() = runTest {
        val auth = FakeAuth()
        val vm = AccountViewModel(repoWith(auth = auth))
        auth.linkResult = LinkResult.Cancelled; vm.link(context)
        assertNull(vm.state.value.message); assertFalse(vm.state.value.busy)
        auth.linkResult = LinkResult.Unavailable("Google sign-in isn't set up in this version of the app yet."); vm.link(context)
        assertEquals("Google sign-in isn't set up in this version of the app yet.", vm.state.value.message)
        auth.linkResult = LinkResult.Failed("Couldn't link your Google account. Check your connection and try again."); vm.link(context)
        assertTrue(vm.state.value.message!!.startsWith("Couldn't link"))
        assertFalse(vm.state.value.linked)
    }
}
