package com.mongodb.stitch.core.admin.services.rules

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.core.admin.Apps
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

enum class FcmActions {
    @JsonProperty("send") Send
}

sealed class RuleCreator {
    data class Aws(val name: String, val actions: Set<String>) : RuleCreator()
    data class AwsS3(val name: String, val actions: Set<AwsS3Actions>) : RuleCreator()
    data class AwsSes(val name: String, val actions: Set<AwsSesActions>) : RuleCreator()
    data class Fcm(val name: String, val actions: Set<FcmActions>) : RuleCreator()
    data class Http(val name: String, val actions: Set<HttpActions>) : RuleCreator()

    data class MongoDb(
        val database: String,
        val collection: String,
        val roles: List<Role>,
        val schema: Schema
    ) : RuleCreator() {
        data class Role(
            val name: String = "default",
            @JsonProperty("apply_when")
            val applyWhen: Document = Document(),
            val fields: Document = Document(),
            @JsonProperty("additional_fields")
            val additionalFields: AdditionalFields = AdditionalFields(),
            val read: Boolean = true,
            val write: Boolean? = null,
            val insert: Boolean = true,
            val delete: Boolean = true
        ) {
            data class AdditionalFields(
                val write: Boolean = true,
                val read: Boolean = true
            )
        }
        data class Schema(
            val properties: Document = Document("_id", Document("bsonType", "objectId"))
        )
    }
    data class Twilio(val name: String, val actions: Set<TwilioActions>) : RuleCreator()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class RuleResponse(@JsonProperty("_id") val _id: String)

// / GET a rule
// / - parameter id: id of the requested rule
fun Apps.App.Services.Service.Rules.rule(id: String): Apps.App.Services.Service.Rules.Rule {
    return Apps.App.Services.Service.Rules.Rule(this.adminAuth, "$url/$id")
}
