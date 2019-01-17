package com.mongodb.stitch.android.core

import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.core.auth.providers.userpassword.UserPasswordAuthProviderClient
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchRequestErrorCode
import com.mongodb.stitch.core.StitchRequestException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.create
import com.mongodb.stitch.core.admin.functions.FunctionCreator
import com.mongodb.stitch.core.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.core.auth.UserType
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousAuthProvider
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.auth.providers.custom.CustomAuthProvider
import com.mongodb.stitch.core.auth.providers.custom.CustomCredential
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordAuthProvider
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.bson.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays
import java.util.Date
import java.util.Calendar
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class StitchAppClientIntTests : BaseStitchAndroidIntTest() {

    @Test
    fun testCustomAuthLogin() {
        val app = createApp()
        val signingKey = "abcdefghijklmnopqrstuvwxyz1234567890"
        addProvider(app.second, ProviderConfigs.Custom(signingKey))

        val client = getAppClient(app.first)
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
                .setAudience(app.first.clientAppId)
                .setSubject("uniqueUserID")
                .setExpiration(Date(((Calendar.getInstance().timeInMillis + (5 * 60 * 1000)))))
                .signWith(SignatureAlgorithm.HS256, signingKey.toByteArray())
                .compact()

        val user = Tasks.await(client.auth.loginWithCredential(CustomCredential(jwt)))
        assertNotNull(user)
        assertTrue(user.id.isNotEmpty())
        assertEquals(CustomAuthProvider.DEFAULT_NAME, user.loggedInProviderName)
        assertEquals(CustomAuthProvider.TYPE, user.loggedInProviderType)
        assertEquals(UserType.NORMAL, user.userType)
        assertTrue(user.identities[0].id.isNotEmpty())
        assertEquals(CustomAuthProvider.TYPE, user.identities[0].providerType)
        assertTrue(client.auth.isLoggedIn)
    }

    @Test
    fun testMultipleLoginSemantics() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
                emailConfirmationUrl = "http://emailConfirmURL.com",
                resetPasswordUrl = "http://resetPasswordURL.com",
                confirmEmailSubject = "email subject",
                resetPasswordSubject = "password subject")
        )
        var client = getAppClient(app.first)

        // check storage
        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)

        // login anonymously
        val anonUser =
                Tasks.await(client.auth.loginWithCredential(
                        AnonymousCredential()
                ))
        assertNotNull(anonUser)

        // check storage
        assertTrue(client.auth.isLoggedIn)
        assertEquals(anonUser.loggedInProviderType, AnonymousAuthProvider.TYPE)

        // login anonymously again and make sure user ID is the same
        assertEquals(anonUser.id,
                Tasks.await(client.auth.loginWithCredential(
                        AnonymousCredential()
                )).id)

        // check storage
        assertTrue(client.auth.isLoggedIn)
        assertEquals(client.auth.user!!.loggedInProviderType, AnonymousAuthProvider.TYPE)

        // login with email provider and make sure user ID is updated
        val emailUserId = registerAndLoginWithUserPass(app.second, client, "test@10gen.com", "hunter1")
        assertNotSame(emailUserId, anonUser.id)

        // check storage
        assertTrue(client.auth.isLoggedIn)
        assertEquals(client.auth.user!!.loggedInProviderType, UserPasswordAuthProvider.TYPE)

        // login with email provider under different user and make sure user ID is updated
        val id2 = registerAndLoginWithUserPass(app.second, client, "test2@10gen.com", "hunter2")
        assertNotSame(emailUserId, id2)

        // check storage
        assertTrue(client.auth.isLoggedIn)
        assertEquals(client.auth.user!!.loggedInProviderType, UserPasswordAuthProvider.TYPE)

        // check everything is as it was
        client = getAppClient(app.first)
        assertTrue(client.auth.isLoggedIn)
        assertEquals(client.auth.user!!.loggedInProviderType, UserPasswordAuthProvider.TYPE)
        assertEquals(client.auth.user?.id, id2)

        assertEquals(client.auth.listUsers().size, 3)
        assertNotNull(client.auth.listUsers().firstOrNull { it.id == emailUserId })
        assertNotNull(client.auth.listUsers().firstOrNull { it.id == id2 })
        assertNotNull(client.auth.listUsers().firstOrNull { it.id == anonUser.id })

        // imitate an app restart
        Stitch.clearApps()

        // check everything is as it was
        client = getAppClient(app.first)
        assertTrue(client.auth.isLoggedIn)
        assertEquals(client.auth.user!!.loggedInProviderType, UserPasswordAuthProvider.TYPE)
        assertEquals(client.auth.user?.id, id2)

        assertEquals(client.auth.listUsers().size, 3)
        assertNotNull(client.auth.listUsers().firstOrNull { it.id == emailUserId })
        assertNotNull(client.auth.listUsers().firstOrNull { it.id == id2 })
        assertNotNull(client.auth.listUsers().firstOrNull { it.id == anonUser.id })

        // Verify that logout clears storage
        Tasks.await(client.auth.logout())
        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)
    }

    @Test
    fun testIdentityLinking() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
                emailConfirmationUrl = "http://emailConfirmURL.com",
                resetPasswordUrl = "http://resetPasswordURL.com",
                confirmEmailSubject = "email subject",
                resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)
        val userPassClient = client.auth.getProviderClient(UserPasswordAuthProviderClient.factory)

        val email = "user@10gen.com"
        val password = "password"
        Tasks.await(userPassClient.registerWithEmail(email, password))

        val conf = app.second.userRegistrations.sendConfirmation(email)
        Tasks.await(userPassClient.confirmUser(conf.token, conf.tokenId))

        val anonUser = Tasks.await(client.auth.loginWithCredential(
                AnonymousCredential()
        ))
        assertNotNull(anonUser)
        assertEquals(anonUser.loggedInProviderType, AnonymousAuthProvider.TYPE)

        val linkedUser = Tasks.await(anonUser.linkWithCredential(UserPasswordCredential(email, password)))

        assertEquals(anonUser.id, linkedUser.id)
        assertEquals(linkedUser.loggedInProviderType, UserPasswordAuthProvider.TYPE)

        assertEquals(client.auth.user!!.identities.size, 2)

        Tasks.await(client.auth.logout())
        assertFalse(client.auth.isLoggedIn)
    }

    @Test
    fun testCallFunction() {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val client = getAppClient(app.first)

        app.second.functions.create(FunctionCreator(
                "testFunction",
                "exports = function(intArg, stringArg) { " +
                        "return { intValue: intArg, stringValue: stringArg} " +
                        "}",
                null,
                false)
        )

        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        val resultDoc = Tasks.await(client.callFunction(
                "testFunction", Arrays.asList(42, "hello"), Document::class.java
        ))

        assertTrue(resultDoc.containsKey("intValue"))
        assertTrue(resultDoc.containsKey("stringValue"))
        assertEquals(42, resultDoc.getInteger("intValue"))
        assertEquals("hello", resultDoc.getString("stringValue"))

        // Ensure that a function call with 1ms timeout fails
        try {
            Tasks.await(client.callFunction(
                    "testFunction",
                    Arrays.asList(42, "hello"),
                    1L,
                    Document::class.java
            ))
        } catch (ex: ExecutionException) {
            assertTrue(ex.cause is StitchRequestException)
            assertEquals(
                    (ex.cause as StitchRequestException).errorCode,
                    StitchRequestErrorCode.TRANSPORT_ERROR
            )
        }
    }
}
