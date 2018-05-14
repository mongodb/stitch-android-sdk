package com.mongodb.stitch.core.admin.services.rules

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.core.internal.net.Method

enum class TwilioActions {
    @JsonProperty("send") Send
}

enum class AwsSesActions {
    @JsonProperty("send") Send
}

sealed class RuleCreator {
    data class Http(val name: String, val actions: Set<Method>) : RuleCreator()
    data class Twilio(val name: String, val actions: Set<TwilioActions>) : RuleCreator()
    data class AwsSes(val name: String, val actions: Set<AwsSesActions>) : RuleCreator()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class RuleResponse
