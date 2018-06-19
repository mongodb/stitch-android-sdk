package com.mongodb.stitch.android.services.twilio

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.TwilioActions
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class TwilioServiceClientIntTests : BaseStitchAndroidIntTest() {

    private val twilioSidProp = "test.stitch.twilioSid"
    private val twilioAuthTokenProp = "test.stitch.twilioAuthToken"

    private fun getTwilioSid(): String {
        return InstrumentationRegistry.getArguments().getString(twilioSidProp, "")
    }

    private fun getTwilioAuthToken(): String {
        return InstrumentationRegistry.getArguments().getString(twilioAuthTokenProp, "")
    }

    @Before
    override fun setup() {
        assumeTrue("no Twilio Sid in properties; skipping test", getTwilioSid().isNotEmpty())
        assumeTrue("no Twilio Auth Token in properties; skipping test", getTwilioAuthToken().isNotEmpty())
        super.setup()
    }

    @Test
    fun testSendMessage() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "twilio",
                "twilio1",
                ServiceConfigs.Twilio(getTwilioSid(), getTwilioAuthToken()))
        addRule(svc.second, RuleCreator.Twilio("default", setOf(TwilioActions.Send)))

        val client = getAppClient(app.first)
        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        val twilio = client.getServiceClient(TwilioServiceClient.factory, "twilio1")

        // Sending a random message to an invalid number should fail
        val to = "+15005550010"
        val from = "+15005550001"
        val body = "I've got it!"
        val mediaUrl = "https://jpegs.com/myjpeg.gif.png"

        try {
            Tasks.await(twilio.sendMessage(to, from, body))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.TWILIO_ERROR, svcEx.errorCode)
        }

        try {
            Tasks.await(twilio.sendMessage(to, from, body, mediaUrl))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.TWILIO_ERROR, svcEx.errorCode)
        }

        // Sending with all good params for Twilio should work
        val fromGood = "+15005550006"

        Tasks.await(twilio.sendMessage(to, fromGood, body))
        Tasks.await(twilio.sendMessage(to, fromGood, mediaUrl))

        // Excluding any required parameters should fail
        try {
            Tasks.await(twilio.sendMessage(to, "", body, mediaUrl))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, svcEx.errorCode)
        }
    }
}
