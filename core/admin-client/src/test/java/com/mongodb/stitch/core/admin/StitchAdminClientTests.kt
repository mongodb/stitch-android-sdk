package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import org.junit.Assert.assertNotNull
import org.junit.Test

class StitchAdminClientTests : BaseStitchIntTest() {
    override fun getStitchBaseURL(): String {
        return System.getProperty("test.stitch.baseURL", "http://localhost:9090")
    }

    @Test
    fun testAdminClient() {
        val adminClient = StitchAdminClient.create(getStitchBaseURL())

        adminClient.logInWithCredential(UserPasswordCredential(
                "unique_user@domain.com",
                "password"
        ))

        val adminGroupId = adminClient.adminProfile().roles.first().groupId
        assertNotNull(adminGroupId)

        val apps = adminClient.apps(adminGroupId)
        assertNotNull(apps.list())
    }
}
