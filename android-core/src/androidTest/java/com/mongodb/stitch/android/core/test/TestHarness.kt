package com.mongodb.stitch.android.core.test

import android.content.Context
import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.core.auth.providers.internal.userpassword.UserPasswordAuthProvider
import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.admin.*
import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.apps.app
import com.mongodb.stitch.core.admin.apps.create
import com.mongodb.stitch.core.admin.authProviders.AuthProvidersResponse
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigWrapper
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.authProviders.authProvider
import com.mongodb.stitch.core.admin.functions.FunctionCreator
import com.mongodb.stitch.core.admin.functions.FunctionResponse
import com.mongodb.stitch.core.admin.services.ServiceConfigWrapper
import com.mongodb.stitch.core.admin.services.ServiceResponse
import com.mongodb.stitch.core.admin.services.service
import com.mongodb.stitch.core.admin.users.UserCreator
import com.mongodb.stitch.core.admin.users.UserResponse
import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient
import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential
import org.bson.Document
import org.bson.types.ObjectId

val defaultServerUrl = StitchAppClientIntegrationTests.getStitchBaseURL()

internal fun buildAdminTestHarness(seedTestApp: Boolean = false,
                                   context: Context,
                                   adminUsername: String = "unique_user@domain.com",
                                   adminPassword: String = "password",
                                   serverUrl: String = defaultServerUrl): TestHarness {

    val harness = TestHarness(
            context = context,
            username = adminUsername,
            password = adminPassword,
            serverUrl = serverUrl
    )

    harness.authenticate()
    if (seedTestApp) {
        harness.createApp()
    }
    return harness
}

internal fun buildClientTestHarness(context: Context,
                                    username: String = "unique_user@domain.com",
                                    password: String = "password",
                                    serverUrl: String = defaultServerUrl): TestHarness {
    val harness: TestHarness = buildAdminTestHarness(
            seedTestApp = true,
            context = context,
            adminUsername = username,
            adminPassword = password,
            serverUrl = serverUrl
    )

    harness.addDefaultUserpassProvider()
    harness.createUser()
    harness.setupStitchClient()
    return harness
}

internal class TestHarness(private val context: Context,
                           private val username: String = "unique_user@domain.com",
                           private val password: String = "password",
                           private val serverUrl: String = defaultServerUrl) {
    private val adminClient: StitchAdminClient by lazy {
        StitchAdminClient.create(serverUrl)
    }
    var testApp: AppResponse? = null
    var stitchAppClient: StitchAppClient? = null
    private var userCredentials: Pair<String, String>? = null
    private var groupId: String? = null
    private var user: UserResponse? = null

    val apps: Apps by lazy {
        this.adminClient.apps(this.groupId!!)
    }

    val app: Apps.App by lazy {
        if (testApp == null) {
            throw RuntimeException("App must be created first")
        }

        this.apps.app(testApp!!.id)
    }

    fun teardown() {
        this.app.remove()
    }

    fun authenticate() {
        this.adminClient.logInWithCredential(
                UserPasswordCredential(
                        CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME,
                        "unique_user@domain.com",
                        "password"
                )
        )
        this.groupId = adminClient.adminProfile().roles.first().groupId
    }

    fun createApp(testAppName: String = "test-${ObjectId().toHexString()}"): AppResponse {
        print(testAppName)
        val app = this.apps.create(name = testAppName)
        this.testApp = app
        return app
    }

    fun createUser(email: String = "test_user@domain.com",
                   password: String = "password"): UserResponse {
        this.userCredentials = Pair(email, password)
        val user = this.app.users.create(data = UserCreator(email = email, password = password))
        this.user = user
        return user
    }

    fun add(serviceConfig: ServiceConfigWrapper, vararg rules: Document): ServiceResponse {
        val serviceView = this.app.services.create(data = serviceConfig)
        rules.map { this.app.services.service(id = serviceView.id).rules.create(data = it) }
        return serviceView


    }

    fun addProvider(config: ProviderConfigs?): AuthProvidersResponse {
        val resp = this.app.authProviders.create(data = ProviderConfigWrapper(config))
        this.app.authProviders.authProvider(resp.id).enable()
        return resp
    }

    fun addDefaultApiKeyProvider() {
        this.app.authProviders.authProvider(
                (this.app.authProviders.list().find { it.type == "api-key" })!!.id
        ).enable()
    }

    fun addDefaultUserpassProvider(): AuthProvidersResponse {
        return this.addProvider(config = ProviderConfigs.Userpass(
                emailConfirmationUrl = "http://emailConfirmURL.com",
                resetPasswordUrl = "http://resetPasswordURL.com",
                confirmEmailSubject = "email subject",
                resetPasswordSubject = "password subject")
        )
    }

    fun addDefaultAnonProvider() {
        try {
            this.app.authProviders.create(data = ProviderConfigWrapper(ProviderConfigs.Anon))
        } catch (err: Error) {

        }
        return this.app.authProviders.authProvider(
                this.app.authProviders.list().find { it.type == "anon-user" }!!.id
        ).enable()
    }

    fun addDefaultCustomTokenProvider(): AuthProvidersResponse {
        return this.addProvider(
                config = ProviderConfigs.Custom(signingKey = "abcdefghijklmnopqrstuvwxyz1234567890")
        )
    }

    fun addTestFunction(): FunctionResponse {
        return this.app.functions.create(FunctionCreator(
                "testFunction",
                "exports = function(intArg, stringArg) { " +
                        "return { intValue: intArg, stringValue: stringArg} " +
                        "}",
                null,
                false)
        )
    }

    fun setupStitchClient() {
        if (this.userCredentials == null) {
            throw RuntimeException("must have user before setting up stitch client")
        }

        Stitch.initialize(this.context)

        val configBuilder = StitchAppClientConfiguration.Builder()
        configBuilder
                .withClientAppId(this.testApp!!.clientAppId)
                .withBaseURL(this.serverUrl)

        this.stitchAppClient = Stitch.initializeAppClient(configBuilder)


        this.stitchAppClient!!.auth.loginWithCredential(
                this.stitchAppClient!!.auth
                        .getProviderClient(UserPasswordAuthProvider.ClientProvider).getCredential(
                        userCredentials!!.first,
                        userCredentials!!.second
                )
        )

        this.addDefaultAnonProvider()
    }
}
