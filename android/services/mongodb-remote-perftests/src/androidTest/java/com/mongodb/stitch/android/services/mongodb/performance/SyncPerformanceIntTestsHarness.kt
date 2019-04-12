package com.mongodb.stitch.android.services.mongodb.performance

import android.support.test.InstrumentationRegistry
import android.util.Log

import com.google.android.gms.tasks.Tasks

import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyCredential
import org.bson.BsonArray
import org.bson.BsonDateTime
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import org.bson.types.ObjectId

import java.util.Date

typealias TestDefinition = (ctx: SyncPerformanceTestContext, numDocs: Int, docSize: Int) -> Unit
typealias BeforeBlock = TestDefinition
typealias AfterBlock = TestDefinition

class TestParams(val runId: ObjectId, val testName: String) {

    val asBson by lazy {
        Document(
            mapOf(
                "runId" to runId,
                "name" to this.testName,
                "dataProbeGranularityMs" to SyncPerformanceTestUtils.getDataGranularity(),
                "numOutliersEachSide" to SyncPerformanceTestUtils.getNumOutliers(),
                "numIters" to SyncPerformanceTestUtils.getNumIters(),
                "date" to BsonDateTime(Date().time),
                "sdk" to "android",
                "host" to SyncPerformanceTestUtils.getHostname(),
                "stitchHost" to SyncPerformanceTestUtils.getStitchHostname(),
                "results" to BsonArray()
            )
        )
    }
}

class SyncPerformanceIntTestsHarness : BaseStitchAndroidIntTest() {
    // Private constants
    private val mongodbUriProp = "test.stitch.mongodbURI"
    private val stitchAPIKeyProp = "test.stitch.androidPerfStitchAPIKey"

    private val stitchOutputAppName = "android-sdk-perf-testing-yuvef"
    private val stitchOutputDbName = "performance"
    private val stitchOutputCollName = "results"

    internal val stitchTestDbName = "performance"
    internal val stitchTestCollName = "rawTestCollAndroid"

    private val transport by lazy { OkHttpInstrumentedTransport() }

    // Private variables
    internal lateinit var outputClient: StitchAppClient
    internal lateinit var outputMongoClient: RemoteMongoClient
    internal lateinit var outputColl: RemoteMongoCollection<Document>

    fun getStitchAPIKey(): String {
        return InstrumentationRegistry.getArguments().getString(stitchAPIKeyProp, "")
    }

    /**
     * Get the uri for where mongodb is running locally.
     */
    fun getMongoDbUri(): String {
        return InstrumentationRegistry.getArguments().getString(mongodbUriProp, "mongodb://localhost:26000")
    }

    override fun getStitchBaseURL(): String {
        return SyncPerformanceTestUtils.getStitchHostname()
    }

    override fun getAppClientConfigurationBuilder(): StitchAppClientConfiguration.Builder {
        return super.getAppClientConfigurationBuilder().withTransport(transport)
    }

    override fun setup() {
        super.setup()
    }

    override fun teardown() {
        super.teardown()
    }

    fun logMessage(message: String) {
        if (SyncPerformanceTestUtils.shouldOutputToStdOut()) {
            Log.d("PerfLog", message)
        }
    }

    fun setupOutputClient() {
        outputClient = when (Stitch.hasAppClient(stitchOutputAppName)) {
            true -> Stitch.getAppClient(stitchOutputAppName)
            false -> Stitch.initializeAppClient(
                stitchOutputAppName,
                StitchAppClientConfiguration.Builder()
                    .withNetworkMonitor(testNetworkMonitor)
                    .withTransport(transport)
                    .build()
            )
        }

        if (!outputClient.auth.isLoggedIn) {
            Tasks.await(
                outputClient.auth.loginWithCredential(UserApiKeyCredential(getStitchAPIKey())))
        }

        outputMongoClient = outputClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
        outputColl = outputMongoClient
            .getDatabase(stitchOutputDbName)
            .getCollection(stitchOutputCollName)
    }

    fun handleTestResult(
        testParams: TestParams,
        runResult: RunResult,
        resultId: ObjectId,
        numDoc: Int,
        docSize: Int
    ): Boolean {
        val failuresBson = runResult.failures.map { it.asBson }
        if (failuresBson.size >= (SyncPerformanceTestUtils.getNumIters() + 1) / 2) {
            logMessage(String.format("(FAILED) %s failed %d times (docSize=%d, numDocs=%d",
                testParams.testName, failuresBson.size, docSize, numDoc))
            failuresBson.forEach { logMessage(it.toJson()) }

            if (SyncPerformanceTestUtils.shouldOutputToStitch()) {
                val filterDocument = Document("_id", resultId)
                val updateDocument = Document(mapOf(
                    "\$push" to Document("results", Document(mapOf(
                        "numDocs" to numDoc,
                        "docSize" to docSize,
                        "success" to false,
                        "failures" to failuresBson)))
                ))
                Tasks.await(outputColl.updateOne(filterDocument, updateDocument))
            }

            return false
        } else {
            logMessage(String.format("(SUCCESS): %s (docSize: %d, numDocs=%d): %s",
                testParams.testName, docSize, numDoc, runResult.asBson.toJson()))

            if (SyncPerformanceTestUtils.shouldOutputToStitch()) {
                val filterDocument = Document("_id", resultId)
                var runResultBson = runResult.asBson
                if (failuresBson.size > 0) {
                    runResultBson.append("failures", failuresBson)
                }
                val updateDocument = Document("\$push", Document("results", runResultBson))
                Tasks.await(outputColl.updateOne(filterDocument, updateDocument))
            }

            return true
        }
    }

    private fun getPerformanceTestingContext(testParams: TestParams): SyncPerformanceTestContext {
        if (SyncPerformanceTestUtils.getStitchHostname() == SyncPerformanceTestUtils.STITCH_PROD_HOST) {
            return ProductionPerformanceContext(
                this@SyncPerformanceIntTestsHarness, testParams, transport)
        } else {
            return LocalPerformanceTestContext(
                this@SyncPerformanceIntTestsHarness, testParams, transport)
        }
    }

    fun runPerformanceTestWithParams(
        testName: String,
        runId: ObjectId,
        testDefinition: TestDefinition,
        beforeEach: BeforeBlock = { _, _, _ -> },
        afterEach: AfterBlock = { _, _, _ -> }
    ) {
        val testParams = TestParams(runId, testName)
        setupOutputClient()

        val resultId = ObjectId()
        if (SyncPerformanceTestUtils.shouldOutputToStitch()) {
            val doc = testParams.asBson.append("_id", resultId)
                .append("stitchHostName", BsonString(getStitchBaseURL()))
                .append("status", BsonString("In Progress"))
            Tasks.await(outputColl.insertOne(doc))
            testHarness.logMessage(String.format("Starting Test: %s", doc.toJson()))
        }

        var testSuccess = true
        for (docSize in SyncPerformanceTestUtils.getDocSizes()) {
            for (numDoc in SyncPerformanceTestUtils.getNumDocs()) {
                val ctx = getPerformanceTestingContext(testParams)

                val runResult = RunResult(numDoc, docSize)

                for (iter in 1..SyncPerformanceTestUtils.getNumIters()) {
                    try {
                        ctx.setup()
                        beforeEach(ctx, numDoc, docSize)

                        val result = ctx.runSingleIteration(numDoc, docSize, testDefinition)
                        runResult.runTimes.add(result.timeTaken)
                        runResult.diskUsages.add(result.diskUsage)
                        runResult.memoryUsages.add(result.memoryUsage)
                        runResult.activeThreadCounts.add(result.activeThreadCount)
                        runResult.networkReceivedBytes.add(result.networkReceived)
                        runResult.networkSentBytes.add(result.networkSent)

                        afterEach(ctx, numDoc, docSize)
                    } catch (e: Exception) {
                        runResult.failures.add(FailureResult(iter, e.localizedMessage,
                            e.stackTrace.map { BsonString(it.toString()) }))
                    } finally {
                        ctx.teardown()
                    }
                }

                if (!handleTestResult(testParams, runResult, resultId, numDoc, docSize)) {
                    testSuccess = false
                }
            }
        }

        if (SyncPerformanceTestUtils.shouldOutputToStitch()) {
            val filter = Document("_id", resultId)
            var update: Document
            if (testSuccess) {
                update = Document("\$set", Document("status", "Success"))
            } else {
                update = Document("\$set", Document("status", "Failure"))
            }
            Tasks.await(outputColl.updateOne(filter, update))
        }
    }
}

data class FailureResult(
    val iteration: Int,
    val reason: String,
    val stackTrace: List<BsonString>
) {
    val asBson by lazy {
        Document(
            mapOf(
                "iteration" to BsonInt32(iteration),
                "reason" to BsonString(reason),
                "stackTrace" to BsonArray(stackTrace)
            )
        )
    }
}

private class DataBlock(data: DoubleArray, numOutliers: Int) {
    var mean = 0.0
    var median = 0.0
    var min = 0.0
    var max = 0.0
    var stdDev = 0.0

    // Compute relevant metrics on init
    init {
        if (numOutliers >= 0 && data.size > 2 * numOutliers) {
            val newData = data.sortedArray().slice((numOutliers)..(data.size - 1 - numOutliers))
            min = newData.first()
            max = newData.last()

            val dataSize = newData.size
            val middle = newData.size / 2

            if (dataSize % 2 == 0) {
                median = (newData[middle - 1] + newData[middle]) / 2
            } else {
                median = newData[middle]
            }

            mean = newData.average()
            stdDev = newData.fold(0.0) {
                accumulator, next -> accumulator + (next - mean) * (next - mean)
            }
            stdDev = Math.sqrt(stdDev / dataSize)
        }
    }

    fun toBson(): Document {
        return Document(
            mapOf(
                "min" to BsonDouble(this.min),
                "max" to BsonDouble(this.max),
                "mean" to BsonDouble(this.mean),
                "median" to BsonDouble(this.median),
                "stdDev" to BsonDouble(this.stdDev)
            )
        )
    }
}

class PartialResult {
    var activeThreadCount: Double = 0.0
    var memoryUsage: Double = 0.0
    var timeTaken: Double = 0.0
    var diskUsage: Double = 0.0
    var networkSent = 0.0
    var networkReceived = 0.0
}

open class RunResult(numDocs: Int, docSize: Int) {
    val runTimes = arrayListOf<Double>()
    val networkSentBytes = arrayListOf<Double>()
    val networkReceivedBytes = arrayListOf<Double>()
    val memoryUsages = arrayListOf<Double>()
    val diskUsages = arrayListOf<Double>()
    var activeThreadCounts = arrayListOf<Double>()
    var failures = arrayListOf<FailureResult>()
    val numOutliers = SyncPerformanceTestUtils.getNumOutliers()

    val asBson by lazy {
        Document(
            mapOf(
                "numDocs" to BsonInt32(numDocs),
                "docSize" to BsonInt32(docSize),
                "success" to true,
                "timeMs" to DataBlock(runTimes.toDoubleArray(), numOutliers).toBson(),
                "networkSentBytes" to DataBlock(networkSentBytes.toDoubleArray(), numOutliers).toBson(),
                "networkReceivedBytes" to DataBlock(networkReceivedBytes.toDoubleArray(), numOutliers).toBson(),
                "memoryBytes" to DataBlock(memoryUsages.toDoubleArray(), numOutliers).toBson(),
                "diskBytes" to DataBlock(diskUsages.toDoubleArray(), numOutliers).toBson(),
                "activeThreadCounts" to DataBlock(activeThreadCounts.toDoubleArray(), numOutliers).toBson(),
                "numFailures" to failures.size
            )
        )
    }
}
