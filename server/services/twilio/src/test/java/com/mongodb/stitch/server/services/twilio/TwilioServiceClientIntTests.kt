package com.mongodb.stitch.server.services.twilio

import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.TwilioActions
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class TwilioServiceClientIntTests : BaseStitchServerIntTest() {

    private val twilioSidProp = "test.stitch.twilioSid"
    private val twilioAuthTokenProp = "test.stitch.twilioAuthToken"

    private fun getTwilioSid(): String {
        return System.getProperty(twilioSidProp, "")
    }

    private fun getTwilioAuthToken(): String {
        return System.getProperty(twilioAuthTokenProp, "")
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
        client.auth.loginWithCredential(AnonymousCredential())

        val twilio = client.getServiceClient(TwilioServiceClient.Factory, "twilio1")

        // Sending a random message to an invalid number should fail
        val to = "+15005550010"
        val from = "+15005550001"
        val body = "I've got it!"
        val mediaUrl = "https://jpegs.com/myjpeg.gif.png"

        try {
            twilio.sendMessage(to, from, body)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.TWILIO_ERROR, ex.errorCode)
        }

        try {
            twilio.sendMessage(to, from, body, mediaUrl)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.TWILIO_ERROR, ex.errorCode)
        }

        // Sending with all good params for Twilio should work
        val fromGood = "+15005550006"

        twilio.sendMessage(to, fromGood, body)
        twilio.sendMessage(to, fromGood, mediaUrl)

        // Excluding any required parameters should fail
        try {
            twilio.sendMessage(to, "", body, mediaUrl)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, ex.errorCode)
        }
    }
}
