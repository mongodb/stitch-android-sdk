package com.mongodb.stitch.android.services.aws.ses

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.AwsSesActions
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class AwsSesServiceClientIntTests : BaseStitchAndroidIntTest() {

    private val awsAccessKeyIdProp = "test.stitch.awsAccessKeyId"
    private val awsSecretAccessKeyProp = "test.stitch.awsSecretAccessKey"

    private fun getAwsAccessKeyId(): String {
        return InstrumentationRegistry.getArguments().getString(awsAccessKeyIdProp, "")
    }

    private fun getAwsSecretAccessKey(): String {
        return InstrumentationRegistry.getArguments().getString(awsSecretAccessKeyProp, "")
    }

    @Before
    override fun setup() {
        assumeTrue("no AWS Access Key Id in properties; skipping test", getAwsAccessKeyId().isNotEmpty())
        assumeTrue("no AWS Secret Access Key in properties; skipping test", getAwsSecretAccessKey().isNotEmpty())
        super.setup()
    }

    @Test
    @Ignore
    fun testSendMessage() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "aws-ses",
                "awsses1",
                ServiceConfigs.AwsSes("us-east-1", getAwsAccessKeyId(), getAwsSecretAccessKey()))
        addRule(svc.second, RuleCreator.AwsSes(
                "default",
                setOf(AwsSesActions.Send)))

        val client = getAppClient(app.first)
        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        val awsSes = client.getServiceClient(AwsSesServiceClient.factory, "awsses1")

        // Sending a random email to an invalid email should fail
        val to = "eliot@stitch-dev.10gen.cc"
        val from = "dwight@10gen"
        val subject = "Hello"
        val body = "again friend"

        try {
            Tasks.await(awsSes.sendEmail(to, from, subject, body))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.AWS_ERROR, svcEx.errorCode)
        }

        // Sending with all good params for SES should work
        val fromGood = "dwight@baas-dev.10gen.cc"

        val result = Tasks.await(awsSes.sendEmail(to, fromGood, subject, body))
        assertTrue(result.messageId.isNotEmpty())

        // Excluding any required parameters should fail
        try {
            Tasks.await(awsSes.sendEmail(to, "", subject, body))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, svcEx.errorCode)
        }
    }
}
