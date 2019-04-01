package com.mongodb.stitch.android.services.mongodb.remote

import android.os.Environment
import android.os.StatFs
import com.google.android.gms.tasks.Tasks
import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.local.internal.AndroidEmbeddedMongoClientFactory
import com.mongodb.stitch.android.services.mongodb.remote.internal.RemoteMongoClientImpl
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.RuleResponse
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyCredential
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import org.bson.Document
import org.bson.types.ObjectId
import java.util.*

class SyncPerformanceTestContext(private val harness: SyncPerformanceIntTestsHarness,
                                 private val testParams: TestParams,
                                 private val transport: OkHttpInstrumentedTransport) {
    // Public variables
    lateinit var testClient: StitchAppClient
        private set
    var testDbName: String = ObjectId().toHexString()
        private set
    var testCollName: String = ObjectId().toHexString()
        private set
    var testNamespace = MongoNamespace(testDbName, testCollName)
        private set
    lateinit var testMongoClient: RemoteMongoClient
        private set
    lateinit var testColl: RemoteMongoCollection<Document>
        private set
    lateinit var testUserId: String
        private set

    val testDataSynchronizer: DataSynchronizer
        get() = (testMongoClient as RemoteMongoClientImpl).dataSynchronizer
    val testNetworkMonitor: BaseStitchIntTest.TestNetworkMonitor
        get() = BaseStitchAndroidIntTest.testNetworkMonitor
    lateinit var mdbService: Apps.App.Services.Service
        private set
    lateinit var mdbRule: RuleResponse
        private set

    fun setup() {
        if (harness.getStitchBaseURL() == "https://stitch.mongodb.com") {
            testDbName = harness.stitchTestDbName
            testCollName = harness.stitchTestCollName
            testColl = harness.outputClient
                .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
                .getDatabase(testDbName)
                .getCollection(testCollName)
            testClient = harness.outputClient
            testMongoClient = harness.outputMongoClient

            if (!harness.outputClient.auth.isLoggedIn) {
                testUserId = Tasks.await(
                    harness.outputClient.auth.loginWithCredential(
                        UserApiKeyCredential(harness.getStitchAPIKey()))).id
            } else {
                testUserId = harness.outputClient.auth.user!!.id
            }

            Tasks.await(testClient.callFunction("deleteAllAsSystemUser", arrayListOf<String>()))
        } else {
            val app = harness.createApp()
            harness.addProvider(app.second, ProviderConfigs.Anon)
            mdbService = harness.addService(
                app.second,
                "mongodb",
                "mongodb1",
                ServiceConfigs.Mongo(harness.getMongoDbUri())).second
            testDbName = ObjectId().toHexString()
            testCollName = ObjectId().toHexString()
            testNamespace = MongoNamespace(testDbName, testCollName)

            val rule = RuleCreator.MongoDb(
                database = testDbName,
                collection = testCollName,
                roles = listOf(RuleCreator.MongoDb.Role(
                    read = true, write = true
                )),
                schema = RuleCreator.MongoDb.Schema())

            mdbRule = harness.addRule(mdbService, rule)

            testClient = harness.getAppClient(app.first)
            testUserId = Tasks.await(testClient.auth.loginWithCredential(AnonymousCredential())).id
            testMongoClient = testClient.getServiceClient(RemoteMongoClient.factory, "mongodb1")
            testColl = testMongoClient.getDatabase(testDbName).getCollection(testCollName)
        }

        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    fun teardown() {
        val syncedIds = Tasks.await(testColl.sync().syncedIds)
        Tasks.await(testColl.sync().desyncMany(*syncedIds.toTypedArray()))

        if (harness.getStitchBaseURL() == "https://stitch.mongodb.com") {
            Tasks.await(testClient.callFunction("deleteAllAsSystemUser", arrayListOf<String>()))
        } else {
            Tasks.await(testColl.deleteMany(Document()))
        }

        if (::testMongoClient.isInitialized) {
            (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
            AndroidEmbeddedMongoClientFactory.getInstance().close()
        }
    }

    private val runtime by lazy { Runtime.getRuntime() }

    private fun generateMemoryAndThreadData(partialResult: PartialResult) = object: Thread() {
        override fun run() {
            this.name = "${testParams.testName}_memory_and_thread_monitor"
            val memoryData = arrayListOf<Long>()
            val threadData = arrayListOf<Int>()

            while (!this.isInterrupted) {
                memoryData.add(runtime.totalMemory() - runtime.freeMemory())
                threadData.add(Thread.activeCount())
                try {
                    sleep(testParams.dataProbeGranularityMs)
                } catch (_: InterruptedException) {
                    break
                }
            }

            partialResult.activeThreadCount = threadData.average()
            partialResult.memoryUsage = memoryData.average()
        }
    }

    fun runSingleIteration(
        numDocs: Int,
        docSize: Int,
        testDefinition: TestDefinition
    ): PartialResult {
        val partialResult = PartialResult()

        val job = generateMemoryAndThreadData(partialResult)
        job.start()
        // Get the before values for necessary metrics
        val statsBefore = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
        val memFreeBefore = statsBefore.freeBlocksLong * statsBefore.blockSizeLong
        val networkSentBefore = transport.bytesUploaded
        val networkReceivedBefore = transport.bytesDownloaded
        // Measure the execution runTimes of running the given block of code
        val timeBefore = Date().time

        Thread.sleep(2000L) // Eventually take this out but needed for testing
        testDefinition(this@SyncPerformanceTestContext, numDocs, docSize)

        job.interrupt()
        job.join()

        // Add metrics
        partialResult.timeTaken = (Date().time - timeBefore).toDouble()
        val statsAfter = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val memFreeAfter = statsAfter.freeBlocksLong * statsAfter.blockSizeLong
        partialResult.diskUsage = (memFreeBefore - memFreeAfter).toDouble()
        partialResult.networkSent = (transport.bytesUploaded - networkSentBefore).toDouble()
        partialResult.networkReceived = (transport.bytesDownloaded - networkReceivedBefore)
            .toDouble()

        return partialResult
    }
}
