package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes

class StitchAdminAuthRoutes : StitchAuthRoutes {
    override fun getSessionRoute(): String {
        return "${StitchAdminClient.apiPath}/auth/session"
    }

    override fun getProfileRoute(): String {
        return "${StitchAdminClient.apiPath}/auth/profile"
    }

    override fun getAuthProviderRoute(providerName: String?): String {
        return "${StitchAdminClient.apiPath}/auth/providers/$providerName"
    }

    override fun getAuthProviderLoginRoute(providerName: String?): String {
        return "${getAuthProviderRoute(providerName)}/login"
    }

    override fun getAuthProviderLinkRoute(providerName: String?): String {
        return "${getAuthProviderLoginRoute(providerName)}?link=true"
    }
}
