package com.mongodb.stitch.core.admin.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.core.admin.Apps

data class ServiceResponse(
    @JsonProperty("_id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("type") val type: String
)

// / GET a service
// / - parameter id: id of the requested service
fun Apps.App.Services.service(id: String): Apps.App.Services.Service {
    return Apps.App.Services.Service(this.adminAuth, "$url/$id")
}
