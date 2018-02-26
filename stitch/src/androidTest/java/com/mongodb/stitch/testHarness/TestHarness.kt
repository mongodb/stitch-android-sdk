package com.mongodb.stitch.testHarness

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.mongodb.stitch.admin.apps.AppResponse
import com.mongodb.stitch.admin.apps.app
import com.mongodb.stitch.admin.apps.create
import com.mongodb.stitch.admin.authProviders.AuthProvidersResponse
import com.mongodb.stitch.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.admin.services.ServiceResponse
import com.mongodb.stitch.admin.services.service
import com.mongodb.stitch.admin.users.UserCreator
import com.mongodb.stitch.admin.users.UserResponse
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.StitchClientFactory
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProvider
import com.mongodb.stitch.await
import org.bson.types.ObjectId
import com.mongodb.stitch.admin.*
import com.mongodb.stitch.admin.authProviders.ProviderConfigWrapper
import com.mongodb.stitch.admin.authProviders.authProvider
import com.mongodb.stitch.admin.services.ServiceConfigWrapper
import org.bson.Document


internal val defaultServerUrl by lazy {
    "http://10.0.2.2:9090" // special alias for host's loopack interface
}

internal fun buildAdminTestHarness(seedTestApp: Boolean = false,
                                   context: Context,
                                   username: String = "unique_user@domain.com",
                                   password: String = "password",
                                   serverUrl: String = defaultServerUrl): TestHarness {
    val harness = TestHarness(
            context = context,
            username = username,
            password = password,
            serverUrl = serverUrl
    )

    await(harness.authenticate())
    if (seedTestApp) {
        await(harness.createApp())
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
            username = username,
            password = password,
            serverUrl = serverUrl
    )

    harness.addDefaultUserpassProvider()
    await(harness.createUser())
    harness.setupStitchClient()
    return harness
}

internal class TestHarness(private val context: Context,
                           private val username: String = "unique_user@domain.com",
                           private val password: String = "password",
                           private val serverUrl: String = defaultServerUrl) {
    val adminClient: StitchAdminClient by lazy {
        await(StitchAdminClient.create(context, serverUrl))
    }
    var testApp: AppResponse? = null
    var stitchClient: StitchClient? = null
    var userCredentials: Pair<String, String>? = null
    var groupId: String? = null
    var user: UserResponse? = null

    val apps: Apps by lazy {
        this.adminClient.apps(this.groupId!!)
    }

    val app: Apps.App by lazy {
        if (testApp == null) {
            throw RuntimeException("App must be created first")
        }

        this.apps.app(testApp!!.id)
    }

    fun teardown(): Task<Unit> {
        if (testApp != null) {
            return this.app.remove()
        }

        return TaskCompletionSource<Unit>().task
    }

    fun authenticate(): Task<Unit> {
        return this.adminClient.logInWithProvider(
                authProvider = EmailPasswordAuthProvider(this.username, this.password)
        ).continueWithTask {
            this.adminClient.userProfile()
        }.continueWith {
            this.groupId = it.result.roles!!.first().groupId
        }
    }

    fun createApp(testAppName: String = "test-${ObjectId().toHexString()}"): Task<AppResponse> {
        print(testAppName)
        return this.apps.create(name = testAppName).continueWith {
            this.testApp = it.result
            it.result
        }
    }

    fun createUser(email: String = "test_user@domain.com",
                   password: String = "password"): Task<UserResponse> {
        this.userCredentials = Pair(email, password)
        return this.app.users.create(data = UserCreator(email = email, password = password)).continueWith {
            this.user = it.result
            this.user
        }
    }

    fun add(serviceConfig: ServiceConfigWrapper, vararg rules: Document): Task<ServiceResponse> {
        return this.app.services.create(data = serviceConfig).continueWith {
            val view = it.result
            rules.map { this.app.services.service(id = view.id).rules.create(data = it) }
            view
        }
    }

    fun addProvider(config: ProviderConfigs?): AuthProvidersResponse {
        val resp = await(this.app.authProviders.create(data = ProviderConfigWrapper(config)))
        await(this.app.authProviders.authProvider(resp.id).enable())
        return resp
    }

    fun addDefaultApiKeyProvider() {
        return await(this.app.authProviders.authProvider(
                await(this.app.authProviders.list()).find { it.type == "api-key" }!!.id
        ).enable())
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
            await(this.app.authProviders.create(data = ProviderConfigWrapper(ProviderConfigs.Anon)))
        } catch (err: Error) {

        }
        return await(this.app.authProviders.authProvider(
                await(this.app.authProviders.list()).find { it.type == "anon-user" }!!.id
        ).enable())
    }

    fun addDefaultCustomTokenProvider(): AuthProvidersResponse {
        return this.addProvider(
                config = ProviderConfigs.Custom(signingKey = "abcdefghijklmnopqrstuvwxyz1234567890")
        )
    }

    fun setupStitchClient() {
        if (this.userCredentials == null) {
            throw RuntimeException("must have user before setting up stitch client")
        }

        this.stitchClient = await(StitchClientFactory.create(
                context,
                this.testApp!!.clientAppId,
                this.serverUrl
        ))
        await(this.stitchClient!!.logInWithProvider(
            EmailPasswordAuthProvider(userCredentials!!.first, userCredentials!!.second)
        ))

        this.addDefaultAnonProvider()
    }
}
