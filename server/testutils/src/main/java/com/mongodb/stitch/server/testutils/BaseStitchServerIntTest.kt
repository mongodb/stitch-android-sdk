package com.mongodb.stitch.server.testutils

import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import com.mongodb.stitch.server.core.Stitch
import com.mongodb.stitch.server.core.StitchAppClient
import com.mongodb.stitch.server.core.auth.providers.userpassword.UserPasswordAuthProviderClient
import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Before
import java.io.File

open class BaseStitchServerIntTest : BaseStitchIntTest() {

    private var clients: MutableList<StitchAppClient> = mutableListOf()
    private val dataDir = System.getProperty("java.io.tmpdir")

    class TestNetworkMonitor: NetworkMonitor {
        var connectedState = false
        override fun isConnected(): Boolean {
            return connectedState
        }
        override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {
            return
        }
    }

    companion object {
        val testNetworkMonitor = TestNetworkMonitor()
    }

    @Before
    override fun setup() {
        super.setup()
    }

    @After
    override fun teardown() {
        clients.forEach { it.auth.logout() }
        clients.clear()
        // TODO: add back after SERVER-35421
//        FileUtils.forceDelete(File(dataDir))
        super.teardown()
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
                        .withDataDirectory(dataDir)
                        .withBaseUrl(getStitchBaseURL())
                        .withNetworkMonitor(testNetworkMonitor).build())
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
