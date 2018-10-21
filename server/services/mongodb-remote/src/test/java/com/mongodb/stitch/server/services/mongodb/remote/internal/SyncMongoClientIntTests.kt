package com.mongodb.stitch.server.services.mongodb.remote.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
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
import com.mongodb.stitch.core.testutils.sync.CoreRemoteMethods
import com.mongodb.stitch.core.testutils.sync.CoreSyncMethods
import com.mongodb.stitch.core.testutils.sync.SyncIntHarness
import com.mongodb.stitch.core.testutils.sync.SyncIntTestRunner
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.server.services.mongodb.remote.Sync
import org.bson.BsonValue
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class SyncMongoClientIntTests : BaseStitchServerIntTest(), SyncIntTestRunner {
    private class RemoteMethods(private val remoteMongoCollection: RemoteMongoCollection<Document>): CoreRemoteMethods {
        override fun insertOne(document: Document): RemoteInsertOneResult {
            return remoteMongoCollection.insertOne(document)
        }

        override fun insertMany(documents: List<Document>): RemoteInsertManyResult {
            return remoteMongoCollection.insertMany(documents)
        }

        override fun find(filter: Document): Iterable<Document?> {
            return remoteMongoCollection.find(filter)
        }

        override fun updateOne(filter: Document, updateDocument: Document): RemoteUpdateResult {
            return remoteMongoCollection.updateOne(filter, updateDocument)
        }

        override fun deleteOne(filter: Bson): RemoteDeleteResult {
            return remoteMongoCollection.deleteOne(filter)
        }
    }

    private class SyncMethods(private val sync: Sync<Document>): CoreSyncMethods {
        override fun configure(conflictResolver: ConflictHandler<Document?>, changeEventListener: ChangeEventListener<Document>?, errorListener: ErrorListener?) {
            sync.configure(conflictResolver, changeEventListener, errorListener)
        }

        override fun syncOne(id: BsonValue) {
            sync.syncOne(id)
        }

        override fun insertOneAndSync(document: Document): RemoteInsertOneResult {
            return sync.insertOneAndSync(document)
        }

        override fun findOneById(id: BsonValue): Document? {
            return sync.findOneById(id)
        }

        override fun updateOneById(documentId: BsonValue, update: Bson): RemoteUpdateResult {
            return sync.updateOneById(documentId, update)
        }

        override fun deleteOneById(documentId: BsonValue): RemoteDeleteResult {
            return sync.deleteOneById(documentId)
        }

        override fun getSyncedIds(): Set<BsonValue> {
            return sync.syncedIds
        }

        override fun desyncOne(id: BsonValue) {
            sync.desyncOne(id)
        }

        override fun find(filter: Bson): Iterable<Document?> {
            return sync.find(filter)
        }
    }

    private val mongodbUriProp = "test.stitch.mongodbURI"
    private lateinit var remoteMongoClient: RemoteMongoClient
    private lateinit var mongoClient: RemoteMongoClient
    private var dbName = ObjectId().toHexString()
    private var collName = ObjectId().toHexString()
    override var namespace = MongoNamespace(dbName, collName)
    override val dataSynchronizer: DataSynchronizer
        get() = (mongoClient as RemoteMongoClientImpl).dataSynchronizer
    override val testNetworkMonitor: TestNetworkMonitor
        get() = BaseStitchServerIntTest.testNetworkMonitor

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
        BaseStitchServerIntTest.testNetworkMonitor.connectedState = true
    }

    @After
    override fun teardown() {
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
        super.teardown()
    }

    override fun remoteMethods(): CoreRemoteMethods {
        val db = remoteMongoClient.getDatabase(dbName)
        Assert.assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        Assert.assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return RemoteMethods(coll)
    }

    override fun syncMethods(): CoreSyncMethods {
        val db = mongoClient.getDatabase(dbName)
        Assert.assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        Assert.assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return SyncMethods(coll.sync())
    }

    private val harness = SyncIntHarness(this)

    @Test
    override fun testSync() {
        harness.testSync()
    }

    @Test
    override fun testUpdateConflicts() {
        harness.testUpdateConflicts()
    }

    @Test
    override fun testUpdateRemoteWins() {
        harness.testUpdateRemoteWins()
    }

    @Test
    override fun testUpdateLocalWins() {
        harness.testUpdateLocalWins()
    }

    @Test
    override fun testDeleteOneByIdNoConflict() {
        harness.testDeleteOneByIdNoConflict()
    }

    @Test
    override fun testDeleteOneByIdConflict() {
        harness.testDeleteOneByIdConflict()
    }

    @Test
    override fun testInsertThenUpdateThenSync() {
        harness.testInsertThenUpdateThenSync()
    }

    @Test
    override fun testInsertThenSyncUpdateThenUpdate() {
        harness.testInsertThenSyncUpdateThenUpdate()
    }

    @Test
    override fun testInsertThenSyncThenRemoveThenInsertThenUpdate() {
        harness.testInsertThenSyncThenRemoveThenInsertThenUpdate()
    }

    @Test
    override fun testRemoteDeletesLocalNoConflict() {
        harness.testRemoteDeletesLocalNoConflict()
    }

    @Test
    override fun testRemoteDeletesLocalConflict() {
        harness.testRemoteDeletesLocalConflict()
    }

    @Test
    override fun testRemoteInsertsLocalUpdates() {
        harness.testRemoteInsertsLocalUpdates()
    }

    @Test
    override fun testRemoteInsertsWithVersionLocalUpdates() {
        harness.testRemoteInsertsWithVersionLocalUpdates()
    }

    @Test
    override fun testResolveConflictWithDelete() {
        harness.testResolveConflictWithDelete()
    }

    @Test
    override fun testTurnDeviceOffAndOn() {
        harness.testTurnDeviceOffAndOn()
    }

    @Test
    override fun testDesync() {
        harness.testDesync()
    }

    @Test
    override fun testInsertInsertConflict() {
        harness.testInsertInsertConflict()
    }

    @Test
    override fun testFrozenDocumentConfig() {
        harness.testFrozenDocumentConfig()
    }

    @Test
    override fun testConfigure() {
        harness.testConfigure()
    }

    @Test
    override fun testSyncVersioningScheme() {
        harness.testSyncVersioningScheme()
    }

    @Test
    override fun testUnsupportedSpvFails() {
        harness.testUnsupportedSpvFails()
    }

    @Test
    override fun testStaleFetchSingle() {
        harness.testStaleFetchSingle()
    }

    @Test
    override fun testStaleFetchSingleDeleted() {
        harness.testStaleFetchSingleDeleted()
    }

    @Test
    override fun testStaleFetchMultiple() {
        harness.testStaleFetchMultiple()
    }

    /**
     * Get the uri for where mongodb is running locally.
     */
    private fun getMongoDbUri(): String {
        return System.getProperty(mongodbUriProp, "mongodb://localhost:26000")
    }
}
