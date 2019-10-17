package com.mongodb.stitch.core.admin.functions

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.stitch.core.admin.Apps

// / For creating or updating a function of an application
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FunctionCreator(
    @JsonProperty("name") val name: String,
    @JsonProperty("source") val source: String,
    @JsonProperty("can_evaluate") val canEvaluate: String? = null,
    @JsonProperty("private") val private: Boolean? = null
)

// / View of a Function of an application
@JsonIgnoreProperties(ignoreUnknown = true)
data class FunctionResponse(
    @JsonProperty("_id") val id: String,
    @JsonProperty("name") val name: String
)

// / GET a Function of an application
// / - parameter fid: id of the function
fun Apps.App.Functions.function(fid: String): Apps.App.Functions.Function {
    return Apps.App.Functions.Function(this.adminAuth, "$url/$fid")
}
