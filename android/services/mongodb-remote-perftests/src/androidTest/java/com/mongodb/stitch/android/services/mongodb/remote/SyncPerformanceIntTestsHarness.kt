package com.mongodb.stitch.android.services.mongodb.remote

import android.support.test.InstrumentationRegistry
import android.util.Log

import com.google.android.gms.tasks.Tasks

import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyCredential
import org.bson.BsonArray
import org.bson.BsonDateTime
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonString
import org.bson.Document
import org.bson.types.ObjectId

import java.util.Date

typealias TestDefinition = (ctx: SyncPerformanceTestContext, numDocs: Int, docSize: Int) -> Unit
typealias BeforeBlock = TestDefinition
typealias AfterBlock = TestDefinition

data class TestParams(
    val runId: ObjectId,
    val testName: String,
    val numIters: Int = 12,
    val numDocs: IntArray = intArrayOf(),
    val docSizes: IntArray = intArrayOf(),
    val dataProbeGranularityMs: Long = 1500L,
    val numOutliersEachSide: Int = 1,
    val stitchHostName: String = "",
    val outputToStdOut: Boolean = true,
    val outputToStitch: Boolean = true,
    val preserveRawOutput: Boolean = false
) {
    val asBson by lazy {
        Document(
            mapOf(
                "runId" to runId,
                "name" to BsonString(this.testName),
                "dataProbeGranularityMs" to BsonInt64(this.dataProbeGranularityMs),
                "numOutliersEachSide" to BsonInt32(this.numOutliersEachSide),
                "numIters" to BsonInt32(this.numIters),
                "date" to BsonDateTime(Date().time),
                "sdk" to BsonString("android"),
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
    internal val stitchTestCollName = "rawTestColl"
    internal var stitchTestHost = ""

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
        if (stitchTestHost.isNotEmpty()) {
            return stitchTestHost
        }
        return InstrumentationRegistry.getArguments().getString(
            "test.stitch.baseURL",
            "http://10.0.2.2:9090"
        )
    }

    override fun getAppClientConfigurationBuilder(): StitchAppClientConfiguration.Builder {
        return super.getAppClientConfigurationBuilder().withTransport(transport)
    }

    override fun setup() {
        super.setup()

        outputClient = when (Stitch.hasAppClient(stitchOutputAppName)) {
            true -> Stitch.getAppClient(stitchOutputAppName)
            false -> Stitch.initializeAppClient(stitchOutputAppName)
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

    fun runPerformanceTestWithParams(
        testParams: TestParams,
        testDefinition: TestDefinition,
        beforeEach: BeforeBlock,
        afterEach: AfterBlock
    ) {
        stitchTestHost = testParams.stitchHostName

        val resultId = ObjectId()
        if (testParams.outputToStitch) {
            val doc = testParams.asBson.append("_id", resultId)
                .append("stitchHostName", BsonString(getStitchBaseURL()))
            Tasks.await(outputColl.insertOne(doc))
        }

        try {
            for (docSize in testParams.docSizes) {
                for (numDoc in testParams.numDocs) {
                    val ctx = SyncPerformanceTestContext(
                        this@SyncPerformanceIntTestsHarness,
                        testParams,
                        transport
                    )

                    val runResult = (1..testParams.numIters).map {
                        ctx.setup()
                        beforeEach(ctx)
                        val result = ctx.runSingleIteration(numDoc, docSize, testDefinition)
                        afterEach(ctx)
                        ctx.teardown()
                        result
                    }.fold(
                        RunResult(numDoc, docSize, testParams.numOutliersEachSide)
                    ) { acc: RunResult, partialResult: PartialResult ->
                        acc.diskUsages.add(partialResult.diskUsage)
                        acc.memoryUsages.add(partialResult.memoryUsage)
                        acc.networkReceivedBytes.add(partialResult.networkReceived)
                        acc.networkSentBytes.add(partialResult.networkSent)
                        acc.activeThreadCounts.add(partialResult.activeThreadCount)
                        acc.runTimes.add(partialResult.timeTaken)
                        acc
                    }

                    // If we are logging to stdout
                    if (testParams.outputToStdOut) {
                        Log.d("perfTests", runResult.asBson.toJson())
                    }

                    // If we are inserting this into stitch
                    if (testParams.outputToStitch) {
                        val filterDocument = Document("_id", resultId)
                        val updateDocument = Document()
                            .append("\$push", Document("results", runResult.asBson.toJson()))
                        Tasks.await(outputColl.updateOne(filterDocument, updateDocument))
                    }
                }
            }
        } catch (e: Exception) {
            Tasks.await(outputColl.deleteOne(Document("_id", resultId)))
            throw e
        }
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

open class RunResult(numDocs: Int, docSize: Int, numOutliers: Int) {
    val runTimes = arrayListOf<Double>()
    val networkSentBytes = arrayListOf<Double>()
    val networkReceivedBytes = arrayListOf<Double>()
    val memoryUsages = arrayListOf<Double>()
    val diskUsages = arrayListOf<Double>()
    var activeThreadCounts = arrayListOf<Double>()

    val asBson by lazy {
        Document(
            mapOf(
                "numDocs" to BsonInt32(numDocs),
                "docSize" to BsonInt32(docSize),
                "timeMs" to DataBlock(runTimes.toDoubleArray(), numOutliers).toBson(),
                "networkSentBytes" to DataBlock(networkSentBytes.toDoubleArray(), numOutliers).toBson(),
                "networkReceivedBytes" to DataBlock(networkReceivedBytes.toDoubleArray(), numOutliers).toBson(),
                "memoryBytes" to DataBlock(memoryUsages.toDoubleArray(), numOutliers).toBson(),
                "diskBytes" to DataBlock(diskUsages.toDoubleArray(), numOutliers).toBson(),
                "activeThreadCounts" to DataBlock(activeThreadCounts.toDoubleArray(), numOutliers).toBson()
            )
        )
    }
}
