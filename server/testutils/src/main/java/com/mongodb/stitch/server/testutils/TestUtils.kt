package com.mongodb.stitch.server.testutils

import com.mongodb.stitch.server.core.StitchAppClient

// StitchAppClient extensions for kotlin
inline fun <reified T> StitchAppClient.callFunction(name: String, args: List<Any>): T {
    return this.callFunction(name, args, T::class.java)
}
