package com.mongodb.stitch.core.admin.services.rules

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.bson.Document

enum class AwsS3Actions {
    @JsonProperty("put") Put,
    @JsonProperty("signPolicy") SignPolicy
}

enum class AwsSesActions {
    @JsonProperty("send") Send
}

enum class HttpActions {
    @JsonProperty("get") Get,
    @JsonProperty("post") Post,
    @JsonProperty("put") Put,
    @JsonProperty("delete") Delete,
    @JsonProperty("head") Head,
    @JsonProperty("patch") Patch
}

enum class TwilioActions {
    @JsonProperty("send") Send
}

sealed class RuleCreator {
    data class AwsS3(val name: String, val actions: Set<AwsS3Actions>) : RuleCreator()
    data class AwsSes(val name: String, val actions: Set<AwsSesActions>) : RuleCreator()
    data class Http(val name: String, val actions: Set<HttpActions>) : RuleCreator()
    data class MongoDb(val namespace: String, private val rule: Document) : RuleCreator() {
        @JsonAnyGetter
        fun toRule(): Map<String, Any> {
            rule["namespace"] = namespace
            return rule
        }
    }
    data class Twilio(val name: String, val actions: Set<TwilioActions>) : RuleCreator()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class RuleResponse
