package com.mongodb.stitch

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.Task
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.test.BuildConfig
import okhttp3.mockwebserver.MockResponse
import java.util.concurrent.CountDownLatch
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Convenience method that allows suspension using a [CountDownLatch].
 *
 * @param[count] how many mutexes in this latch. defaults to 1
 * @param[block] block of code to be executed. includes [CountDownLatch] context
 *
 * @return block result
 */
inline fun <R> latch(count: Int = 1, block: CountDownLatch.() -> R): R {
    // instantiate a new latch with the count arg
    val latch = CountDownLatch(count)
    // call the block arg on the latch object
    val r = latch.block()
    // wait until the latch has been counted down
    latch.await()
    // return the result of the block
    return r
}

fun clearStitchClient(ctx: Context, stitchClient: StitchClient) {
    // clear all instances of the internal [SharedPreferences] to start with a clean slate
    stitchClient.properties.clear()
    stitchClient.javaClass.kotlin.declaredMemberProperties.first {
        it.name == "_preferences"
    }.also { it.isAccessible = true }.get(stitchClient).also {
        (it as SharedPreferences).edit().clear().commit()
    }

    // clear out the global preferences as well
    val globPrefPath = String.format(StitchClientTest.SHARED_PREFERENCES_NAME, stitchClient.appId)
    val globalPreferences = ctx.getSharedPreferences(
            globPrefPath,
            Context.MODE_PRIVATE
    )
    globalPreferences.edit().clear().commit()
}

/**
 * Convenience method that wraps a [Task] in the [latch] method, awaiting the result.
 *
 * @param[task] pending task to wait for
 *
 * @return result of the task after it's been completed
 */
fun <T> await(task: Task<T>): T {
    // run the task and de-latch upon completion
    latch { task.addOnCompleteListener { this.countDown() } }

    // return task result
    return task.result
}

/**
 * Custom assertion for arbitrary booleans and exceptions.
 *
 * @param[assertion] boolean value to assert truthiness
 * @param[exception] optional exception to present in the event of a failure
 */
fun assertThat(assertion: Boolean, exception: Exception? = null) {
    if (BuildConfig.DEBUG && !assertion) {
        if (exception != null) {
            throw exception
        } else {
            throw AssertionError("Failed assertion")
        }
    }
}

/**
 * Convenience builder for [MockResponse] class
 *
 * @param[body] string based body of response
 * @param[responseCode] HTTP status code of response
 * @param[contentType] Content-Type header of response
 * @param[block] builder block for functional building of [MockResponse] beyond previous params
 */
fun mockResponseBuilder(body: String,
                        responseCode: Int = 200,
                        contentType: String = "application/json",
                        block: MockResponse.() -> Unit = {}): MockResponse {
    val mockResponse = MockResponse()
    mockResponse.setBody(body)
    mockResponse.setResponseCode(responseCode)
    mockResponse.setHeader("Content-Type", contentType)
    mockResponse.block()
    return mockResponse
}
