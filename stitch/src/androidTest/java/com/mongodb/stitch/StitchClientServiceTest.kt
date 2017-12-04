package com.mongodb.stitch

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Base64
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.auth.apiKey.APIKey
import com.mongodb.stitch.android.auth.apiKey.APIKeyProvider
import com.mongodb.stitch.android.auth.custom.CustomAuthProvider
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProvider
import com.mongodb.stitch.android.services.mongodb.MongoClient
import org.bson.*
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals


/**
 * Test public methods of StitchClient, running through authentication
 * and pipeline flows. Full service test.
 */
@RunWith(AndroidJUnit4::class)
class StitchClientServiceTest {
    /** Base context from test runner */
    private val instrumentationCtx: Context by lazy { InstrumentationRegistry.getContext() }
    /** [StitchClient] for this test */
    private val stitchClient: StitchClient by lazy {
        StitchClient(
                instrumentationCtx,
                "test-jsf-fpleb",
                "https://stitch-dev.mongodb.com"
        )
    }

    private val email: String = "stitch@mongodb.com"
    private val pass: String = "stitchuser"

    @Before
    fun setup() {
        clearStitchClient(instrumentationCtx, stitchClient)
    }

    @After
    fun clear() {
        if (!stitchClient.isAuthenticated) {
            return
        }

        await(stitchClient.logout())
        await(stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
        val auth = stitchClient.auth!!
        await(auth.fetchApiKeys()).forEach {
            await(auth.deleteApiKey(it.id))
        }
    }

    @Test
    fun testGetAuthProviders() {
        val authProviders = await(this.stitchClient.authProviders)

        assertThat(authProviders.customAuths?.count() == 1)
    }

    /**
     * Test creating and logging in with an api key
     */
    @Test
    fun testCreateSelfApiKey() {
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
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
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
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
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
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
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
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

    @Test
    fun testMongo() {
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))

        val mongoClient = MongoClient(this.stitchClient, "mongodb-atlas")
        val coll = mongoClient.getDatabase("todo").getCollection("items")

        val currentCount = await(coll.count(Document()))

        await(coll.insertOne(Document(mapOf("bill" to "jones", "owner_id" to stitchClient.userId))))

        await(coll.count(Document()).addOnCompleteListener { assertEquals(it.result, currentCount + 1) })

        await(coll.insertMany(listOf(
                Document(mapOf("bill" to "jones", "owner_id" to stitchClient.userId)),
                Document(mapOf("bill" to "jones", "owner_id" to stitchClient.userId))
        )))

        await(coll.find(Document(mapOf("owner_id" to stitchClient.userId)), 10).addOnCompleteListener {
            assertEquals(it.result.size.toLong(), currentCount + 3)
        })

        await(coll.deleteMany(Document(mapOf("owner_id" to stitchClient.userId))).addOnCompleteListener {
            assertThat(it.isSuccessful, it.exception)
        })
    }

    @Test
    fun testExecuteFunction() {
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
        await(this.stitchClient.executeServiceFunction(
                "send",
                "tw1",
                mapOf("from" to "+15005550006", "to" to "+19088392649", "body" to "Fee-fi-fo-fum")
        ).addOnCompleteListener {
            assertThat(it.isSuccessful, it.exception)
        })
    }

    /**
     * Generates a HMAC for the given [data] with the given [key] using HMAC-SHA256.
     *
     * @returns HMAC
     */
    private fun createHmac(data: ByteArray, key: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)

        val hmac = mac.doFinal(data)
        return hmac
    }

    @Test
    fun testCustomAuth() {
        val headers = Base64.encodeToString(Document(mapOf(
                "alg" to "HS256",
                "typ" to "JWT"
        )).toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()).toByteArray(),
                (Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))

        val payload = Base64.encodeToString(Document(mapOf(
                "exp" to ((Date().time / 1000) + (5 * 60)),
                "iat" to (Date().time / 1000),
                "nbf" to (Date().time / 1000),
                "stitch_meta" to mapOf(
                        "email" to "name@example.com",
                        "name" to "Joe Bloggs",
                        "picture" to "https://goo.gl/xqR6Jd"
                ),
                "aud" to "test-jsf-fpleb",
                "sub" to "uniqueUserID"
        )).toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()).toByteArray(),
                (Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))

        val signature = Base64.encodeToString(
                createHmac("$headers.$payload".toByteArray(),
                           "abcdefghijklmnopqrstuvwxyz1234567890".toByteArray()),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

        val jwt = "$headers.$payload.$signature"
        val userId = await(stitchClient.logInWithProvider(CustomAuthProvider(jwt)))

        assertThat(userId != null)
    }
}
