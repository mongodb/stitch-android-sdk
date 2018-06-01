package com.mongodb.stitch.android.testutils

import android.support.test.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.core.auth.providers.userpassword.UserPasswordAuthProviderClient
import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import org.junit.After
import org.junit.Before

open class BaseStitchAndroidIntTest : BaseStitchIntTest() {

    private var clients: MutableList<StitchAppClient> = mutableListOf()

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
        Stitch.initialize(InstrumentationRegistry.getContext())
    }

    @After
    override fun teardown() {
        clients.forEach { it.auth.logout() }
        clients.clear()
        super.teardown()
    }

    override fun getStitchBaseURL(): String {
        return InstrumentationRegistry.getArguments().getString("test.stitch.baseURL", "http://10.0.2.2:9090")
    }

    fun getAppClient(app: AppResponse): StitchAppClient {
        if (Stitch.hasAppClient(app.clientAppId)) {
            return Stitch.getAppClient(app.clientAppId)
        }
        val client = Stitch.initializeAppClient(
                app.clientAppId,
                StitchAppClientConfiguration.Builder()
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

        Tasks.await(emailPassClient.registerWithEmail(email, pass))

        val conf = app.userRegistrations.sendConfirmation(email)

        Tasks.await(emailPassClient.confirmUser(conf.token, conf.tokenId))

        val user = Tasks.await(client.auth.loginWithCredential(UserPasswordCredential(email, pass)))

        return user.id
    }
}
