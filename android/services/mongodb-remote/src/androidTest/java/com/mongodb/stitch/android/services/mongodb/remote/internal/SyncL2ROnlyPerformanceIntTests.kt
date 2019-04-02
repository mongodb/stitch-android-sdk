package com.mongodb.stitch.android.services.mongodb.remote.internal

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import org.bson.BsonObjectId
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.Test
import kotlin.random.Random

class SyncL2ROnlyPerformanceIntTests {
    private var testHarness = SyncPerformanceIntTestsHarness()

    private val runId: ObjectId by lazy { ObjectId() }

    companion object {
        private val TAG = SyncL2ROnlyPerformanceIntTests::class.java.simpleName
    }

    // right now, these are not the full range of parameters required by the ticket, because
    // currently it's not possible to L2R sync-many more than 1MB of docs at a time due to the
    // Stitch request size limit. These will need to be batched under the hood.
    private val docSizes = intArrayOf(1024) //, 2048, 5120, 10240, 25600, 51200, 102400)
    private val numDocs = intArrayOf(100, 500) //, 1000, 5000, 10000, 25000)

    private fun generateRandomString(length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwzyz1234567890"
        var str = ""
        repeat(length) {
            str += alphabet[Random.nextInt(alphabet.length)]
        }
        return str
    }

    private fun generateDocuments(docSizeInBytes: Int, numDocs: Int): List<Document> {
        val docList = ArrayList<Document>()

        // to generate the documents, we will use 7-character field names, and 54-character
        // strings as the field values. For each field, we expect 3 bytes of overhead.
        // (the BSON string type code, and two null terminators). This way, each field is 64 bytes.
        // All of the doc sizes we use in this test are divisible by 16, so the number of fields
        // we generate in the document will be the desired document size divided by 16.
        // To account for the 5 byte overhead of defining a BSON document, and the 17 bytes
        // overhead of defining the objectId _id the first field will
        // have 32 characters.
        repeat(numDocs) {
            val doc = Document().append(generateRandomString(7), generateRandomString(32))
            repeat(docSizeInBytes / 16 - 1) {
                doc.append(generateRandomString(7), generateRandomString(54))
            }

            docList.add(doc)
        }
        return docList
    }

    @Test
    fun testInitialSync() {
        val testName = "testL2R_InitialSync"
        Log.d(TAG, testName)

        val params = SyncPerformanceIntTestsHarness.TestParams(
                runId = runId,
                testName = testName,
                dataProbeGranularityMs = 400L,
                docSizes = intArrayOf(1024),//, 2048, 5120, 10240, 25600, 51960, 102400),
                numDocs = intArrayOf(100, 500),//, 1000, 5000, 10000, 25000),
                numIters = 3,
                numOutliersEachSide = 0,
                outputToStitch = true,
                stitchHostName = "https://stitch.mongodb.com"
        )

        // Local variable for list of document ids captured by the test definition closures below.
        // This should change for each iteration of the test.
        var documentIdsForCurrentTest: List<BsonValue>? = null

        testHarness.runPerformanceTestWithParams(
                params,
                testSetup = { docSize: Int, numDocs: Int ->
                    // Generate and insert the documents outside of the test so that the document
                    // generation and local insert process is not a measurable part of the test. We
                    // are primarily concerned with the performance of configuring the local
                    // documents as synced, and the time it takes to sync those local documents
                    // remotely.
                    Log.i(TAG,"Setting up $testName for $numDocs $docSize-byte docs")
                    val documentsForCurrentTest = generateDocuments(docSize, numDocs)

                    Tasks.await(testHarness.testColl.insertMany(documentsForCurrentTest))
                    documentIdsForCurrentTest = documentsForCurrentTest.map {
                        val objId = it.getObjectId("_id")
                        BsonObjectId(objId)
                    }
                },
                testDefinition = { _, _ ->
                    // Initial sync for a purely L2R scenario means syncing locally present
                    // document ids, and performing a single sync pass to synchronize those
                    // local documents to the remote cluster.

                    // halt the test if the sync harness was configured incorrectly
                    if (documentIdsForCurrentTest == null) {
                        error("test harness setup function never ran")
                    }

                    val sync = testHarness.testColl.sync()

                    Tasks.await(sync.configure(
                            DefaultSyncConflictResolvers.remoteWins(),
                            null,
                            ExceptionListener { id, ex ->
                                Log.e(TAG, "unexpected sync error with id $id: ${ex.localizedMessage}")
                                error(ex)
                            }
                    ))

                    Tasks.await(sync.syncMany(
                        *documentIdsForCurrentTest!!.toTypedArray())
                    )

                    val syncPassSucceeded = testHarness.testDataSynchronizer.doSyncPass()

                    // don't report results if the sync pass failed
                    if (!syncPassSucceeded) {
                        error("sync pass failed")
                    }
                },
                testTeardown = { _, numDocs: Int ->
                    // Verify that the test did indeed synchronize the provided documents remotely,
                    // halting the test and invalidating its results otherwise
                    if(numDocs.toLong() != Tasks.await(testHarness.testColl.count())) {
                        error("test did not successfully perform the initial sync")
                    }
                }
        )
    }

    @Test
    fun testDisconnectReconnect() {
        val testName = "testL2R_DisconnectReconnect"
        Log.d(TAG, testName)

        val params = SyncPerformanceIntTestsHarness.TestParams(
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

        // Local variable for list of document ids captured by the test definition closures below.
        // This should change for each iteration of the test.
        var documentIdsForCurrentTest: List<BsonValue>? = null

        testHarness.runPerformanceTestWithParams(
                params,
                testSetup = { docSize: Int, numDocs: Int ->
                    // Generate and insert the documents, and perform the initial sync.
                    Log.i(TAG,"Setting up $testName for $numDocs $docSize-byte docs")
                    val documentsForCurrentTest = generateDocuments(docSize, numDocs)

                    Tasks.await(testHarness.testColl.insertMany(documentsForCurrentTest))
                    documentIdsForCurrentTest = documentsForCurrentTest.map {
                        val objId = it.getObjectId("_id")
                        BsonObjectId(objId)
                    }

                    val sync = testHarness.testColl.sync()

                    Tasks.await(sync.configure(
                            DefaultSyncConflictResolvers.remoteWins(),
                            null,
                            ExceptionListener { id, ex ->
                                Log.e(TAG, "unexpected sync error with id $id: ${ex.localizedMessage}")
                                error(ex)
                            }
                    ))

                    Tasks.await(sync.syncMany(
                            *documentIdsForCurrentTest!!.toTypedArray())
                    )

                    val syncPassSucceeded = testHarness.testDataSynchronizer.doSyncPass()

                    // don't report results if the sync pass failed
                    if (!syncPassSucceeded) {
                        error("sync pass failed")
                    }

                    testHarness.testNetworkMonitor.connectedState = false
                    while(testHarness.testDataSynchronizer.areAllStreamsOpen()) {
                        Log.i(TAG, "waiting for streams to close")
                        Thread.sleep(1000)
                    }
                },
                testDefinition = { _, _ ->
                    // Reconnect the DataSynchronizer, and wait for the streams to reopen. The
                    // stream being open indicates that the doc configs are now set as stale.
                    // Check every 10ms so we're not doing too much work on this thread, and don't
                    // log anything, so as not to pollute the test results with logging overhead.
                    testHarness.testNetworkMonitor.connectedState = true
                    while(!testHarness.testDataSynchronizer.areAllStreamsOpen()) {
                        Thread.sleep(10)
                    }

                    // Do the sync pass that will perform the stale document fetch
                    val syncPassSucceeded = testHarness.testDataSynchronizer.doSyncPass()

                    // Halt the test if the sync pass failed
                    if (!syncPassSucceeded) {
                        error("sync pass failed")
                    }
                },
                testTeardown = { _, numDocs: Int ->
                    // Verify that the test did indeed synchronize the provided documents remotely,
                    // halting the test and invalidating its results otherwise
                    if(numDocs.toLong() != Tasks.await(testHarness.testColl.count())) {
                        error("test did not successfully perform the initial sync")
                    }
                }
        )
    }

    @Test
    fun testSyncPass() {
        val changeEventPercentages = doubleArrayOf(0.0, 0.01, 0.10, 0.25, 0.50, 1.0)

        // perform the sync pass test for each desired percentage of changed documents
        changeEventPercentages.forEach { doTestSyncPass(it) }
    }

    private fun doTestSyncPass(pctOfDocsWithChangeEvents: Double) {
        val testName = "testL2R_${pctOfDocsWithChangeEvents}DocsChanged"
        Log.d(TAG, testName)

        val params = SyncPerformanceIntTestsHarness.TestParams(
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

        // Local variable for list of document ids captured by the test definition closures below.
        // This should change for each iteration of the test.
        var documentIdsForCurrentTest: List<BsonValue>? = null
        var numberOfChangedDocs: Int? = null

        testHarness.runPerformanceTestWithParams(
                params,
                testSetup = { docSize: Int, numDocs: Int ->
                    // Generate and insert the documents, and perform the initial sync.
                    Log.i(TAG,"Setting up $testName test for $numDocs $docSize-byte docs")
                    val documentsForCurrentTest = generateDocuments(docSize, numDocs)

                    Tasks.await(testHarness.testColl.insertMany(documentsForCurrentTest))
                    documentIdsForCurrentTest = documentsForCurrentTest.map {
                        val objId = it.getObjectId("_id")
                        BsonObjectId(objId)
                    }

                    val sync = testHarness.testColl.sync()

                    Tasks.await(sync.configure(
                            DefaultSyncConflictResolvers.remoteWins(),
                            null,
                            ExceptionListener { id, ex ->
                                Log.e(TAG, "unexpected sync error with id $id: ${ex.localizedMessage}")
                                error(ex)
                            }
                    ))

                    Tasks.await(sync.syncMany(*documentIdsForCurrentTest!!.toTypedArray()))

                    val syncPassSucceeded = testHarness.testDataSynchronizer.doSyncPass()

                    // don't report results if the sync pass failed
                    if (!syncPassSucceeded) {
                        error("sync pass failed")
                    }

                    // randomly sample a percentage of the documents to trigger local updates
                    // that will be synced to the remote in the next sync pass
                    val shuffledIds = documentIdsForCurrentTest!!.shuffled()

                    val idsToUpdate = if (pctOfDocsWithChangeEvents > 0.0) shuffledIds.subList(
                            0,
                            Math.round(pctOfDocsWithChangeEvents*numDocs).toInt()
                    ) else emptyList()

                    idsToUpdate.forEach { id ->
                        Tasks.await(sync.updateOne(
                                Document("_id", id),
                                Document("\$set", Document("newField", "blah"))
                        ))
                    }

                    numberOfChangedDocs = idsToUpdate.size
                },
                testDefinition = { _, _ ->
                    // Do the sync pass that will sync the changed L2R documents
                    val syncPassSucceeded = testHarness.testDataSynchronizer.doSyncPass()

                    // halt the test if the sync pass failed
                    if (!syncPassSucceeded) {
                        error("sync pass failed")
                    }
                },
                testTeardown = { _, numDocs: Int ->
                    // Verify that the test did indeed synchronize the provided documents remotely,
                    // and that the documents that were supposed to be updated got updated.
                    // Halt the test and invalidate its results otherwise
                    if(numDocs.toLong() != Tasks.await(testHarness.testColl.count())) {
                        error("test did not successfully perform the initial sync")
                    }

                    val numOfDocsWithNewField = Tasks.await(testHarness.testColl.count(
                            Document("newField", Document("\$exists", true))))

                    if(numberOfChangedDocs!!.toLong() != numOfDocsWithNewField) {
                        error("test did not successfully perform the l2r pass")
                    }
                }
        )
    }

}