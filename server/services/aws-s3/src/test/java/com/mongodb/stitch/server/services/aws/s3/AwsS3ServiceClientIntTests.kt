package com.mongodb.stitch.server.services.aws.s3

import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.AwsS3Actions
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.internal.common.IoUtils.readAllToString
import com.mongodb.stitch.core.internal.net.Method
import com.mongodb.stitch.core.internal.net.OkHttpTransport
import com.mongodb.stitch.core.internal.net.Request
import org.bson.types.Binary
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class AwsS3ServiceClientIntTests : BaseStitchServerIntTest() {

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
    @Ignore
    fun testPutObject() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "aws-s3",
                "awss31",
                ServiceConfigs.AwsS3("us-east-1", getAwsAccessKeyId(), getAwsSecretAccessKey()))
        addRule(svc.second, RuleCreator.AwsS3(
                "default",
                setOf(AwsS3Actions.Put)))

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())

        val awsS3 = client.getServiceClient(AwsS3ServiceClient.factory, "awss31")

        // Putting to an bad bucket should fail
        val bucket = "notmystuff"
        val key = ObjectId().toHexString()
        val acl = "public-read"
        val contentType = "plain/text"
        val body = "hello again friend; did you miss me"

        try {
            awsS3.putObject(bucket, key, acl, contentType, body)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.AWS_ERROR, ex.errorCode)
        }

        // Putting with all good params for S3 should work
        val bucketGood = "stitch-test-sdkfiles"
        val transport = OkHttpTransport()

        var result = awsS3.putObject(bucketGood, key, acl, contentType, body)
        var expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        var httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, readAllToString(httpResult.body))

        val bodyBin = Binary(body.toByteArray(StandardCharsets.UTF_8))
        result = awsS3.putObject(bucketGood, key, acl, contentType, bodyBin)
        expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, readAllToString(httpResult.body))

        result = awsS3.putObject(bucketGood, key, acl, contentType, bodyBin.data)
        expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, readAllToString(httpResult.body))

        val bodyInput = ByteArrayInputStream(bodyBin.data)
        result = awsS3.putObject(bucketGood, key, acl, contentType, bodyInput)
        expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, readAllToString(httpResult.body))

        // Excluding any required parameters should fail
        try {
            awsS3.putObject("", key, acl, contentType, body)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, ex.errorCode)
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    fun testSignPolicy() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "aws-s3",
                "awss31",
                ServiceConfigs.AwsS3("us-east-1", getAwsAccessKeyId(), getAwsSecretAccessKey()))
        addRule(svc.second, RuleCreator.AwsS3(
                "default",
                setOf(AwsS3Actions.SignPolicy)))

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())

        val awsS3 = client.getServiceClient(AwsS3ServiceClient.factory, "awss31")

        val bucket = "notmystuff"
        val key = ObjectId().toHexString()
        val acl = "public-read"
        val contentType = "plain/text"

        val result = awsS3.signPolicy(bucket, key, acl, contentType)
        assertTrue(result.policy.isNotEmpty())
        assertTrue(result.signature.isNotEmpty())
        assertTrue(result.algorithm.isNotEmpty())
        assertTrue(result.date.isNotEmpty())
        assertTrue(result.credential.isNotEmpty())

        // Excluding any required parameters should fail
        try {
            awsS3.signPolicy("", key, acl, contentType)
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, ex.errorCode)
        }
    }
}
