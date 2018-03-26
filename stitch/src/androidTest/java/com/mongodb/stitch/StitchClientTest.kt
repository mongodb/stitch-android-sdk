package com.mongodb.stitch

import android.os.Build
import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.admin.get
import com.mongodb.stitch.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.admin.userRegistrations.sendResetPassword
import com.mongodb.stitch.android.Auth
import com.mongodb.stitch.android.AuthListener
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.auth.AuthInfo
import com.mongodb.stitch.android.auth.anonymous.AnonymousAuthProvider
import com.mongodb.stitch.android.auth.anonymous.AnonymousAuthProviderInfo
import com.mongodb.stitch.android.auth.custom.CustomAuthProviderInfo
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProvider
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProviderInfo
import com.mongodb.stitch.android.auth.oauth2.facebook.FacebookAuthProviderInfo
import com.mongodb.stitch.android.auth.oauth2.google.GoogleAuthProviderInfo
import com.mongodb.stitch.testHarness.TestHarness
import com.mongodb.stitch.testHarness.defaultServerUrl
import junit.framework.Assert
import org.bson.Document
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.jvm.isAccessible
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class StitchClientTest: StitchTestCase() {
    @Test
    fun testGetAppId() {
        assertThat(stitchClient.appId == await(harness.app.get()).clientAppId)
    }

    @Test
    fun testGetBaseUrl() {
        assertThat(stitchClient.baseUrl == defaultServerUrl)
    }

    @Test
    fun testGetUserId() {
        val userId = await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
        assertThat(stitchClient.userId == userId)
    }

    @Test
    fun testGetContext() {
        assertThat(stitchClient.context == instrumentationCtx)
    }

    @Test
    fun testGetAuth() {
        await(stitchClient.logout())
        assertFails("Must first authenticate", { stitchClient.auth })

        await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
        assertNotNull(stitchClient.auth)
    }

    @Test
    fun testIsAuthenticated() {
        await(stitchClient.logout())
        assertFalse(stitchClient.isAuthenticated)

        await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
        assertTrue(stitchClient.isAuthenticated)
    }

    @Test
    fun testGetLoggedInProviderType() {
        await(stitchClient.logout())

        assertThat(stitchClient.loggedInProviderType == "")

        val anon = AnonymousAuthProvider()
        await(stitchClient.logInWithProvider(anon))

        assertThat(stitchClient.loggedInProviderType == anon.type)

        registerAndLogin()
        assertThat(stitchClient.loggedInProviderType == "local-userpass")

        await(stitchClient.logout())
        assertThat(stitchClient.loggedInProviderType == "")
    }

    @Test
    fun testLogout() {
        assertNull(await(stitchClient.logout()))

        assertFalse(stitchClient.isAuthenticated)
        assertFails("Must first authenticate", { stitchClient.auth })

        await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
        assertNotNull(stitchClient.auth)
        assertTrue(stitchClient.isAuthenticated)

        assertNull(await(stitchClient.logout()))

        assertFalse(stitchClient.isAuthenticated)
        assertFails("Must first authenticate", { stitchClient.auth })
    }

    @Test
    fun testLoginWithProvider() {
        await(stitchClient.logout())
        harness.addDefaultCustomTokenProvider()
        harness.addDefaultApiKeyProvider()

        await(stitchClient.authProviders).let {
            arrayOf(
                it.emailPassword,
                it.anonymous,
                it.customAuth,
                it.facebook,
                it.google
            ).mapNotNull { it }
        }.forEach {
            await(stitchClient.logout())
            when (it) {
                is EmailPasswordAuthProviderInfo -> {
                    assertThat(it.config.emailConfirmationUrl ==
                            TestHarness.defaultUserPassConfig.emailConfirmationUrl)
                    assertThat(it.config.resetPasswordUrl ==
                            TestHarness.defaultUserPassConfig.resetPasswordUrl)
                    assertNotNull(registerAndLogin())
                }
                is AnonymousAuthProviderInfo -> {
                    val anon = AnonymousAuthProvider()
                    assertThat(it.name == anon.type)
                    assertThat(it.type == anon.type)
                    assertNotNull(await(stitchClient.logInWithProvider(anon)))
                }
                is CustomAuthProviderInfo -> return
                is FacebookAuthProviderInfo -> return
                is GoogleAuthProviderInfo -> return
            }
        }
    }

    @Test
    fun testLinkWithProvider() {
        await(this.stitchClient.register(email, pass))
        val conf = await(this.harness.app.userRegistrations.sendConfirmation(email))
        await(this.stitchClient.emailConfirm(conf.token, conf.tokenId))
        val anonUserId = await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
        assertThat(anonUserId != null)
        Assert.assertEquals(stitchClient.loggedInProviderType, AnonymousAuthProvider.AUTH_TYPE)

        Assert.assertEquals(anonUserId, await(stitchClient.linkWithProvider(EmailPasswordAuthProvider(email, pass))))
        Assert.assertEquals(stitchClient.loggedInProviderType, EmailPasswordAuthProvider.AUTH_TYPE)

        val userProfile = await(stitchClient.auth!!.userProfile)
        Assert.assertEquals(userProfile.identities.size, 2)

        await(stitchClient.logout())
        Assert.assertFalse(stitchClient.isAuthenticated)
        assertFails { await(stitchClient.linkWithProvider(EmailPasswordAuthProvider(email, pass))) }
    }

    @Test
    fun testRegister() {
        assertThat(await(this.stitchClient.register(email, pass)))
    }

    @Test
    fun testEmailConfirm() {
        assertThat(await(stitchClient.register(email, pass)))
        val conf = await(this.harness.app.userRegistrations.sendConfirmation(email))
        assertThat(await(stitchClient.emailConfirm(conf.token, conf.tokenId)))
        assertNotNull(await(stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass))))
    }

    @Test
    fun testSendEmailConfirm() {
        assertThat(await(stitchClient.register(email, pass)))
        assertThat(await(stitchClient.sendEmailConfirm(email)))
        assertFails { await(stitchClient.sendEmailConfirm("foo")) }
    }

    @Test
    fun testSendResetPassword() {
        assertThat(await(stitchClient.register(email, pass)))
        assertThat(await(stitchClient.sendResetPassword(email)))
        assertFails { await(stitchClient.sendResetPassword("foo")) }
    }

    @Test
    fun testResetPassword() {
        registerAndLogin()
        val conf = await(harness.app.userRegistrations.sendResetPassword(email))
        assertThat(await(stitchClient.resetPassword(conf.token, conf.tokenId, pass + "_foo")))
        assertFails { await(stitchClient.resetPassword("foo", "bar", pass)) }
    }

    @Test
    fun testAddAuthListener() {
        var result = 0
        latch(2) {
            stitchClient.addAuthListener(object : AuthListener {
                override fun onLogin() {
                    result++
                    countDown()
                }

                override fun onLogout() {
                    result++
                    countDown()
                }
            })

            await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
            await(stitchClient.logout())
        }

        assertThat(result == 2)
    }

    @Test
    fun testRemoveAuthListener() {
        var result = 0
        latch(2) {
            val authListener = object : AuthListener {
                override fun onLogin() {
                    result++
                    countDown()
                }

                override fun onLogout() {
                    result++
                    countDown()
                }
            }
            stitchClient.addAuthListener(authListener)
            stitchClient.removeAuthListener(authListener)
            await(stitchClient.logInWithProvider(AnonymousAuthProvider()))
            await(stitchClient.logout())
        }

        assertThat(result == 0)
    }

    @Test
    fun testGetDeviceInfo() {
        registerAndLogin()
        val getDeviceInfo = privateFunctionFor(StitchClient::class, "getDeviceInfo")

        assertNotNull(getDeviceInfo)
        getDeviceInfo.isAccessible = true
        val doc = getDeviceInfo.call(stitchClient) as Document

        assertThat(doc["platform"] == "android")
        assertThat(doc["platformVersion"] == Build.VERSION.RELEASE)
        assertThat(doc.containsKey("appVersion")) // null from test, but we want the key to be there
        assertThat(doc["appId"] == instrumentationCtx.packageName)

        val getAuthInfo = privateFunctionFor(Auth::class, "getAuthInfo")
        val authInfo = getAuthInfo.call(stitchClient.auth) as AuthInfo

        assertThat(doc["deviceId"] == authInfo.deviceId)
    }

    @Test
    fun testRegistrationFields() {
        val registrationFields = privateInnerClassFor(StitchClient::class, "RegistrationFields")

        val token = privateStaticPropertyFor(registrationFields, "TOKEN").getter.call() as String
        val tokenId = privateStaticPropertyFor(registrationFields, "TOKEN_ID").getter.call() as String
        val password = privateStaticPropertyFor(registrationFields, "PASSWORD").getter.call() as String

        assertThat(token == "token")
        assertThat(tokenId == "tokenId")
        assertThat(password == "password")
    }

    @Test
    fun testDeviceFields() {
        val deviceFields = privateInnerClassFor(StitchClient::class, "DeviceFields")

        val deviceId = privateStaticPropertyFor(deviceFields, "DEVICE_ID").getter.call() as String
        val appId = privateStaticPropertyFor(deviceFields, "APP_ID").getter.call() as String
        val appVersion = privateStaticPropertyFor(deviceFields, "APP_VERSION").getter.call() as String
        val platform = privateStaticPropertyFor(deviceFields, "PLATFORM").getter.call() as String
        val platformVersion = privateStaticPropertyFor(deviceFields, "PLATFORM_VERSION").getter.call() as String


        assertThat(deviceId == "deviceId")
        assertThat(appId == "appId")
        assertThat(appVersion == "appVersion")
        assertThat(platform == "platform")
        assertThat(platformVersion == "platformVersion")
    }
}
