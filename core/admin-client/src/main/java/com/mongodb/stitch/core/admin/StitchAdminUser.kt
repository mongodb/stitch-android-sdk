package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.StitchUserIdentity
import com.mongodb.stitch.core.auth.StitchUserProfile
import com.mongodb.stitch.core.auth.UserType
import com.mongodb.stitch.core.auth.internal.CoreStitchUser
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl
import org.bson.Document

import java.util.Date

class StitchAdminUser(
    private val id: String,
    private val deviceId: String,
    private val loggedInProviderType: String,
    private val loggedInProviderName: String,
    private val profile: StitchUserProfileImpl?,
    private val isLoggedIn: Boolean,
    private val lastAuthActivity: Date,
    private val customData: Document?
) : CoreStitchUser {

    override fun getLoggedInProviderType(): String {
        return loggedInProviderType
    }

    override fun getLoggedInProviderName(): String {
        return loggedInProviderName
    }

    override fun getUserType(): UserType? {
        return profile?.userType
    }

    override fun getProfile(): StitchUserProfile? {
        return profile
    }

    override fun getIdentities(): MutableList<out StitchUserIdentity>? {
        return profile?.identities
    }

    override fun getId(): String {
        return id
    }

    override fun getDeviceId(): String {
        return deviceId
    }

    override fun isLoggedIn(): Boolean {
        return isLoggedIn
    }

    override fun getLastAuthActivity(): Date {
        return lastAuthActivity
    }

    override fun getCustomData(): Document {
        return customData ?: Document()
    }
}
