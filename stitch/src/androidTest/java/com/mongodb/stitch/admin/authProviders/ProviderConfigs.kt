package com.mongodb.stitch.admin.authProviders

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

enum class GoogleMetadataFields(val value: String) {
    Name("name"),
    Email("email")
}

sealed class ProviderConfigs(@JsonIgnore val type: String) {
    object Anon: ProviderConfigs("anon-user")
    object ApiKey: ProviderConfigs("api-key")
    data class Google(@JsonProperty("clientId") val clientId: String,
                      @JsonProperty("clientSecret") val clientSecret: String):
            ProviderConfigs("oauth2-google")
    data class Userpass(@JsonProperty("emailConfirmationUrl") val emailConfirmationUrl: String,
                        @JsonProperty("resetPasswordUrl") val resetPasswordUrl: String,
                        @JsonProperty("confirmEmailSubject") val confirmEmailSubject: String,
                        @JsonProperty("resetPasswordSubject") val resetPasswordSubject: String): ProviderConfigs("local-userpass")
    data class Custom(@JsonProperty("signingKey") val signingKey: String): ProviderConfigs("custom-token")

}

internal data class ProviderConfigWrapper(@JsonProperty("config")
                                          val config: ProviderConfigs?,
                                          @JsonProperty("type") val type: String = config?.type ?: "anon-user")
