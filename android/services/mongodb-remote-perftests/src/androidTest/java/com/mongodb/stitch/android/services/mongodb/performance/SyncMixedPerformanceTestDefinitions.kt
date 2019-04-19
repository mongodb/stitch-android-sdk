package com.mongodb.stitch.android.services.mongodb.performance

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.services.mongodb.performance.SyncPerformanceTestUtils.Companion.assertIntsAreEqualOrThrow
import com.mongodb.stitch.android.services.mongodb.performance.SyncPerformanceTestUtils.Companion.doSyncPass
import com.mongodb.stitch.android.services.mongodb.performance.SyncPerformanceTestUtils.Companion.performLocalUpdate
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.ObjectId

class SyncMixedPerformanceTestDefinitions {
    companion object {

        /*
         * Before: Perform remote insert of numDoc / 2 documents
         *         Perform a local insert of numDoc / 2 documents
         *         Ensure there are numConflict conflicts
         * Test: Configure sync to sync on the inserted docs and perform a sync pass
         * After: Ensure that the initial sync worked as expected
         */
        fun testInitialSync(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            SyncPerformanceTestUtils.getConflictPercentages().forEach { conflictPercentage ->
                performInitialSync(testHarness, runId, conflictPercentage)
            }
        }

        fun performInitialSync(
            testHarness: SyncPerformanceIntTestsHarness,
            runId: ObjectId,
            conflictPercentage: Double
        ) {
            val testName = "Mixed_InitialSync_${(conflictPercentage * 100).toInt()}__PctDocsConflicts"

            // Local variable for list of documents captured by the test definition closures below.
            // This should change for each iteration of the test.
            var documentIds: Set<BsonValue>? = null

            testHarness.runPerformanceTestWithParams(
                testName, runId,
                beforeEach = { ctx, numDocs, docSize ->
                    val docIDs = mutableSetOf<BsonValue>()
                    val numEach = numDocs / 2

                    // Generate the documents that are to be synced via R2L and remotely insert them
                    val remoteDocs = SyncPerformanceTestUtils.generateDocuments(docSize, numEach)
                    val remoteInsertResult = Tasks.await(ctx.testColl.insertMany(remoteDocs))
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        remoteInsertResult.insertedIds.size,
                        numEach)
                    docIDs.addAll(remoteInsertResult.insertedIds.map { it.value })

                    // Use numEach * pctConflict of the remote documents in a local insert
                    val numConflicts = (numEach * conflictPercentage).toInt()
                    var localDocs = remoteDocs.subList(0, numConflicts) +
                        SyncPerformanceTestUtils.generateDocuments(docSize, numEach - numConflicts)
                    val localInsertResult = Tasks.await(ctx.testColl.sync().insertMany(localDocs))
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        localInsertResult.insertedIds.size,
                        numEach)
                    docIDs.addAll(localInsertResult.insertedIds.map { it.value })

                    //  Ensure that the documents properly conflicted
                    assertIntsAreEqualOrThrow(
                        docIDs.size,
                        numDocs - numConflicts,
                        "Union of local and remote document ids")

                    documentIds = docIDs.toSet()
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
                    Tasks.await(sync.syncMany(*(documentIds!!.toTypedArray())))

                    // Perform syncPass() and halt the test if the pass fails
                    SyncPerformanceTestUtils.doSyncPass(ctx)
                },
                afterEach = { ctx, numDocs, _ ->
                    // Verify that the test did indeed synchronize the provided documents correctly
                    val numConflicts = (numDocs / 2 * conflictPercentage).toInt()
                    SyncPerformanceTestUtils.assertLocalAndRemoteDBCount(ctx, numDocs - numConflicts)
                }
            )
        }
        /*
        * Before: Perform remote insert of numDoc / 2 documents
        *         Perform a local insert of numDoc / 2 documents
        *         Configure sync(), perform sync pass, disconnect networkMonitor
        *         Ensure sync worked properly
        * Test: Reconnect the network monitor and perform sync pass
        * After: Ensure that the sync pass worked as expected
        */
        fun testDisconnectReconnect(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            val testName = "Mixed_DisconnectReconnect"

            testHarness.runPerformanceTestWithParams(
                testName, runId,
                beforeEach = { ctx, numDocs, docSize ->
                    val sync = ctx.testColl.sync()

                    // Generate the documents that are to be synced and insert them
                    val remoteIds = SyncPerformanceTestUtils.insertToRemote(
                        ctx, numDocs / 2, docSize
                    )
                    val localIds = SyncPerformanceTestUtils.insertToLocal(
                        ctx, numDocs / 2, docSize
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
                    Tasks.await(sync.syncMany(*(remoteIds.toTypedArray())))
                    Tasks.await(sync.syncMany(*(localIds.toTypedArray())))

                    // Perform sync pass, it will throw an exception if it fails
                    SyncPerformanceTestUtils.doSyncPass(ctx)

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
                    SyncPerformanceTestUtils.assertLocalAndRemoteDBCount(ctx, numDocs)
                }
            )
        }

        /*
         * Before: Perform remote insert of numDoc / 2 documents
         *         Perform a local insert of numDoc / 2 documents
         *         Configure sync(), perform sync pass
         *         Update numChangeEvents / 2 documents remotely
         *         Update numChangeEvents / 2 documents locally
         *              Where numConflicts docs are updates on the same documents
         * Test: Perform sync pass
         * After: Ensure that the sync pass worked properly
         */
        fun testSyncPass(testHarness: SyncPerformanceIntTestsHarness, runId: ObjectId) {
            // Run doTestSyncPass() for all changeEvent Percentages found in SyncPerfTestUtils
            SyncPerformanceTestUtils.getChangeEventPercentages().forEach { changeEventPercentage ->
                SyncPerformanceTestUtils.getConflictPercentages().forEach { conflictPercentage ->
                    doTestSyncPass(testHarness, runId, changeEventPercentage, conflictPercentage)
                }
            }
        }

        private fun doTestSyncPass(
            testHarness: SyncPerformanceIntTestsHarness,
            runId: ObjectId,
            pctOfDocsWithChangeEvents: Double,
            pctOfDocsWithConflicts: Double
        ) {
            val testName = "Mixed_SyncPass_${(pctOfDocsWithChangeEvents * 100).toInt()}" +
                "_PctDocsChanged_${(pctOfDocsWithConflicts * 100).toInt()}_PctDocsConflicts"

            testHarness.runPerformanceTestWithParams(
                testName, runId,
                beforeEach = { ctx, numDocs: Int, docSize: Int ->
                    val sync = ctx.testColl.sync()

                    // Generate the documents that are to be synced and insert them
                    val remoteIds = SyncPerformanceTestUtils.insertToRemote(
                        ctx, numDocs / 2, docSize
                    )
                    val localIds = SyncPerformanceTestUtils.insertToLocal(
                        ctx, numDocs / 2, docSize
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
                    Tasks.await(sync.syncMany(*(remoteIds.toTypedArray())))
                    Tasks.await(sync.syncMany(*(localIds.toTypedArray())))

                    // Perform sync pass, it will throw an exception if it fails
                    SyncPerformanceTestUtils.doSyncPass(ctx)

                    // Verify that the test did indeed synchronize the provided documents locally
                    SyncPerformanceTestUtils.assertLocalAndRemoteDBCount(ctx, numDocs)

                    // Shuffle the documents
                    val allIds = Tasks.await(sync.syncedIds).toList().shuffled()

                    // Remotely update the desired percentage of documents and check it works
                    val numRemoteChange = (pctOfDocsWithChangeEvents * numDocs / 2).toInt()
                    SyncPerformanceTestUtils.performRemoteUpdate(ctx, allIds.take(numRemoteChange))

                    // Locally update the documents that will conflict with the remote update
                    val numLocalConflict = (numRemoteChange * pctOfDocsWithConflicts).toInt()
                    SyncPerformanceTestUtils.performLocalUpdate(ctx, allIds.take(numLocalConflict))

                    // Then update the documents that will not have conflicts
                    SyncPerformanceTestUtils.performLocalUpdate(ctx,
                        allIds.takeLast(numRemoteChange - numLocalConflict), numLocalConflict)
                },
                testDefinition = { ctx, _, _ ->
                    // Do the sync pass that will sync the remote changes to the local collection
                    // Perform syncPass() and halt the test if the pass fails
                    SyncPerformanceTestUtils.doSyncPass(ctx)
                },
                afterEach = { ctx, numDocs: Int, _ ->
                    // Verify that the test did indeed synchronize the updates locally
                    SyncPerformanceTestUtils.assertLocalAndRemoteDBCount(ctx, numDocs)

                    val numRemoteUpdates = (numDocs / 2 * pctOfDocsWithChangeEvents).toInt()
                    val numLocalUpdates = numRemoteUpdates -
                        (numRemoteUpdates * pctOfDocsWithConflicts).toInt()

                    // Both the local and remote should have
                    //      numRemoteUpdates documents with {newField: "remote"}
                    //      numLocalUpdates documents with {newField: "local"}
                    var res = Tasks.await(ctx.testColl.count(Document("newField", "remote")))
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        res.toInt(), numRemoteUpdates, "Num Remote Updates After Test"
                    )

                    res = Tasks.await(ctx.testColl.sync().count(Document("newField", "remote")))
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        res.toInt(), numRemoteUpdates, "Num Local Updates After Test"
                    )

                    res = Tasks.await(ctx.testColl.count(Document("newField", "local")))
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        res.toInt(), numLocalUpdates, "Num Remote Updates After Test"
                    )

                    res = Tasks.await(ctx.testColl.sync().count(Document("newField", "local")))
                    SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                        res.toInt(), numLocalUpdates  , "Num Local Updates After Test"
                    )
                }
            )
        }
    }
}
