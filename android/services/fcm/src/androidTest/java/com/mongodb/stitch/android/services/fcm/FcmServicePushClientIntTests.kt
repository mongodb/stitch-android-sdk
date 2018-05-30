package com.mongodb.stitch.android.services.fcm

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.FcmActions
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FcmServicePushClientIntTests : BaseStitchAndroidIntTest() {

    private val fcmSenderIdProp = "test.stitch.fcmSenderId"
    private val fcmApiKeyProp = "test.stitch.fcmApiKey"

    private fun getFcmSenderId(): String {
        return InstrumentationRegistry.getArguments().getString(fcmSenderIdProp, "")
    }

    private fun getFcmApiKey(): String {
        return InstrumentationRegistry.getArguments().getString(fcmApiKeyProp, "")
    }

    @Before
    override fun setup() {
        assumeTrue("no FCM sender id in properties; skipping test", getFcmSenderId().isNotEmpty())
        assumeTrue("no FCM API key in properties; skipping test", getFcmApiKey().isNotEmpty())
        super.setup()
    }

    @Test
    fun testRegister() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "gcm",
                "gcm",
                ServiceConfigs.Fcm(getFcmSenderId(), getFcmApiKey()))
        addRule(svc.second, RuleCreator.Fcm("default", setOf(FcmActions.Send)))

        val client = getAppClient(app.first)
        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        val fcm = client.push.getClient(FcmServicePushClient.Factory, "gcm")

        // Can register and deregister multiple times.
        Tasks.await(fcm.register("hello"))
        Tasks.await(fcm.register("hello"))
        Tasks.await(fcm.deregister())
        Tasks.await(fcm.deregister())
    }
}
