package com.mongodb.stitch.android.services.mongodb.performance

import android.support.test.runner.AndroidJUnit4
import org.bson.types.ObjectId

import org.junit.Test
import org.junit.runner.RunWith

val testHarness = SyncPerformanceIntTestsHarness()

@RunWith(AndroidJUnit4::class)
class SyncPerformanceTests {

    companion object {
        private val runId by lazy {
            ObjectId()
        }
    }

    // Tests for L2R-only scenarios
    @Test
    fun testL2ROnlyInitialSync() {
        SyncL2ROnlyPerformanceTestDefinitions.testInitialSync(testHarness, runId)
    }

    @Test
    fun testL2ROnlyDisconnectReconnect() {
        SyncL2ROnlyPerformanceTestDefinitions.testDisconnectReconnect(testHarness, runId)
    }

    @Test
    fun testL2ROnlySyncPass() {
        SyncL2ROnlyPerformanceTestDefinitions.testSyncPass(testHarness, runId)
    }

    // Tests for R2L-Only Scenarios
    @Test
    fun testR2LOnlyInitialSync() {
        SyncR2LOnlyPerformanceTestDefinitions.testInitialSync(testHarness, runId)
    }

    @Test
    fun testR2LOnlyDisconnectReconnect() {
        SyncR2LOnlyPerformanceTestDefinitions.testDisconnectReconnect(testHarness, runId)
    }

    @Test
    fun testR2LOnlySyncPass() {
        SyncR2LOnlyPerformanceTestDefinitions.testSyncPass(testHarness, runId)
    }
}
