package com.mongodb.stitch.android.services.http

import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.HttpActions
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.http.HttpMethod
import com.mongodb.stitch.core.services.http.HttpRequest
import org.bson.Document
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Arrays
import java.util.HashMap
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class HttpServiceClientIntTests : BaseStitchAndroidIntTest() {

    @Test
    fun testExecute() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "http",
                "http1",
                ServiceConfigs.Http)
        addRule(svc.second, RuleCreator.Http(
                "default",
                setOf(HttpActions.Delete)))

        val client = getAppClient(app.first)
        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        val httpClient = client.getServiceClient(HttpServiceClient.factory, "http1")

        // Specifying a request with form and body should fail
        var badUrl = "http:/aol.com"
        val method = HttpMethod.DELETE
        val body = "hello world!".toByteArray(StandardCharsets.UTF_8)
        val cookies = HashMap<String, String>()
        cookies["bob"] = "barker"
        val form = HashMap<String, String>()
        val headers = HashMap<String, Collection<String>>()
        headers["myHeader"] = listOf("value1", "value2")

        var badRequest = HttpRequest.Builder()
                .withUrl(badUrl)
                .withMethod(method)
                .withBody(body)
                .withCookies(cookies)
                .withForm(form)
                .withHeaders(headers)
                .build()

        try {
            Tasks.await(httpClient.execute(badRequest))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, svcEx.errorCode)
        }

        // Executing a request against a bad domain should fail
        badUrl = "http://127.0.0.1:234"

        badRequest = HttpRequest.Builder()
                .withUrl(badUrl)
                .withMethod(method)
                .withBody(body)
                .withCookies(cookies)
                .withHeaders(headers)
                .build()

        try {
            Tasks.await(httpClient.execute(badRequest))
            fail()
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchServiceException)
            val svcEx = ex.cause as StitchServiceException
            assertEquals(StitchServiceErrorCode.HTTP_ERROR, svcEx.errorCode)
        }

        val retryAttempts = 3
        for (i in 1..retryAttempts) {
            // A correctly specific request should succeed
            val goodRequest = HttpRequest.Builder()
                    .withUrl("https://httpbin.org/delete")
                    .withMethod(method)
                    .withBody(body)
                    .withCookies(cookies)
                    .withHeaders(headers)
                    .build()
            val response = Tasks.await(httpClient.execute(goodRequest))

            if (i != retryAttempts && response.statusCode != 200) {
                Thread.sleep(Duration.ofSeconds(5).toMillis())
                continue
            }

            assertEquals("200 OK", response.status)
            assertEquals(200, response.statusCode)
            assertTrue(response.contentLength in 300..500)
            Assert.assertNotNull(response.body)
            val dataDoc = Document.parse(String(response.body!!))
            assertTrue(Arrays.equals(
                    body,
                    dataDoc.getString("data").toByteArray(StandardCharsets.UTF_8)))
            val headersDoc = dataDoc.get("headers") as Document
            assertEquals("value1,value2", headersDoc["Myheader"])
            assertEquals("bob=barker", headersDoc["Cookie"])
        }
    }
}
