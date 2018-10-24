package com.mongodb.stitch.android.services.mongodb.remote.internal

import android.support.test.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.Sync
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import com.mongodb.stitch.core.testutils.sync.ProxyRemoteMethods
import com.mongodb.stitch.core.testutils.sync.ProxySyncMethods
import com.mongodb.stitch.core.testutils.sync.SyncIntTestProxy
import com.mongodb.stitch.core.testutils.sync.SyncIntTestRunner
import org.bson.BsonValue
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class SyncMongoClientIntTests : BaseStitchAndroidIntTest(), SyncIntTestRunner {
    private class RemoteMethods(private val remoteMongoCollection: RemoteMongoCollection<Document>) : ProxyRemoteMethods {
        override fun insertOne(document: Document): RemoteInsertOneResult {
            return Tasks.await(remoteMongoCollection.insertOne(document))
        }
        override fun insertMany(documents: List<Document>): RemoteInsertManyResult {
            return Tasks.await(remoteMongoCollection.insertMany(documents))
        }

        override fun find(filter: Document): Iterable<Document?> {
            return Tasks.await(remoteMongoCollection.find(filter).into(mutableListOf<Document>()))
        }

        override fun updateOne(filter: Document, updateDocument: Document): RemoteUpdateResult {
            return Tasks.await(remoteMongoCollection.updateOne(filter, updateDocument))
        }

        override fun deleteOne(filter: Bson): RemoteDeleteResult {
            return Tasks.await(remoteMongoCollection.deleteOne(filter))
        }
    }

    private class SyncMethods(private val sync: Sync<Document>) : ProxySyncMethods {
        override fun configure(
            conflictResolver: ConflictHandler<Document?>,
            changeEventListener: ChangeEventListener<Document>?,
            errorListener: ErrorListener?
        ) {
            sync.configure(conflictResolver, changeEventListener, errorListener)
        }

        override fun syncOne(id: BsonValue) {
            sync.syncOne(id)
        }

        override fun insertOneAndSync(document: Document): RemoteInsertOneResult {
            return Tasks.await(sync.insertOneAndSync(document))
        }

        override fun findOneById(id: BsonValue): Document? {
            return Tasks.await(sync.findOneById(id))
        }

        override fun updateOneById(documentId: BsonValue, update: Bson): RemoteUpdateResult {
            return Tasks.await(sync.updateOneById(documentId, update))
        }

        override fun deleteOneById(documentId: BsonValue): RemoteDeleteResult {
            return Tasks.await(sync.deleteOneById(documentId))
        }

        override fun desyncOne(id: BsonValue) {
            sync.desyncOne(id)
        }

        override fun getSyncedIds(): Set<BsonValue> {
            return sync.syncedIds
        }

        override fun find(filter: Bson): Iterable<Document?> {
            return Tasks.await(sync.find(filter).into(mutableListOf<Document>()))
        }
    }

    private var dbName = ObjectId().toHexString()
    private var collName = ObjectId().toHexString()
    override var namespace = MongoNamespace(dbName, collName)
    override val dataSynchronizer: DataSynchronizer
        get() = (mongoClient as RemoteMongoClientImpl).dataSynchronizer
    override val testNetworkMonitor: BaseStitchIntTest.TestNetworkMonitor
        get() = BaseStitchAndroidIntTest.testNetworkMonitor

    private val mongodbUriProp = "test.stitch.mongodbURI"
    private lateinit var remoteMongoClient: RemoteMongoClient
    private lateinit var mongoClient: RemoteMongoClient

    private val testProxy = SyncIntTestProxy(this)

    @Before
    override fun setup() {
        Assume.assumeTrue("no MongoDB URI in properties; skipping test", getMongoDbUri().isNotEmpty())
        super.setup()

        val app = createApp()
        val app2 = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app2.second, ProviderConfigs.Anon)
        val svc = addService(
            app.second,
            "mongodb",
            "mongodb1",
            ServiceConfigs.Mongo(getMongoDbUri()))
        val svc2 = addService(
            app2.second,
            "mongodb",
            "mongodb1",
            ServiceConfigs.Mongo(getMongoDbUri()))

        val rule = Document()
        rule["read"] = Document()
        rule["write"] = Document()
        rule["other_fields"] = Document()

        dbName = ObjectId().toHexString()
        collName = ObjectId().toHexString()
        namespace = MongoNamespace(dbName, collName)

        addRule(svc.second, RuleCreator.MongoDb("$dbName.$collName", rule))
        addRule(svc2.second, RuleCreator.MongoDb("$dbName.$collName", rule))

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())
        mongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        remoteMongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    @After
    override fun teardown() {
        if (::mongoClient.isInitialized) {
            (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
        }
        super.teardown()
    }

    override fun remoteMethods(): ProxyRemoteMethods {
        val db = remoteMongoClient.getDatabase(dbName)
        Assert.assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        Assert.assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return RemoteMethods(coll)
    }

    override fun syncMethods(): ProxySyncMethods {
        val db = mongoClient.getDatabase(dbName)
        Assert.assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        Assert.assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return SyncMethods(coll.sync())
    }

    @Test
    override fun testSync() {
        testProxy.testSync()
    }

    @Test
    override fun testUpdateConflicts() {
        testProxy.testUpdateConflicts()
    }

    @Test
    override fun testUpdateRemoteWins() {
        testProxy.testUpdateRemoteWins()
    }

    @Test
    override fun testUpdateLocalWins() {
        testProxy.testUpdateLocalWins()
    }

    @Test
    override fun testDeleteOneByIdNoConflict() {
        testProxy.testDeleteOneByIdNoConflict()
    }

    @Test
    override fun testDeleteOneByIdConflict() {
        testProxy.testDeleteOneByIdConflict()
    }

    @Test
    override fun testInsertThenUpdateThenSync() {
        testProxy.testInsertThenUpdateThenSync()
    }

    @Test
    override fun testInsertThenSyncUpdateThenUpdate() {
        testProxy.testInsertThenSyncUpdateThenUpdate()
    }

    @Test
    override fun testInsertThenSyncThenRemoveThenInsertThenUpdate() {
        testProxy.testInsertThenSyncThenRemoveThenInsertThenUpdate()
    }

    @Test
    override fun testRemoteDeletesLocalNoConflict() {
        testProxy.testRemoteDeletesLocalNoConflict()
    }

    @Test
    override fun testRemoteDeletesLocalConflict() {
        testProxy.testRemoteDeletesLocalConflict()
    }

    @Test
    override fun testRemoteInsertsLocalUpdates() {
        testProxy.testRemoteInsertsLocalUpdates()
    }

    @Test
    override fun testRemoteInsertsWithVersionLocalUpdates() {
        testProxy.testRemoteInsertsWithVersionLocalUpdates()
    }

    @Test
    override fun testResolveConflictWithDelete() {
        testProxy.testResolveConflictWithDelete()
    }

    @Test
    override fun testTurnDeviceOffAndOn() {
        testProxy.testTurnDeviceOffAndOn()
    }

    @Test
    override fun testDesync() {
        testProxy.testDesync()
    }

    @Test
    override fun testInsertInsertConflict() {
        testProxy.testInsertInsertConflict()
    }

    @Test
    override fun testFrozenDocumentConfig() {
        testProxy.testFrozenDocumentConfig()
    }

    @Test
    override fun testConfigure() {
    }

    @Test
    override fun testSyncVersioningScheme() {
        testProxy.testSyncVersioningScheme()
    }

    @Test
    override fun testUnsupportedSpvFails() {
        testProxy.testUnsupportedSpvFails()
    }

    @Test
    override fun testStaleFetchSingle() {
        testProxy.testStaleFetchSingle()
    }

    @Test
    override fun testStaleFetchSingleDeleted() {
        testProxy.testStaleFetchSingleDeleted()
    }

    @Test
    override fun testStaleFetchMultiple() {
        testProxy.testStaleFetchMultiple()
    }

    /**
     * Get the uri for where mongodb is running locally.
     */
    private fun getMongoDbUri(): String {
        return InstrumentationRegistry.getArguments().getString(mongodbUriProp, "mongodb://localhost:26000")
    }
}
