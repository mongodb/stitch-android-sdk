package com.mongodb.stitch.android.services.mongodb.performance

import android.support.test.runner.AndroidJUnit4
import android.util.Log

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers

import org.bson.Document
import org.bson.types.ObjectId

import org.junit.Test
import org.junit.runner.RunWith

val testHarness = SyncPerformanceIntTestsHarness()

@RunWith(AndroidJUnit4::class)
class SyncPerformanceTests {
    private val outputToStitch = false

    companion object {
        private val runId by lazy {
            ObjectId()
        }
    }

    // private var testHarness = SyncPerformanceIntTestsHarness()

    // Tests for L2R-only scenarios
    @Test
    fun testL2ROnlyInitialSync() {
        SyncL2ROnlyPerformanceTestDefinitions.testInitialSync(testHarness, runId)
    }

    @Test
    fun testL2ROnlyDisconnectReconnect() {
        SyncL2ROnlyPerformanceTestDefinitions.testDisconnectReconnect(testHarness, runId)
    }

    @Test
    fun testL2ROnlySyncPass() {
        SyncL2ROnlyPerformanceTestDefinitions.testSyncPass(testHarness, runId)
    }

    // Placeholder tests until the whole performance testing suite is in place

    @Test
    fun sampleTest1() {
        var documents1 = arrayListOf<Document>().toList()
        var documents2 = arrayListOf<Document>().toList()

        testHarness.runPerformanceTestWithParams("sampleTest1", runId,
            testDefinition = { ctx, numDocs, docSize ->
                Log.d("perfTests", String.format("Running %s with %d docs of size %d",
                    "initialSync", docSize, numDocs))

                assertEqualsThrows(0L, Tasks.await(ctx.testColl.count()))
                Tasks.await(ctx.testColl.insertMany(documents1))
                assertEqualsThrows(numDocs.toLong(), Tasks.await(ctx.testColl.count()))

                Tasks.await(ctx.testColl.sync().insertMany(documents2))
                doSyncPass(ctx)
            }, beforeEach = { ctx, numDocs, docSize ->
                Log.d("perfTests", String.format("Custom beforeEach for %d docs of size %d",
                    docSize, numDocs))

                documents1 = SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)
                documents2 = SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)

                Tasks.await(ctx.testColl.sync().configure(
                    DefaultSyncConflictResolvers.remoteWins(),
                    null,
                    ExceptionListener { id, ex ->
                        Log.e("perfTests", "unexpected sync error with id " +
                            "$id: ${ex.localizedMessage}")
                        error(ex)
                    }
                ))
        }, afterEach = { ctx, numDocs, docSize ->
                Log.d("perfTests", String.format("Custom afterEach for %d docs of size %d",
                    docSize, numDocs))
                assertEqualsThrows(numDocs, Tasks.await(ctx.testColl.sync().count()).toInt())
                assertEqualsThrows(numDocs, Tasks.await(ctx.testColl.sync().syncedIds).size)
                assertEqualsThrows((numDocs * 2).toLong(), Tasks.await(ctx.testColl.count()))
        })
    }

    fun <T> assertEqualsThrows(expected: T, actual: T, message: String = "") {
        if (expected == actual) {
            return
        }
        throw Exception(String.format("Expected: %s but found %s: %s", expected, actual, message))
    }

    private fun doSyncPass(ctx: SyncPerformanceTestContext) {
        if (ctx.testNetworkMonitor.connectedState) {
            var counter = 0
            while (!ctx.testDataSynchronizer.areAllStreamsOpen()) {
                Thread.sleep(5)

                // if this hangs longer than 30 seconds, throw an error
                counter += 1
                if (counter > 500) {
                    Log.e("perfTests", "stream never opened after reconnect")
                    error("stream never opened after reconnect")
                }
            }

            ctx.testDataSynchronizer.doSyncPass()
        }
    }
}
