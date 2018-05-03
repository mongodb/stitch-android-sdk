package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.StitchCredential
import com.mongodb.stitch.core.internal.common.MemoryStorage
import com.mongodb.stitch.core.internal.net.Method
import com.mongodb.stitch.core.internal.net.OkHttpTransport
import com.mongodb.stitch.core.internal.net.StitchAuthRequest
import com.mongodb.stitch.core.internal.net.StitchRequestClient

class StitchAdminClient private constructor(
        private val adminAuth: StitchAdminAuth,
        private val authRoutes: StitchAdminAuthRoutes
) {
    companion object {
        const val apiPath = "/api/admin/v3.0"
        private const val defaultServerUrl = "http://localhost:9090"
        private const val defaultTransportTimeoutMillis = 15000L

        fun create(baseUrl: String = defaultServerUrl,
                   timeoutMillis: Long = defaultTransportTimeoutMillis): StitchAdminClient {
            val requestClient = StitchRequestClient(
                    baseUrl,
                    OkHttpTransport(),
                    timeoutMillis)
            val authRoutes = StitchAdminAuthRoutes()

            val adminAuth = StitchAdminAuth(
                    requestClient,
                    authRoutes,
                    MemoryStorage())

            return StitchAdminClient(adminAuth, authRoutes)
        }
    }

    fun apps(groupId: String): Apps {
        return Apps(
                this.adminAuth,
                "${StitchAdminClient.apiPath}/groups/$groupId/apps"
        )
    }

    fun adminProfile(): StitchAdminUserProfile {
        val reqBuilder = StitchAuthRequest.Builder()
        reqBuilder.withMethod(Method.GET).withPath(authRoutes.profileRoute)

        val response = adminAuth.doAuthenticatedRequest(reqBuilder.build())

        return objMapper.readValue(response.body, StitchAdminUserProfile::class.java)
    }

    fun logInWithCredential(credential: StitchCredential): StitchAdminUser {
        return adminAuth.loginWithCredentialBlocking(credential)
    }

    fun logout() {
        return adminAuth.logoutBlocking()
    }
}
