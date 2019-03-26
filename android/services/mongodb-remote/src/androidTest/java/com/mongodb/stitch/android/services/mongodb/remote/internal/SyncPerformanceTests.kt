package com.mongodb.stitch.android.services.mongodb.remote.internal

import android.os.Environment
import android.os.StatFs
import android.support.test.InstrumentationRegistry
import android.util.Log

import com.google.android.gms.tasks.Tasks

import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.local.internal.AndroidEmbeddedMongoClientFactory
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.RuleResponse
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.Assert
import com.mongodb.stitch.core.testutils.BaseStitchIntTest

import kotlin.collections.*
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.*

import org.bson.*
import org.bson.types.Binary
import org.bson.types.ObjectId

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test


class SyncPerformanceTests : BaseStitchAndroidIntTest() {

    lateinit var client: StitchAppClient

    private var dbName = ObjectId().toHexString()
    private var collName = ObjectId().toHexString()
    private var namespace = MongoNamespace(dbName, collName)
    private val dataSynchronizer: DataSynchronizer
        get() = (mongoClient as RemoteMongoClientImpl).dataSynchronizer
    private val testNetworkMonitor: BaseStitchIntTest.TestNetworkMonitor
        get() = BaseStitchAndroidIntTest.testNetworkMonitor
    private val mongodbUriProp = "test.stitch.mongodbURI"
    private lateinit var mongoClient: RemoteMongoClient
    private lateinit var coll: RemoteMongoCollection<Document>

    private lateinit var mdbService: Apps.App.Services.Service
    private lateinit var mdbRule: RuleResponse

    private lateinit var userId: String

    @Before
    override fun setup() {
        super.setup()
    }

    private fun setupIter(withURI: String) {
        Assume.assumeTrue("no MongoDB URI in properties; skipping test", withURI.isNotEmpty())
        setup()

        val app = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        mdbService = addService(
                app.second,
                "mongodb",
                "mongodb1",
                ServiceConfigs.Mongo(withURI)).second

        dbName = ObjectId().toHexString()
        collName = ObjectId().toHexString()
        namespace = MongoNamespace(dbName, collName)

        val rule = RuleCreator.MongoDb(
                database = dbName,
                collection = collName,
                roles = listOf(RuleCreator.MongoDb.Role(
                        read = true, write = true
                )),
                schema = RuleCreator.MongoDb.Schema())
        mdbRule = addRule(mdbService, rule)

        this.client = getAppClient(app.first)
        userId = Tasks.await(client.auth.loginWithCredential(AnonymousCredential())).id
        mongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        coll = mongoClient.getDatabase(dbName).getCollection(collName)
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    @After
    override fun teardown() {
        super.teardown()
    }

    private fun teardownIter() {
        val syncedIds = Tasks.await(coll.sync().syncedIds)
        Tasks.await(coll.sync().desyncMany(*syncedIds.toTypedArray()))
        Log.d("perfTests", client.auth.isLoggedIn.toString())

        Tasks.await(coll.deleteMany(Document()))

        if (::mongoClient.isInitialized) {
            (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
            AndroidEmbeddedMongoClientFactory.getInstance().close()
        }

        Log.d("perfTests", "tearing down")

        teardown()
    }


    private fun runPerformanceTestWithParams(testParams: TestParams,
                                             block: () -> Unit) = runBlocking {

        val runtime = Runtime.getRuntime()

        if (testParams.outputToStitch) {
            // We will need to setup a stitchClient
        }

        for (docSize in testParams.docSizes) {
            for (numDoc in testParams.numDocs) {

                // There data structures hold the metric results for all n iterations of this test
                var timeData            = arrayListOf<Long>()
                var cpuData             = arrayListOf<Double>()
                var memoryData          = arrayListOf<Long>()
                var diskData            = arrayListOf<Long>()
                var threadData          = arrayListOf<Int>()
                var networkSentData     = arrayListOf<Long>()
                var networkReceivedData = arrayListOf<Long>()

                for (iter in 1..testParams.numIters) {

                    // These data structures will have  < (time / dataGranularityMs) for the
                    // point-in-time metrics collected
                    var cpuDataIter     = arrayListOf<Double>()
                    var memoryDataIter  = arrayListOf<Long>()
                    var threadDataIter  = arrayListOf<Int>()

                    // If we pass a desired Stitch URI into the testParams then use that URI
                    // Otherwise, use the value in local.properties or localhost:26000
                    if (testParams.stitchHostName.isNotEmpty()) {
                        setupIter(testParams.stitchHostName)
                    } else {
                        setupIter(InstrumentationRegistry.getArguments().getString(mongodbUriProp,
                                "mongodb://localhost:26000"))
                    }

                    coroutineScope {
                        // Launch coroutine to collect point-in-time data metrics and then delay
                        // for dataProbeGranularityMs
                        val job = launch {
                            while (true) {
                                delay(testParams.dataProbeGranularityMs)
                                cpuDataIter.add(2.0)
                                memoryDataIter.add(runtime.totalMemory() - runtime.freeMemory())
                                val stats = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
                                val memFree = stats.freeBlocksLong * stats.blockSizeLong
                                Log.d("perfTests", "HI")
                                threadDataIter.add(Thread.activeCount())
                            }
                        }

                        val statsBefore = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
                        val memFreeBefore = statsBefore.freeBlocksLong * statsBefore.blockSizeLong

                        // Measure the execution time of runnning the given block of code
                        // delay(4000L) // Eventually take this out but needed for testing
                        val time = measureTimeMillis(block)
                        Log.d("perfTests", "After Block")


                        // Not entirely sure which one to use here, but cancelAndJoin() seems right
                        // job.cancel()
                        job.cancelAndJoin()

                        // Average the point-in-time data metrics
                        cpuData.add(cpuDataIter.average())
                        memoryData.add(memoryDataIter.average().toLong())

                        val statsAfter = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
                        val memFreeAfter = statsAfter.freeBlocksLong * statsAfter.blockSizeLong

                        diskData.add(memFreeBefore - memFreeAfter)
                        threadData.add(threadDataIter.average().toInt())

                        // Add the execution time and network information from transport
                        timeData.add(time)
                        networkSentData.add(100000L)
                        networkReceivedData.add(200000L)
                    }

                    // Create RunResults option which performs outlier extraction, and computes the
                    // desired statistical metrics
                    val runResults = RunResults(numDoc, docSize, testParams.numIters,
                            testParams.numOutliersEachSide, timeData.toLongArray(),
                            networkSentData.toLongArray(), networkReceivedData.toLongArray(),
                            cpuData.toDoubleArray(), memoryData.toLongArray(),
                            diskData.toLongArray(), threadData.toIntArray())

                    // If we are logging to stdout
                    if (testParams.outputToStdOut) {
                        Log.d("perfTests", runResults.toBson().toJson())
                    }

                    // If we are inserting this into stitch
                    if (testParams.outputToStitch) {
                        Log.d("perfTests", "Trying to insert via stitch")
                    }

                    // Reset the StitchApp
                    teardownIter()

                }
            }
        }
    }

    private fun initialSync() {
        Log.d("perfTests", "SUP")
        val numDesiredDocs = 40
        val documents = getDocuments(numDesiredDocs, 1024)
        assertEquals(Tasks.await(coll.count()), 0L)
        Tasks.await(coll.insertMany(documents))
        Log.d("perfTests", "Inserted")

        assertEquals(Tasks.await(coll.count()), numDesiredDocs.toLong())
    }


    @Test
    fun performanceTest1() {
        val testParams = TestParams(
                testName = "myCustomTest",
                dataProbeGranularityMs = 400L,
                docSizes = intArrayOf(1),
                numDocs = intArrayOf(10),
                numIters = 3,
                numOutliersEachSide = 0,
                outputToStitch = true
        )
        runPerformanceTestWithParams(testParams, this::initialSync)
    }

    private fun getDocuments(
            numberOfDocs: Int,
            sizeOfDocsInBytes: Int
    ): List<Document>? {

        val user = client.auth.user ?: return null
        val array: List<Byte> = (0 until sizeOfDocsInBytes).map { 0.toByte() }
        val docs: List<Document> = (0 until numberOfDocs).map {
            Document(mapOf(
                    "_id" to ObjectId(),
                    "owner_id" to user.id,
                    "bin" to Binary(array.toByteArray())
            ))
        }
        return docs
    }

    @Test
    fun performanceTest2() {

    }
}

private data class TestParams(
        val testName: String,
        val numIters: Int = 12,
        val numDocs: IntArray = intArrayOf(),
        val docSizes: IntArray = intArrayOf(),
        val dataProbeGranularityMs: Long = 1500L,
        val numOutliersEachSide: Int = 1,
        val stitchHostName: String = "",
        val outputToStdOut: Boolean = true,
        val outputToStitch: Boolean = false,
        val preserveRawOutput: Boolean = false) {
}

interface DataBlock<T: Number> {
    var mean: Double
    var median: T
    var stdDev: Double
    var min: T
    var max: T

    fun toBson(): BsonDocument
}

private class IntDataBlock(data : IntArray, numOutliers: Int): DataBlock<Int> {
    override var mean   = 0.0
    override var median = 0
    override var min    = 0
    override var max    = 0
    override var stdDev = 0.0

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
            stdDev = data.fold(0.0, { accumulator, next -> accumulator + (next - mean) * (next - mean)})
            stdDev =  Math.sqrt(stdDev / dataSize)
        }
    }

    // Compute relevant metrics on init
    override fun toBson(): BsonDocument {
        return BsonDocument()
                .append("min", BsonInt32(this.min))
                .append("max", BsonInt32(this.max))
                .append("mean", BsonDouble(this.mean))
                .append("median", BsonInt32(this.median))
                .append("stdDev", BsonDouble(this.stdDev))
    }
}

private class DoubleDataBlock(data: DoubleArray, numOutliers: Int): DataBlock<Double> {
    override var mean   = 0.0
    override var median = 0.0
    override var min    = 0.0
    override var max    = 0.0
    override var stdDev = 0.0

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
            stdDev = data.fold(0.0, { accumulator, next -> accumulator + (next - mean) * (next - mean)})
            stdDev =  Math.sqrt(stdDev / dataSize)
        }
    }

    override fun toBson(): BsonDocument {
        return BsonDocument()
                .append("min", BsonDouble(this.min))
                .append("max", BsonDouble(this.max))
                .append("mean", BsonDouble(this.mean))
                .append("median", BsonDouble(this.median))
                .append("stdDev", BsonDouble(this.stdDev))
    }
}

private class LongDataBlock(data: LongArray, numOutliers: Int): DataBlock<Long> {
    override var mean   = 0.0
    override var median = 0L
    override var min    = 0L
    override var max    = 0L
    override var stdDev = 0.0

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
            stdDev = data.fold(0.0, { accumulator, next -> accumulator + (next - mean) * (next - mean)})
            stdDev =  Math.sqrt(stdDev / dataSize)
        }
    }

    override fun toBson(): BsonDocument {
        return BsonDocument()
                .append("min", BsonInt64(this.min))
                .append("max", BsonInt64(this.max))
                .append("mean", BsonDouble(this.mean))
                .append("median", BsonInt64(this.median))
                .append("stdDev", BsonDouble(this.stdDev))
    }
}

private class RunResults(numDocs: Int,
                 docSize: Int,
                 numIters: Int,
                 numOutliers: Int,
                 time: LongArray,
                 networkSentBytes: LongArray,
                 networkReceivedBytes: LongArray,
                 cpu: DoubleArray,
                 memory: LongArray,
                 disk: LongArray,
                 threads: IntArray) {

    var numDocs: Int = 0
    var docSize: Int = 0
    var numIters: Int = 0

    var timeResults: DataBlock<Long>? = null
    var networkSentResults: DataBlock<Long>? = null
    var networkReceivedResults: DataBlock<Long>? = null
    var cpuResults: DataBlock<Double>? = null
    var memoryResults: DataBlock<Long>? = null
    var diskResults: DataBlock<Long>? = null
    var threadResults: DataBlock<Int>? = null


    init {
        this.numDocs = numDocs
        this.docSize = docSize
        this.numIters = numIters
        this.timeResults = LongDataBlock(time, numOutliers)
        this.networkSentResults = LongDataBlock(networkSentBytes, numOutliers)
        this.networkReceivedResults = LongDataBlock(networkReceivedBytes, numOutliers)
        this.cpuResults = DoubleDataBlock(cpu, numOutliers)
        this.memoryResults = LongDataBlock(memory, numOutliers)
        this.diskResults = LongDataBlock(disk, numOutliers)
        this.threadResults = IntDataBlock(threads, numOutliers)
    }

    fun toBson(): BsonDocument {
        return BsonDocument()
                .append("numDocs", BsonInt32(this.numDocs))
                .append("docSize", BsonInt32(this.docSize))
                .append("numIters", BsonInt32(this.numIters))
                .append("timeMs", timeResults?.toBson() ?: BsonString("Error"))
                .append("networkSentBytes", networkSentResults?.toBson() ?: BsonString("Error"))
                .append("networkReceivedBytes", networkReceivedResults?.toBson() ?: BsonString("Error"))
                .append("cpu", cpuResults?.toBson() ?: BsonString("Error"))
                .append("memory", memoryResults?.toBson() ?: BsonString("Error"))
                .append("disk", diskResults?.toBson() ?: BsonString("Error"))
                .append("threads", threadResults?.toBson() ?: BsonString("Error"))
    }

}