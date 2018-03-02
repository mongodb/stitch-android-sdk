package com.mongodb.stitch.admin.services

import com.fasterxml.jackson.annotation.JsonProperty

sealed class ServiceConfigs {
    object Http: ServiceConfigs()
    data class AwsSes(val region: String,
                      val accessKeyId: String,
                      val secretAccessKey: String): ServiceConfigs()
    data class Twilio(val accountSid: String,
                      val authToken: String): ServiceConfigs()
    data class Mongo(@JsonProperty("uri") val uri: String): ServiceConfigs()
    data class GCM(@JsonProperty("senderId") val senderId: String,
                   @JsonProperty("apiKey") val apiKey: String): ServiceConfigs()
}

internal data class ServiceConfigWrapper(val name: String,
                                         val type: String,
                                         val config: ServiceConfigs)
