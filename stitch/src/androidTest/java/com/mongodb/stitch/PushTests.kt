package com.mongodb.stitch

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.support.test.rule.ServiceTestRule
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApi
import com.mongodb.stitch.admin.services.ServiceConfigWrapper
import com.mongodb.stitch.admin.services.ServiceConfigs
import com.mongodb.stitch.android.push.PushProviderName
import com.mongodb.stitch.android.push.gcm.GCMPushClient
import com.mongodb.stitch.android.push.gcm.GCMPushProviderInfo
import com.mongodb.stitch.pushTest.AndroidTestGCMListenerService
import org.bson.Document
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.MockitoAnnotations
import kotlin.test.assertFails
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class PushTests: StitchTestCase() {
    private val senderId = "405021717222"
    @Mock
    var mockGoogleApiAvailability: GoogleApiAvailability? = null

    @get:Rule
    val mServiceRule = ServiceTestRule()

    @Before
    override fun setup() {
        super.setup()

        System.setProperty("dexmaker.dexcache", instrumentationCtx.cacheDir.path)
        MockitoAnnotations.initMocks(this)
        await(harness.add(ServiceConfigWrapper(
                name = "gcm",
                type = "gcm",
                config = ServiceConfigs.GCM(
                        senderId = senderId,
                        apiKey = "AIzaSyDLW2UlIbT6wZQev8x_tUX_YlHZIECrzZc"
                )),
                rules = Document(mapOf(
                        "name" to "testRule",
                        "actions" to listOf("send"),
                        "when" to mapOf<String, String>()
                ))
        ))

    }

    @Test
    fun testGetPushProviders() {
        registerAndLogin()

        val pushProviders = await(stitchClient.pushProviders)
        assertThat(pushProviders.hasGCM())
        assertNotNull(pushProviders.gcm)
    }

    @Test
    fun testGcm() {
        registerAndLogin()

        val gcm = await(stitchClient.pushProviders).gcm
        assertThat(gcm.senderId == senderId)
        assertThat(gcm.provider == PushProviderName.GCM)
        assertThat(gcm.service == "gcm")

        val doc = gcm.toDocument()
        assertThat((doc["config"] as Document)["senderId"] == senderId)
    }



    @Test
    fun testNewGCMPushClient() {
        registerAndLogin()

        `when`(mockGoogleApiAvailability!!.isGooglePlayServicesAvailable(instrumentationCtx))
                .thenReturn(ConnectionResult.API_UNAVAILABLE)

        val singleton = GoogleApiAvailability::class.java.declaredFields.first {
            it.type == GoogleApiAvailability::class.java
        }
        singleton.isAccessible = true
        singleton.set(GoogleApiAvailability::class.java, mockGoogleApiAvailability)

        assertFails {
            GCMPushClient(instrumentationCtx, stitchClient, await(stitchClient.pushProviders).gcm)
        }

        `when`(mockGoogleApiAvailability!!.isGooglePlayServicesAvailable(instrumentationCtx))
                .thenReturn(ConnectionResult.SUCCESS)

        assertFails {
            GCMPushClient(instrumentationCtx, stitchClient, GCMPushProviderInfo.fromProperties())
        }
    }

    @Test
    fun testPushManager() {
        registerAndLogin()
        val pushManager = stitchClient.push
        assertNotNull(pushManager)


        val provider = pushManager.forProvider(await(stitchClient.pushProviders).gcm)
                as GCMPushClient
        await(provider.register())

        val intent = Intent(
                instrumentationCtx,
                AndroidTestGCMListenerService::class.java
        )

        mServiceRule.bindService(intent)

        await(provider.subscribeToTopic("foo-bar"))

        var passed = false
        latch(timeout = 10000) {
            AndroidTestGCMListenerService.addListener {
                assertNotNull(it)
                assertThat(it!!.appId == stitchClient.appId)
                assertThat(it.providerId == senderId)
                assertThat(it.hasData())
                assertThat(it.hasRawData())
                assertThat(it.data.containsKey("message"))
                assertThat(it.data["message"] == "test_data")
                passed = true
                countDown()
            }

            await(stitchClient.executeServiceFunction(
                    "send",
                    "gcm",
                    mapOf(
                            "to" to "/topics/foo-bar",
                            "data" to mapOf(
                                    "message" to "test_data"
                            )
                    )
            ))
        }

        assertThat(passed)
    }
}
