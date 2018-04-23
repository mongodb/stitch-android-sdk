package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient
import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class StitchAdminClientTests {
    @Test
    fun testAdminClient() {
        val adminClient = StitchAdminClient.create()

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
