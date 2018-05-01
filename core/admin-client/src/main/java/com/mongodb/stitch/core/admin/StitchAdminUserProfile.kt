package com.mongodb.stitch.core.admin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.core.auth.internal.models.ApiStitchUserIdentity

@JsonIgnoreProperties(ignoreUnknown = true)
data class StitchAdminUserProfile(
    @JsonProperty("type") val userType: String,
    @JsonProperty("data") val data: Map<String, String>,
    @JsonProperty("identities") val identities: List<ApiStitchUserIdentity>,
    @JsonProperty("roles") val roles: List<Role>
) {

    data class Role(
        @JsonProperty("role_name") val roleName: String,
        @JsonProperty("group_id") val groupId: String
    )
}
