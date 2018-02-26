package com.mongodb.stitch.admin.userRegistrations

import com.android.volley.Request.Method.POST
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.gms.tasks.Task
import com.mongodb.stitch.admin.Apps
import com.mongodb.stitch.admin.objMapper

/// Struct that allows the retrieval of the token
/// and tokenId of a confirmation email, for the sake
/// of skirting email registration
internal data class ConfirmationEmail(val token: String,
                                      @JsonProperty("token_id") val tokenId: String)

/// GET confirmation email token and tokenId
/// - parameter email: email that the confirmation email was sent to
internal fun Apps.App.UserRegistrations.sendConfirmation(email: String): Task<ConfirmationEmail> {
    return this.httpClient.executeRequest(POST, "$url/by_email/$email/send_confirm")
            .continueWith { task ->
        if (!task.isSuccessful) {
            throw task.exception!!
        }

        objMapper.readValue(task.result, ConfirmationEmail::class.java)
    }
}
