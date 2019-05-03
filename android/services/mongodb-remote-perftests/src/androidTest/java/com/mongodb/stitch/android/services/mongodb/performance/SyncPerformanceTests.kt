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
    /*
     * Before: Perform local insert of numDoc documents
     * Test: Configure sync to sync on the inserted docs and perform a sync pass
     * After: Ensure that the initial sync worked as expected
     */
    @Test
    fun testL2ROnlyInitialSync() {
        SyncL2ROnlyPerformanceTestDefinitions.testInitialSync(testHarness, runId)
    }

    /*
     * Before: Perform local insert of numDoc documents, configure sync(),
     *              perform sync pass, disconnect networkMonitor
     * Test: Reconnect the network monitor and perform sync pass
     * After: Ensure that the sync pass worked as expected
     */
    @Test
    fun testL2ROnlyDisconnectReconnect() {
        SyncL2ROnlyPerformanceTestDefinitions.testDisconnectReconnect(testHarness, runId)
    }

    /*
     * Before: Perform local insert of numDoc documents, configure sync(), perform sync pass
     *              perform local update for numChangeEvent documents
     * Test: Perform sync pass
     * After: Ensure that the sync pass worked properly
     */
    @Test
    fun testL2ROnlySyncPass() {
        SyncL2ROnlyPerformanceTestDefinitions.testSyncPass(testHarness, runId)
    }

    // Tests for R2L-Only Scenarios

    /*
     * Before: Perform remote insert of numDoc documents
     * Test: Configure sync to sync on the inserted docs and perform a sync pass
     * After: Ensure that the initial sync worked as expected
     */
    @Test
    fun testR2LOnlyInitialSync() {
        SyncR2LOnlyPerformanceTestDefinitions.testInitialSync(testHarness, runId)
    }

    /*
     * Before: Perform remote insert of numDoc documents, configure sync(),
     *              perform sync pass, disconnect networkMonitor
     * Test: Reconnect the network monitor and perform sync pass
     * After: Ensure that the sync pass worked as expected
     */
    @Test
    fun testR2LOnlyDisconnectReconnect() {
        SyncR2LOnlyPerformanceTestDefinitions.testDisconnectReconnect(testHarness, runId)
    }

    /*
     * Before: Perform remote insert of numDoc documents
     *         Configure sync(), perform sync pass
     *         Perform remote update for numChangeEvent documents
     *         Perform local update on numConflict documents
     * Test: Perform sync pass
     * After: Ensure that the sync pass worked properly
     */
    @Test
    fun testR2LOnlySyncPass() {
        SyncR2LOnlyPerformanceTestDefinitions.testSyncPass(testHarness, runId)
    }

    // Tests for Mixed R2L-L2R Scenarios

    /*
     * Before: Perform remote insert of numDoc / 2 documents
     *         Perform a local insert of numDoc / 2 documents
     *         Ensure there are numConflict conflicts
     * Test: Configure sync to sync on the inserted docs and perform a sync pass
     * After: Ensure that the initial sync worked as expected
     */
    @Test
    fun testMixedInitialSync() {
        SyncMixedPerformanceTestDefinitions.testInitialSync(testHarness, runId)
    }

    /*
     * Before: Perform remote insert of numDoc / 2 documents
     *         Perform a local insert of numDoc / 2 documents
     *         Configure sync(), perform sync pass, disconnect networkMonitor
     *         Ensure sync worked properly
     * Test: Reconnect the network monitor and perform sync pass
     * After: Ensure that the sync pass worked as expected
     */
    @Test
    fun testMixedDisconnectReconnect() {
        SyncMixedPerformanceTestDefinitions.testDisconnectReconnect(testHarness, runId)
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
    @Test
    fun testMixedOnlySyncPass() {
        SyncMixedPerformanceTestDefinitions.testSyncPass(testHarness, runId)
    }
}
