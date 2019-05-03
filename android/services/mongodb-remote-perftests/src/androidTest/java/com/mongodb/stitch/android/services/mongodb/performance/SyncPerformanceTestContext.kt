package com.mongodb.stitch.android.services.mongodb.performance

import android.os.Environment
import android.os.StatFs

import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.core.StitchAppClient

import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.internal.RemoteMongoClientImpl
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import java.util.Date
import org.bson.Document
import org.bson.types.ObjectId

abstract class SyncPerformanceTestContext(
    private val harness: SyncPerformanceIntTestsHarness,
    private val testName: String
) {
    // Public variables
    protected var testDbName: String = ObjectId().toHexString()
    protected var testCollName: String = ObjectId().toHexString()
    protected var testNamespace = MongoNamespace(testDbName, testCollName)
    val testNetworkMonitor: BaseStitchIntTest.TestNetworkMonitor
        get() = BaseStitchAndroidIntTest.testNetworkMonitor

    open lateinit var testClient: StitchAppClient
    open lateinit var testMongoClient: RemoteMongoClient
    open lateinit var testColl: RemoteMongoCollection<Document>
    open lateinit var testUserId: String
    val testDataSynchronizer: DataSynchronizer
        get() = (testMongoClient as RemoteMongoClientImpl).dataSynchronizer

    companion object {
        val TAG = SyncPerformanceTestContext::class.java.simpleName
    }

    abstract fun setup()
    abstract fun teardown()

    private val runtime by lazy { Runtime.getRuntime() }

    private fun generateMemoryAndThreadData(partialResult: PartialResult) = object : Thread() {
        override fun run() {
            this.name = "${testName}_memory_and_thread_monitor"
            val memoryData = arrayListOf<Long>()
            val threadData = arrayListOf<Int>()

            while (!this.isInterrupted) {
                memoryData.add(runtime.totalMemory() - runtime.freeMemory())
                threadData.add(Thread.activeCount())
                try {
                    sleep(SyncPerformanceTestUtils.getDataGranularity())
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
        val memFreeBeforeData = StatFs(Environment.getDataDirectory().path).freeBytes
        val networkSentBefore = harness.transport.bytesUploaded
        val networkReceivedBefore = harness.transport.bytesDownloaded
        val timeBefore = Date().time

        testDefinition(this@SyncPerformanceTestContext, numDocs, docSize)

        job.interrupt()
        job.join()

        // Add metrics
        partialResult.timeTaken = (Date().time - timeBefore).toDouble()
        val memFreeAfterData = StatFs(Environment.getDataDirectory().path).freeBytes
        partialResult.diskUsage = (memFreeBeforeData - memFreeAfterData).toDouble()
        partialResult.networkSent = (harness.transport.bytesUploaded - networkSentBefore).toDouble()
        partialResult.networkReceived = (harness.transport.bytesDownloaded - networkReceivedBefore)
            .toDouble()

        return partialResult
    }
}
