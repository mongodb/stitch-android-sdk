package com.mongodb.stitch.android.services.mongodb.remote.internal

import android.os.CpuUsageInfo
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import android.support.test.InstrumentationRegistry
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.Sync
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.RuleResponse
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import com.mongodb.stitch.core.testutils.sync.ProxyRemoteMethods
import com.mongodb.stitch.core.testutils.sync.ProxySyncMethods
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.local.internal.AndroidEmbeddedMongoClientFactory
import com.mongodb.stitch.core.auth.internal.CoreStitchUser
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import kotlinx.coroutines.*
import org.bson.*
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.collections.*
import kotlin.concurrent.thread
import java.util.Timer
import kotlin.system.measureTimeMillis

//import com.sun.management.OperatingSystemMXBean;


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

    private lateinit var mdbService: Apps.App.Services.Service
    private lateinit var mdbRule: RuleResponse

    private lateinit var userId1: String
    private lateinit var userId2: String
    private lateinit var userId3: String

    @Before
    override fun setup() {
        Assume.assumeTrue("no MongoDB URI in properties; skipping test", getMongoDbUri().isNotEmpty())
        super.setup()

        val app = createApp()
        val app2 = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app2.second, ProviderConfigs.Anon)
        mdbService = addService(
                app.second,
                "mongodb",
                "mongodb1",
                ServiceConfigs.Mongo(getMongoDbUri())).second
        val svc2 = addService(
                app2.second,
                "mongodb",
                "mongodb1",
                ServiceConfigs.Mongo(getMongoDbUri()))

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
        addRule(svc2.second, rule)
        addProvider(app.second, config = ProviderConfigs.Userpass(
                emailConfirmationUrl = "http://emailConfirmURL.com",
                resetPasswordUrl = "http://resetPasswordURL.com",
                confirmEmailSubject = "email subject",
                resetPasswordSubject = "password subject")
        )
        this.client = getAppClient(app.first)
        userId3 = Tasks.await(client.auth.loginWithCredential(AnonymousCredential())).id
        userId2 = registerAndLoginWithUserPass(app.second, client, "test1@10gen.com", "password")
        userId1 = registerAndLoginWithUserPass(app.second, client, "test2@10gen.com", "password")
        mongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    @After
    override fun teardown() {
        if (::mongoClient.isInitialized) {
            (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
            AndroidEmbeddedMongoClientFactory.getInstance().close()
        }
        super.teardown()
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
                                Log.d("perfTests", memFree.toString())
                                threadDataIter.add(Thread.activeCount())
                            }
                        }

                        val statsBefore = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
                        val memFreeBefore = statsBefore.freeBlocksLong * statsBefore.blockSizeLong

                        // Measure the execution time of runnning the given block of code
                        delay(4000L) // Eventually take this out but needed for testing
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

                    // If we are interting this into stitch
                    if (testParams.outputToStitch) {
                        Log.d("perfTests", "Trying to insert via stitch")
                    }

                }
            }
        }
    }

    private fun initialSync() {
        // Thread.sleep(4000L)
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



    @Test
    fun performanceTest2() {
        for (i in 0..10) {
            Log.d("perfTests", i.toString())
            try {
                Log.d("perfTests", readCore(0).toString())
            }
            catch (e: Exception) {
                Log.d("perfTests", e.toString())
            }
        }

    }
    /**
     * Get the uri for where mongodb is running locally.
     */
    private fun getMongoDbUri(): String {
        return InstrumentationRegistry.getArguments().getString(mongodbUriProp, "mongodb://localhost:26000")
    }

    fun readCore(i: Int): Float {
        /*
     * how to calculate multicore this function reads the bytes from a
     * logging file in the android system (/proc/stat for cpu values) then
     * puts the line into a string then spilts up each individual part into
     * an array then(since he know which part represents what) we are able
     * to determine each cpu total and work then combine it together to get
     * a single float for overall cpu usage
     */
        try {
            Log.d("perfTests", "A")
            val reader = RandomAccessFile("/proc/stat", "r")
            Log.d("perfTests", "B")
            // skip to the line we need
            for (ii in 0 until i + 1) {
                Log.d("perfTests", "C")
                val line = reader.readLine()
                Log.d("perfTests", line)
            }
            var load = reader.readLine()

            // cores will eventually go offline, and if it does, then it is at
            // 0% because it is not being
            // used. so we need to do check if the line we got contains cpu, if
            // not, then this core = 0
            if (load.contains("cpu")) {
                var toks = load.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                // we are recording the work being used by the user and
                // system(work) and the total info
                // of cpu stuff (total)
                // http://stackoverflow.com/questions/3017162/how-to-get-total-cpu-usage-in-linux-c/3017438#3017438

                val work1 = (java.lang.Long.parseLong(toks[1]) + java.lang.Long.parseLong(toks[2])
                        + java.lang.Long.parseLong(toks[3]))
                val total1 = (java.lang.Long.parseLong(toks[1]) + java.lang.Long.parseLong(toks[2])
                        + java.lang.Long.parseLong(toks[3]) + java.lang.Long.parseLong(toks[4])
                        + java.lang.Long.parseLong(toks[5]) + java.lang.Long.parseLong(toks[6])
                        + java.lang.Long.parseLong(toks[7]) + java.lang.Long.parseLong(toks[8]))

                try {
                    // short sleep time = less accurate. But android devices
                    // typically don't have more than
                    // 4 cores, and I'n my app, I run this all in a second. So,
                    // I need it a bit shorter
                    Thread.sleep(300)
                } catch (e: Exception) {
                    Log.d("perfTests", e.toString())
                }

                reader.seek(0)
                // skip to the line we need
                for (ii in 0 until i + 1) {
                    reader.readLine()
                }
                load = reader.readLine()

                // cores will eventually go offline, and if it does, then it is
                // at 0% because it is not being
                // used. so we need to do check if the line we got contains cpu,
                // if not, then this core = 0%
                if (load.contains("cpu")) {
                    reader.close()
                    toks = load.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                    val work2 = (java.lang.Long.parseLong(toks[1]) + java.lang.Long.parseLong(toks[2])
                            + java.lang.Long.parseLong(toks[3]))
                    val total2 = (java.lang.Long.parseLong(toks[1]) + java.lang.Long.parseLong(toks[2])
                            + java.lang.Long.parseLong(toks[3]) + java.lang.Long.parseLong(toks[4])
                            + java.lang.Long.parseLong(toks[5]) + java.lang.Long.parseLong(toks[6])
                            + java.lang.Long.parseLong(toks[7]) + java.lang.Long.parseLong(toks[8]))

                    // here we find the change in user work and total info, and
                    // divide by one another to get our total
                    // seems to be accurate need to test on quad core
                    // http://stackoverflow.com/questions/3017162/how-to-get-total-cpu-usage-in-linux-c/3017438#3017438

                    return if (total2 - total1 == 0L)
                        0f
                    else
                        (work2 - work1).toFloat() / (total2 - total1)

                } else {
                    reader.close()
                    return 0f
                }

            } else {
                reader.close()
                return 0f
            }

        } catch (ex: IOException) {
            Log.d("perfTests", ex.localizedMessage)
            ex.printStackTrace()
        }

        return 0f
    }
}

data class TestParams(
        val testName: String,
        val numIters: Int = 12,
        val numDocs: IntArray = intArrayOf(),
        val docSizes: IntArray = intArrayOf(),
        val dataProbeGranularityMs: Long = 1500L,
        val numOutliersEachSide: Int = 1,
        val stitchHostName: String = "Need to hook this up later",
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

class IntDataBlock(data : IntArray, numOutliers: Int): DataBlock<Int> {
    override var mean   = 0.0
    override var median = 0
    override var min    = 0
    override var max    = 0
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
                .append("min", BsonInt32(this.min))
                .append("max", BsonInt32(this.max))
                .append("mean", BsonDouble(this.mean))
                .append("median", BsonInt32(this.median))
                .append("stdDev", BsonDouble(this.stdDev))
    }
}

class DoubleDataBlock(data: DoubleArray, numOutliers: Int): DataBlock<Double> {
    override var mean   = 0.0
    override var median = 0.0
    override var min    = 0.0
    override var max    = 0.0
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
                .append("min", BsonDouble(this.min))
                .append("max", BsonDouble(this.max))
                .append("mean", BsonDouble(this.mean))
                .append("median", BsonDouble(this.median))
                .append("stdDev", BsonDouble(this.stdDev))
    }
}

class LongDataBlock(data: LongArray, numOutliers: Int): DataBlock<Long> {
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

class RunResults(numDocs: Int,
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