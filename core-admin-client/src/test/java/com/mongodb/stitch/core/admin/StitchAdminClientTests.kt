package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient
import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

import org.junit.BeforeClass

class StitchAdminClientTests {

    companion object {
        fun getStitchBaseURL(): String {
            return System.getProperty("test.stitch.baseURL");
        }

        @BeforeClass @JvmStatic
        fun setup() {
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

    @Test
    fun testAdminClient() {
        val adminClient = StitchAdminClient.create(getStitchBaseURL())

        adminClient.logInWithCredential(UserPasswordCredential(
                CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME,
                "unique_user@domain.com",
                "password"
        ))

        val adminGroupId = adminClient.adminProfile().roles.first().groupId
        assertNotNull(adminGroupId)

        val apps = adminClient.apps(adminGroupId)
        assertNotNull(apps.list())
    }
}
