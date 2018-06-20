package com.mongodb.stitch.server.testutils

import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import com.mongodb.stitch.server.core.Stitch
import com.mongodb.stitch.server.core.StitchAppClient
import com.mongodb.stitch.server.core.auth.providers.userpassword.UserPasswordAuthProviderClient
import org.junit.After
import org.junit.Before

open class BaseStitchServerIntTest : BaseStitchIntTest() {

    private var clients: MutableList<StitchAppClient> = mutableListOf()

    @Before
    override fun setup() {
        super.setup()
        Stitch.initialize()
    }

    @After
    override fun teardown() {
        super.teardown()
        clients.forEach { it.auth.logout() }
    }

    override fun getStitchBaseURL(): String {
        return System.getProperty("test.stitch.baseURL", "http://localhost:9090")
    }

    fun getAppClient(app: AppResponse): StitchAppClient {
        if (Stitch.hasAppClient(app.clientAppId)) {
            return Stitch.getAppClient(app.clientAppId)
        }
        val client = Stitch.initializeAppClient(
                app.clientAppId,
                StitchAppClientConfiguration.Builder()
                        .withBaseUrl(getStitchBaseURL()).build())
        clients.add(client)
        return client
    }

    // Registers a new email/password user, and logs them in, returning the user's ID
    fun registerAndLoginWithUserPass(
        app: Apps.App,
        client: StitchAppClient,
        email: String,
        pass: String
    ): String {
        val emailPassClient = client.auth.getProviderClient(
                UserPasswordAuthProviderClient.factory
        )

        emailPassClient.registerWithEmail(email, pass)

        val conf = app.userRegistrations.sendConfirmation(email)

        emailPassClient.confirmUser(conf.token, conf.tokenId)

        val user = client.auth.loginWithCredential(UserPasswordCredential(email, pass))

        return user.id
    }
}
