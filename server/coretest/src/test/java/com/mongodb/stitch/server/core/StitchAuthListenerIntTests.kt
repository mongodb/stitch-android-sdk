package com.mongodb.stitch.server.core

import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import com.mongodb.stitch.server.core.auth.StitchAuth
import com.mongodb.stitch.server.core.auth.StitchAuthListener
import com.mongodb.stitch.server.core.auth.StitchUser
import com.mongodb.stitch.server.core.auth.providers.userpassword.UserPasswordAuthProviderClient
import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StitchAuthListenerIntTests : BaseStitchServerIntTest() {

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
            override fun onUserLoggedIn(auth: StitchAuth?, loggedInUser: StitchUser?) {
                Assert.assertNotNull(auth)
                Assert.assertNotNull(loggedInUser)
                countDownLatch.countDown()
            }
        })

        Assert.assertFalse(client.auth.isLoggedIn)
        Assert.assertNull(client.auth.user)

        client.auth.loginWithCredential(AnonymousCredential())

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
            override fun onUserAdded(auth: StitchAuth?, addedUser: StitchUser?) {
                Assert.assertNotNull(auth)
                Assert.assertNotNull(addedUser)
                countDownLatch.countDown()
            }
        })

        Assert.assertFalse(client.auth.isLoggedIn)
        Assert.assertNull(client.auth.user)

        client.auth.loginWithCredential(AnonymousCredential())

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
                Assert.assertNotNull(auth)
                Assert.assertNotNull(currentActiveUser)
                Assert.assertNotNull(previousActiveUser)
                countDownLatch.countDown()
            }
        })

        Assert.assertFalse(client.auth.isLoggedIn)
        Assert.assertNull(client.auth.user)

        client.auth.loginWithCredential(AnonymousCredential())
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
                Assert.assertNotNull(auth)
                Assert.assertNotNull(loggedOutUser)
                countDownLatch.countDown()
            }
        })

        Assert.assertFalse(client.auth.isLoggedIn)
        Assert.assertNull(client.auth.user)

        client.auth.loginWithCredential(AnonymousCredential())
        registerAndLoginWithUserPass(app.second, client, "email@10gen.com", "tester10")
        client.auth.logout()
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
            override fun onUserRemoved(auth: StitchAuth?, removedUser: StitchUser?) {
                Assert.assertNotNull(auth)
                Assert.assertNotNull(removedUser)
                countDownLatch.countDown()
            }
        })

        Assert.assertFalse(client.auth.isLoggedIn)
        Assert.assertNull(client.auth.user)

        client.auth.loginWithCredential(AnonymousCredential())

        client.auth.removeUser()

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
                Assert.assertNotNull(auth)
                Assert.assertNotNull(linkedUser)
                countDownLatch.countDown()
            }
        })

        Assert.assertFalse(client.auth.isLoggedIn)
        Assert.assertNull(client.auth.user)

        val userPassClient = client.auth.getProviderClient(UserPasswordAuthProviderClient.factory)

        val email = "user@10gen.com"
        val password = "password"
        userPassClient.registerWithEmail(email, password)

        val conf = app.second.userRegistrations.sendConfirmation(email)
        userPassClient.confirmUser(conf.token, conf.tokenId)

        val anonUser = client.auth.loginWithCredential(AnonymousCredential())

        anonUser.linkWithCredential(
            UserPasswordCredential(email, password))

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
            override fun onListenerRegistered(auth: StitchAuth?) {
                Assert.assertNotNull(auth)
                countDownLatch.countDown()
            }
        })

        assert(countDownLatch.await(10, TimeUnit.SECONDS))
    }
}
