package com.mongodb.stitch.android.core.auth.providers.userapikey

import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchClientErrorCode
import com.mongodb.stitch.core.StitchClientException
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyCredential
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class UserApiKeyAuthProviderClientIntTests : BaseStitchAndroidIntTest() {
    private fun prepareApp(): Pair<AppResponse, Apps.App> {
        val app = createApp()
        addProvider(app.second, config = ProviderConfigs.Userpass(
                emailConfirmationUrl = "http://emailConfirmURL.com",
                resetPasswordUrl = "http://resetPasswordURL.com",
                confirmEmailSubject = "email subject",
                resetPasswordSubject = "password subject")
        )
        enableApiKeyProvider(app.second)
        return app
    }

    @Test
    fun testCreateSelfApiKey() {
        val app = prepareApp()
        val client = getAppClient(app.first)
        val originalUserId = registerAndLoginWithUserPass(
                app.second, client, email = "test@10gen.com", pass = "hunter2")

        val apiKeyClient = client.auth.getAuthenticatedProviderClient(
                UserApiKeyAuthProviderClient.Factory
        )

        val key = Tasks.await(
                apiKeyClient.createApiKey("key_test")
        )

        assertNotNull(key)
        assertNotNull(key.key)

        Tasks.await(client.auth.logout())
        val user = Tasks.await(client.auth.loginWithCredential(UserApiKeyCredential(key.key)))
        assertEquals(originalUserId, user.id)
    }

    @Test
    fun testFetchApiKey() {
        val app = prepareApp()
        val client = getAppClient(app.first)
        registerAndLoginWithUserPass(app.second, client, email = "test@10gen.com", pass = "hunter2")

        val apiKeyClient = client.auth.getAuthenticatedProviderClient(
                UserApiKeyAuthProviderClient.Factory
        )

        val key = Tasks.await(
                apiKeyClient.createApiKey("key_test")
        )

        assertNotNull(key)
        assertNotNull(key.key)

        val fetchedKey = Tasks.await(
                apiKeyClient.fetchApiKey(key.id)
        )

        assertNotNull(fetchedKey)
        assertNull(fetchedKey.key)
        assertEquals(key.id, fetchedKey.id)
    }

    @Test
    fun testFetchApiKeys() {
        val app = prepareApp()
        val client = getAppClient(app.first)
        registerAndLoginWithUserPass(app.second, client, email = "test@10gen.com", pass = "hunter2")

        val apiKeyClient = client.auth.getAuthenticatedProviderClient(
                UserApiKeyAuthProviderClient.Factory
        )

        val keys: List<UserApiKey> = (0..3).map {
            Tasks.await(apiKeyClient.createApiKey("selfApiKeyTest$it"))
        }

        keys.forEach { assertNotNull(it.key) }

        val partialKeys = Tasks.await(apiKeyClient.fetchApiKeys())
        assertEquals(keys.size, partialKeys.size)

        val partialKeyIds = keys.map { it.id }
        keys.forEach { assertTrue(partialKeyIds.contains(it.id)) }
    }

    @Test
    fun testEnableDisableDeleteApiKey() {
        val app = prepareApp()
        val client = getAppClient(app.first)
        registerAndLoginWithUserPass(app.second, client, email = "test@10gen.com", pass = "hunter2")

        val apiKeyClient = client.auth.getAuthenticatedProviderClient(
                UserApiKeyAuthProviderClient.Factory
        )

        val key = Tasks.await(apiKeyClient.createApiKey("key_test"))
        assertNotNull(key)
        assertNotNull(key.key)

        Tasks.await(apiKeyClient.disableApiKey(key.id))
        assertTrue(Tasks.await(apiKeyClient.fetchApiKey(key.id)).disabled)

        Tasks.await(apiKeyClient.enableApiKey(key.id))
        assertFalse(Tasks.await(apiKeyClient.fetchApiKey(key.id)).disabled)

        Tasks.await(apiKeyClient.deleteApiKey(key.id))
        assertEquals(0, Tasks.await(apiKeyClient.fetchApiKeys()).size)
    }

    @Test
    fun testCreateKeyWithInvalidName() {
        val app = prepareApp()
        val client = getAppClient(app.first)
        registerAndLoginWithUserPass(
                app.second, client, email = "test@10gen.com", pass = "hunter2")

        val apiKeyClient = client.auth.getAuthenticatedProviderClient(
                UserApiKeyAuthProviderClient.Factory
        )

        try {
            Tasks.await(apiKeyClient.createApiKey("$()!$"))
            fail("did not fail when creating key with invalid name")
        } catch (e: ExecutionException) {
            assertTrue(e.cause is StitchServiceException)
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER,
                    (e.cause as StitchServiceException).errorCode)
        }
    }

    @Test
    fun testFetchNonexistentKey() {
        val app = prepareApp()
        val client = getAppClient(app.first)
        registerAndLoginWithUserPass(
                app.second, client, email = "test@10gen.com", pass = "hunter2")

        val apiKeyClient = client.auth.getAuthenticatedProviderClient(
                UserApiKeyAuthProviderClient.Factory
        )

        try {
            Tasks.await(apiKeyClient.fetchApiKey(ObjectId()))
            fail("found a nonexistent key")
        } catch (e: ExecutionException) {
            assertTrue(e.cause is StitchServiceException)
            assertEquals(StitchServiceErrorCode.API_KEY_NOT_FOUND,
                    (e.cause as StitchServiceException).errorCode)
        }
    }

    @Test
    fun testLoggedOut() {
        val app = createApp()
        val client = getAppClient(app.first)
        try {
            client.auth.getAuthenticatedProviderClient(
                    UserApiKeyAuthProviderClient.Factory
            )
            fail("created an authenticated client while logged out")
        } catch (e: Exception) {
            assertTrue(e is StitchClientException)
            assertEquals(StitchClientErrorCode.MUST_AUTHENTICATE_FIRST,
                    (e as StitchClientException).errorCode)
        }
    }
}
