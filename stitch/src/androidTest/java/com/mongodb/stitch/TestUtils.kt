package com.mongodb.stitch

import android.content.Context
import android.content.SharedPreferences
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.mongodb.stitch.admin.userRegistrations.sendConfirmation
import com.mongodb.stitch.android.StitchClient
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProvider
import com.mongodb.stitch.android.push.PushClient.SHARED_PREFERENCES_NAME
import com.mongodb.stitch.android.test.BuildConfig
import com.mongodb.stitch.testHarness.TestHarness
import com.mongodb.stitch.testHarness.buildClientTestHarness
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.staticProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertNotNull

/**
 * Convenience method that allows suspension using a [CountDownLatch].
 *
 * @param[count] how many mutexes in this latch. defaults to 1
 * @param[block] block of code to be executed. includes [CountDownLatch] context
 *
 * @return block result
 */
inline fun <R> latch(count: Int = 1, timeout: Long = 5000, block: CountDownLatch.() -> R): R {
    // instantiate a new latch with the count arg
    val latch = CountDownLatch(count)
    // call the block arg on the latch object
    val r = latch.block()
    // wait until the latch has been counted down
    latch.await(timeout, TimeUnit.MILLISECONDS)
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
    val globPrefPath = String.format(SHARED_PREFERENCES_NAME, stitchClient.appId)
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

fun privateInnerClassFor(kClass: KClass<*>, name: String): KClass<*> {
    val kClass = kClass.nestedClasses.firstOrNull { it.simpleName == name }

    assertNotNull(kClass)

    return kClass!!
}

fun privateStaticPropertyFor(kClass: KClass<*>, name: String): KProperty<*> {
    val prop = kClass.staticProperties.firstOrNull { it.name == name }

    assertNotNull(prop)
    prop!!.isAccessible = true
    return prop
}

fun privateMemberPropertyFor(kClass: KClass<*>, name: String): KProperty<*> {
    val prop = kClass.declaredMemberProperties.firstOrNull { it.name == name }

    assertNotNull(prop)
    prop!!.isAccessible = true
    return prop
}

fun privateFunctionFor(kClass: KClass<*>, name: String): KFunction<*> {
    val func = kClass.declaredFunctions.firstOrNull { it.name == name }

    assertNotNull(func)

    func!!.isAccessible = true
    return func
}

@Ignore
@RunWith(AndroidJUnit4::class)
open class StitchTestCase {
    internal val instrumentationCtx: Context by lazy { InstrumentationRegistry.getContext() }

    internal var harness = buildClientTestHarness(context = instrumentationCtx)
    internal var stitchClient: StitchClient = harness.stitchClient!!
    internal val email: String = "stitch@10gen.com"
    internal val pass: String = "stitchuser"

    @Before
    open fun setup() {
        this.harness = buildClientTestHarness(context = instrumentationCtx)
        this.stitchClient = harness.stitchClient!!
        await(this.stitchClient.logout())
    }

    @After
    open fun clear() {
        await(this.stitchClient.logout())
        await(this.harness.teardown())
    }

    fun registerAndLogin(email: String = this.email,
                         pass: String = this.pass): String {
        await(this.stitchClient.register(email, pass))
        val conf = await(this.harness.app.userRegistrations.sendConfirmation(email))
        await(this.stitchClient.emailConfirm(conf.token, conf.tokenId))
        return await(this.stitchClient.logInWithProvider(EmailPasswordAuthProvider(email, pass)))
    }
}
