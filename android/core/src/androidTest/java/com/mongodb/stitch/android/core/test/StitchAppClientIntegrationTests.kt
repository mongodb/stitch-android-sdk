package com.mongodb.stitch.android.core.test

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.core.auth.providers.internal.anonymous.AnonymousAuthProvider
import com.mongodb.stitch.android.core.auth.providers.internal.custom.CustomAuthProvider
import com.mongodb.stitch.android.core.auth.providers.internal.userpassword.UserPasswordAuthProvider
import com.mongodb.stitch.core.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl
import com.mongodb.stitch.core.auth.providers.anonymous.CoreAnonymousAuthProviderClient
import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bson.Document
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays
import java.util.Date
import java.util.Calendar
import java.util.Random

@RunWith(AndroidJUnit4::class)
class StitchAppClientIntegrationTests {
    private val instrumentationCtx: Context by lazy { InstrumentationRegistry.getContext() }

    private var harness = buildClientTestHarness(context = instrumentationCtx)
    private var stitchAppClient: StitchAppClient = harness.stitchAppClient!!

    companion object {
        private const val email: String = "stitch@10gen.com"
        private const val pass: String = "stitchuser"

        // TODO: Refactor to integration test setup class
        fun getStitchBaseURL(): String {
            return InstrumentationRegistry.getArguments().getString("test.stitch.baseURL")
        }

        @BeforeClass
        @JvmStatic
        fun setupTest() {
            var httpClient = OkHttpClient()
            val request = Request.Builder().url(getStitchBaseURL()).method("GET", null).build()
            try {
                val response = httpClient.newCall(request).execute()
                assertTrue("Expected Stitch server to be available at '${getStitchBaseURL()}'", response.isSuccessful)
            } catch (e: Exception) {
                fail("Expected Stitch server to be available at '${getStitchBaseURL()}': ${e.message}")
            }
        }
    }

    @Before
    fun setup() {
        this.harness = buildClientTestHarness(context = instrumentationCtx)
        this.stitchAppClient = harness.stitchAppClient!!

        Tasks.await(this.stitchAppClient.auth.logout())
    }

    @After
    fun teardown() {
        Tasks.await(this.stitchAppClient.auth.logout())
        this.harness.teardown()
    }

    // Registers a new email/password user, and logs them in, returning the user's ID
    private fun registerAndLogin(
        stitchClient: StitchAppClient = this.stitchAppClient,
        email: String = StitchAppClientIntegrationTests.email,
        pass: String = StitchAppClientIntegrationTests.pass
    ): String {
        val emailPassClient = stitchClient.auth.getProviderClient(
                UserPasswordAuthProvider.ClientProvider
        )

        Tasks.await(emailPassClient.registerWithEmail(email, pass))

        val conf = this.harness.app.userRegistrations.sendConfirmation(email)

        Tasks.await(emailPassClient.confirmUser(conf.token, conf.tokenId))

        val user = Tasks.await(stitchClient.auth.loginWithCredential(
                emailPassClient.getCredential(email, pass)
        ))

        return user.id
    }

//    // Restore these tests once the user API key provider client has been implemented (STITCH-1448)
//    /**
//     * Test creating and logging in with an api key
//     */
//    @Test
//    fun testCreateSelfApiKey() {
//        harness.addDefaultApiKeyProvider()
//        registerAndLogin()
//        val auth = stitchClient.auth?.let { it } ?: return
//
//        val key = Tasks.await(
//                auth.createApiKey("key_test").addOnCompleteListener {
//                    assertThat(it.isSuccessful, it.exception)
//                }
//        )
//
//        assertThat(key != null)
//        assertThat(key.key != null)
//
//        Tasks.await(this.stitchClient.logout())
//        Tasks.await(this.stitchClient.logInWithProvider(APIKeyProvider(key.key!!)).addOnCompleteListener {
//            assertThat(it.isSuccessful, it.exception)
//            assertThat(it.result != null)
//        })
//    }
//
//    @Test
//    fun testFetchApiKey() {
//        harness.addDefaultApiKeyProvider()
//        registerAndLogin()
//        val auth = stitchClient.auth?.let { it } ?: return
//
//        val key = Tasks.await(
//                auth.createApiKey("key_test").addOnCompleteListener {
//                    assertThat(it.isSuccessful, it.exception)
//                }
//        )
//
//        assertThat(key != null)
//        assertThat(key.key != null)
//
//        val partialKey = Tasks.await(auth.fetchApiKey(key.id))
//        assertThat(partialKey.name == key.name)
//        assertThat(partialKey.id == key.id)
//        assertThat(!partialKey.disabled)
//    }
//
//    @Test
//    fun testFetchApiKeys() {
//        harness.addDefaultApiKeyProvider()
//        registerAndLogin()
//        val auth = stitchClient.auth?.let { it } ?: return
//
//        val keys: List<APIKey> = (0..3).map {
//            Tasks.await(auth
//                    .createApiKey("selfApiKeyTest$it")
//                    .addOnCompleteListener {
//                        assertThat(it.isSuccessful, it.exception)
//                    })
//        }
//
//        keys.forEach { assertThat(it.key != null) }
//
//        val partialKeys = Tasks.await(auth.fetchApiKeys())
//        val partialKeysMapped = partialKeys.associateBy { it.id }
//
//        partialKeys.forEach {
//            assertThat(partialKeysMapped.containsKey(it.id))
//        }
//    }
//
//    @Test
//    fun testEnableDisableApiKey() {
//        harness.addDefaultApiKeyProvider()
//        registerAndLogin()
//        val auth = stitchClient.auth?.let { it } ?: return
//
//        val key = Tasks.await(
//                auth.createApiKey("key_test").addOnCompleteListener {
//                    assertThat(it.isSuccessful, it.exception)
//                }
//        )
//
//        assertThat(key != null)
//        assertThat(key.key != null)
//
//        Tasks.await(auth.disableApiKey(key.id))
//
//        assertThat(Tasks.await(auth.fetchApiKey(key.id)).disabled)
//
//        Tasks.await(auth.enableApiKey(key.id))
//
//        assertThat(!Tasks.await(auth.fetchApiKey(key.id)).disabled)
//    }
//

    @Test
    fun testCustomAuth() {
        harness.addDefaultCustomTokenProvider()
        registerAndLogin()
        val jwt = Jwts.builder()
                .setHeader(
                        mapOf(
                                "alg" to "HS256",
                                "typ" to "JWT"
                        ))
                .claim("stitch_meta",
                        mapOf(
                                "email" to "name@example.com",
                                "name" to "Joe Bloggs",
                                "picture" to "https://goo.gl/xqR6Jd"
                        ))
                .setIssuedAt(Date())
                .setNotBefore(Date())
                .setAudience(harness.testApp!!.clientAppId)
                .setSubject("uniqueUserID")
                .setExpiration(Date(((Calendar.getInstance().timeInMillis + (5 * 60 * 1000)))))
                .signWith(SignatureAlgorithm.HS256,
                        "abcdefghijklmnopqrstuvwxyz1234567890".toByteArray())
                .compact()

        val customAuthClient = stitchAppClient.auth.getProviderClient(CustomAuthProvider.ClientProvider)

        val user = Tasks.await(stitchAppClient.auth.loginWithCredential(
                customAuthClient.getCredential(jwt)
        ))

        print(user.id)

        assertNotNull(user)
    }

    @Test
    fun testMultipleLoginSemantics() {
        val auth = stitchAppClient.auth
        Tasks.await(auth.logout())

        // check storage
        assertFalse(auth.isLoggedIn)
        assertNull(auth.user)

        // login anonymously
        val anonUser = Tasks.await(
                auth.loginWithCredential(
                        auth.getProviderClient(AnonymousAuthProvider.ClientProvider).credential
                ))
        assertNotNull(anonUser)

        // check storage
        assertTrue(auth.isLoggedIn)
        assertEquals(anonUser.loggedInProviderType, CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME)

        // login anonymously again and make sure user ID is the same
        assertEquals(anonUser.id, Tasks.await(
                auth.loginWithCredential(
                        auth.getProviderClient(AnonymousAuthProvider.ClientProvider).credential
                )).id)

        // check storage
        assertTrue(auth.isLoggedIn)
        assertEquals(auth.user.loggedInProviderType, CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME)

        // login with email provider and make sure user ID is updated
        val emailUserId = registerAndLogin()
        assertNotSame(emailUserId, anonUser.id)

        // check storage
        assertTrue(auth.isLoggedIn)
        assertEquals(auth.user.loggedInProviderType, CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME)

        // login with email provider under different user and make sure user ID is updated
        val id2 = registerAndLogin(email = "test2@10gen.com", pass = "hunter2")
        assertNotSame(emailUserId, id2)

        // check storage
        assertTrue(auth.isLoggedIn)
        assertEquals(auth.user.loggedInProviderType, CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME)

        // Verify that logout clears storage
        Tasks.await(auth.logout())
        assertFalse(auth.isLoggedIn)
        assertNull(auth.user)
    }

    @Test
    fun testIdentityLinking() {
        val auth = stitchAppClient.auth
        val userPassClient = auth.getProviderClient(UserPasswordAuthProvider.ClientProvider)

        Tasks.await(userPassClient.registerWithEmail(email, pass))
        val conf = this.harness.app.userRegistrations.sendConfirmation(email)
        Tasks.await(userPassClient.confirmUser(conf.token, conf.tokenId))

        val anonUser = Tasks.await(auth.loginWithCredential(
                auth.getProviderClient(AnonymousAuthProvider.ClientProvider).credential
        ))
        assertNotNull(anonUser)
        assertEquals(anonUser.loggedInProviderType, CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME)

        val linkedUser = Tasks.await(
                anonUser.linkWithCredential(userPassClient.getCredential(email, pass))
        )

        assertEquals(anonUser.id, linkedUser.id)
        assertEquals(linkedUser.loggedInProviderType, CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME)

        val userProfile = auth.user.profile as StitchUserProfileImpl
        assertEquals(userProfile.identities.size, 2)

        Tasks.await(auth.logout())
        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun testCallFunction() {
        harness.addTestFunction()

        Tasks.await(stitchAppClient.auth.loginWithCredential(
                stitchAppClient.auth.getProviderClient(AnonymousAuthProvider.ClientProvider).credential
        ))

        val randomInt = Random().nextInt()
        val resultDoc = Tasks.await(stitchAppClient.callFunction<Document>(
                "testFunction", Arrays.asList(randomInt, "hello")
        ))

        assertTrue(resultDoc.containsKey("intValue"))
        assertTrue(resultDoc.containsKey("stringValue"))
        assertEquals(randomInt, resultDoc.getInteger("intValue"))
        assertEquals("hello", resultDoc.getString("stringValue"))
    }
}
