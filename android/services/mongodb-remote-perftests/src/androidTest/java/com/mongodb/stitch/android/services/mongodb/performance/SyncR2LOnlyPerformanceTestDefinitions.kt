package com.mongodb.stitch.android.services.mongodb.performance

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.services.mongodb.performance.SyncPerformanceTestUtils.Companion.assertIntsAreEqualOrThrow
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.ObjectId

class SyncR2LOnlyPerformanceTestDefinitions {
    companion object {

        fun testInitialSync(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            val testName = "R2L_InitialSync"

            // Local variable for list of documents captured by the test definition closures below.
            // This should change for each iteration of the test.
            var documentIdsForCurrentTest: List<BsonValue?>? = null

            // Initial sync for a purely R2L Scenario means inserting remote documents and then
            // configuring syncMany() on the inserted document id's and performing a sync pass.
            testHarness.runPerformanceTestWithParams(
                testName, runId,
                beforeEach = { ctx, numDocs, docSize ->
                    // Generate the documents that are to be synced via R2L and remotely insert them
                    documentIdsForCurrentTest = SyncPerformanceTestUtils.insertToRemote(
                        ctx, numDocs, docSize
                    )
                },
                testDefinition = { ctx, _, _ ->
                    val sync = ctx.testColl.sync()

                    // If sync fails for any reason, halt the test
                    Tasks.await(sync.configure(
                        DefaultSyncConflictResolvers.remoteWins(),
                        null,
                        ExceptionListener { id, ex ->
                            testHarness.logMessage("unexpected sync error with id " +
                                "$id: ${ex.localizedMessage}")
                            error(ex)
                        }
                    ))

                    // Sync() on all of the inserted document ids
                    Tasks.await(sync.syncMany(*(documentIdsForCurrentTest!!.toTypedArray())))

                    // Perform syncPass() and halt the test if the pass fails
                    if (!SyncPerformanceTestUtils.doSyncPass(ctx)) {
                        error("sync pass failed")
                    }
                },
                afterEach = { ctx, numDocs, _ ->
                    // Verify that the test did indeed synchronize the provided documents locally
                    SyncPerformanceTestUtils.asertLocalAndRemoteDBCount(ctx, numDocs)
                }
            )
        }

        fun testDisconnectReconnect(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            val testName = "R2L_DisconnectReconnect"

            // Local variable for list of documents captured by the test definition closures below.
            // This should change for each iteration of the test.
            var documentIdsForCurrentTest: List<BsonValue?>? = null

            testHarness.runPerformanceTestWithParams(
                testName, runId,
                beforeEach = { ctx, numDocs, docSize ->
                    val sync = ctx.testColl.sync()

                    // Generate the documents that are to be synced via R2L and insert remotely
                    val ids = SyncPerformanceTestUtils.insertToRemote(
                        ctx, numDocs, docSize
                    )

                    // If sync fails for any reason, halt the test
                    Tasks.await(sync.configure(
                        DefaultSyncConflictResolvers.remoteWins(),
                        null,
                        ExceptionListener { id, ex ->
                            testHarness.logMessage("unexpected sync error with id " +
                                "$id: ${ex.localizedMessage}")
                            error(ex)
                        }
                    ))

                    // Sync() on all of the inserted document ids
                    Tasks.await(sync.syncMany(*(ids!!.toTypedArray())))

                    // Halt the test if the sync pass failed
                    if (!SyncPerformanceTestUtils.doSyncPass(ctx)) {
                        error("sync pass failed")
                    }

                    // Verify that the test did indeed synchronize the provided documents locally
                    val numSyncedIds = Tasks.await(sync.syncedIds).size
                    val numLocalDocs = Tasks.await(sync.count())

                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        numSyncedIds, numDocs, "Number of Synced Ids")
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        numLocalDocs.toInt(), numDocs, "Number of Local Documents")

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

                    // Perform syncPass() and halt the test if the pass fails
                    if (!syncPassSucceeded) {
                        error("sync pass failed")
                    }
                },
                afterEach = { ctx, numDocs, _ ->
                    // Verify that the test did indeed synchronize the updates locally
                    SyncPerformanceTestUtils.asertLocalAndRemoteDBCount(ctx, numDocs)
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
            val testName = "testR2L_SyncPass_${pctOfDocsWithChangeEvents}_PctDocsChanged"

            // Local variable for the number of docs updated in the test
            // This should change for each iteration of the test.
            var numberOfChangedDocs: Int? = null

            testHarness.runPerformanceTestWithParams(
                testName, runId,
                beforeEach = { ctx, numDocs: Int, docSize: Int ->
                    val sync = ctx.testColl.sync()

                    // Generate the documents that are to be synced via R2L and insert remotely
                    val ids = SyncPerformanceTestUtils.insertToRemote(
                        ctx, numDocs, docSize
                    )

                    // If sync fails for any reason, halt the test
                    Tasks.await(ctx.testColl.sync().configure(
                        DefaultSyncConflictResolvers.remoteWins(),
                        null,
                        ExceptionListener { id, ex ->
                            testHarness.logMessage(
                                "unexpected sync error with id " +
                                "$id: ${ex.localizedMessage}")
                            error(ex)
                        }
                    ))

                    // Sync on the ids inserted remotely
                    Tasks.await(sync.syncMany(*(ids!!.toTypedArray())))

                    // Halt the test if the sync pass failed
                    if (!SyncPerformanceTestUtils.doSyncPass(ctx)) {
                        error("sync pass failed")
                    }

                    // Verify that the test did indeed synchronize the provided documents locally
                    val numSyncedIds = Tasks.await(sync.syncedIds).size
                    val numLocalDocs = Tasks.await(sync.count())

                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        numSyncedIds, numDocs, "Number of Synced Ids")
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        numLocalDocs.toInt(), numDocs, "Number of Local Documents")

                    // Disconnect the DataSynchronizer and wait
                    // for the underlying streams to close
                    ctx.testNetworkMonitor.connectedState = false
                    while (ctx.testDataSynchronizer.areAllStreamsOpen()) {
                        testHarness.logMessage("waiting for streams to close")
                        Thread.sleep(1000)
                    }

                    // Randomly sample a percentage of the documents
                    // that will be locally updated
                    val shuffledDocIds = ids.shuffled()
                    val numChangedDocs = Math.round(pctOfDocsWithChangeEvents*numDocs).toInt()
                    val docsToUpdate =
                        if (pctOfDocsWithChangeEvents > 0.0)
                            shuffledDocIds.subList(0, numChangedDocs)
                        else
                            emptyList()

                    val updateResult = Tasks.await(ctx.testColl.updateMany(
                        Document("_id", Document("\$in", docsToUpdate)),
                        Document("\$set", Document("newField", "blah"))
                    ))

                    // Assert that the update worked
                    assertIntsAreEqualOrThrow(updateResult.matchedCount.toInt(), numChangedDocs,
                        "RemoteUpdateResult.matchedCount")
                    assertIntsAreEqualOrThrow(updateResult.modifiedCount.toInt(), numChangedDocs,
                        "RemoteUpdateResult.modifiedCount")

                    val numDocsChangedRemotely = Tasks.await(ctx.testColl.count(
                        Document("newField", Document("\$exists", true))
                    ))
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        numChangedDocs, numDocsChangedRemotely.toInt(), "Remote document updates"
                    )

                    numberOfChangedDocs = numChangedDocs
                },
                testDefinition = { ctx, _, _ ->
                    // Do the sync pass that will sync the remote changes to the local collection
                    // Perform syncPass() and halt the test if the pass fails
                    if (!SyncPerformanceTestUtils.doSyncPass(ctx)) {
                        error("sync pass failed")
                    }

                    if (!SyncPerformanceTestUtils.doSyncPass(ctx)) {
                        error("sync pass failed")
                    }
                },
                afterEach = { ctx, numDocs: Int, _ ->
                    // Verify that the test did indeed synchronize the updates locally
                    SyncPerformanceTestUtils.asertLocalAndRemoteDBCount(ctx, numDocs)

                    // Verify the updates were applied locally
                    val numChangedDocs = numberOfChangedDocs ?: -1
                    val numDocsChangedLocally = Tasks.await(ctx.testColl.sync().count(
                        Document("newField", Document("\$exists", true))
                    )).toInt()
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        numDocsChangedLocally, numChangedDocs, "Local document updates"
                    )
                }
            )
        }
    }
}
