package com.mongodb.stitch.android.services.mongodb.performance

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.local.LocalMongoDbService

import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.internal.RemoteMongoClientImpl
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyCredential
import org.bson.Document

class ProductionPerformanceContext(
    private val harness: SyncPerformanceIntTestsHarness,
    private val testParams: TestParams,
    private val transport: OkHttpInstrumentedTransport
) : SyncPerformanceTestContext(harness, testParams, transport) {
    // Public variables
    override lateinit var testClient: StitchAppClient
    override lateinit var testMongoClient: RemoteMongoClient
    override lateinit var testColl: RemoteMongoCollection<Document>
    override lateinit var testUserId: String

    companion object {
        val TAG = SyncPerformanceTestContext::class.java.simpleName
    }

    override fun setup() {
        testDbName = harness.stitchTestDbName
        testCollName = harness.stitchTestCollName
        testColl = harness.outputClient
            .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
            .getDatabase(testDbName)
            .getCollection(testCollName)
        testClient = harness.outputClient
        testMongoClient = harness.outputMongoClient

        if (!harness.outputClient.auth.isLoggedIn) {
            testUserId = Tasks.await(
                harness.outputClient.auth.loginWithCredential(
                    UserApiKeyCredential(harness.getStitchAPIKey()))).id
        } else {
            testUserId = harness.outputClient.auth.user!!.id
        }

        clearLocalDB()
        deleteTestCollection()

        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    override fun teardown() {
        clearLocalDB()
        deleteTestCollection()
    }

    private fun deleteTestCollection() {
        // This is a workaround for the fact that deleteMany will time out when trying to delete
        // over 2500 documents at a time. Ideally we'd drop the collection, but it's currently not
        // possible directly via Stitch

        // we're testing up to 25K docs, and we can delete 2500-3000 per pass,
        val maxRetries = 15

        for (i in 0..maxRetries) {
            try {
                Tasks.await(testClient.callFunction(
                    "deleteAllAsSystemUser", arrayListOf<String>())
                )
                break
            } catch (e: Exception) {
                if (i < maxRetries) {
                    Log.i(TAG, "Error deleting all documents: ${e.localizedMessage}, retrying")
                } else {
                    Log.e(TAG, "Error deleting all documents: ${e.localizedMessage}, no more retries")
                }
            }
        }
    }

    private fun clearLocalDB() {
        val syncedIds = Tasks.await(testColl.sync().syncedIds)
        Tasks.await(testColl.sync().desyncMany(*syncedIds.toTypedArray()))
        LocalMongoDbService.clearallLocalDBs()
    }
}
