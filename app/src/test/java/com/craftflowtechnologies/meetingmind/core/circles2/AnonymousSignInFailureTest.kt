package com.craftflowtechnologies.meetingmind.core.circles2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnonymousSignInFailureTest {
    @Test
    fun aDisabledProviderSaysSoInsteadOfSignedOut() {
        // Both spellings of the Firebase code: the Android SDK sends the ERROR_ form, the docs use the dashed form.
        for (code in listOf("ERROR_OPERATION_NOT_ALLOWED", "operation-not-allowed", "ERROR_ADMIN_RESTRICTED_OPERATION")) {
            val f = anonymousSignInFailure(code)
            assertEquals(FailureKind.SignedOut, f.kind)
            assertEquals("Circles isn't set up to open accounts yet. Please try again later.", f.message)
            assertEquals(code, f.code)
        }
    }

    @Test
    fun aNetworkErrorIsOffline() {
        assertEquals(FailureKind.Offline, anonymousSignInFailure("ERROR_NETWORK_REQUEST_FAILED").kind)
    }

    @Test
    fun anUnknownCodeKeepsTheGenericMessageAndTheCodeForDiagnosis() {
        val f = anonymousSignInFailure("ERROR_SOMETHING_NEW")
        assertEquals(CirclesFailure.signedOut.message, f.message)
        assertEquals("ERROR_SOMETHING_NEW", f.code)
        assertNotEquals("Circles isn't set up to open accounts yet. Please try again later.", f.message)
    }

    @Test
    fun aMissingCodeIsGenericAndCarriesNoCode() {
        val f = anonymousSignInFailure(null)
        assertEquals(CirclesFailure.signedOut.message, f.message)
        assertEquals(null, f.code)
    }
}
