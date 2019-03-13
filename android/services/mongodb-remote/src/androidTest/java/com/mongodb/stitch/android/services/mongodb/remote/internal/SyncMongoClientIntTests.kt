package com.mongodb.stitch.android.services.mongodb.remote.internal

import android.support.test.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.mongodb.MongoNamespace
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.Sync
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.RuleResponse
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer
import com.mongodb.stitch.core.testutils.BaseStitchIntTest
import com.mongodb.stitch.core.testutils.sync.ProxyRemoteMethods
import com.mongodb.stitch.core.testutils.sync.ProxySyncMethods
import com.mongodb.stitch.core.testutils.sync.SyncIntTestProxy
import com.mongodb.stitch.core.testutils.sync.SyncIntTestRunner
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.local.internal.AndroidEmbeddedMongoClientFactory
import com.mongodb.stitch.core.auth.internal.CoreStitchUser
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import org.bson.BsonValue
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.Binary
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
            exceptionListener: ExceptionListener?
        ): Void? {
            return Tasks.await(
                sync.configure(conflictResolver, changeEventListener, exceptionListener)
            )
        }

        override fun syncOne(id: BsonValue): Void? {
            return Tasks.await(sync.syncOne(id))
        }

        override fun syncMany(vararg id: BsonValue) {
            Tasks.await(sync.syncMany(*id))
        }

        override fun count(filter: Bson): Long {
            return Tasks.await(sync.count(filter))
        }

        override fun aggregate(pipeline: List<Bson>): Iterable<Document?> {
            return Tasks.await(sync.aggregate(pipeline).into(mutableListOf<Document>()))
        }

        override fun insertOne(document: Document): SyncInsertOneResult {
            return Tasks.await(sync.insertOne(document))
        }

        override fun insertMany(documents: List<Document>): SyncInsertManyResult {
            return Tasks.await(sync.insertMany(documents))
        }

        override fun updateOne(filter: Bson, update: Bson, updateOptions: SyncUpdateOptions): SyncUpdateResult {
            return Tasks.await(sync.updateOne(filter, update, updateOptions))
        }

        override fun updateMany(filter: Bson, update: Bson, updateOptions: SyncUpdateOptions): SyncUpdateResult {
            return Tasks.await(sync.updateMany(filter, update, updateOptions))
        }

        override fun deleteOne(filter: Bson): SyncDeleteResult {
            return Tasks.await(sync.deleteOne(filter))
        }

        override fun deleteMany(filter: Bson): SyncDeleteResult {
            return Tasks.await(sync.deleteMany(filter))
        }

        override fun desyncOne(id: BsonValue): Void? {
            return Tasks.await(sync.desyncOne(id))
        }

        override fun getSyncedIds(): Set<BsonValue> {
            return Tasks.await(sync.syncedIds)
        }

        override fun find(filter: Bson): Iterable<Document?> {
            return Tasks.await(sync.find(filter).into(mutableListOf<Document>()))
        }

        override fun resumeSyncForDocument(documentId: BsonValue): Boolean {
            return Tasks.await(sync.resumeSyncForDocument(documentId))
        }

        override fun getPausedDocumentIds(): Set<BsonValue> {
            return Tasks.await(sync.pausedDocumentIds)
        }
    }

    lateinit var client: StitchAppClient

    private var dbName = ObjectId().toHexString()
    private var collName = ObjectId().toHexString()
    override var namespace = MongoNamespace(dbName, collName)
    override val dataSynchronizer: DataSynchronizer
        get() = (mongoClient as RemoteMongoClientImpl).dataSynchronizer
    override val testNetworkMonitor: BaseStitchIntTest.TestNetworkMonitor
        get() = BaseStitchAndroidIntTest.testNetworkMonitor

    private val mongodbUriProp = "test.stitch.mongodbURI"
    private lateinit var mongoClient: RemoteMongoClient

    private val testProxy = SyncIntTestProxy(this)
    override lateinit var mdbService: Apps.App.Services.Service
    override lateinit var mdbRule: RuleResponse

    override lateinit var userId1: String
    override lateinit var userId2: String
    override lateinit var userId3: String

    @Before
    override fun setup() {
        Assume.assumeTrue("no MongoDB URI in properties; skipping test", getMongoDbUri().isNotEmpty())
        super.setup()

        val app = createApp()
        val app2 = createApp()

        addProvider(app.second, ProviderConfigs.Anon)
        addProvider(app2.second, ProviderConfigs.Anon)
        mdbService = addService(
            app.second,
            "mongodb",
            "mongodb1",
            ServiceConfigs.Mongo(getMongoDbUri())).second
        val svc2 = addService(
            app2.second,
            "mongodb",
            "mongodb1",
            ServiceConfigs.Mongo(getMongoDbUri()))

        dbName = ObjectId().toHexString()
        collName = ObjectId().toHexString()
        namespace = MongoNamespace(dbName, collName)

        val rule = RuleCreator.MongoDb(
            database = dbName,
            collection = collName,
            roles = listOf(RuleCreator.MongoDb.Role(
                read = true, write = true
            )),
            schema = RuleCreator.MongoDb.Schema())
        mdbRule = addRule(mdbService, rule)
        addRule(svc2.second, rule)
        addProvider(app.second, config = ProviderConfigs.Userpass(
            emailConfirmationUrl = "http://emailConfirmURL.com",
            resetPasswordUrl = "http://resetPasswordURL.com",
            confirmEmailSubject = "email subject",
            resetPasswordSubject = "password subject")
        )
        this.client = getAppClient(app.first)
        userId3 = Tasks.await(client.auth.loginWithCredential(AnonymousCredential())).id
        userId2 = registerAndLoginWithUserPass(app.second, client, "test1@10gen.com", "password")
        userId1 = registerAndLoginWithUserPass(app.second, client, "test2@10gen.com", "password")
        mongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        BaseStitchAndroidIntTest.testNetworkMonitor.connectedState = true
    }

    @After
    override fun teardown() {
        if (::mongoClient.isInitialized) {
            (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
            AndroidEmbeddedMongoClientFactory.getInstance().close()
        }
        super.teardown()
    }

    override fun remoteMethods(): ProxyRemoteMethods {
        val db = mongoClient.getDatabase(dbName)
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

    override fun reloginUser2() {
        Tasks.await(
            client.auth.loginWithCredential(UserPasswordCredential("test1@10gen.com", "password")))
    }

    override fun listUsers(): List<CoreStitchUser> {
        return client.auth.listUsers()
    }

    override fun switchUser(userId: String) {
        client.auth.switchToUserWithId(userId)
    }

    override fun removeUser(userId: String) {
        Tasks.await(client.auth.removeUserWithId(userId))
    }

    override fun currentUserId(): String? {
        return client.auth.user?.id
    }

    @Test
    override fun testInitSyncPerf() {
//        testProxy.testInitSyncPerf()
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
    override fun testConfigure() {
        testProxy.testConfigure()
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

    @Test
    override fun testShouldUpdateUsingUpdateDescription() {
        testProxy.testShouldUpdateUsingUpdateDescription()
    }

    @Test
    override fun testResumeSyncForDocumentResumesSync() {
        testProxy.testResumeSyncForDocumentResumesSync()
    }

    @Test
    override fun testReadsBeforeAndAfterSync() {
        testProxy.testReadsBeforeAndAfterSync()
    }

    @Test
    override fun testInsertManyNoConflicts() {
        testProxy.testInsertManyNoConflicts()
    }

    @Test
    override fun testUpdateManyNoConflicts() {
        testProxy.testUpdateManyNoConflicts()
    }

    @Test
    override fun testDeleteManyNoConflicts() {
        testProxy.testDeleteManyNoConflicts()
    }

    @Test
    override fun testSyncVersionFieldNotEditable() {
        testProxy.testSyncVersionFieldNotEditable()
    }

    @Test
    override fun testConflictForEmptyVersionDocuments() {
        testProxy.testConflictForEmptyVersionDocuments()
    }

    @Test
    override fun testMultiUserSupport() {
        testProxy.testMultiUserSupport()
    }
    /**
     * Get the uri for where mongodb is running locally.
     */
    private fun getMongoDbUri(): String {
        return InstrumentationRegistry.getArguments().getString(mongodbUriProp, "mongodb://localhost:26000")
    }
}
