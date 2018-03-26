package com.mongodb.stitch.admin.pushNotifications

import com.android.volley.Request
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.gms.tasks.Task
import com.mongodb.stitch.admin.Apps

data class PushNotificationsCreate(val message: String,
                                   val label: String,
                                   val topic: String,
                                   val state: String = "draft")

@JsonIgnoreProperties(ignoreUnknown = true)
data class PushNotificationsResponse(@JsonProperty("_id") val id: String)

internal fun Apps.App.PushNotifications.send(id: String): Task<Unit> {
    return this.httpClient.executeRequest(
            Request.Method.POST,
            "${this.url}/$id/send"
    ).continueWith { task ->
        if (!task.isSuccessful) {
            throw task.exception!!
        }
    }
}
