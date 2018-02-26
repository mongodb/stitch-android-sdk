package com.mongodb.stitch.admin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.StitchClientFactory
import com.mongodb.stitch.android.StitchException
import com.mongodb.stitch.android.auth.AuthProvider
import com.mongodb.stitch.android.auth.UserProfile
import com.mongodb.stitch.await
import com.mongodb.stitch.testHarness.defaultServerUrl
import org.bson.Document

internal class StitchAdminClient private constructor(private val context: Context,
                                                     private val baseUrl: String) {
    internal companion object {
        internal const val TAG = "StitchAdminClient"

        internal const val PLATFORM = "android"
        internal const val SHARED_PREFERENCES_NAME = "com.mongodb.stitch.sdk.SharedPreferences.%s"
        internal const val PREF_DEVICE_ID_NAME = "deviceId"

        internal fun create(context: Context,
                            baseUrl: String = defaultServerUrl): Task<StitchAdminClient> {
            val completionSource = TaskCompletionSource<StitchAdminClient>()
            completionSource.setResult(StitchAdminClient(context, baseUrl))
            return completionSource.task
        }
    }

    private val httpClient: StitchClient by lazy {
        await(StitchClientFactory.create(context, "", baseUrl, "api/admin/v3.0"))
    }

    private val _preferences = context.getSharedPreferences(
            String.format(
                    StitchAdminClient.SHARED_PREFERENCES_NAME,
                    "<admin>"
            ), Context.MODE_PRIVATE
    )

    fun apps(groupId: String): Apps {
        return Apps(httpClient, "groups/$groupId/apps")
    }

    private object DeviceFields {
        internal const val DEVICE_ID = "deviceId"
        internal const val APP_ID = "appId"
        internal const val APP_VERSION = "appVersion"
        internal const val PLATFORM = "platform"
        internal const val PLATFORM_VERSION = "platformVersion"
    }

    private object AuthFields {
        internal val OPTIONS = "options"
        internal val DEVICE = "device"
    }

    /**
     * @return Whether or not this client has stored a device ID.
     */
    private fun hasDeviceId(): Boolean {
        return _preferences.contains(PREF_DEVICE_ID_NAME)
    }

    /**
     * @return The client's device ID if there is one.
     */
    private fun getDeviceId(): String {
        return _preferences.getString(PREF_DEVICE_ID_NAME, "")
    }

    /**
     * @return A [Document] representing the information for this device
     * from the context of this app.
     */
    private fun getDeviceInfo(): Document {
        val info = Document()

        if (hasDeviceId()) {
            info[DeviceFields.DEVICE_ID] = getDeviceId()
        }

        val packageName = context.packageName
        val manager = context.packageManager

        try {
            val pkgInfo = manager.getPackageInfo(packageName, 0)
            info[DeviceFields.APP_VERSION] = pkgInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Error while getting info for app package", e)
            throw StitchException.StitchClientException(e)
        }

        info[DeviceFields.APP_ID] = packageName
        info[DeviceFields.PLATFORM] = PLATFORM
        info[DeviceFields.PLATFORM_VERSION] = Build.VERSION.RELEASE

        return info
    }

    /**
     * @param provider The provider that will handle authentication.
     * @return A [Document] representing all information required for
     * an auth request against a specific provider.
     */
    private fun getAuthRequest(provider: AuthProvider): Document {
        return getAuthRequest(provider.authPayload)
    }

    /**
     * @param request Arbitrary document for authentication
     * @return A [Document] representing all information required for
     * an auth request against a specific provider.
     */
    private fun getAuthRequest(request: Document): Document {
        val options = Document()
        options[AuthFields.DEVICE] = getDeviceInfo()
        request[AuthFields.OPTIONS] = options
        return request
    }

    /**
     * Logs the current user in using a specific auth provider.
     *
     * @param authProvider The provider that will handle the login.
     * @return A task containing the ID of the logged in user.
     */
    fun logInWithProvider(authProvider: AuthProvider): Task<String> {
        return httpClient.logInWithProvider(authProvider)
    }

    fun userProfile(): Task<UserProfile> {
        return httpClient.auth!!.userProfile
    }
}
