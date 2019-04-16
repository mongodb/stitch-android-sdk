package com.mongodb.stitch.android.services.mongodb.performance

import android.support.test.InstrumentationRegistry
import org.bson.Document
import kotlin.random.Random

/**
 * A collection of utility functions for writing performance tests.
 */
class SyncPerformanceTestUtils {
    companion object {
        private const val stitchHostnameProp = "test.stitch.androidPerfStitchHostname"
        private const val defaultStitchHostname = "https://stitch.mongodb.com"

        private const val itersProp = "test.stitch.androidPerfIters"
        private const val defaultIters = "3"

        internal fun getConfiguredStitchHostname(): String {
            return InstrumentationRegistry.getArguments().getString(
                    stitchHostnameProp, defaultStitchHostname
            )
        }

        internal fun getConfiguredIters(): Int {
            return Integer.parseInt(InstrumentationRegistry.getArguments().getString(
                    itersProp, defaultIters
            ))
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
