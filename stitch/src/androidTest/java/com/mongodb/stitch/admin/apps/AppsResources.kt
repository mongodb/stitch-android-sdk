package com.mongodb.stitch.admin.apps

import com.android.volley.Request.Method.POST
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.gms.tasks.Task
import com.mongodb.stitch.admin.Apps
import com.mongodb.stitch.admin.objMapper

/// View into a specific application
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppResponse(@JsonProperty("_id") val id: String,
                       @JsonProperty("name") val name: String,
                       @JsonProperty("client_app_id") val clientAppId: String)

/// POST a new application
/// - parameter name: name of the new application
/// - parameter defaults: whether or not to enable default values
internal fun Apps.create(name: String, defaults: Boolean = false): Task<AppResponse> {
    return this.httpClient.executeRequest(
            POST,
            "${this.url}?defaults=$defaults",
            "{\"name\": \"$name\"}"
    ).continueWith { task ->
        if (!task.isSuccessful) {
            throw task.exception!!
        }

        objMapper.readValue(
            task.result,
            AppResponse::class.java
        )
    }
}

/// GET an application
/// - parameter id: id for the application
internal fun Apps.app(appId: String): Apps.App {
    return Apps.App(this.httpClient, "$url/$appId")
}
