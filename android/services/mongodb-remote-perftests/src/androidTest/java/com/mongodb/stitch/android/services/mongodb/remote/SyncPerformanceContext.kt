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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bson.Document
import org.bson.types.ObjectId
import java.util.*

class SyncPerformanceContext(private val harness: SyncPerformanceIntTestsHarness,
                             private val testParams: TestParams,
                             private val transport: OkHttpInstrumentedTransport) {
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
    lateinit var mdbService: Apps.App.Services.Service
    lateinit var mdbRule: RuleResponse

    private fun setup() {
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
            harness.mdbService = harness.addService(
                app.second,
                "mongodb",
                "mongodb1",
                ServiceConfigs.Mongo(harness.getMongoDbUri())).second

            harness.testDbName = ObjectId().toHexString()
            harness.testCollName = ObjectId().toHexString()
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

    private fun teardownIter() {
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

    private suspend fun generateMemoryAndThreadData(partialResult: PartialResult) = coroutineScope {
        launch(Dispatchers.IO) {
            val memoryDataIter = arrayListOf<Long>()
            val threadDataIter = arrayListOf<Int>()

            while (isActive) {
                memoryDataIter.add(runtime.totalMemory() - runtime.freeMemory())
                threadDataIter.add(Thread.activeCount())
                delay(testParams.dataProbeGranularityMs)
            }

            partialResult.activeThreadCount = threadDataIter.average()
            partialResult.memoryUsage = memoryDataIter.average()
        }
    }

    suspend fun runSingleIteration(numDocs: Int,
                                   docSize: Int,
                                   testDefinition: TestDefinition) = coroutineScope {
        setup()

        val partialResult = PartialResult()

        // Launch coroutine to collect point-in-runTimes data metrics and then delay
        // for dataProbeGranularityMs
        val job = generateMemoryAndThreadData(partialResult)

        // Get the before values for necessary metrics
        val statsBefore = StatFs(Environment.getExternalStorageDirectory().getAbsolutePath())
        val memFreeBefore = statsBefore.freeBlocksLong * statsBefore.blockSizeLong
        val networkSentBefore = transport.bytesUploaded
        val networkReceivedBefore = transport.bytesDownloaded
        // Measure the execution runTimes of running the given block of code
        val timeBefore = Date().time

        delay(2000L) // Eventually take this out but needed for testing
        testDefinition(numDocs, docSize)

        job.cancelAndJoin()

        // Add metrics
        partialResult.timeTaken = (Date().time - timeBefore).toDouble()
        val statsAfter = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val memFreeAfter = statsAfter.freeBlocksLong * statsAfter.blockSizeLong
        partialResult.diskUsage = (memFreeBefore - memFreeAfter).toDouble()
        partialResult.networkSent = (transport.bytesUploaded - networkSentBefore).toDouble()
        partialResult.networkReceived = (transport.bytesDownloaded - networkReceivedBefore).toDouble()

        // Reset the StitchApp
        teardownIter()

        partialResult
    }
}
