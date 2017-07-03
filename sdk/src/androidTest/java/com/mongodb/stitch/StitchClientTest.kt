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
import io.appflate.restmock.MatchableCall
import io.appflate.restmock.RESTMockServer
import io.appflate.restmock.RESTMockServerStarter
import io.appflate.restmock.android.AndroidAssetsFileParser
import io.appflate.restmock.android.AndroidLogger
import io.appflate.restmock.utils.RequestMatchers.pathContains
import io.appflate.restmock.utils.RequestMatchers.pathEndsWith
import org.bson.Document
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Test public methods of StitchClient, running through authentication
 * and pipeline flows.
 */
@RunWith(AndroidJUnit4::class)
class StitchClientTest {
    companion object {
        const val FAKE_ACCESS_TOKEN = "fake-access-token"
        const val FAKE_REFRESH_TOKEN = "fake-refresh-token"
        const val FAKE_USER_ID = "fake-user-id"
        const val FAKE_DEVICE_ID = "fake-device-id"

        const val FAKE_DEFER_URL = "https://mock.com"
        const val FAKE_GOOGLE_CLIENT_ID = "fake-google-client-id"
        const val FAKE_FACEBOOK_CLIENT_ID = "fake-facebook-client-id"
        const val FAKE_METADATA_FIELD_EMAIL = "email"
        const val FAKE_METADATA_FIELD_BIRTHDAY = "birthday"

        const val FAKE_PIPELINE_LITERAL_FOO = "foo"
        const val FAKE_PIPELINE_LITERAL_BAR = "bar"

        const val FAKE_DOMAIN_ID = "fake-domain-id"
        const val FAKE_ANON_IDENTITY = "anon/user"
        const val FAKE_USER_TYPE = "normal"

        const val FAKE_SENDER_ID = "595341599960"

        const val SHARED_PREFERENCES_NAME = "com.mongodb.stitch.sdk.push.SharedPreferences.%s"
        const val PREF_CONFIGS = "gcm.configs"
        const val GCM_KEY = "gcm"
        const val GCM_CONFIG_KEY = "config"
        const val GCM_TYPE_KEY = "type"
        const val GCM_SENDER_ID_KEY = "senderId"

        /** Mock data for all available providers [StitchClient.getAuthProviders] */
        val mockFullProviderData = JSONObject(mapOf(
                "anon/user" to emptyMap<String, String>(),
                "local/userpass" to mapOf(
                        "emailConfirmationUrl" to FAKE_DEFER_URL,
                        "resetPasswordUrl" to FAKE_DEFER_URL
                ),
                "oauth2/google" to mapOf(
                        "clientId" to FAKE_GOOGLE_CLIENT_ID,
                        "metadataFields" to arrayOf(
                                FAKE_METADATA_FIELD_EMAIL,
                                FAKE_METADATA_FIELD_BIRTHDAY
                        )
                ),
                "oauth2/facebook" to mapOf(
                        "clientId" to FAKE_FACEBOOK_CLIENT_ID,
                        "metadataFields" to arrayOf(
                                FAKE_METADATA_FIELD_EMAIL,
                                FAKE_METADATA_FIELD_BIRTHDAY
                        )
                )
        )).toString()

        /** Mock data for available providers [StitchClient.getAuthProviders] */
        val mockPartialProviderData = JSONObject(mapOf(
                "anon/user" to emptyMap<String, String>(),
                "oauth2/google" to mapOf(
                        "clientId" to FAKE_GOOGLE_CLIENT_ID,
                        "metadataFields" to arrayOf(
                                FAKE_METADATA_FIELD_EMAIL,
                                FAKE_METADATA_FIELD_BIRTHDAY
                        )
                )
        )).toString()

        /** Mock data for auth object from [StitchClient.logInWithProvider] */
        val mockAuthData = JSONObject(mapOf(
                "accessToken" to FAKE_ACCESS_TOKEN,
                "refreshToken" to FAKE_REFRESH_TOKEN,
                "userId" to FAKE_USER_ID,
                "deviceId" to FAKE_DEVICE_ID
        )).toString()

        /** Mock data for pipeline execution from [StitchClient.executePipeline] */
        val mockPipelineData = JSONObject(mapOf(
                "result" to arrayOf(FAKE_PIPELINE_LITERAL_FOO, FAKE_PIPELINE_LITERAL_BAR)
        )).toString()

        /** Mock data for user profile from [StitchClient.getUserProfile] */
        val mockUserData = JSONObject(mapOf(
                "domainId" to FAKE_DOMAIN_ID,
                "userId" to FAKE_USER_ID,
                "identities" to arrayOf(mapOf(
                        "id" to FAKE_USER_ID,
                        "provider" to FAKE_ANON_IDENTITY)
                ),
                "data" to null,
                "type" to FAKE_USER_TYPE
        )).toString()

        val mockPushData = JSONObject(mapOf(
                GCM_KEY to mapOf(
                        GCM_CONFIG_KEY to mapOf(GCM_SENDER_ID_KEY to FAKE_SENDER_ID),
                        GCM_TYPE_KEY to GCM_KEY
                )
        )).toString()
    }

    /** Base context from test runner */
    private val instrumentationCtx: Context = InstrumentationRegistry.getContext()
    /** [StitchClient] for this test */
    private var stitchClient: StitchClient? = null
    /** matchableCall associated with [RESTMockServer] for auth provider call */
    private var matchableCallAuth: MatchableCall? = null

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

        // clear out the global preferences as well
        val globPrefPath = String.format(SHARED_PREFERENCES_NAME, stitchClient.appId)
        val globalPreferences = instrumentationCtx.getSharedPreferences(
                globPrefPath,
                Context.MODE_PRIVATE
        )
        globalPreferences.edit().clear().commit()

        // mock out all calls related to auth
        RESTMockServer.whenPOST(pathContains("auth")).thenReturn(mockResponseBuilder(mockAuthData))
        matchableCallAuth = RESTMockServer.whenGET(pathEndsWith("auth")).thenReturn(
                mockResponseBuilder(mockFullProviderData)
        )
        RESTMockServer.whenDELETE(pathEndsWith("auth")).thenReturnEmpty(200)

        // mock out all calls related to pipelines
        RESTMockServer.whenPOST(pathEndsWith("pipeline")).thenReturn(mockResponseBuilder(mockPipelineData))

        // mock out user profile call
        RESTMockServer.whenGET(pathEndsWith("me")).thenReturn(mockResponseBuilder(mockUserData))

        // mock out push providers call
        RESTMockServer.whenGET(pathEndsWith("push")).thenReturn(mockResponseBuilder(mockPushData))
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
                    assertThat(it.clientId == FAKE_GOOGLE_CLIENT_ID)
                is FacebookAuthProviderInfo ->
                    assertThat(it.applicationId == FAKE_FACEBOOK_CLIENT_ID)
            }
        }

        val nextAuthCall = RESTMockServer.whenGET(pathEndsWith("auth")).thenReturn(
                mockResponseBuilder(mockPartialProviderData)
        )

        RESTMockServer.replaceMatchableCall(matchableCallAuth, nextAuthCall)
        // fetch partially mocked authProviders
        await(stitchClient!!.authProviders).let {
            // assert all providers provided in mock are available
            assertThat(it.hasAnonymous() && !it.hasEmailPassword()
                    && !it.hasFacebook() && it.hasGoogle())

            // return as an array for convenience
            arrayOf(it.anonymous, it.google)
        }.forEach {
            // assert google and facebook auth providers have their
            // respective ids
            when (it) {
                is GoogleAuthProviderInfo ->
                    assertThat(it.clientId == FAKE_GOOGLE_CLIENT_ID)
            }
        }

        RESTMockServer.replaceMatchableCall(
                nextAuthCall,
                RESTMockServer.whenGET(pathEndsWith("auth")).thenReturn(mockResponseBuilder("{}"))
        )

        // fetch empty mocked authProviders
        await(stitchClient!!.authProviders).let {
            // assert all providers provided in mock are available
            assertThat(!it.hasAnonymous() && !it.hasEmailPassword()
                    && !it.hasFacebook() && !it.hasGoogle())
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
        assertThat(userProfile.id == FAKE_USER_ID && userProfile.identities.size == 1)
        val identity = userProfile.identities.first()
        assertThat(identity.id == FAKE_USER_ID && identity.provider == FAKE_ANON_IDENTITY)

        // assign the auth object in scope and assert it has been mapped
        // properly from the mock data
        val auth = stitchClient!!.auth
        assertThat(auth.accessToken == FAKE_ACCESS_TOKEN && auth.deviceId == FAKE_DEVICE_ID
                && auth.userId == FAKE_USER_ID)

        // log out and assert that we are no longer authenticated and that
        // the [AuthListener] has been called
        await(stitchClient!!.logout())
        assertThat(!stitchClient!!.isAuthenticated)
        assertThat(!loggedIn)
    }

    /**
     * Test push notification registration flow
     */
    @Test
    fun testPush() {
        // log in anonymously
        await(stitchClient!!.logInWithProvider(AnonymousAuthProvider()))

        // fetch available pushProviders
        val pushProviders = await(stitchClient!!.pushProviders)

        // assert that gcm has been properly parsed from the mock response
        assertThat(pushProviders.hasGCM())

        // register with the "server"
        await(stitchClient!!.push.forProvider(pushProviders.gcm).register())

        // fetch the global preferences and assert that the config object that
        // we've saved matches what is expected
        val globPrefPath = String.format(SHARED_PREFERENCES_NAME, stitchClient!!.appId)
        val globalPreferences = instrumentationCtx.getSharedPreferences(globPrefPath, Context.MODE_PRIVATE)
        val doc = Document.parse(globalPreferences.getString(PREF_CONFIGS, "{}"))

        val gcmDoc = Document.parse(doc[GCM_KEY] as String)

        assertThat(gcmDoc[GCM_TYPE_KEY] == GCM_KEY)
        assertThat((gcmDoc[GCM_CONFIG_KEY] as Document)[GCM_SENDER_ID_KEY] == FAKE_SENDER_ID)

        // deregister from the "server"
        await(stitchClient!!.push.forProvider(pushProviders.gcm).deregister())

        // assert that the global prefs have been properly cleared of the gcm config
        val prefs = Document.parse(globalPreferences.getString(PREF_CONFIGS, "{}"))
        assertThat(prefs.isEmpty())
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
                "items" to listOf(FAKE_PIPELINE_LITERAL_FOO, FAKE_PIPELINE_LITERAL_BAR)
        ))))

        assertThat(pipelineData.containsAll(
                listOf(FAKE_PIPELINE_LITERAL_FOO, FAKE_PIPELINE_LITERAL_BAR))
        )
    }
}
