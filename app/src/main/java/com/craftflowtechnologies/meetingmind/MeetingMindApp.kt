package com.craftflowtechnologies.meetingmind

import android.app.Application
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.DisabledBillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.RevenueCatBillingGateway

/** Sets up billing once per process. With no RevenueCat key the app is simply free: nothing is offered and nothing breaks. */
class MeetingMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val key = BuildConfig.REVENUECAT_API_KEY
        Billing.gateway = if (key.isBlank()) DisabledBillingGateway
        else runCatching { RevenueCatBillingGateway(this, key) as com.craftflowtechnologies.meetingmind.core.billing.BillingGateway }.getOrDefault(DisabledBillingGateway)
    }
}
