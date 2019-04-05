package com.mongodb.stitch.android.services.mongodb.performance

import com.google.android.gms.tasks.Tasks
import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.local.internal.AndroidEmbeddedMongoClientFactory

import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.internal.RemoteMongoClientImpl
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential

import org.bson.Document
import org.bson.types.ObjectId

class LocalPerformanceTestContext(
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
        harness.setup()

        val app = harness.createApp()
        harness.addProvider(app.second, ProviderConfigs.Anon)

        val mdbService = harness.addService(
            app.second,
            "mongodb",
            "mongodb1",
            ServiceConfigs.Mongo(harness.getMongoDbUri())).second

        testDbName = ObjectId().toHexString()
        testCollName = ObjectId().toHexString()
        testNamespace = MongoNamespace(testDbName, testCollName)

        val rule = RuleCreator.MongoDb(
            database = testDbName,
            collection = testCollName,
            roles = listOf(RuleCreator.MongoDb.Role(
                read = true, write = true
            )),
            schema = RuleCreator.MongoDb.Schema())
        val mdbRule = harness.addRule(mdbService, rule)

        testClient = harness.getAppClient(app.first)
        testUserId = Tasks.await(testClient.auth.loginWithCredential(AnonymousCredential())).id
        testMongoClient = testClient.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        testColl = testMongoClient.getDatabase(testDbName).getCollection(testCollName)

        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    override fun teardown() {
        if (::testMongoClient.isInitialized) {
            (testMongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
            AndroidEmbeddedMongoClientFactory.getInstance().close()
        }

        harness.teardown()
    }
}
