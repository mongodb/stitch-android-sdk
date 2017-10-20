package com.mongodb.stitch

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.auth.apiKey.APIKey
import com.mongodb.stitch.android.auth.apiKey.APIKeyProvider
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
                "{TEST-APP-NAME}",
                "http://localhost:8080"
        )
    }

    private val email: String = "android_service_test@stitch.com"
    private val pass: String = "android"

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

    /**
     * Test creating and logging in with an api key
     */
    @Test
    fun testCreateSelfApiKey() {
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
        val auth = stitchClient.auth?.let { it } ?: return

        val key = await(
                auth.createApiKey("selfApiKeyTest").addOnCompleteListener {
                    assertThat(it.isSuccessful, it.exception)
                }
        )

        assertThat(key != null)
        assertThat(key.key != null)

        await(this.stitchClient.logout())
        await(this.stitchClient.logInWithProvider(APIKeyProvider(key.key!!)).addOnCompleteListener {
            assertThat(it.isSuccessful, it.exception)
            assertThat(it.result.accessToken != null)
        })
    }

    @Test
    fun testFetchApiKey() {
        await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
        val auth = stitchClient.auth?.let { it } ?: return

        val key = await(
                auth.createApiKey("selfApiKeyTest").addOnCompleteListener {
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
                auth.createApiKey("selfApiKeyTest").addOnCompleteListener {
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
}
