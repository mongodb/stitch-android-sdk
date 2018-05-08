package com.mongodb.stitch.core.admin.apps

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.objMapper
import com.mongodb.stitch.core.internal.net.Method
import com.mongodb.stitch.core.internal.net.StitchAuthRequest

// / View into a specific application
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppResponse(
    @JsonProperty("_id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("client_app_id") val clientAppId: String
)

// / POST a new application
// / - parameter name: name of the new application
// / - parameter defaults: whether or not to enable default values
fun Apps.create(name: String, defaults: Boolean = false): AppResponse {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.POST)
            .withPath("${this.url}?defaults=$defaults")
            .withBody("{\"name\": \"$name\"}".toByteArray())

    val response = adminAuth.doAuthenticatedRequest(reqBuilder.build())
    return objMapper.readValue(
            response.body,
            AppResponse::class.java
    )
}

// / GET an application
// / - parameter id: id for the application
fun Apps.app(appId: String): Apps.App {
    return Apps.App(this.adminAuth, "$url/$appId")
}
