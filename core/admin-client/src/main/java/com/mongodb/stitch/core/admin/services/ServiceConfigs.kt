package com.mongodb.stitch.core.admin.services

import com.fasterxml.jackson.annotation.JsonProperty

sealed class ServiceConfigs {
    object Http : ServiceConfigs()
    class AwsSes(region: String, accessKeyId: String, secretAccessKey: String) : ServiceConfigs()
    class Twilio(accountSid: String, authToken: String) : ServiceConfigs()
    data class Mongo(@JsonProperty("uri") val uri: String) : ServiceConfigs()
}

data class ServiceConfigWrapper(
    val name: String,
    val type: String,
    val config: ServiceConfigs
)
