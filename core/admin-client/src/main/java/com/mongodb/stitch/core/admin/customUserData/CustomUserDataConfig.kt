package com.mongodb.stitch.core.admin.customUserData

import com.fasterxml.jackson.annotation.JsonProperty

data class CustomUserDataConfig(
    @JsonProperty("mongo_service_id") val mongoServiceId: String,
    @JsonProperty("database_name") val databaseName: String,
    @JsonProperty("collection_name") val collectionName: String,
    @JsonProperty("user_id_field") val userIdField: String,
    @JsonProperty("enabled") val enabled: Boolean
)
