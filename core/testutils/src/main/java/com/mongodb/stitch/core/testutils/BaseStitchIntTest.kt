package com.mongodb.stitch.core.testutils

import com.mongodb.stitch.core.admin.Apps.App
import com.mongodb.stitch.core.admin.Apps.App.Services.Service
import com.mongodb.stitch.core.admin.StitchAdminClient
import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.apps.app
import com.mongodb.stitch.core.admin.apps.create
import com.mongodb.stitch.core.admin.authProviders.AuthProvidersResponse
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigWrapper
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.authProviders.authProvider
import com.mongodb.stitch.core.admin.create
import com.mongodb.stitch.core.admin.enable
import com.mongodb.stitch.core.admin.list
import com.mongodb.stitch.core.admin.remove
import com.mongodb.stitch.core.admin.services.ServiceConfigWrapper
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.ServiceResponse
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.RuleResponse
import com.mongodb.stitch.core.admin.services.service
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyAuthProvider
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before

abstract class BaseStitchIntTest {

    private val adminClient: StitchAdminClient by lazy {
        StitchAdminClient.create(getStitchBaseURL())
    }
    private var groupId: String = ""
    private val apps: MutableList<App> = mutableListOf()
    private var initialized = false

    abstract fun getStitchBaseURL(): String

    @Before
    open fun setup() {
        // Verify stitch is up
        val httpClient = OkHttpClient()
        val request = Request.Builder().url(getStitchBaseURL()).method("GET", null).build()
        try {
            val response = httpClient.newCall(request).execute()
            assertTrue("Expected Stitch server to be available at '${getStitchBaseURL()}'", response.isSuccessful)
        } catch (e: Exception) {
            fail("Expected Stitch server to be available at '${getStitchBaseURL()}': ${e.message}")
        }

        adminClient.logInWithCredential(
                UserPasswordCredential(
                        "unique_user@domain.com",
                        "password"
                )
        )
        groupId = adminClient.adminProfile().roles.first().groupId
        initialized = true
    }

    @After
    open fun teardown() {
        if (!initialized) {
            return
        }
        apps.forEach { it.remove() }
        apps.clear()
        adminClient.logout()
    }

    fun createApp(appName: String = "test-${ObjectId().toHexString()}"): Pair<AppResponse, App> {
        val appInfo = adminClient.apps(groupId).create(name = appName)
        val app = adminClient.apps(groupId).app(appInfo.id)
        apps.add(app)
        return Pair(appInfo, app)
    }

    fun addProvider(app: App, config: ProviderConfigs?): AuthProvidersResponse {
        val resp = app.authProviders.create(data = ProviderConfigWrapper(config))
        app.authProviders.authProvider(resp.id).enable()
        return resp
    }

    fun enableApiKeyProvider(app: App) {
        val responses = app.authProviders.list()
        val apiKeyProvider = responses.first { it.name == UserApiKeyAuthProvider.DEFAULT_NAME }
        app.authProviders.authProvider(apiKeyProvider.id).enable()
    }

    fun addService(app: App, type: String, name: String, config: ServiceConfigs): Pair<ServiceResponse, Service> {
        val svcInfo = app.services.create(data = ServiceConfigWrapper(name, type, config))
        val svc = app.services.service(svcInfo.id)
        return Pair(svcInfo, svc)
    }

    fun addRule(svc: Service, config: RuleCreator): RuleResponse {
        return svc.rules.create(config)
    }
}
