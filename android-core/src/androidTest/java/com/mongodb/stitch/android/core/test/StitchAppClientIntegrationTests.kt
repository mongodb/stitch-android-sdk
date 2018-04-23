package com.mongodb.stitch.android.core.test

import android.support.test.InstrumentationRegistry
import android.content.Context
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks.await
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

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import junit.framework.Assert.*
import org.bson.Document
import java.util.*

@RunWith(AndroidJUnit4::class)
class StitchAppClientIntegrationTests {
    private val instrumentationCtx: Context by lazy { InstrumentationRegistry.getContext() }

    private var harness = buildClientTestHarness(context = instrumentationCtx)
    private var stitchAppClient: StitchAppClient = harness.stitchAppClient!!

    companion object {
        private const val email: String = "stitch@10gen.com"
        private const val pass: String = "stitchuser"
    }

    @Before
    fun setup() {
        this.harness = buildClientTestHarness(context = instrumentationCtx)
        this.stitchAppClient = harness.stitchAppClient!!

        await(this.stitchAppClient.auth.logout())
    }

    @After
    fun teardown() {
        await(this.stitchAppClient.auth.logout())
        this.harness.teardown()
    }

    // Registers a new email/password user, and logs them in, returning the user's ID
    private fun registerAndLogin(stitchClient: StitchAppClient = this.stitchAppClient,
                                 email: String = StitchAppClientIntegrationTests.email,
                                 pass: String = StitchAppClientIntegrationTests.pass): String {
        val emailPassClient = stitchClient.auth.getProviderClient(
                UserPasswordAuthProvider.ClientProvider
        )

        await(emailPassClient.registerWithEmail(email, pass))

        val conf = this.harness.app.userRegistrations.sendConfirmation(email)

        await(emailPassClient.confirmUser(conf.token, conf.tokenId))

        val user = await(stitchClient.auth.loginWithCredential(
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
//        val key = await(
//                auth.createApiKey("key_test").addOnCompleteListener {
//                    assertThat(it.isSuccessful, it.exception)
//                }
//        )
//
//        assertThat(key != null)
//        assertThat(key.key != null)
//
//        await(this.stitchClient.logout())
//        await(this.stitchClient.logInWithProvider(APIKeyProvider(key.key!!)).addOnCompleteListener {
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
//        val key = await(
//                auth.createApiKey("key_test").addOnCompleteListener {
//                    assertThat(it.isSuccessful, it.exception)
//                }
//        )
//
//        assertThat(key != null)
//        assertThat(key.key != null)
//
//        val partialKey = await(auth.fetchApiKey(key.id))
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
//            await(auth
//                    .createApiKey("selfApiKeyTest$it")
//                    .addOnCompleteListener {
//                        assertThat(it.isSuccessful, it.exception)
//                    })
//        }
//
//        keys.forEach { assertThat(it.key != null) }
//
//        val partialKeys = await(auth.fetchApiKeys())
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
//        val key = await(
//                auth.createApiKey("key_test").addOnCompleteListener {
//                    assertThat(it.isSuccessful, it.exception)
//                }
//        )
//
//        assertThat(key != null)
//        assertThat(key.key != null)
//
//        await(auth.disableApiKey(key.id))
//
//        assertThat(await(auth.fetchApiKey(key.id)).disabled)
//
//        await(auth.enableApiKey(key.id))
//
//        assertThat(!await(auth.fetchApiKey(key.id)).disabled)
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
                .setExpiration(Date(((Calendar.getInstance().timeInMillis + (5*60*1000)))))
                .signWith(SignatureAlgorithm.HS256,
                        "abcdefghijklmnopqrstuvwxyz1234567890".toByteArray())
                .compact()

        val customAuthClient = stitchAppClient.auth.getProviderClient(CustomAuthProvider.ClientProvider)

        val user = await(stitchAppClient.auth.loginWithCredential(
                customAuthClient.getCredential(jwt)
        ))

        print(user.id)

        assertNotNull(user)
    }

    @Test
    fun testMultipleLoginSemantics() {
        val auth = stitchAppClient.auth
        await(auth.logout())

        // check storage
        assertFalse(auth.isLoggedIn)
        assertNull(auth.user)

        // login anonymously
        val anonUser = await(
                auth.loginWithCredential(
                        auth.getProviderClient(AnonymousAuthProvider.ClientProvider).credential
                ))
        assertNotNull(anonUser)

        // check storage
        assertTrue(auth.isLoggedIn)
        assertEquals(anonUser.loggedInProviderType, CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME)

        // login anonymously again and make sure user ID is the same
        assertEquals(anonUser.id, await(
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
        await(auth.logout())
        assertFalse(auth.isLoggedIn)
        assertNull(auth.user)
    }

    @Test
    fun testIdentityLinking() {
        val auth = stitchAppClient.auth
        val userPassClient = auth.getProviderClient(UserPasswordAuthProvider.ClientProvider)


        await(userPassClient.registerWithEmail(email, pass))
        val conf = this.harness.app.userRegistrations.sendConfirmation(email)
        await(userPassClient.confirmUser(conf.token, conf.tokenId))

        val anonUser = await(auth.loginWithCredential(
                auth.getProviderClient(AnonymousAuthProvider.ClientProvider).credential
        ))
        assertNotNull(anonUser)
        assertEquals(anonUser.loggedInProviderType, CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME)


        val linkedUser = await(
                anonUser.linkWithCredential(userPassClient.getCredential(email, pass))
        )

        assertEquals(anonUser.id, linkedUser.id)
        assertEquals(linkedUser.loggedInProviderType, CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME)

        val userProfile = auth.user.profile as StitchUserProfileImpl
        assertEquals(userProfile.identities.size, 2)

        await(auth.logout())
        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun testCallFunction() {
        harness.addTestFunction()

        await(stitchAppClient.auth.loginWithCredential(
                stitchAppClient.auth.getProviderClient(AnonymousAuthProvider.ClientProvider).credential
        ))

        val randomInt = Random().nextInt()
        val resultDoc = await(stitchAppClient.callFunction<Document>(
                "testFunction", Arrays.asList(randomInt, "hello")
        ))

        assertTrue(resultDoc.containsKey("intValue"))
        assertTrue(resultDoc.containsKey("stringValue"))
        assertEquals(randomInt, resultDoc.getInteger("intValue"))
        assertEquals("hello", resultDoc.getString("stringValue"))
    }
}
