package com.mongodb.stitch

import com.google.android.gms.tasks.Task
import com.mongodb.stitch.android.test.BuildConfig
import java.util.concurrent.CountDownLatch

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
