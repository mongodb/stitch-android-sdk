package com.mongodb.stitch.core.admin.userRegistrations

import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.objMapper
import com.mongodb.stitch.core.internal.net.Method
import com.mongodb.stitch.core.internal.net.StitchAuthRequest

// / Class that allows the retrieval of the token
// / and tokenId of a confirmation email, for the sake
// / of skirting email registration
data class ConfirmationEmail(
    val token: String,
    @JsonProperty("token_id") val tokenId: String
)

// / GET confirmation email token and tokenId
// / - parameter email: email that the confirmation email was sent to
fun Apps.App.UserRegistrations.sendConfirmation(email: String): ConfirmationEmail {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.POST)
            .withPath("$url/by_email/$email/send_confirm")

    val response = adminAuth.doAuthenticatedRequest(reqBuilder.build())
    return objMapper.readValue(
            response.body,
            ConfirmationEmail::class.java
    )
}
