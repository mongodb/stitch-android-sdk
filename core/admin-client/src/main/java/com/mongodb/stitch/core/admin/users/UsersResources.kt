package com.mongodb.stitch.core.admin.users

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.core.admin.Apps


/// Creates a new user for an application
data class UserCreator(val email: String,
                       val password: String)

/// View of a User of an application
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserResponse(@JsonProperty("_id") val id: String?)

/// GET a user of an application
/// - parameter uid: id of the user
fun Apps.App.Users.user(uid: String): Apps.App.Users.User {
    return Apps.App.Users.User(this.adminAuth, "$url/$uid")
}
