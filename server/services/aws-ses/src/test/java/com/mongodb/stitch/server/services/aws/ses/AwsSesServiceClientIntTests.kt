package com.mongodb.stitch.server.services.aws.ses

import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
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

class AwsSesServiceClientIntTests : BaseStitchServerIntTest() {

    private val awsAccessKeyIdProp = "test.stitch.awsAccessKeyId"
    private val awsSecretAccessKeyProp = "test.stitch.awsSecretAccessKey"

    private fun getAwsAccessKeyId(): String {
        return System.getProperty(awsAccessKeyIdProp, "")
    }

    private fun getAwsSecretAccessKey(): String {
        return System.getProperty(awsSecretAccessKeyProp, "")
    }

    @Before
    override fun setup() {
        assumeTrue("no AWS Access Key Id in properties; skipping test", getAwsAccessKeyId().isNotEmpty())
        assumeTrue("no AWS Secret Access Key in properties; skipping test", getAwsSecretAccessKey().isNotEmpty())
        super.setup()
    }

    @Test
    @SuppressWarnings("deprecation")
    // TODO: STITCH-2463
    @Ignore
    fun testSendEmail() {
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
        client.auth.loginWithCredential(AnonymousCredential())

        val awsSes = client.getServiceClient(AwsSesServiceClient.factory, "awsses1")

        // Sending a random email to an invalid email should fail
        val to = "eliot@stitch-dev.10gen.cc"
        val from = "dwight@10gen"
        val subject = "Hello"
        val body = "again friend"

        try {
            awsSes.sendEmail(to, from, subject, body)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.AWS_ERROR, ex.errorCode)
        }

        // Sending with all good params for SES should work
        val fromGood = "dwight@baas-dev.10gen.cc"

        val result = awsSes.sendEmail(to, fromGood, subject, body)
        assertTrue(result.messageId.isNotEmpty())

        // Excluding any required parameters should fail
        try {
            awsSes.sendEmail(to, "", subject, body)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, ex.errorCode)
        }
    }
}
