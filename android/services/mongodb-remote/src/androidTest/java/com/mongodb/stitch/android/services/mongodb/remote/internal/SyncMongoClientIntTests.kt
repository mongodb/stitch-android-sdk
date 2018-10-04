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
import com.mongodb.stitch.core.internal.common.OperationResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import org.bson.BsonDocument

import org.bson.BsonObjectId
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class SyncMongoClientIntTests : BaseStitchAndroidIntTest() {

    private val mongodbUriProp = "test.stitch.mongodbURI"

    private var remoteMongoClientOpt: RemoteMongoClient? = null
    private val remoteMongoClient: RemoteMongoClient
        get() = remoteMongoClientOpt!!
    private var mongoClientOpt: RemoteMongoClient? = null
    private val mongoClient: RemoteMongoClient
        get() = mongoClientOpt!!
    private var dbName = ObjectId().toHexString()
    private var collName = ObjectId().toHexString()

    companion object {
        private val testNetworkMonitor = TestNetworkMonitor()
    }

    private fun getMongoDbUri(): String {
        return InstrumentationRegistry.getArguments().getString(mongodbUriProp, "mongodb://localhost:26000")
    }

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
        addRule(svc.second, RuleCreator.MongoDb("$dbName.$collName", rule))
        addRule(svc2.second, RuleCreator.MongoDb("$dbName.$collName", rule))

        val client = getAppClient(app.first)
        Tasks.await(client.auth.loginWithCredential(AnonymousCredential()))
        mongoClientOpt = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        remoteMongoClientOpt = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        goOnline()
    }

    @After
    override fun teardown() {
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
        super.teardown()
    }

    class TestNetworkMonitor : NetworkMonitor {
        private var _connectedState = false
        var connectedState: Boolean
            set(value) {
                _connectedState = value
                listeners.forEach { it.onNetworkStateChanged() }
            }
            get() = _connectedState

        var listeners = mutableListOf<NetworkMonitor.StateListener>()

        override fun isConnected(): Boolean {
            return connectedState
        }

        override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {
            listeners.add(listener)
        }
    }

    private fun getTestSync(): Sync<Document> {
        val db = mongoClient.getDatabase(dbName)
        assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return coll.sync()
    }

    private fun getTestCollRemote(): RemoteMongoCollection<Document> {
        val db = remoteMongoClient.getDatabase(dbName)
        assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return coll
    }

    @Test
    fun testSync() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val doc1 = Document("hello", "world")
            val doc2 = Document("hello", "friend")
            doc2["proj"] = "field"
            remoteColl.insertMany(listOf(doc1, doc2))

            // get the document
            val doc = Tasks.await(remoteColl.find(doc1).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            // start watching it and always set the value to hello world in a conflict
            coll.configure({ id: BsonValue, localEvent: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                if (id.equals(doc1Id)) {
                    val merged = localEvent.fullDocument.getInteger("foo") +
                            remoteEvent.fullDocument.getInteger("foo")
                    val newDocument = Document(HashMap<String, Any>(remoteEvent.fullDocument))
                    newDocument["foo"] = merged
                    newDocument
                } else {
                    Document("hello", "world")
                }
            }, null, null)
            coll.syncOne(doc1Id)
            syncPass()

            // 1. updating a document remotely should not be reflected until coming back online.
            goOffline()
            val doc1Update = Document("\$inc", Document("foo", 1))
            val result = Tasks.await(remoteColl.updateOne(
                    doc1Filter,
                    doc1Update))
            assertEquals(1, result.matchedCount)
            syncPass()
            assertEquals(doc, coll.findOneById(doc1Id))
            goOnline()
            syncPass()
            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // 2. insertOneAndSync should work offline and then sync the document when online.
            goOffline()
            val doc3 = Document("so", "syncy")
            val insResult = coll.insertOneAndSync(doc3)
            assertEquals(doc3, withoutVersionId(Tasks.await(coll.findOneById(insResult.insertedId))!!))
            syncPass()
            assertNull(remoteColl.find(Document("_id", doc3["_id"])).first())
            goOnline()
            syncPass()
            assertEquals(doc3, withoutVersionId(Tasks.await(remoteColl.find(Document("_id", doc3["_id"])).first())!!))

            // 3. updating a document locally that has been updated remotely should invoke the conflict
            // resolver.
            val result2 = Tasks.await(remoteColl.updateOne(
                    doc1Filter,
                    withNewVersionIdSet(doc1Update)))
            assertEquals(1, result2.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            val result3 = Tasks.await(coll.updateOneById(
                    doc1Id,
                    doc1Update))
            assertEquals(1, result3.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id)!!)))
            // first pass will invoke the conflict handler and update locally but not remotely yet
            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            expectedDocument["foo"] = 4
            expectedDocument.remove("fooOps")
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id)!!)))
            // second pass will update with the ack'd version id
            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id)!!)))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
        }
    }

    @Test
    fun testUpdateConflicts() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, localEvent: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                val merged = Document(localEvent.fullDocument)
                remoteEvent.fullDocument.forEach {
                    if (localEvent.fullDocument.containsKey(it.key)) {
                        return@forEach
                    }
                    merged[it.key] = it.value
                }
                merged
            }, null, null)
            coll.syncOne(doc1Id)
            syncPass()

            // Update remote
            val remoteUpdate = withNewVersionIdSet(Document("\$set", Document("remote", "update")))
            var result = Tasks.await(remoteColl.updateOne(doc1Filter, remoteUpdate))
            assertEquals(1, result.matchedCount)
            val expectedRemoteDocument = Document(doc)
            expectedRemoteDocument["remote"] = "update"
            assertEquals(expectedRemoteDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))

            // Update local
            val localUpdate = Document("\$set", Document("local", "updateWow"))
            result = Tasks.await(coll.updateOneById(doc1Id, localUpdate))
            assertEquals(1, result.matchedCount)
            val expectedLocalDocument = Document(doc)
            expectedLocalDocument["local"] = "updateWow"
            assertEquals(expectedLocalDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id)!!)))

            // first pass will invoke the conflict handler and update locally but not remotely yet
            syncPass()
            assertEquals(expectedRemoteDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            expectedLocalDocument["remote"] = "update"
            assertEquals(expectedLocalDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id)!!)))

            // second pass will update with the ack'd version id
            syncPass()
            assertEquals(expectedLocalDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id)!!)))
            assertEquals(expectedLocalDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
        }
    }

    @Test
    fun testUpdateRemoteWins() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            docToInsert["foo"] = 1
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(DefaultSyncConflictResolvers.remoteWins(), null, null)
            coll.syncOne(doc1Id)
            syncPass()

            val expectedDocument = Document(doc)
            var result = Tasks.await(remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 2)))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            result = Tasks.await(coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            syncPass()
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
        }
    }

    @Test
    fun testUpdateLocalWins() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            docToInsert["foo"] = 1
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            coll.syncOne(doc1Id)
            syncPass()

            val expectedDocument = Document(doc)
            var result = Tasks.await(remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 2)))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            result = Tasks.await(coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            syncPass()
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
        }
    }

    @Test
    fun testDeleteOneByIdNoConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(failingConflictHandler, null, null)
            coll.syncOne(doc1Id)
            syncPass()

            goOffline()
            val result = Tasks.await(coll.deleteOneById(doc1Id))
            assertEquals(1, result.deletedCount)

            val expectedDocument = withoutVersionId(Document(doc))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            syncPass()
            assertNull(remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testDeleteOneByIdConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("well", "shoot")
            }, null, null)
            coll.syncOne(doc1Id)
            syncPass()

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, Tasks.await(remoteColl.updateOne(
                    doc1Filter,
                    withNewVersionIdSet(doc1Update))).matchedCount)

            goOffline()
            val result = Tasks.await(coll.deleteOneById(doc1Id))
            assertEquals(1, result.deletedCount)

            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            expectedDocument.remove("hello")
            expectedDocument.remove("foo")
            expectedDocument["well"] = "shoot"
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
        }
    }

    @Test
    fun testInsertThenUpdateThenSync() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")

            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)

            val doc = Tasks.await(coll.findOneById(insertResult.insertedId))!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, Tasks.await(coll.updateOneById(doc1Id, doc1Update)).matchedCount)

            val expectedDocument = withoutVersionId(Document(doc))
            expectedDocument["foo"] = 1
            assertNull(remoteColl.find(doc1Filter).first())
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            goOnline()
            syncPass()

            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
        }
    }

    @Test
    fun testInsertThenSyncUpdateThenUpdate() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")

            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)

            val doc = Tasks.await(coll.findOneById(insertResult.insertedId))!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            goOnline()
            syncPass()
            val expectedDocument = withoutVersionId(Document(doc))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, Tasks.await(coll.updateOneById(doc1Id, doc1Update)).matchedCount)

            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
        }
    }

    @Test
    fun testInsertThenSyncThenRemoveThenInsertThenUpdate() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)
            syncPass()

            val doc = Tasks.await(coll.findOneById(insertResult.insertedId))!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)
            val expectedDocument = withoutVersionId(Document(doc))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            assertEquals(1, Tasks.await(coll.deleteOneById(doc1Id)).deletedCount)
            coll.insertOneAndSync(doc)
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, Tasks.await(coll.updateOneById(doc1Id, doc1Update)).matchedCount)

            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
        }
    }

    @Test
    fun testRemoteDeletesLocalNoConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(failingConflictHandler, null, null)
            coll.syncOne(doc1Id)

            syncPass()

            assertEquals(coll.syncedIds.size, 1)

            val isLocked = watchFor(ChangeEvent.OperationType.DELETE)

            remoteColl.deleteOne(doc1Filter)

            while (isLocked.get()) {
            }

            syncPass()

            assertNull(remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))

            // This should not re-sync the document
            syncPass()
            remoteColl.insertOne(doc)
            syncPass()

            assertEquals(doc, Tasks.await(remoteColl.find(doc1Filter).first()))
            assertNull(Tasks.await(coll.findOneById(doc1Id)))
        }
    }

    @Test
    fun testRemoteDeletesLocalConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("hello", "world")
            }, null, null)
            coll.syncOne(doc1Id)
            syncPass()
            assertEquals(doc, coll.findOneById(doc1Id))
            Assert.assertNotNull(coll.findOneById(doc1Id))

            goOffline()
            remoteColl.deleteOne(doc1Filter)
            assertEquals(1, Tasks.await(coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))).matchedCount)

            goOnline()
            syncPass()
            assertNull(remoteColl.find(doc1Filter).first())
            Assert.assertNotNull(Tasks.await(coll.findOneById(doc1Id)))

            syncPass()
            Assert.assertNotNull(Tasks.await(remoteColl.find(doc1Filter).first()))
            Assert.assertNotNull(Tasks.await(coll.findOneById(doc1Id)))
        }
    }

    @Test
    fun testRemoteInsertsLocalUpdates() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("hello", "again")
            }, null, null)
            coll.syncOne(doc1Id)

            syncPass()

            assertEquals(doc, coll.findOneById(doc1Id))
            Assert.assertNotNull(coll.findOneById(doc1Id))

            remoteColl.deleteOne(doc1Filter)
            remoteColl.insertOne(withNewVersionId(doc))
            assertEquals(1, Tasks.await(coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))).matchedCount)

            syncPass()

            assertEquals(doc, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            val expectedDocument = Document("_id", doc1Id.value)
            expectedDocument["hello"] = "again"
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
        }
    }

    @Test
    fun testRemoteInsertsWithVersionLocalUpdates() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(withNewVersionId(docToInsert))

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(failingConflictHandler, null, null)
            coll.syncOne(doc1Id)
            syncPass()
            assertEquals(doc, coll.findOneById(doc1Id))

            assertEquals(1, Tasks.await(coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))).matchedCount)

            syncPass()
            val expectedDocument = Document(withoutVersionId(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
        }
    }

    @Test
    fun testResolveConflictWithDelete() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(withNewVersionId(docToInsert))

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                null
            }, null, null)
            coll.syncOne(doc1Id)
            syncPass()
            assertEquals(doc, coll.findOneById(doc1Id))
            Assert.assertNotNull(coll.findOneById(doc1Id))

            val isLocked = watchFor(ChangeEvent.OperationType.UPDATE)

            assertEquals(1, Tasks.await(remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 1))))).matchedCount)
            assertEquals(1, Tasks.await(coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))).matchedCount)

            while (isLocked.get()) {
            }
            syncPass()
            val expectedDocument = Document(withoutVersionId(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))

            goOffline()
            assertNull(Tasks.await(coll.findOneById(doc1Id)))

            goOnline()
            syncPass()
            assertNull(Tasks.await(remoteColl.find(doc1Filter).first()))
            assertNull(Tasks.await(coll.findOneById(doc1Id)))
        }
    }

    @Test
    fun testTurnDeviceOffAndOn() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            docToInsert["foo"] = 1
            remoteColl.insertOne(docToInsert)

            val doc = Tasks.await(remoteColl.find(docToInsert).first())!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            powerCycleDevice()

            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            coll.syncOne(doc1Id)

            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            syncPass()

            val expectedDocument = Document(doc)
            var result = Tasks.await(remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 2)))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            result = Tasks.await(coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))

            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            syncPass() // does nothing with no conflict handler

            assertEquals(1, coll.syncedIds.size)
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            syncPass() // resolves the conflict

            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
        }
    }

    @Test
    fun testDesync() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val docToInsert = Document("hello", "world")
            coll.configure(failingConflictHandler, null, null)
            val doc1Id = coll.insertOneAndSync(docToInsert).insertedId

            assertEquals(docToInsert, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            coll.desyncOne(doc1Id)
            syncPass()
            assertNull(Tasks.await(coll.findOneById(doc1Id)))
        }
    }

    @Test
    fun testInsertInsertConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()
            val remoteColl = getTestCollRemote()

            val docToInsert = Document("_id", "hello")

            remoteColl.insertOne(docToInsert)
            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("friend", "welcome")
            }, null, null)
            val doc1Id = coll.insertOneAndSync(docToInsert).insertedId

            val doc1Filter = Document("_id", doc1Id)

            syncPass()
            val expectedDocument = Document(docToInsert)
            expectedDocument["friend"] = "welcome"
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            assertEquals(docToInsert, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))

            syncPass()
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(coll.findOneById(doc1Id))!!))
            assertEquals(expectedDocument, withoutVersionId(Tasks.await(remoteColl.find(doc1Filter).first())!!))
        }
    }

    private fun syncPass() {
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.doSyncPass()
    }

    private fun watchFor(operation: ChangeEvent.OperationType, watcher: (OperationResult<ChangeEvent<BsonDocument>, Any>) -> Unit = {}): AtomicBoolean {
        val isLocked = AtomicBoolean(true)
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.queueDisposableWatcher(MongoNamespace(dbName, collName)) {
            if (it.isSuccessful && it.geResult() != null && it.geResult().operationType == operation) {
                watcher(it)
                isLocked.set(false)
            }
        }
        return isLocked
    }

    private fun powerCycleDevice() {
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.reloadConfig()
    }

    private fun goOffline() {
        testNetworkMonitor.connectedState = false
    }

    private fun goOnline() {
        testNetworkMonitor.connectedState = true
    }

    private fun withoutIds(documents: Collection<Document>): Collection<Document> {
        val list = ArrayList<Document>(documents.size)
        documents.forEach { list.add(withoutId(it)) }
        return list
    }

    private fun withoutId(document: Document): Document {
        val newDoc = Document(document)
        newDoc.remove("_id")
        return newDoc
    }

    private fun withoutVersionId(document: Document): Document {
        val newDoc = Document(document)
        newDoc.remove("__stitch_sync_version")
        return newDoc
    }

    private fun withNewVersionId(document: Document): Document {
        val newDocument = Document(HashMap(document))
        newDocument["__stitch_sync_version"] = UUID.randomUUID().toString()
        return newDocument
    }

    private fun withNewVersionIdSet(document: Document): Document {
        return appendDocumentToKey(
                "\$set",
                document,
                Document("__stitch_sync_version", UUID.randomUUID().toString()))
    }

    private fun appendDocumentToKey(key: String, on: Document, toAppend: Document): Document {
        val newDocument = Document(HashMap(on))
        var found = false
        newDocument.forEach {
            if (it.key != key) {
                return@forEach
            }
            found = true
            val valueAtKey = (it.value as Document)
            toAppend.forEach {
                valueAtKey[it.key] = it.value
            }
        }
        if (!found) {
            newDocument[key] = toAppend
        }
        return newDocument
    }

    private val failingConflictHandler: ConflictHandler<Document> = ConflictHandler {
        _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
        fail("did not expect a conflict")
        throw IllegalStateException("unreachable")
    }

    private fun testSyncInBothDirections(testFun: () -> Unit) {
        val dataSync = (mongoClient as RemoteMongoClientImpl).dataSynchronizer
        println("running tests with L2R going first")
        dataSync.swapSyncDirection(true)
        testFun()

        teardown()
        setup()
        println("running tests with R2L going first")
        dataSync.swapSyncDirection(false)
        testFun()
    }
}
