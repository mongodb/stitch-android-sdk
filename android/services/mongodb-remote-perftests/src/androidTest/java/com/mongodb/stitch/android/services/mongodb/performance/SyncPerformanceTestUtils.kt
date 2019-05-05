package com.mongodb.stitch.android.services.mongodb.performance

import android.support.test.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncConfiguration
import org.bson.BsonValue
import org.bson.Document
import kotlin.random.Random

/**
 * A collection of utility functions for writing performance tests.
 */
class SyncPerformanceTestUtils {
    companion object {
        const val STITCH_PROD_HOST = "https://stitch.mongodb.com"

        private const val stitchHostnameProp = "test.stitch.perf.stitchHost"
        private const val defaultStitchHostname = "http://10.0.2.2:9090"

        private const val itersProp = "test.stitch.perf.iters"
        private const val defaultIters = "5"

        private const val hostnameProp = "test.stitch.perf.hostname"
        private const val defaultHostname = "Local"

        private const val docSizesProp = "test.stitch.perf.docSizes"
        private const val defaultDocSizes = "1024,2048,5120,10240,25600,51200,102400"

        private const val numDocsProp = "test.stitch.perf.numDocs"
        private const val defaultNumDocs = "100,500,1000,5000,10000,25000"

        private const val dataGranularityProp = "test.stitch.perf.dataGranularity"
        private const val defaultDataGranularity = "1000"

        private const val numOutliersProp = "test.stitch.perf.outliers"
        private const val defaultNumOutliers = "0"

        private const val outputToStdOutProp = "test.stitch.perf.outputStdout"
        private const val defaultOutputToStdOut = "true"

        private const val outputToStitchProp = "test.stitch.perf.outputStitch"
        private const val defaultOutputToStitch = "true"

        private const val preserveRawOutputProp = "test.stitch.perf.rawOutput"
        private const val defaultPreserveRawOutput = "false"

        private const val changeEventPercentagesProp = "test.stitch.perf.changeEventPercentages"
        private const val defaultChangeEventPercentages = "0.0,0.01,0.10,0.25,0.50,1.0"

        private const val conflictPercentagesProp = "test.stitch.perf.conflictPercentages"
        private const val defaultConflictPercentagesProp = "0.0,0.1,0.5,1.0"

        internal fun getStitchHostname(): String {
            return InstrumentationRegistry.getArguments().getString(
                    stitchHostnameProp, defaultStitchHostname
            )
        }

        internal fun getHostname(): String {
            return InstrumentationRegistry.getArguments().getString(hostnameProp, defaultHostname)
        }

        internal fun getNumIters(): Int {
            return Integer.parseInt(InstrumentationRegistry.getArguments().getString(
                    itersProp, defaultIters
            ))
        }

        internal fun getDocSizes(): IntArray {
            return InstrumentationRegistry.getArguments().getString(docSizesProp, defaultDocSizes)
                .split(",").map { Integer.parseInt(it) }.toIntArray()
        }

        internal fun getNumDocs(): IntArray {
            return InstrumentationRegistry.getArguments().getString(numDocsProp, defaultNumDocs)
                .split(",").map { Integer.parseInt(it) }.toIntArray()
        }

        internal fun getDataGranularity(): Long {
            return Integer.parseInt(InstrumentationRegistry.getArguments().getString(
                dataGranularityProp, defaultDataGranularity
            )).toLong()
        }

        internal fun getNumOutliers(): Int {
            return Integer.parseInt(InstrumentationRegistry.getArguments().getString(
                numOutliersProp, defaultNumOutliers
            ))
        }

        internal fun getChangeEventPercentages(): DoubleArray {
            return InstrumentationRegistry.getArguments()
                .getString(changeEventPercentagesProp, defaultChangeEventPercentages)
                .split(",").map { it.toDouble() }.toDoubleArray()
        }

        internal fun getConflictPercentages(): DoubleArray {
            return InstrumentationRegistry.getArguments()
                .getString(conflictPercentagesProp, defaultConflictPercentagesProp)
                .split(",").map { it.toDouble() }.toDoubleArray()
        }

        internal fun shouldOutputToStdOut(): Boolean {
            return InstrumentationRegistry.getArguments().getString(
                outputToStdOutProp, defaultOutputToStdOut)!!.toBoolean()
        }

        internal fun shouldOutputToStitch(): Boolean {
            return InstrumentationRegistry.getArguments().getString(
                outputToStitchProp, defaultOutputToStitch)!!.toBoolean()
        }

        internal fun shouldPreserveRawOutput(): Boolean {
            return InstrumentationRegistry.getArguments().getString(
                preserveRawOutputProp, defaultPreserveRawOutput)!!.toBoolean()
        }

        internal fun assertLocalAndRemoteDBCount(ctx: SyncPerformanceTestContext, numDocs: Int) {
            // Verify that the test did indeed synchronize the updates remotely
            val numSyncedIds = Tasks.await(ctx.testColl.sync().syncedIds).size
            val numLocalDocs = Tasks.await(ctx.testColl.sync().count()).toInt()
            val numRemoteDocs = Tasks.await(ctx.testColl.count()).toInt()

            SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                numSyncedIds, numDocs, "Number of Synced Ids")
            SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                numLocalDocs, numDocs, "Number of Local Documents")
            SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                numRemoteDocs, numDocs, "Number of Remote Documents")
        }

        internal fun assertIntsAreEqualOrThrow(actual: Int, expected: Int, message: String = "") {
            if (expected == actual) {
                return
            }
            throw PerformanceTestingException(
                String.format("Expected: %s but found %s: %s", expected, actual, message))
        }

        internal fun doSyncPass(ctx: SyncPerformanceTestContext) {
            ctx.testNetworkMonitor.connectedState = true
            var counter = 0
            while (!ctx.testDataSynchronizer.areAllStreamsOpen()) {
                Thread.sleep(30)
                // if this hangs longer than 30 seconds, throw a PerformanceTestingException
                counter += 1
                if (counter > 1000) {
                    throw PerformanceTestingException("Stream never opened after reconnect")
                }
            }

            if (!ctx.testDataSynchronizer.doSyncPass()) {
                throw PerformanceTestingException("Sync Pass Failed")
            }
        }

        internal fun insertToRemote(
            ctx: SyncPerformanceTestContext,
            numDocs: Int,
            docSize: Int
        ): List<BsonValue> {
            val docs = SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)
            var insertManyResult = Tasks.await(ctx.testColl.insertMany(docs))
            assertIntsAreEqualOrThrow(insertManyResult.insertedIds.size, numDocs)

            return insertManyResult.insertedIds.map { it.value }
        }

        internal fun insertToLocal(
            ctx: SyncPerformanceTestContext,
            numDocs: Int,
            docSize: Int
        ): List<BsonValue> {
            val docs = SyncPerformanceTestUtils.generateDocuments(docSize, numDocs)
            var insertManyResult = Tasks.await(ctx.testColl.sync().insertMany(docs))
            assertIntsAreEqualOrThrow(insertManyResult.insertedIds.size, numDocs)

            return insertManyResult.insertedIds.map { it.value }
        }

        internal fun generateDocuments(docSizeInBytes: Int, numDocs: Int): List<Document> {
            val docList = ArrayList<Document>()

            // To generate the documents, we use 7-character field names, and 54-character
            // strings as the field values. For each field, we expect 3 bytes of overhead.
            // (the BSON string type code, and two null terminators). This way, each field is 64
            // bytes. All of the doc sizes we use in this test are divisible by 64, so the number
            // of fields we generate in the document will be the desired document size divided by
            // 64. To account for the 5 byte overhead of defining a BSON document, and the 17 bytes
            // overhead of defining the objectId _id, the first field will have 32 characters.
            repeat(numDocs) {
                val doc = Document()
                        .append(generateRandomString(7), generateRandomString(32))
                repeat(docSizeInBytes / 64 - 1) {
                    doc.append(generateRandomString(7), generateRandomString(54))
                }

                docList.add(doc)
            }
            return docList
        }

        private fun generateRandomString(length: Int): String {
            val alphabet = "abcdefghijklmnopqrstuvwzyz1234567890"
            var str = ""
            repeat(length) {
                str += alphabet[Random.nextInt(alphabet.length)]
            }
            return str
        }

        fun performRemoteUpdate(
            ctx: SyncPerformanceTestContext,
            ids: List<BsonValue>
        ) {
            val updateResult = Tasks.await(ctx.testColl.updateMany(
                Document("_id", Document("\$in", ids)),
                Document("\$set", Document("newField", "remote"))
            ))

            // Assert that the remote update worked
            assertIntsAreEqualOrThrow(
                updateResult.matchedCount.toInt(),
                ids.size,
                "RemoteUpdateResult.matchedCount")
            assertIntsAreEqualOrThrow(
                updateResult.modifiedCount.toInt(),
                ids.size,
                "RemoteUpdateResult.modifiedCount")

            val numDocsChangedRemotely = Tasks.await(ctx.testColl.count(
                Document("newField", "remote")
            ))

            SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                ids.size,
                numDocsChangedRemotely.toInt(),
                "Remote document updates"
            )
        }

        fun performLocalUpdate(
            ctx: SyncPerformanceTestContext,
            ids: List<BsonValue>,
            additionalCount: Int = 0
        ) {
            val updateResult = Tasks.await(ctx.testColl.sync().updateMany(
                Document("_id", Document("\$in", ids)),
                Document("\$set", Document("newField", "local"))
            ))

            // Assert that the remote update worked
            assertIntsAreEqualOrThrow(
                updateResult.matchedCount.toInt(),
                ids.size,
                "LocalUpdateResult.matchedCount")
            assertIntsAreEqualOrThrow(
                updateResult.modifiedCount.toInt(),
                ids.size,
                "LocalUpdateResult.modifiedCount")

            val numDocsChangedLocally = Tasks.await(ctx.testColl.sync().count(
                Document("newField", "local")
            ))

            SyncPerformanceTestUtils.assertIntsAreEqualOrThrow(
                ids.size + additionalCount,
                numDocsChangedLocally.toInt(),
                "Local document updates"
            )
        }

        fun defaultConfigure(ctx: SyncPerformanceTestContext) {
            val config = SyncConfiguration.Builder()
                .withConflictHandler(DefaultSyncConflictResolvers.remoteWins<Document>())
                .withExceptionListener(ExceptionListener { id, ex ->
                    testHarness.logMessage(
                        "unexpected sync error with id " +
                            "$id: ${ex.localizedMessage}")
                    error(ex)
                }).build()

            Tasks.await(ctx.testColl.sync().configure(config))
        }
    }
}
