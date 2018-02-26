package com.mongodb.stitch.admin.authProviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.admin.Apps

/// View into a specific auth provider
data class AuthProvidersResponse(@JsonProperty("_id") val id: String,
                                 @JsonProperty("disabled") val disabled: Boolean,
                                 @JsonProperty("name") val name: String,
                                 @JsonProperty("type") val type: String)

internal fun Apps.App.AuthProviders.authProvider(providerId: String): Apps.App.AuthProviders.AuthProvider {
    /// GET an auth provider
    /// - parameter providerId: id of the provider
    return Apps.App.AuthProviders.AuthProvider(this.httpClient, "$url/$providerId")
}
