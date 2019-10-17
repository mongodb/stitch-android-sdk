package com.mongodb.stitch.core.admin.authProviders

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.bson.types.ObjectId

sealed class ProviderConfigs(@JsonIgnore val type: String) {
    object Anon : ProviderConfigs("anon-user")
    object ApiKey : ProviderConfigs("api-key")
    data class Userpass(
        @JsonProperty("emailConfirmationUrl") val emailConfirmationUrl: String,
        @JsonProperty("resetPasswordUrl") val resetPasswordUrl: String,
        @JsonProperty("confirmEmailSubject") val confirmEmailSubject: String,
        @JsonProperty("resetPasswordSubject") val resetPasswordSubject: String
    ) : ProviderConfigs("local-userpass")

    data class Custom(@JsonProperty("signingKey") val signingKey: String) : ProviderConfigs("custom-token")
    data class Function(@JsonProperty("authFunctionId") val authFunctionId: String,
                        @JsonProperty("authFunctionName") val authFunctionName: String) : ProviderConfigs("custom-function")
}

data class ProviderConfigWrapper(
    @JsonProperty("config")
    val config: ProviderConfigs?,
    @JsonProperty("type") val type: String = config?.type
            ?: "anon-user"
)
