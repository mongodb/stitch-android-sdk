package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.StitchCredential
import com.mongodb.stitch.core.auth.internal.CoreStitchAuth
import com.mongodb.stitch.core.auth.internal.DeviceFields
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes
import com.mongodb.stitch.core.auth.internal.StitchUserFactory
import com.mongodb.stitch.core.internal.common.Storage
import com.mongodb.stitch.core.internal.net.StitchRequestClient
import org.bson.Document

/**
 * A special implementation of CoreStitchAuth that communicates with the MongoDB Stitch Admin API.
 */
class StitchAdminAuth(
    requestClient: StitchRequestClient,
    authRoutes: StitchAuthRoutes,
    storage: Storage
) :
        CoreStitchAuth<StitchAdminUser>(
                requestClient,
                authRoutes,
                storage,
                true
        ) {

    override fun getUserFactory(): StitchUserFactory<StitchAdminUser> {
        return StitchUserFactory { id,
                                   deviceId,
                                   loggedInProviderType,
                                   loggedInProviderName,
                                   userProfile,
                                   isLoggedIn ->
            StitchAdminUser(
                    id,
                    deviceId,
                    loggedInProviderType,
                    loggedInProviderName,
                    userProfile,
                    isLoggedIn
            )
        }
    }

    override fun onAuthEvent() { /* do nothing */
    }

    override fun onActiveUserChanged(currentActiveUser: StitchAdminUser?,
                                     previousActiveUser: StitchAdminUser?) {
    }

    override fun onListenerInitialized() {
    }

    override fun onUserAdded(createdUser: StitchAdminUser?) {
    }

    override fun onUserLoggedIn(loggedInUser: StitchAdminUser?) {
    }

    override fun onUserLoggedOut(loggedOutUser: StitchAdminUser?) {
    }

    override fun onUserRemoved(removedUser: StitchAdminUser?) {
    }

    override fun onUserLinked(linkedUser: StitchAdminUser?) {
    }

    public override fun getDeviceInfo(): Document {
        val document = Document()
        document[DeviceFields.APP_ID] = "MongoDB Stitch Java/Kotlin Admin Client"
        document[DeviceFields.PLATFORM] = System.getProperty("os.name")
        return document
    }

    public override fun loginWithCredentialInternal(credential: StitchCredential?): StitchAdminUser {
        return super.loginWithCredentialInternal(credential)
    }

    public override fun logoutInternal() {
        super.logoutInternal()
    }
}
