package com.mongodb.stitch

import android.content.Context
import android.content.SharedPreferences
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.android.AuthListener
import com.mongodb.stitch.android.PipelineStage
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.auth.anonymous.AnonymousAuthProvider
import com.mongodb.stitch.android.auth.oauth2.facebook.FacebookAuthProviderInfo
import com.mongodb.stitch.android.auth.oauth2.google.GoogleAuthProviderInfo
import io.appflate.restmock.RESTMockServer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import io.appflate.restmock.utils.RequestMatchers.pathContains
import org.json.JSONObject
import io.appflate.restmock.android.AndroidLogger
import io.appflate.restmock.android.AndroidAssetsFileParser
import io.appflate.restmock.RESTMockServerStarter
import io.appflate.restmock.utils.RequestMatchers.pathEndsWith

/**
 * Test public methods of StitchClient, running through authentication
 * and pipeline flows.
 */
@RunWith(AndroidJUnit4::class)
class StitchClientTest {
    companion object {
        const val FakeAccessToken = "fake-access-token"
        const val FakeRefreshToken = "fake-refresh-token"
        const val FakeUserId = "fake-user-id"
        const val FakeDeviceId = "fake-device-id"

        const val FakeDeferUrl = "https://mock.com"
        const val FakeGoogleClientId = "fake-google-client-id"
        const val FakeFacebookClientId = "fake-facebook-client-id"
        const val FakeMetadataFieldEmail = "email"
        const val FakeMetadataFieldBirthday = "birthday"

        const val FakePipelineLiteralFoo = "foo"
        const val FakePipelineLiteralBar = "bar"

        const val FakeDomainId = "fake-domain-id"
        const val FakeAnonIdentity = "anon/user"
        const val FakeUserType = "normal"

        /** Mock data for available providers [StitchClient.getAuthProviders] */
        val mockProviderData = JSONObject(mapOf(
                "anon/user" to emptyMap<String, String>(),
                "local/userpass" to mapOf(
                        "emailConfirmationUrl" to FakeDeferUrl,
                        "resetPasswordUrl" to FakeDeferUrl
                ),
                "oauth2/google" to mapOf(
                        "clientId" to FakeGoogleClientId,
                        "metadataFields" to arrayOf(
                                FakeMetadataFieldEmail,
                                FakeMetadataFieldBirthday
                        )
                ),
                "oauth2/facebook" to mapOf(
                        "clientId" to FakeFacebookClientId,
                        "metadataFields" to arrayOf(
                                FakeMetadataFieldEmail,
                                FakeMetadataFieldBirthday
                        )
                )
        )).toString()

        /** Mock data for auth object from [StitchClient.logInWithProvider] */
        val mockAuthData = JSONObject(mapOf(
                "accessToken" to FakeAccessToken,
                "refreshToken" to FakeRefreshToken,
                "userId" to FakeUserId,
                "deviceId" to FakeDeviceId
        )).toString()

        /** Mock data for pipeline execution from [StitchClient.executePipeline] */
        val mockPipelineData = JSONObject(mapOf(
                "result" to arrayOf(FakePipelineLiteralFoo, FakePipelineLiteralBar)
        )).toString()

        /** Mock data for user profile from [StitchClient.getUserProfile] */
        val mockUserData = JSONObject(mapOf(
                "domainId" to FakeDomainId,
                "userId" to FakeUserId,
                "identities" to arrayOf(mapOf(
                        "id" to FakeUserId,
                        "provider" to FakeAnonIdentity)
                ),
                "data" to null,
                "type" to FakeUserType
        )).toString()
    }

    /** Base context from test runner */
    private val instrumentationCtx: Context = InstrumentationRegistry.getContext()
    /** [StitchClient] for this test */
    private var stitchClient: StitchClient? = null

    @Before
    fun setup() {
        // start the mock server
        RESTMockServerStarter.startSync(AndroidAssetsFileParser(instrumentationCtx), AndroidLogger())

        // instantiate a new StitchClient using a dummy name and the mock baseUrl
        val stitchClient = StitchClient(instrumentationCtx, "dummy-app", RESTMockServer.getUrl())
        this.stitchClient = stitchClient

        // clear all instances of the internal [SharedPreferences] to start with a clean slate
        stitchClient.properties.clear()
        stitchClient.javaClass.kotlin.declaredMemberProperties.first {
            it.name == "_preferences"
        }.also { it.isAccessible = true }.get(stitchClient).also {
            (it as SharedPreferences).edit().clear().commit()
        }

        // mock out all calls related to auth
        RESTMockServer.whenPOST(pathContains("auth")).thenReturnString(mockAuthData)
        RESTMockServer.whenGET(pathEndsWith("auth")).thenReturnString(mockProviderData)
        RESTMockServer.whenDELETE(pathEndsWith("auth")).thenReturnEmpty(200)

        // mock out all calls related to pipelines
        RESTMockServer.whenPOST(pathEndsWith("pipeline")).thenReturnString(mockPipelineData)

        // mock out user profile call
        RESTMockServer.whenGET(pathEndsWith("me")).thenReturnString(mockUserData)
    }

    /**
     * Test authentication flow
     */
    @Test
    fun testAuth() {
        var loggedIn = false

        // add an auth listener to the stitchClient, asserting logged in
        // and logged out status later in this test
        stitchClient!!.addAuthListener(object : AuthListener {
            override fun onLogin() {
                loggedIn = true
            }

            override fun onLogout(lastProvider: String?) {
                loggedIn = false
            }
        })

        // assert that we have not authenticated yet
        assertThat(!stitchClient!!.isAuthenticated)

        // fetch mocked authProviders
        await(stitchClient!!.authProviders).let {
            // assert all providers provided in mock are available
            assertThat(it.hasAnonymous() && it.hasEmailPassword()
                    && it.hasFacebook() && it.hasGoogle())

            // return as an array for convenience
            arrayOf(it.anonymous, it.emailPassword, it.facebook, it.google)
        }.forEach {
            // assert google and facebook auth providers have their
            // respective ids
            when (it) {
                is GoogleAuthProviderInfo ->
                    assertThat(it.clientId == FakeGoogleClientId)
                is FacebookAuthProviderInfo ->
                    assertThat(it.applicationId == FakeFacebookClientId)
            }
        }

        // log in anonymously
        await(stitchClient!!.logInWithProvider(AnonymousAuthProvider()))

        // assert that the [AuthListener] we previously added has been called
        assertThat(loggedIn)

        // assert that isAuthenticated has been properly flagged
        assertThat(stitchClient!!.isAuthenticated)

        // fetch the user profile and assert it has been mapped properly
        // from the mock data
        val userProfile = await(stitchClient!!.userProfile)
        assertThat(userProfile.id == FakeUserId && userProfile.identities.size == 1)
        val identity = userProfile.identities.first()
        assertThat(identity.id == FakeUserId && identity.provider == FakeAnonIdentity)

        // assign the auth object in scope and assert it has been mapped
        // properly from the mock data
        val auth = stitchClient!!.auth
        assertThat(auth.accessToken == FakeAccessToken && auth.deviceId == FakeDeviceId
                && auth.userId == FakeUserId)

        // log out and assert that we are no longer authenticated and that
        // the [AuthListener] has been called
        await(stitchClient!!.logout())
        assertThat(!stitchClient!!.isAuthenticated)
        assertThat(!loggedIn)
    }

    /**
     * Test pipeline flow
     */
    @Test
    fun testPipeline() {
        // log in anonymously to be able to execute pipelines
        await(stitchClient!!.logInWithProvider(AnonymousAuthProvider()))

        // execute a new pipeline and assert that it contains the mocked data
        val pipelineData = await(stitchClient!!.executePipeline(PipelineStage("literal", mapOf(
                "items" to listOf(FakePipelineLiteralFoo, FakePipelineLiteralBar)
        ))))
        assertThat(pipelineData.containsAll(listOf(FakePipelineLiteralFoo, FakePipelineLiteralBar)))
    }
}
