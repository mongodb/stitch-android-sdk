package com.mongodb.stitch.server.services.fcm

import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.FcmActions
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.fcm.FcmSendMessageNotification
import com.mongodb.stitch.core.services.fcm.FcmSendMessagePriority
import com.mongodb.stitch.core.services.fcm.FcmSendMessageRequest
import org.bson.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class FcmServiceClientIntTests : BaseStitchServerIntTest() {

    private val fcmSenderIdProp = "test.stitch.fcmSenderId"
    private val fcmApiKeyProp = "test.stitch.fcmApiKey"

    private fun getFcmSenderId(): String {
        return System.getProperty(fcmSenderIdProp, "")
    }

    private fun getFcmApiKey(): String {
        return System.getProperty(fcmApiKeyProp, "")
    }

    @Before
    override fun setup() {
        assumeTrue("no FCM sender id in properties; skipping test", getFcmSenderId().isNotEmpty())
        assumeTrue("no FCM API key in properties; skipping test", getFcmApiKey().isNotEmpty())
        super.setup()
    }

    @Test
    fun testSendMessage() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "gcm",
                "gcm",
                ServiceConfigs.Fcm(getFcmSenderId(), getFcmApiKey()))
        addRule(svc.second, RuleCreator.Fcm("default", setOf(FcmActions.Send)))

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())

        val fcm = client.getServiceClient(FcmServiceClient.factory, "gcm")

        val collapseKey = "one"
        val contentAvaialble = true
        val data = Document("hello", "world")
        val mutableContent = true

        val badge = "myBadge"
        val body = "hellllo"
        val bodyLocArgs = "woo"
        val bodyLocKey = "hoo"
        val clickAction = "how"
        val color = "are"
        val icon = "you"
        val sound = "doing"
        val tag = "today"
        val title = "my"
        val titleLocArgs = "good"
        val titleLocKey = "friend"
        val notification = FcmSendMessageNotification.Builder()
                .withBadge(badge)
                .withBody(body)
                .withBodyLocArgs(bodyLocArgs)
                .withBodyLocKey(bodyLocKey)
                .withClickAction(clickAction)
                .withColor(color)
                .withIcon(icon)
                .withSound(sound)
                .withTag(tag)
                .withTitle(title)
                .withTitleLocArgs(titleLocArgs)
                .withTitleLocKey(titleLocKey)
                .build()

        val priority = FcmSendMessagePriority.HIGH
        val timeToLive = 2419200L

        val fullRequest = FcmSendMessageRequest.Builder()
                .withCollapseKey(collapseKey)
                .withContentAvailable(contentAvaialble)
                .withData(data)
                .withMutableContent(mutableContent)
                .withNotification(notification)
                .withPriority(priority)
                .withTimeToLive(timeToLive)
                .build()

        // Sending to a invalid registration should fail
        val to = "who"
        var result = fcm.sendMessageTo(to, fullRequest)
        assertEquals(0, result.successes)
        assertEquals(1, result.failures)
        assertEquals(1, result.failureDetails.size)
        assertEquals(0, result.failureDetails[0].index)
        assertEquals("InvalidRegistration", result.failureDetails[0].error)
        assertNull(result.failureDetails[0].userId)

        // Sending to a topic should work
        val topic = "/topics/what"
        result = fcm.sendMessageTo(topic, fullRequest)
        assertEquals(1, result.successes)
        assertEquals(0, result.failures)
        assertEquals(0, result.failureDetails.size)

        result = fcm.sendMessageToRegistrationTokens(listOf("one", "two"), fullRequest)
        assertEquals(0, result.successes)
        assertEquals(2, result.failures)
        assertEquals(2, result.failureDetails.size)
        assertEquals(0, result.failureDetails[0].index)
        assertEquals("InvalidRegistration", result.failureDetails[0].error)
        assertNull(result.failureDetails[0].userId)
        assertEquals(1, result.failureDetails[1].index)
        assertEquals("InvalidRegistration", result.failureDetails[1].error)
        assertNull(result.failureDetails[1].userId)

        // Any invalid parameters should fail
        val badRequest = FcmSendMessageRequest.Builder()
                .withTimeToLive(100000000000000L)
                .build()
        try {
            fcm.sendMessageTo(to, badRequest)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, ex.errorCode)
        }
    }
}
