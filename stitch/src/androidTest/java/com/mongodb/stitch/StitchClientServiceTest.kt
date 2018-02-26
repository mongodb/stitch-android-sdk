package com.mongodb.stitch

import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.admin.create
import com.mongodb.stitch.admin.services.ServiceConfigWrapper
import com.mongodb.stitch.admin.services.ServiceConfigs
import com.mongodb.stitch.admin.services.service
import com.mongodb.stitch.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.android.auth.anonymous.AnonymousAuthProvider
import com.mongodb.stitch.android.auth.apiKey.APIKey
import com.mongodb.stitch.android.auth.apiKey.APIKeyProvider
import com.mongodb.stitch.android.auth.custom.CustomAuthProvider
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProvider
import com.mongodb.stitch.android.services.mongodb.MongoClient
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.bson.*
import org.junit.Test
import org.junit.runner.RunWith
import junit.framework.Assert.*
import org.bson.types.ObjectId
import org.junit.Ignore
import java.util.*
import kotlin.test.assertNotEquals

/**
 * Test public methods of StitchClient, running through authentication
 * and pipeline flows. Full service test.
 */
@RunWith(AndroidJUnit4::class)
class StitchClientServiceTest: StitchTestCase() {
    @Test
    fun testFetchAuthProviders() {
        harness.addDefaultCustomTokenProvider()
        val authInfo = await(stitchClient.authProviders)

        assertThat(authInfo.hasEmailPassword())
        assertThat(authInfo.emailPassword!!.config.emailConfirmationUrl == "http://emailConfirmURL.com")
        assertThat(authInfo.emailPassword!!.config.resetPasswordUrl == "http://resetPasswordURL.com")

        assertThat(authInfo.customAuth != null)
    }

    /**
     * Test creating and logging in with an api key
     */
    @Test
    fun testCreateSelfApiKey() {
        harness.addDefaultApiKeyProvider()
        registerAndLogin()
        val auth = stitchClient.auth?.let { it } ?: return

        val key = await(
                auth.createApiKey("key_test").addOnCompleteListener {
                    assertThat(it.isSuccessful, it.exception)
                }
        )

        assertThat(key != null)
        assertThat(key.key != null)

        await(this.stitchClient.logout())
        await(this.stitchClient.logInWithProvider(APIKeyProvider(key.key!!)).addOnCompleteListener {
            assertThat(it.isSuccessful, it.exception)
            assertThat(it.result != null)
        })
    }

    @Test
    fun testFetchApiKey() {
        harness.addDefaultApiKeyProvider()
        registerAndLogin()
        val auth = stitchClient.auth?.let { it } ?: return

        val key = await(
                auth.createApiKey("key_test").addOnCompleteListener {
                    assertThat(it.isSuccessful, it.exception)
                }
        )

        assertThat(key != null)
        assertThat(key.key != null)

        val partialKey = await(auth.fetchApiKey(key.id))
        assertThat(partialKey.name == key.name)
        assertThat(partialKey.id == key.id)
        assertThat(!partialKey.disabled)
    }

    @Test
    fun testFetchApiKeys() {
        harness.addDefaultApiKeyProvider()
        registerAndLogin()
        val auth = stitchClient.auth?.let { it } ?: return

        val keys: List<APIKey> = (0..3).map {
            await(auth
                    .createApiKey("selfApiKeyTest$it")
                    .addOnCompleteListener {
                        assertThat(it.isSuccessful, it.exception)
                    })
        }

        keys.forEach { assertThat(it.key != null) }

        val partialKeys = await(auth.fetchApiKeys())
        val partialKeysMapped = partialKeys.associateBy { it.id }

        partialKeys.forEach {
            assertThat(partialKeysMapped.containsKey(it.id))
        }
    }

    @Test
    fun testEnableDisableApiKey() {
        harness.addDefaultApiKeyProvider()
        registerAndLogin()
        val auth = stitchClient.auth?.let { it } ?: return

        val key = await(
                auth.createApiKey("key_test").addOnCompleteListener {
                    assertThat(it.isSuccessful, it.exception)
                }
        )

        assertThat(key != null)
        assertThat(key.key != null)

        await(auth.disableApiKey(key.id))

        assertThat(await(auth.fetchApiKey(key.id)).disabled)

        await(auth.enableApiKey(key.id))

        assertThat(!await(auth.fetchApiKey(key.id)).disabled)
    }

    val TEST_DB = ObjectId().toString()
    val TEST_COLLECTION = ObjectId().toString()

    @Test
    fun testMongo() {
        registerAndLogin()
        val mongodbService = await(
                harness.app.services.create(data = ServiceConfigWrapper(
                        name = "mdb",
                        type = "mongodb",
                        config = ServiceConfigs.Mongo(uri = "mongodb://localhost:26000"))
                )
        )
        await(harness.app.services.service(mongodbService.id).rules.create(
                Document(mapOf(
                        "name" to "testRule",
                        "namespace" to "$TEST_DB.$TEST_COLLECTION",
                        "read" to mapOf("%%true" to true),
                        "write" to mapOf("%%true" to true),
                        "valid" to mapOf("%%true" to true),
                        "fields" to mapOf<String, Map<String, String>>(
                                "_id" to emptyMap(),
                                "owner_id" to emptyMap(),
                                "a" to emptyMap(),
                                "b" to emptyMap(),
                                "c" to emptyMap(),
                                "d" to emptyMap()
                        )
                ))
        ))
        val mongoClient = MongoClient(this.stitchClient, "mdb")
        val coll = mongoClient.getDatabase(TEST_DB).getCollection(TEST_COLLECTION)

        val currentCount = await(coll.count(Document()))

        await(coll.insertOne(Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId))))

        await(coll.count(Document()).addOnCompleteListener { assertEquals(it.result, currentCount + 1) })

        await(coll.insertMany(listOf(
                Document(mapOf("a" to 1, "owner_id" to stitchClient.userId)),
                Document(mapOf("a" to 1, "owner_id" to stitchClient.userId))
        )))

        await(coll.find(Document(mapOf("owner_id" to stitchClient.userId)), 10).addOnCompleteListener {
            assertEquals(it.result.size.toLong(), currentCount + 3)
        })

        await(coll.deleteMany(Document(mapOf("owner_id" to stitchClient.userId))).addOnCompleteListener {
            assertThat(it.isSuccessful, it.exception)
        })
    }

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
                .setAudience(stitchClient.appId)
                .setSubject("uniqueUserID")
                .setExpiration(Date(((Calendar.getInstance().timeInMillis + (5*60*1000)))))
                .signWith(SignatureAlgorithm.HS256,
                        "abcdefghijklmnopqrstuvwxyz1234567890".toByteArray())
                .compact()


        val userId = await(stitchClient.logInWithProvider(CustomAuthProvider(jwt)))

        assertThat(userId != null)
    }

    @Test
    fun testMultipleLoginSemantics() {
        await(stitchClient.logout())

        // check storage
        assertFalse(stitchClient.isAuthenticated)
        assertEquals(stitchClient.loggedInProviderType, "")

        // login anonymously
        val anonUserId = await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
        assertThat(anonUserId != null)

        // check storage
        assertTrue(stitchClient.isAuthenticated)
        assertEquals(stitchClient.loggedInProviderType, AnonymousAuthProvider.AUTH_TYPE)

        // login anonymously again and make sure user ID is the same
        assertEquals(anonUserId, await(stitchClient.logInWithProvider(AnonymousAuthProvider())))

        // check storage
        assertTrue(stitchClient.isAuthenticated)
        assertEquals(stitchClient.loggedInProviderType, AnonymousAuthProvider.AUTH_TYPE)

        // login with email provider and make sure user ID is updated
        val emailUserId = registerAndLogin()
        assertNotEquals(emailUserId, anonUserId)

        // check storage
        assertTrue(stitchClient.isAuthenticated)
        assertEquals(stitchClient.loggedInProviderType, EmailPasswordAuthProvider.AUTH_TYPE)

        // login with email provider under different user and make sure user ID is updated
        val id2 = registerAndLogin(email = "test2@10gen.com", pass = "hunter2")
        assertNotEquals(emailUserId, id2)

        // check storage
        assertTrue(stitchClient.isAuthenticated)
        assertEquals(stitchClient.loggedInProviderType, EmailPasswordAuthProvider.AUTH_TYPE)

        // Verify that logout clears storage
        await(stitchClient.logout())
        assertFalse(stitchClient.isAuthenticated)
        assertEquals(stitchClient.loggedInProviderType, "")
    }

    // current testing framework we cannot dynamically create identities to test this functionality. Once we have the
    // appropriate framework we can re-enable this test.
    @Test
    fun testIdentityLinking() {
        await(this.stitchClient.register(email, pass))
        val conf = await(this.harness.app.userRegistrations.sendConfirmation(email))
        await(this.stitchClient.emailConfirm(conf.token, conf.tokenId))
        val anonUserId = await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
        assertThat(anonUserId != null)
        assertEquals(stitchClient.loggedInProviderType, AnonymousAuthProvider.AUTH_TYPE)

        assertEquals(anonUserId, await(stitchClient.linkWithProvider(EmailPasswordAuthProvider(email, pass))))
        assertEquals(stitchClient.loggedInProviderType, EmailPasswordAuthProvider.AUTH_TYPE)

        val userProfile = await(stitchClient.auth!!.userProfile)
        assertEquals(userProfile.identities.size, 2)

        await(stitchClient.logout())
        assertFalse(stitchClient.isAuthenticated)
    }
}
