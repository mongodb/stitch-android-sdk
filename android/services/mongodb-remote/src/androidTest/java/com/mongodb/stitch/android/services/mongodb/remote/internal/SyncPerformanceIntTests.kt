package com.mongodb.stitch.android.services.mongodb.remote.internal

import android.util.Log

import com.google.android.gms.tasks.Tasks

import org.bson.types.Binary
import org.bson.Document
import org.bson.types.ObjectId

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class SyncPerformanceIntTests : SyncPerformanceIntTestsHarness() {
    //private lateinit var runId: ObjectId
    private var testHarness = SyncPerformanceIntTestsHarness()

    private fun getDocuments(
        numberOfDocs: Int,
        sizeOfDocsInBytes: Int
    ): List<Document>? {

        val array: List<Byte> = (0 until sizeOfDocsInBytes).map { 0.toByte() }
        val docs: List<Document> = (0 until numberOfDocs).map {
            Document(mapOf(
                "_id" to ObjectId(),
                "bin" to Binary(array.toByteArray())
            ))
        }
        return docs
    }

    companion object {

        private lateinit var runId: ObjectId

        @BeforeClass
        @JvmStatic
        fun initialize() {
            runId = ObjectId()
        }
    }

    @Before
    override fun setup() {
        super.setup()
    }

    @After
    override fun teardown() {
        super.teardown()
    }

    @Test
    fun testInitialSync() {
        Log.d("perfTests", "testInitialSync")
        val testParams = TestParams(
            runId = runId ,
            testName = "testInitialSync",
            dataProbeGranularityMs = 400L,
            docSizes = intArrayOf(50),
            numDocs = intArrayOf(100),
            numIters = 5,
            numOutliersEachSide = 1,
            outputToStitch = true
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
            outputToStitch = true
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
