package com.mongodb.stitch.android.services.mongodb.remote

import android.util.Log

import com.google.android.gms.tasks.Tasks

import org.bson.types.Binary
import org.bson.Document
import org.bson.types.ObjectId

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SyncPerformanceIntTests {
    private val outputToStitch = false

    companion object {
        private val runId by lazy {
            ObjectId()
        }

    }

    private var testHarness = SyncPerformanceIntTestsHarness()

    private fun getDocuments(
        numberOfDocs: Int,
        sizeOfDocsInBytes: Int
    ): List<Document>? {
        val array: List<Byte> = (0 until sizeOfDocsInBytes).map { 0.toByte() }
        return (0 until numberOfDocs).map {
            Document(mapOf(
                "_id" to ObjectId(),
                "bin" to Binary(array.toByteArray())
            ))
        }
    }

    @Test
    fun testInitialSync() {
        Log.d("perfTests", "testInitialSync")
        val testParams = TestParams(
            runId = runId,
            testName = "testInitialSync",
            dataProbeGranularityMs = 400L,
            docSizes = intArrayOf(50),
            numDocs = intArrayOf(100),
            numIters = 3,
            numOutliersEachSide = 0,
            outputToStitch = outputToStitch,
            stitchHostName = "https://stitch.mongodb.com"
        )

        testHarness.runPerformanceTestWithParams(testParams) { docSize, numDocs ->
            val documents = getDocuments(numDocs, docSize)
            assertEquals(Tasks.await(testHarness.testColl.count()), 0L)
            Tasks.await(testHarness.testColl.insertMany(documents))
            assertEquals(Tasks.await(testHarness.testColl.count()), numDocs.toLong())
        }
        Log.d("perfTests", "testInitialSync")
    }

    @Test
    fun testDisconnectReconnect() {
        Log.d("perfTests", "testDisconnectReconnect")
        val testParams = TestParams(
            runId = runId,
            testName = "testDisconnectReconnect",
            dataProbeGranularityMs = 400L,
            docSizes = intArrayOf(30, 60),
            numDocs = intArrayOf(100, 150),
            numIters = 3,
            numOutliersEachSide = 0,
            outputToStitch = outputToStitch
        )

        testHarness.runPerformanceTestWithParams(testParams) { docSize, numDocs ->
            val documents = getDocuments(numDocs, docSize)
            assertEquals(Tasks.await(testHarness.testColl.count()), 0L)
            Tasks.await(testHarness.testColl.insertMany(documents))
            assertEquals(Tasks.await(testHarness.testColl.count()), numDocs.toLong())
        }
        Log.d("perfTests", "testDisconnectReconnect")
    }
}
