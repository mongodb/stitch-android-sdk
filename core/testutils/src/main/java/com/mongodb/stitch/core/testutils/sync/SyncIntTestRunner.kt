package com.mongodb.stitch.core.testutils.sync

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Test running interface for Sync integration tests.
 *
 * Each @Test method in this interface reflects a test
 * that must be implemented to properly test Sync.
 *
 * The tests should be proxied to a [SyncIntTestProxy] proxy.
 * [SyncIntTestProxy] and [SyncIntTestRunner] should be in sync
 * on the these test methods.
 */
interface SyncIntTestRunner {
    /**
     * An integrated DataSynchronizer.
     */
    val dataSynchronizer: DataSynchronizer

    /**
     * A network monitor that allows us to control the network state
     * of the dataSynchronizer.
     */
    val testNetworkMonitor: BaseStitchIntTest.TestNetworkMonitor

    /**
     * A namespace to be used with these tests.
     */
    val namespace: MongoNamespace

    /**
     * A series of remote methods, independent of platform,
     * that have been normalized for testing.
     *
     * @return [ProxyRemoteMethods]
     */
    fun remoteMethods(): ProxyRemoteMethods

    /**
     * A series of sync methods, independent of platform,
     * that have been normalized for testing.
     *
     * @return [ProxySyncMethods]
     */
    fun syncMethods(): ProxySyncMethods

    /* TEST METHODS */
    @Before
    fun teardown()

    @After
    fun setup()

    @Test
    fun testSync()

    @Test
    fun testUpdateConflicts()

    @Test
    fun testUpdateRemoteWins()

    @Test
    fun testUpdateLocalWins()

    @Test
    fun testDeleteOneByIdNoConflict()

    @Test
    fun testDeleteOneByIdConflict()

    @Test
    fun testInsertThenUpdateThenSync()

    @Test
    fun testInsertThenSyncUpdateThenUpdate()

    @Test
    fun testInsertThenSyncThenRemoveThenInsertThenUpdate()

    @Test
    fun testRemoteDeletesLocalNoConflict()

    @Test
    fun testRemoteDeletesLocalConflict()

    @Test
    fun testRemoteInsertsLocalUpdates()

    @Test
    fun testRemoteInsertsWithVersionLocalUpdates()

    @Test
    fun testResolveConflictWithDelete()

    @Test
    fun testTurnDeviceOffAndOn()

    @Test
    fun testDesync()

    @Test
    fun testInsertInsertConflict()

    @Test
    fun testFrozenDocumentConfig()

    @Test
    fun testConfigure()

    @Test
    fun testSyncVersioningScheme()

    @Test
    fun testUnsupportedSpvFails()

    @Test
    fun testStaleFetchSingle()

    @Test
    fun testStaleFetchSingleDeleted()

    @Test
    fun testStaleFetchMultiple()
}