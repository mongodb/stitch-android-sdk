package com.mongodb.stitch.server.services.aws

import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.internal.common.IoUtils.readAllToString
import com.mongodb.stitch.core.internal.net.Method
import com.mongodb.stitch.core.internal.net.OkHttpTransport
import com.mongodb.stitch.core.internal.net.Request
import com.mongodb.stitch.core.services.aws.AwsRequest
import org.bson.Document
import org.bson.types.Binary
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class AwsServiceClientIntTests : BaseStitchServerIntTest() {

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
    fun testPutObject() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "aws",
                "aws1",
                ServiceConfigs.Aws(getAwsAccessKeyId(), getAwsSecretAccessKey()))
        addRule(svc.second, RuleCreator.Aws(
                "default",
                setOf("s3:PutObject")))

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())

        val awsS3 = client.getServiceClient(AwsServiceClient.factory, "aws1")

        // Putting to an bad bucket should fail
        val bucket = "notmystuff"
        val key = ObjectId().toHexString()
        val acl = "public-read"
        val contentType = "plain/text"
        val body = "hello again friend; did you miss me"
        val args = Document()
        args["Bucket"] = bucket
        args["Key"] = key
        args["ACL"] = acl
        args["ContentType"] = contentType
        args["Body"] = body

        try {
            awsS3.execute(AwsRequest.Builder()
                    .withService("s3")
                    .withAction("PutObject")
                    .withArguments(args)
                    .build())
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.AWS_ERROR, ex.errorCode)
        }

        // Putting with all good params for S3 should work
        val bucketGood = "stitch-test-sdkfiles"
        val transport = OkHttpTransport()

        args["Bucket"] = bucketGood
        var result = awsS3.execute(AwsRequest.Builder()
                .withService("s3")
                .withAction("PutObject")
                .withArguments(args)
                .build(),
                Document::class.java)
        var location = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"
        assertTrue(result.containsKey("ETag"))

        var httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(location)
                .withTimeout(1500L)
                .build())
        assertEquals(body, readAllToString(httpResult.body))

        val bodyBin = Binary(body.toByteArray(StandardCharsets.UTF_8))
        args["Body"] = bodyBin
        awsS3.execute(AwsRequest.Builder()
                .withService("s3")
                .withAction("PutObject")
                .withArguments(args)
                .build(),
                Document::class.java)
        assertTrue(result.containsKey("ETag"))
        location = "https://stitch-test-sdkfiles.s3.amazonaws.com/$key"

        httpResult = transport.roundTrip(Request.Builder()
                .withMethod(Method.GET)
                .withUrl(location)
                .withTimeout(1500L)
                .build())
        assertEquals(body, readAllToString(httpResult.body))
    }
}
