package com.mongodb.stitch.android.services.mongodb.remote

import android.support.test.runner.AndroidJUnit4
import android.util.Log

import com.google.android.gms.tasks.Tasks

import org.bson.types.Binary
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.After

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncPerformanceTests {
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

    @Before
    fun setup() {
        this.testHarness.setup()
    }

    @After
    fun teardown() {
        this.testHarness.teardown()
    }

    @Test
    fun testInitialSync() {
        val testParams = TestParams(
            runId = runId,
            testName = "testInitialSync",
            dataProbeGranularityMs = 400L,
            docSizes = intArrayOf(50),
            numDocs = intArrayOf(10),
            numIters = 3,
            numOutliersEachSide = 0,
            outputToStitch = outputToStitch,
            stitchHostName = "https://stitch.mongodb.com"
        )

        testHarness.runPerformanceTestWithParams(testParams, { ctx, docSize, numDocs ->
            val documents = getDocuments(numDocs, docSize)
            assertEquals(0L, Tasks.await(ctx.testColl.count()))
            Tasks.await(ctx.testColl.insertMany(documents))
            assertEquals(numDocs.toLong(), Tasks.await(ctx.testColl.count()))
        })
    }

    @Test
    fun testDisconnectReconnect() {
        val testParams = TestParams(
            runId = runId,
            testName = "testDisconnectReconnect",
            dataProbeGranularityMs = 400L,
            docSizes = intArrayOf(30, 60),
            numDocs = intArrayOf(10),
            numIters = 3,
            numOutliersEachSide = 0,
            outputToStitch = outputToStitch
        )

        testHarness.runPerformanceTestWithParams(testParams, { ctx, docSize, numDocs ->
            val documents = getDocuments(numDocs, docSize)
            assertEquals(Tasks.await(ctx.testColl.count()), 0L)
            Tasks.await(ctx.testColl.insertMany(documents))
            assertEquals(Tasks.await(ctx.testColl.count()), numDocs.toLong())
        })
    }
}
