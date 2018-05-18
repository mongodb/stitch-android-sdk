package com.mongodb.stitch.server.core.auth.providers.userapikey

import com.mongodb.stitch.core.StitchClientErrorCode
import com.mongodb.stitch.core.StitchClientException
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyCredential
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey
import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UserApiKeyAuthProviderClientIntTests : BaseStitchServerIntTest() {
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

        val key = apiKeyClient.createApiKey("key_test")

        assertNotNull(key)
        assertNotNull(key.key)

        client.auth.logout()
        val user = client.auth.loginWithCredential(UserApiKeyCredential(key.key))
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

        val key = apiKeyClient.createApiKey("key_test")

        assertNotNull(key)
        assertNotNull(key.key)

        val fetchedKey = apiKeyClient.fetchApiKey(key.id)

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
            apiKeyClient.createApiKey("selfApiKeyTest$it")
        }

        keys.forEach { assertNotNull(it.key) }

        val partialKeys = apiKeyClient.fetchApiKeys()
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

        val key = apiKeyClient.createApiKey("key_test")
        assertNotNull(key)
        assertNotNull(key.key)

        apiKeyClient.disableApiKey(key.id)
        assertTrue(apiKeyClient.fetchApiKey(key.id).disabled)

        apiKeyClient.enableApiKey(key.id)
        assertFalse(apiKeyClient.fetchApiKey(key.id).disabled)

        apiKeyClient.deleteApiKey(key.id)
        assertEquals(0, apiKeyClient.fetchApiKeys().size)
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
            apiKeyClient.createApiKey("$()!$")
            fail("did not fail when creating key with invalid name")
        } catch (e: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, e.errorCode)
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
            apiKeyClient.fetchApiKey(ObjectId())
            fail("found a nonexistent key")
        } catch (e: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.API_KEY_NOT_FOUND, e.errorCode)
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