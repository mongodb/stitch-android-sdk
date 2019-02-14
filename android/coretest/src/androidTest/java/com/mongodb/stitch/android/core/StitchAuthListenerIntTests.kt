package com.mongodb.stitch.android.core

import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.core.auth.StitchAuth
import com.mongodb.stitch.android.core.auth.StitchAuthListener
import com.mongodb.stitch.android.core.auth.StitchUser
import com.mongodb.stitch.android.core.auth.providers.userpassword.UserPasswordAuthProviderClient
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StitchAuthListenerIntTests : BaseStitchAndroidIntTest() {
    @Test
    fun testOnUserLoggedInDispatched() {
        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)

        val countDownLatch = CountDownLatch(1)

        client.auth.addAuthListener(object : StitchAuthListener {
            override fun onAuthEvent(auth: StitchAuth?) {
            }

            override fun onUserLoggedIn(auth: StitchAuth?, loggedInUser: StitchUser?) {
                assertNotNull(auth)
                assertNotNull(loggedInUser)
                countDownLatch.countDown()
            }
        })

        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)

        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testOnAddedUserDispatched() {
        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)

        val countDownLatch = CountDownLatch(1)

        client.auth.addAuthListener(object : StitchAuthListener {
            override fun onAuthEvent(auth: StitchAuth?) {
            }

            override fun onUserAdded(auth: StitchAuth?, addedUser: StitchUser?) {
                assertNotNull(auth)
                assertNotNull(addedUser)
                countDownLatch.countDown()
            }
        })

        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)

        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testOnActiveUserChangedDispatched() {
        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)

        val countDownLatch = CountDownLatch(1)

        client.auth.addAuthListener(object : StitchAuthListener {
            override fun onAuthEvent(auth: StitchAuth?) {
            }

            override fun onActiveUserChanged(
                auth: StitchAuth?,
                currentActiveUser: StitchUser?,
                previousActiveUser: StitchUser?
            ) {
                assertNotNull(auth)
                assertNotNull(currentActiveUser)
                assertNotNull(previousActiveUser)
                countDownLatch.countDown()
            }
        })

        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)

        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))
        registerAndLoginWithUserPass(app.second, client, "email@10gen.com", "tester10")

        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testOnUserLoggedOutDispatched() {
        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)

        val countDownLatch = CountDownLatch(1)

        client.auth.addAuthListener(object : StitchAuthListener {
            override fun onAuthEvent(auth: StitchAuth?) {
            }

            override fun onUserLoggedOut(
                auth: StitchAuth?,
                loggedOutUser: StitchUser?
            ) {
                assertNotNull(auth)
                assertNotNull(loggedOutUser)
                countDownLatch.countDown()
            }
        })

        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)

        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))
        registerAndLoginWithUserPass(app.second, client, "email@10gen.com", "tester10")
        Tasks.await(client.auth.logout())
        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testOnUserRemovedDispatched() {
        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)

        val countDownLatch = CountDownLatch(1)

        client.auth.addAuthListener(object : StitchAuthListener {
            override fun onAuthEvent(auth: StitchAuth?) {
            }

            override fun onUserRemoved(auth: StitchAuth?, removedUser: StitchUser?) {
                assertNotNull(auth)
                assertNotNull(removedUser)
                countDownLatch.countDown()
            }
        })

        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)

        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        Tasks.await(client.auth.removeUser())

        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testOnUserLinkedDispatched() {
        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)

        val countDownLatch = CountDownLatch(1)

        client.auth.addAuthListener(object : StitchAuthListener {
            override fun onAuthEvent(auth: StitchAuth?) {
            }

            override fun onUserLinked(auth: StitchAuth?, linkedUser: StitchUser?) {
                assertNotNull(auth)
                assertNotNull(linkedUser)
                countDownLatch.countDown()
            }
        })

        assertFalse(client.auth.isLoggedIn)
        assertNull(client.auth.user)

        val userPassClient = client.auth.getProviderClient(UserPasswordAuthProviderClient.factory)

        val email = "user@10gen.com"
        val password = "password"
        Tasks.await(userPassClient.registerWithEmail(email, password))

        val conf = app.second.userRegistrations.sendConfirmation(email)
        Tasks.await(userPassClient.confirmUser(conf.token, conf.tokenId))

        val anonUser = Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))

        Tasks.await(anonUser.linkWithCredential(
            UserPasswordCredential(email, password)))

        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testOnListenerRegisteredDispatched() {
        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )

        val client = getAppClient(app.first)

        val countDownLatch = CountDownLatch(1)

        client.auth.addAuthListener(object : StitchAuthListener {
            override fun onAuthEvent(auth: StitchAuth?) {
            }

            override fun onListenerRegistered(auth: StitchAuth?) {
                assertNotNull(auth)
                countDownLatch.countDown()
            }
        })

        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }
}
