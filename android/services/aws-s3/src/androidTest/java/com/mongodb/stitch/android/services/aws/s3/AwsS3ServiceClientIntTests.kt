package com.mongodb.stitch.android.services.aws.s3

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.AwsS3Actions
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.internal.common.IoUtils
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
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class AwsS3ServiceClientIntTests : BaseStitchAndroidIntTest() {

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
        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        val awsS3 = client.getServiceClient(AwsS3ServiceClient.factory, "awss31")

        // Putting to an bad bucket should fail
        val bucket = "notmystuff"
        val key = ObjectId().toHexString()
        val acl = "public-read"
        val contentType = "plain/text"
        val body = "hello again friend; did you miss me"

        try {
            Tasks.await(awsS3.putObject(bucket, key, acl, contentType, body))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.AWS_ERROR, svcEx.errorCode)
        }

        // Putting with all good params for S3 should work
        val bucketGood = "stitch-test-sdkfiles"
        val transport = OkHttpTransport()

        var result = Tasks.await(awsS3.putObject(bucketGood, key, acl, contentType, body))
        var expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        var httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, IoUtils.readAllToString(httpResult.body))

        val bodyBin = Binary(body.toByteArray(StandardCharsets.UTF_8))
        result = Tasks.await(awsS3.putObject(bucketGood, key, acl, contentType, bodyBin))
        expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, IoUtils.readAllToString(httpResult.body))

        result = Tasks.await(awsS3.putObject(bucketGood, key, acl, contentType, bodyBin.data))
        expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, IoUtils.readAllToString(httpResult.body))

        val bodyInput = ByteArrayInputStream(bodyBin.data)
        result = Tasks.await(awsS3.putObject(bucketGood, key, acl, contentType, bodyInput))
        expectedLocation = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertEquals(expectedLocation, result.location)

        httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(expectedLocation)
                .withTimeout(60000L)
                .build())
        assertEquals(body, IoUtils.readAllToString(httpResult.body))

        // Excluding any required parameters should fail
        try {
            Tasks.await(awsS3.putObject("", key, acl, contentType, body))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, svcEx.errorCode)
        }
    }

    @Test
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
        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        val awsS3 = client.getServiceClient(AwsS3ServiceClient.factory, "awss31")

        val bucket = "notmystuff"
        val key = ObjectId().toHexString()
        val acl = "public-read"
        val contentType = "plain/text"

        val result = Tasks.await(awsS3.signPolicy(bucket, key, acl, contentType))
        assertTrue(result.policy.isNotEmpty())
        assertTrue(result.signature.isNotEmpty())
        assertTrue(result.algorithm.isNotEmpty())
        assertTrue(result.date.isNotEmpty())
        assertTrue(result.credential.isNotEmpty())

        // Excluding any required parameters should fail
        try {
            Tasks.await(awsS3.signPolicy("", key, acl, contentType))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, svcEx.errorCode)
        }
    }
}
