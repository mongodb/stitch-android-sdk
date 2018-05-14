package com.mongodb.stitch.core.admin.services

import com.fasterxml.jackson.annotation.JsonProperty

sealed class ServiceConfigs {
    object Http : ServiceConfigs()
    data class AwsS3(val region: String, val accessKeyId: String, val secretAccessKey: String) : ServiceConfigs()
    data class AwsSes(val region: String, val accessKeyId: String, val secretAccessKey: String) : ServiceConfigs()
    data class Twilio(@JsonProperty("sid") val accountSid: String, @JsonProperty("auth_token") val authToken: String) : ServiceConfigs()
    data class Mongo(@JsonProperty("uri") val uri: String) : ServiceConfigs()
}

data class ServiceConfigWrapper(
    val name: String,
    val type: String,
    val config: ServiceConfigs
)
