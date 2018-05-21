package com.mongodb.stitch.core.admin

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes

class StitchAdminAuthRoutes : StitchAuthRoutes {
    override fun getBaseAuthRoute(): String {
        return "${StitchAdminClient.apiPath}/auth"
    }
    override fun getSessionRoute(): String {
        return "$baseAuthRoute/session"
    }

    override fun getProfileRoute(): String {
        return "$baseAuthRoute/profile"
    }

    override fun getAuthProviderRoute(providerName: String?): String {
        return "$baseAuthRoute/providers/$providerName"
    }

    override fun getAuthProviderLoginRoute(providerName: String?): String {
        return "${getAuthProviderRoute(providerName)}/login"
    }

    override fun getAuthProviderLinkRoute(providerName: String?): String {
        return "${getAuthProviderLoginRoute(providerName)}?link=true"
    }

    override fun getAuthProviderExtensionRoute(providerName: String, path: String): String {
        return "${getAuthProviderRoute(providerName)}/$path"
    }
}
