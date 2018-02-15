package com.mongodb.stitch.admin.services

sealed class ServiceConfigs {
    object Http: ServiceConfigs()
    class AwsSes(region: String, accessKeyId: String, secretAccessKey: String): ServiceConfigs()
    class Twilio(accountSid: String, authToken: String): ServiceConfigs()
}

internal data class ServiceConfigWrapper(val name: String,
                                         val type: String,
                                         val config: ServiceConfigs)
