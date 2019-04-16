package com.mongodb.stitch.android.services.mongodb.performance

import android.support.test.InstrumentationRegistry
import org.bson.Document
import kotlin.random.Random

/**
 * A collection of utility functions for writing performance tests.
 */
class SyncPerformanceTestUtils {
    companion object {
        public const val STITCH_PROD_HOST = "https://stitch.mongodb.com"

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

        internal fun generateDocuments(docSizeInBytes: Int, numDocs: Int): List<Document> {
            val docList = ArrayList<Document>()

            // To generate the documents, we use 7-character field names, and 54-character
            // strings as the field values. For each field, we expect 3 bytes of overhead.
            // (the BSON string type code, and two null terminators). This way, each field is 64
            // bytes. All of the doc sizes we use in this test are divisible by 16, so the number
            // of fields we generate in the document will be the desired document size divided by
            // 16. To account for the 5 byte overhead of defining a BSON document, and the 17 bytes
            // overhead of defining the objectId _id, the first field will have 32 characters.
            repeat(numDocs) {
                val doc = Document()
                        .append(generateRandomString(7), generateRandomString(32))
                repeat(docSizeInBytes / 16 - 1) {
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
    }
}
