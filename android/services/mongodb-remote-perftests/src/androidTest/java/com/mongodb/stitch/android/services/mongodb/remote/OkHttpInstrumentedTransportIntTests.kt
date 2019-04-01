package com.mongodb.stitch.android.services.mongodb.remote

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.functions.FunctionCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpInstrumentedTransportIntTests : BaseStitchAndroidIntTest() {
    private val transport = OkHttpInstrumentedTransport()
    override fun getAppClientConfigurationBuilder(): StitchAppClientConfiguration.Builder {
        return super.getAppClientConfigurationBuilder().withTransport(transport)
    }

    @Test
    fun testIntercept() {
        val (response, app) = this.createApp()
        this.addFunction(app, FunctionCreator(
            "echo",
            "exports = function(arg) { return arg; }",
            null,
            false
        ))

        this.addProvider(app, ProviderConfigs.Anon)
        val appClient = this.getAppClient(response)

        Tasks.await(appClient.auth.loginWithCredential(AnonymousCredential()))

        val foo = Tasks.await(appClient.callFunction("echo", listOf("foo"), String::class.java))
        assertEquals("foo", foo)

        assertTrue(transport.bytesDownloaded in 1600..1650)
        assertTrue(transport.bytesUploaded in 1500..1550)
    }
}
