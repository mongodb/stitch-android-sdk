package com.mongodb.stitch.android.services.mongodb.performance

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import org.bson.Document
import org.bson.types.ObjectId

class SyncL2ROnlyPerformanceTestDefinitions {
    companion object {

        fun testInitialSync(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            val testName = "testL2R_InitialSync"

            // Local variable for list of documents captured by the test definition closures below.
            // This should change for each iteration of the test.
            var documentsForCurrentTest: List<Document>? = null

            testHarness.runPerformanceTestWithParams(
                    testName, runId,
                    beforeEach = { _, numDocs, docSize ->
                        // Generate the documents that are to be synced via L2R
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
                                    testHarness.logMessage(
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
                        // Verify that the test did indeed synchronize the updates remotely
                        SyncPerformanceTestUtils.assertLocalAndRemoteDBCount(ctx, numDocs)
                    }
            )
        }

        fun testDisconnectReconnect(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            val testName = "testL2R_DisconnectReconnect"

            testHarness.runPerformanceTestWithParams(
                    testName, runId,
                    beforeEach = { ctx, numDocs: Int, docSize: Int ->
                        // Generate and insert the documents, and perform the initial sync.
                        val documentsForCurrentTest =
                                SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)

                        val sync = ctx.testColl.sync()

                        // If sync fails for any reason, halt the test
                        Tasks.await(sync.configure(
                                DefaultSyncConflictResolvers.remoteWins(),
                                null,
                                ExceptionListener { id, ex ->
                                    testHarness.logMessage(
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
                            testHarness.logMessage("waiting for streams to close")
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
                        var counter = 0
                        while (!ctx.testDataSynchronizer.areAllStreamsOpen()) {
                            Thread.sleep(10)

                            // if this hangs longer than 30 seconds, throw an error
                            counter += 1
                            if (counter > 3000) {
                                testHarness.logMessage("stream never opened after reconnect")
                                error("stream never opened after reconnect")
                            }
                        }

                        // Do the sync pass that will perform the stale document fetch
                        val syncPassSucceeded = ctx.testDataSynchronizer.doSyncPass()

                        // Halt the test if the sync pass failed
                        if (!syncPassSucceeded) {
                            error("sync pass failed")
                        }
                    },
                    afterEach = { ctx, numDocs: Int, _ ->
                        // Verify that the test did indeed synchronize the updates remotely
                        SyncPerformanceTestUtils.assertLocalAndRemoteDBCount(ctx, numDocs)
                    }
            )
        }

        fun testSyncPass(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            // Run doTestSyncPass() for all changeEvent Percentages found in SyncPerfTestUtils
            SyncPerformanceTestUtils.getChangeEventPercentages().forEach {
                doTestSyncPass(testHarness, runId, it)
            }
        }

        private fun doTestSyncPass(
            testHarness: SyncPerformanceIntTestsHarness,
            runId: ObjectId,
            pctOfDocsWithChangeEvents: Double
        ) {
            val testName = "testL2R_SyncPass_${pctOfDocsWithChangeEvents}DocsChanged"

            // Local variable for the number of docs updated in the test
            // This should change for each iteration of the test.
            var numberOfChangedDocs: Int? = null

            testHarness.runPerformanceTestWithParams(
                    testName, runId,
                    beforeEach = { ctx, numDocs: Int, docSize: Int ->
                        // Generate and insert the documents, and perform the initial sync.
                        val documentsForCurrentTest =
                                SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)

                        val sync = ctx.testColl.sync()

                        // If sync fails for any reason, halt the test
                        Tasks.await(sync.configure(
                                DefaultSyncConflictResolvers.remoteWins(),
                                null,
                                ExceptionListener { id, ex ->
                                    testHarness.logMessage(
                                            "unexpected sync error with id " +
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

                        // Randomly sample a percentage of the documents
                        // that will be locally updated
                        val numDocsChanged = Math.round(pctOfDocsWithChangeEvents*numDocs).toInt()
                        val docsToUpdate =
                            if (pctOfDocsWithChangeEvents > 0.0)
                                shuffledDocs.subList(0, numDocsChanged)
                            else
                                emptyList()

                        val updateResult = Tasks.await(sync.updateMany(
                            Document("_id", Document("\$in", docsToUpdate.map { it["_id"] })),
                            Document("\$set", Document("newField", "blah"))
                        ))

                        // Assert that the update worked
                        SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(updateResult.matchedCount.toInt(), numDocsChanged,
                            "RemoteUpdateResult.matchedCount"
                        )
                        SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(updateResult.modifiedCount.toInt(), numDocsChanged,
                            "RemoteUpdateResult.modifiedCount"
                        )
                        val numDocsChangedLocally = Tasks.await(sync.count(
                            Document("newField", Document("\$exists", true))
                        ))
                        SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                            numDocsChanged, numDocsChangedLocally.toInt(), "Local document updates"
                        )

                        numberOfChangedDocs = numDocsChanged
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
                        // Verify that the test did indeed synchronize the updates remotely
                        SyncPerformanceTestUtils.assertLocalAndRemoteDBCount(ctx, numDocs)

                        // Verify that the test did indeed synchronize the provided documents
                        // remotely, and that the documents that were supposed to be updated got
                        // updated.
                        val numDocsChanged = numberOfChangedDocs ?: -1
                        val numDocsWithNewField = Tasks.await(ctx.testColl.count(
                                Document("newField", Document("\$exists", true))))
                        SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                            numDocsChanged, numDocsWithNewField.toInt(), "Remotely synced updates")
                    }
            )
        }
    }
}
