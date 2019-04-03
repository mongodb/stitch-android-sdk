package com.mongodb.stitch.android.services.mongodb.remote

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import org.bson.Document

import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import org.bson.types.ObjectId

class SyncL2ROnlyPerformanceTestDefinitions {
    companion object {
        private val TAG = SyncL2ROnlyPerformanceTestDefinitions::class.java.simpleName

        // NOTE: Many of the tests above 1024 bytes and above 500 docs will fail for various
        // reasons because they hit undocumented limits. These failures along with stacktraces will
        // be present in the reported results
        private val docSizes = intArrayOf(1024, 2048, 5120, 10240, 25600, 51200, 102400)
        private val numDocs = intArrayOf(100, 500, 1000, 5000, 10000, 25000)

        fun testInitialSync(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            val testName = "testL2R_InitialSync"
            Log.d(TAG, testName)

            val params = TestParams(
                    runId = runId,
                    testName = testName,
                    dataProbeGranularityMs = 400L,
                    docSizes = docSizes,
                    numDocs = numDocs,
                    numIters = 3,
                    numOutliersEachSide = 0,
                    outputToStitch = true,
                    stitchHostName = "https://stitch.mongodb.com"
            )

            // Local variable for list of documents captured by the test definition closures below.
            // This should change for each iteration of the test.
            var documentsForCurrentTest: List<Document>? = null

            testHarness.runPerformanceTestWithParams(
                    params,
                    beforeEach = { _, numDocs, docSize ->
                        // Generate the documents that are to be synced via L2R
                        Log.i(TAG, "Setting up $testName for $numDocs $docSize-byte docs")
                        documentsForCurrentTest =
                                SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)
                    },
                    testDefinition = { ctx, _, _ ->
                        // Initial sync for a purely L2R scenario means inserting local documents,
                        // and performing a sync pass to sync those documents up to the remote.
                        val sync = ctx.testColl.sync()

                        // If sync fails for any reason, halt the test
                        Tasks.await(sync.configure(
                                DefaultSyncConflictResolvers.remoteWins(),
                                null,
                                ExceptionListener { id, ex ->
                                    Log.e(
                                            TAG,
                                            "unexpected sync error with id " +
                                            "$id: ${ex.localizedMessage}"
                                    )
                                    error(ex)
                                }
                        ))

                        Tasks.await(ctx.testColl.sync().insertMany(documentsForCurrentTest))

                        val syncPassSucceeded = ctx.testDataSynchronizer.doSyncPass()

                        // Halt the test if the sync pass failed
                        if (!syncPassSucceeded) {
                            error("sync pass failed")
                        }
                    },
                    afterEach = { ctx, numDocs, _ ->
                        // Verify that the test did indeed synchronize
                        // the provided documents remotely
                        val numOfDocsSynced = Tasks.await(ctx.testColl.count())
                        if (numDocs.toLong() != numOfDocsSynced) {
                            Log.e(TAG, "$numDocs != $numOfDocsSynced")
                            error("test did not correctly perform the initial sync")
                        }
                    }
            )
        }

        fun testDisconnectReconnect(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            val testName = "testL2R_DisconnectReconnect"
            Log.d(TAG, testName)

            val params = TestParams(
                    runId = runId,
                    testName = testName,
                    dataProbeGranularityMs = 400L,
                    docSizes = docSizes,
                    numDocs = numDocs,
                    numIters = 3,
                    numOutliersEachSide = 0,
                    outputToStitch = true,
                    stitchHostName = "https://stitch.mongodb.com"
            )

            testHarness.runPerformanceTestWithParams(
                    params,
                    beforeEach = { ctx, numDocs: Int, docSize: Int ->
                        // Generate and insert the documents, and perform the initial sync.
                        Log.i(TAG, "Setting up $testName for $numDocs $docSize-byte docs")
                        val documentsForCurrentTest =
                                SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)

                        val sync = ctx.testColl.sync()

                        // If sync fails for any reason, halt the test
                        Tasks.await(sync.configure(
                                DefaultSyncConflictResolvers.remoteWins(),
                                null,
                                ExceptionListener { id, ex ->
                                    Log.e(
                                            TAG,
                                            "unexpected sync error with id " +
                                            "$id: ${ex.localizedMessage}"
                                    )
                                    error(ex)
                                }
                        ))

                        Tasks.await(sync.insertMany(documentsForCurrentTest))

                        val syncPassSucceeded = ctx.testDataSynchronizer.doSyncPass()

                        // Halt the test if the sync pass failed
                        if (!syncPassSucceeded) {
                            error("sync pass failed")
                        }

                        // Disconnect the DataSynchronizer and wait
                        // for the underlying streams to close
                        ctx.testNetworkMonitor.connectedState = false
                        while (ctx.testDataSynchronizer.areAllStreamsOpen()) {
                            Log.i(TAG, "waiting for streams to close")
                            Thread.sleep(1000)
                        }
                    },
                    testDefinition = { ctx, _, _ ->
                        // Reconnect the DataSynchronizer, and wait for the streams to reopen. The
                        // stream being open indicates that the doc configs are now set as stale.
                        // Check every 10ms so we're not doing too much work on this thread, and
                        // don't log anything, so as not to pollute the test results with logging
                        // overhead.
                        ctx.testNetworkMonitor.connectedState = true
                        while (!ctx.testDataSynchronizer.areAllStreamsOpen()) {
                            Thread.sleep(10)
                        }

                        // Do the sync pass that will perform the stale document fetch
                        val syncPassSucceeded = ctx.testDataSynchronizer.doSyncPass()

                        // Halt the test if the sync pass failed
                        if (!syncPassSucceeded) {
                            error("sync pass failed")
                        }
                    },
                    afterEach = { ctx, numDocs: Int, _ ->
                        // Verify that the test did indeed synchronize
                        // the provided documents remotely
                        val numOfDocsSynced = Tasks.await(ctx.testColl.count())
                        if (numDocs.toLong() != numOfDocsSynced) {
                            Log.e(TAG, "$numDocs != $numOfDocsSynced")
                            error("test did not correctly perform the initial sync")
                        }
                    }
            )
        }

        fun testSyncPass(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            // Do an L2R sync pass test where
            // - no documents are changed
            // - 1% of documents are changed
            // - 10% of documents are changed
            // - 25% of documents are changed
            // - 50% of documents are changed
            // - 100% of documents are changed
            val changeEventPercentages = doubleArrayOf(0.0, 0.01, 0.10, 0.25, 0.50, 1.0)

            changeEventPercentages.forEach { doTestSyncPass(testHarness, runId, it) }
        }

        private fun doTestSyncPass(
            testHarness: SyncPerformanceIntTestsHarness,
            runId: ObjectId,
            pctOfDocsWithChangeEvents: Double
        ) {
            val testName = "testL2R_SyncPass_${pctOfDocsWithChangeEvents}DocsChanged"
            Log.d(TAG, testName)

            val params = TestParams(
                    runId = runId,
                    testName = testName,
                    dataProbeGranularityMs = 400L,
                    docSizes = docSizes,
                    numDocs = numDocs,
                    numIters = 3,
                    numOutliersEachSide = 0,
                    outputToStitch = true,
                    stitchHostName = "https://stitch.mongodb.com"
            )

            // Local variable for the number of docs updated in the test
            // This should change for each iteration of the test.
            var numberOfChangedDocs: Int? = null

            testHarness.runPerformanceTestWithParams(
                    params,
                    beforeEach = { ctx, numDocs: Int, docSize: Int ->
                        // Generate and insert the documents, and perform the initial sync.
                        Log.i(TAG, "Setting up $testName test for $numDocs $docSize-byte docs")
                        val documentsForCurrentTest =
                                SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)

                        val sync = ctx.testColl.sync()

                        // If sync fails for any reason, halt the test
                        Tasks.await(sync.configure(
                                DefaultSyncConflictResolvers.remoteWins(),
                                null,
                                ExceptionListener { id, ex ->
                                    Log.e(
                                            TAG, "unexpected sync error with id " +
                                            "$id: ${ex.localizedMessage}")
                                    error(ex)
                                }
                        ))

                        Tasks.await(sync.insertMany(documentsForCurrentTest))

                        val syncPassSucceeded = ctx.testDataSynchronizer.doSyncPass()

                        // Halt the test if the sync pass failed
                        if (!syncPassSucceeded) {
                            error("sync pass failed")
                        }

                        // Randomly sample a percentage of the documents
                        // that will be locally updated
                        val shuffledDocs = documentsForCurrentTest.shuffled()

                        val docsToUpdate =
                                if (pctOfDocsWithChangeEvents > 0.0)
                                    shuffledDocs.subList(
                                        0,
                                        Math.round(pctOfDocsWithChangeEvents*numDocs).toInt())
                                else
                                    emptyList()

                        docsToUpdate.forEach {
                            Tasks.await(sync.updateOne(
                                    Document("_id", it["_id"]),
                                    Document("\$set", Document("newField", "blah"))
                            ))
                        }

                        numberOfChangedDocs = docsToUpdate.size
                    },
                    testDefinition = { ctx, _, _ ->
                        // Do the sync pass that will sync the
                        // local changes to the remote collection
                        val syncPassSucceeded = ctx.testDataSynchronizer.doSyncPass()

                        // Halt the test if the sync pass failed
                        if (!syncPassSucceeded) {
                            error("sync pass failed")
                        }
                    },
                    afterEach = { ctx, numDocs: Int, _ ->
                        // Verify that the test did indeed synchronize
                        // the provided documents remotely
                        val numOfDocsSynced = Tasks.await(ctx.testColl.count())
                        if (numDocs.toLong() != numOfDocsSynced) {
                            Log.e(TAG, "$numDocs != $numOfDocsSynced")
                            error("test did not correctly perform the initial sync")
                        }

                        // Verify that the test did indeed synchronize the provided documents
                        // remotely, and that the documents that were supposed to be updated got
                        // updated.
                        val numOfDocsWithNewField = Tasks.await(ctx.testColl.count(
                                Document("newField", Document("\$exists", true))))
                        if (numberOfChangedDocs!!.toLong() != numOfDocsWithNewField) {
                            Log.e(TAG, "$numberOfChangedDocs != $numOfDocsWithNewField")
                            error("test did not correctly perform the l2r pass")
                        }
                    }
            )
        }
    }
}
