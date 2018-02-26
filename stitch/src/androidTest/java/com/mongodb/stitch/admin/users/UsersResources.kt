package com.mongodb.stitch.admin.users

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.admin.Apps


/// Creates a new user for an application
internal data class UserCreator(val email: String,
                                val password: String)

/// View of a User of an application
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class UserResponse(@JsonProperty("_id") val id: String?)

/// GET a user of an application
/// - parameter uid: id of the user
internal fun Apps.App.Users.user(uid: String): Apps.App.Users.User {
    return Apps.App.Users.User(this.httpClient, "$url/$uid")
}
