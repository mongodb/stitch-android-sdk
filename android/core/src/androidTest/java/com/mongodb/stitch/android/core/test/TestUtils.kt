package com.mongodb.stitch.android.core.test

import com.google.android.gms.tasks.Task
import com.mongodb.stitch.android.core.StitchAppClient

// StitchAppClient extensions for kotlin
inline fun <reified T> StitchAppClient.callFunction(name: String, args: List<Any>): Task<T> {
    return this.callFunction(name, args, T::class.java)
}
