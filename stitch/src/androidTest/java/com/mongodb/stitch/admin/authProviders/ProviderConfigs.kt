package com.mongodb.stitch.admin.authProviders

sealed class ProviderConfigs {
    object Anon: ProviderConfigs()
    class Userpass(emailConfirmationUrl: String,
                   resetPasswordUrl: String,
                   confirmEmailSubject: String,
                   resetPasswordSubject: String)
    class Custom(signingKey: String)
}

internal data class ProviderConfigWrapper(val type: String,
                                          val config: ProviderConfigs)