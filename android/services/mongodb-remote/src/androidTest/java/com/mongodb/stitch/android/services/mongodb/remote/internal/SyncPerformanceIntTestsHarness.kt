package com.mongodb.stitch.android.services.mongodb.remote.internal

import android.os.Environment
import android.os.StatFs
import android.support.test.InstrumentationRegistry
import android.util.Log

import com.google.android.gms.tasks.Tasks

import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.local.internal.AndroidEmbeddedMongoClientFactory
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.StitchAppClientConfiguration
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.RuleResponse
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import kotlinx.coroutines.Dispatchers.IO

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import org.bson.BsonArray
import org.bson.BsonDateTime
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonString
import org.bson.Document
import org.bson.types.ObjectId

import org.junit.After
import org.junit.Before

import java.util.Date

typealias TestDefinition = (Int, Int) -> Unit

open class SyncPerformanceIntTestsHarness : BaseStitchAndroidIntTest() {

    // Private constants
    private val mongodbUriProp = "test.stitch.mongodbURI"
    private val stitchOutputAppName = "stitchdocsexamples-pqwyr"
    private val stitchOutputDbName = "stress"
    private val stitchOutputCollName = "results"
    private var stitchTestHost = ""

    // Private variables
    private lateinit var outputClient: StitchAppClient
    private lateinit var outputColl: RemoteMongoCollection<Document>
    private lateinit var mdbService: Apps.App.Services.Service
    private lateinit var mdbRule: RuleResponse

    // Public variables
    lateinit var testClient: StitchAppClient
    var testDbName = ObjectId().toHexString()
    var testCollName = ObjectId().toHexString()
    var testNamespace = MongoNamespace(testDbName, testCollName)
    lateinit var testMongoClient: RemoteMongoClient
    lateinit var testColl: RemoteMongoCollection<Document>
    lateinit var testUserId: String

    val testDataSynchronizer: DataSynchronizer
        get() = (testMongoClient as RemoteMongoClientImpl).dataSynchronizer
    val testNetworkMonitor: BaseStitchIntTest.TestNetworkMonitor
        get() = BaseStitchAndroidIntTest.testNetworkMonitor

    /**
     * Get the uri for where mongodb is running locally.
     */
    private fun getMongoDbUri(): String {
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

    private val transport = OkHttpInstrumentedTransport()
    override fun getAppClientConfigurationBuilder(): StitchAppClientConfiguration.Builder {
        return super.getAppClientConfigurationBuilder().withTransport(transport)
    }

    @Before
    override fun setup() {
        super.setup()

        if (!Stitch.hasAppClient(stitchOutputAppName)) {
            outputClient = Stitch.initializeAppClient(stitchOutputAppName)
        } else {
            outputClient = Stitch.getAppClient(stitchOutputAppName)
        }

        if (!outputClient.auth.isLoggedIn) {
            Tasks.await(outputClient.auth.loginWithCredential(AnonymousCredential()))
        }

        outputColl = outputClient
            .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
            .getDatabase(stitchOutputDbName)
            .getCollection(stitchOutputCollName)
    }

    private fun setupIter() {

        // Setup Stitch app to use for the tests
        // We need a new one for each iteration
        super.setup()
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        mdbService = addService(
                app.second,
                "mongodb",
                "mongodb1",
                ServiceConfigs.Mongo(getMongoDbUri())).second

        testDbName = ObjectId().toHexString()
        testCollName = ObjectId().toHexString()
        testNamespace = MongoNamespace(testDbName, testCollName
        )

        val rule = RuleCreator.MongoDb(
                database = testDbName,
                collection = testCollName,
                roles = listOf(RuleCreator.MongoDb.Role(
                        read = true, write = true
                )),
                schema = RuleCreator.MongoDb.Schema())

        mdbRule = addRule(mdbService, rule)

        testClient = getAppClient(app.first)
        testUserId = Tasks.await(testClient.auth.loginWithCredential(AnonymousCredential())).id
        testMongoClient = testClient.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        testColl = testMongoClient.getDatabase(testDbName).getCollection(testCollName)
        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    @After
    override fun teardown() {
        super.teardown()
    }

    private fun teardownIter() {
        val syncedIds = Tasks.await(testColl.sync().syncedIds)
        Tasks.await(testColl.sync().desyncMany(*syncedIds.toTypedArray()))
        Tasks.await(testColl.deleteMany(Document()))

        if (::testMongoClient.isInitialized) {
            (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
            AndroidEmbeddedMongoClientFactory.getInstance().close()
        }

        teardown()
    }

    public fun runPerformanceTestWithParams(
        testParams: TestParams,
        testDefinition: TestDefinition
    ) = runBlocking {
        setup()
        val runtime = Runtime.getRuntime()
        stitchTestHost = testParams.stitchHostName

        val resultId = ObjectId()
        if (testParams.outputToStitch) {
            val doc = testParams.toBson().append("_id", resultId)
            Tasks.await(outputColl.insertOne(doc))
        }

        try {
            for (docSize in testParams.docSizes) {
                for (numDoc in testParams.numDocs) {

                    // There data structures hold the metric results for all n iterations of this test
                    var timeData = arrayListOf<Double>()
                    var cpuData = arrayListOf<Double>()
                    var memoryData = arrayListOf<Double>()
                    var diskData = arrayListOf<Double>()
                    var threadData = arrayListOf<Double>()
                    var networkSentData = arrayListOf<Double>()
                    var networkReceivedData = arrayListOf<Double>()

                    for (iter in 1..testParams.numIters) {

                        // These data structures will have  < (time / dataGranularityMs) for the
                        // point-in-time metrics collected
                        var cpuDataIter = arrayListOf<Double>()
                        var memoryDataIter = arrayListOf<Long>()
                        var threadDataIter = arrayListOf<Int>()

                        // Setup the Stitch Host
                        setupIter()

                        coroutineScope {
                            // Launch coroutine to collect point-in-time data metrics and then delay
                            // for dataProbeGranularityMs
                            val job = launch(IO) {
                                while (isActive) {
                                    // TODO: Add in this value when we find way to get this value
                                    cpuDataIter.add(100.09)
                                    memoryDataIter.add(runtime.totalMemory() - runtime.freeMemory())
                                    threadDataIter.add(Thread.activeCount())
                                    delay(testParams.dataProbeGranularityMs)
                                }
                            }

                            // Get the before values for necessary metrics
                            val statsBefore = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
                            val memFreeBefore = statsBefore.freeBlocksLong * statsBefore.blockSizeLong
                            val networkSentBefore = transport.bytesUploaded
                            val networkReceivedBefore = transport.bytesDownloaded

                            // Measure the execution time of runnning the given block of code
                            val timeBefore = Date().time
                            delay(2000L) // Eventually take this out but needed for testing
                            testDefinition(numDoc, docSize)

                            // Not entirely sure which one to use here, but cancelAndJoin() seems right
                            // job.cancel()
                            job.cancelAndJoin()

                            timeData.add((Date().time - timeBefore).toDouble())

                            val statsAfter = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
                            val memFreeAfter = statsAfter.freeBlocksLong * statsAfter.blockSizeLong
                            diskData.add((memFreeBefore - memFreeAfter).toDouble())

                            // Average the point-in-time data metrics
                            threadData.add(threadDataIter.average())
                            cpuData.add(cpuDataIter.average())
                            memoryData.add(memoryDataIter.average())

                            // TODO: Add in these values when instrumented transport is implemented
                            networkSentData.add((transport.bytesUploaded - networkSentBefore).toDouble())
                            networkReceivedData.add((transport.bytesDownloaded - networkReceivedBefore).toDouble())
                        }

                        // Reset the StitchApp
                        teardownIter()
                    }

                    // Create RunResults option which performs outlier extraction, and computes the
                    // desired statistical metrics
                    val runResults = RunResults(numDoc, docSize, testParams.numIters,
                        testParams.numOutliersEachSide, timeData.toDoubleArray(),
                        networkSentData.toDoubleArray(), networkReceivedData.toDoubleArray(),
                        cpuData.toDoubleArray(), memoryData.toDoubleArray(),
                        diskData.toDoubleArray(), threadData.toDoubleArray())

                    // If we are logging to stdout
                    if (testParams.outputToStdOut) {
                        Log.d("perfTests", runResults.toBson().toJson())
                    }

                    // If we are inserting this into stitch
                    if (testParams.outputToStitch) {
                        val filterDocument = Document("_id", resultId)
                        val updateDocument = Document()
                            .append("\$push", Document("results", runResults.toBson()))
                        Tasks.await(outputColl.updateOne(filterDocument, updateDocument))
                    }
                }
            }
        } catch (e: Exception) {
            Tasks.await(outputColl.deleteOne(Document("_id", resultId)))
            throw e
        }
    }

    public data class TestParams(
        val runId: ObjectId,
        val testName: String,
        val numIters: Int = 12,
        val numDocs: IntArray = intArrayOf(),
        val docSizes: IntArray = intArrayOf(),
        val dataProbeGranularityMs: Long = 1500L,
        val numOutliersEachSide: Int = 1,
        val stitchHostName: String = "",
        val outputToStdOut: Boolean = true,
        val outputToStitch: Boolean = false,
        val preserveRawOutput: Boolean = false
    ) {
        fun toBson(): Document {
            return Document(
                mapOf(
                    "runId" to runId,
                    "name" to BsonString(this.testName),
                    "dataProbeGranularityMs" to BsonInt64(this.dataProbeGranularityMs),
                    "numOutliersEachSide" to BsonInt32(this.numOutliersEachSide),
                    "stitchHostName" to BsonString(this.stitchHostName),
                    "date" to BsonDateTime(Date().time),
                    "results" to BsonArray()
                )
            )
        }
    }
}

private class DoubleDataBlock(data: DoubleArray, numOutliers: Int) {
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
            stdDev = newData.fold(0.0) { accumulator, next -> accumulator + (next - mean) * (next - mean) }
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

private class RunResults(
    numDocs: Int,
    docSize: Int,
    numIters: Int,
    numOutliers: Int,
    time: DoubleArray,
    networkSentBytes: DoubleArray,
    networkReceivedBytes: DoubleArray,
    cpu: DoubleArray,
    memory: DoubleArray,
    disk: DoubleArray,
    threads: DoubleArray
) {
    var numDocs: Int = 0
    var docSize: Int = 0
    var numIters: Int = 0

    val timeResults: DoubleDataBlock
    val networkSentResults: DoubleDataBlock
    val networkReceivedResults: DoubleDataBlock
    val cpuResults: DoubleDataBlock
    val memoryResults: DoubleDataBlock
    val diskResults: DoubleDataBlock
    val threadResults: DoubleDataBlock

    init {
        this.numDocs = numDocs
        this.docSize = docSize
        this.numIters = numIters
        this.timeResults = DoubleDataBlock(time, numOutliers)
        this.networkSentResults = DoubleDataBlock(networkSentBytes, numOutliers)
        this.networkReceivedResults = DoubleDataBlock(networkReceivedBytes, numOutliers)
        this.cpuResults = DoubleDataBlock(cpu, numOutliers)
        this.memoryResults = DoubleDataBlock(memory, numOutliers)
        this.diskResults = DoubleDataBlock(disk, numOutliers)
        this.threadResults = DoubleDataBlock(threads, numOutliers)
    }

    fun toBson(): Document {
        return Document(
            mapOf(
                "numDocs" to BsonInt32(this.numDocs),
                "docSize" to BsonInt32(this.docSize),
                "numIters" to BsonInt32(this.numIters),
                "timeMs" to this.timeResults.toBson(),
                "networkSentBytes" to this.networkSentResults.toBson(),
                "networkReceivedBytes" to this.networkReceivedResults.toBson(),
                "cpu" to cpuResults.toBson(),
                "memoryBytes" to memoryResults.toBson(),
                "diskBytes" to diskResults.toBson(),
                "threads" to threadResults.toBson()
            )
        )
    }
}
