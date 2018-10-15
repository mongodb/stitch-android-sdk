package com.mongodb.stitch.server.services.mongodb.remote.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.internal.common.Callback
import com.mongodb.stitch.core.internal.common.OperationResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.server.services.mongodb.remote.Sync
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.lang.Exception
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SyncMongoClientIntTests : BaseStitchServerIntTest() {

    private val mongodbUriProp = "test.stitch.mongodbURI"

    private var remoteMongoClientOpt: RemoteMongoClient? = null
    private val remoteMongoClient: RemoteMongoClient
        get() = remoteMongoClientOpt!!
    private var mongoClientOpt: RemoteMongoClient? = null
    private val mongoClient: RemoteMongoClient
        get() = mongoClientOpt!!
    private var dbName = ObjectId().toHexString()
    private var collName = ObjectId().toHexString()
    private var namespace = MongoNamespace(dbName, collName)

    private fun getMongoDbUri(): String {
        return System.getProperty(mongodbUriProp, "mongodb://localhost:26000")
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
        namespace = MongoNamespace(dbName, collName)

        addRule(svc.second, RuleCreator.MongoDb("$dbName.$collName", rule))
        addRule(svc2.second, RuleCreator.MongoDb("$dbName.$collName", rule))

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())
        mongoClientOpt = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.stop()
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.disableSyncThread()
        remoteMongoClientOpt = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
        goOnline()
    }

    @After
    override fun teardown() {
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
        super.teardown()
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
            val doc = remoteColl.find(doc1).first()!!
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
            streamAndSync()

            // 1. updating a document remotely should not be reflected until coming back online.
            goOffline()
            val doc1Update = Document("\$inc", Document("foo", 1))
            val result = remoteColl.updateOne(
                doc1Filter,
                doc1Update)
            assertEquals(1, result.matchedCount)
            streamAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))
            goOnline()
            streamAndSync()
            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // 2. insertOneAndSync should work offline and then sync the document when online.
            goOffline()
            val doc3 = Document("so", "syncy")
            val insResult = coll.insertOneAndSync(doc3)
            assertEquals(doc3, withoutSyncVersion(coll.findOneById(insResult.insertedId)!!))
            streamAndSync()
            assertNull(remoteColl.find(Document("_id", doc3["_id"])).first())
            goOnline()
            streamAndSync()
            assertEquals(doc3, withoutSyncVersion(remoteColl.find(Document("_id", doc3["_id"])).first()!!))

            // 3. updating a document locally that has been updated remotely should invoke the conflict
            // resolver.
            val sem = watchForEvents(this.namespace)
            val result2 = remoteColl.updateOne(
                doc1Filter,
                withNewSyncVersionSet(doc1Update))
            sem.acquire()
            assertEquals(1, result2.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            val result3 = coll.updateOneById(
                doc1Id,
                doc1Update)
            assertEquals(1, result3.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            // first pass will invoke the conflict handler and update locally but not remotely yet
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 4
            expectedDocument.remove("fooOps")
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            // second pass will update with the ack'd version id
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testUpdateConflicts() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = remoteColl.find(docToInsert).first()!!
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
            streamAndSync()

            // Update remote
            val remoteUpdate = withNewSyncVersionSet(Document("\$set", Document("remote", "update")))
            val sem = watchForEvents(this.namespace)
            var result = remoteColl.updateOne(doc1Filter, remoteUpdate)
            sem.acquire()
            assertEquals(1, result.matchedCount)
            val expectedRemoteDocument = Document(doc)
            expectedRemoteDocument["remote"] = "update"
            assertEquals(expectedRemoteDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

            // Update local
            val localUpdate = Document("\$set", Document("local", "updateWow"))
            result = coll.updateOneById(doc1Id, localUpdate)
            assertEquals(1, result.matchedCount)
            val expectedLocalDocument = Document(doc)
            expectedLocalDocument["local"] = "updateWow"
            assertEquals(expectedLocalDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // first pass will invoke the conflict handler and update locally but not remotely yet
            streamAndSync()
            assertEquals(expectedRemoteDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedLocalDocument["remote"] = "update"
            assertEquals(expectedLocalDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // second pass will update with the ack'd version id
            streamAndSync()
            assertEquals(expectedLocalDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            assertEquals(expectedLocalDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
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

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(DefaultSyncConflictResolvers.remoteWins(), null, null)
            coll.syncOne(doc1Id)
            streamAndSync()

            val expectedDocument = Document(doc)
            val sem = watchForEvents(this.namespace)
            var result = remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
            sem.acquire()
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            streamAndSync()
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
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

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            coll.syncOne(doc1Id)
            streamAndSync()

            val expectedDocument = Document(doc)
            val sem = watchForEvents(this.namespace)
            var result = remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
            sem.acquire()
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            streamAndSync()
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testDeleteOneByIdNoConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(failingConflictHandler, null, null)
            coll.syncOne(doc1Id)
            streamAndSync()

            goOffline()
            val result = coll.deleteOneById(doc1Id)
            assertEquals(1, result.deletedCount)

            val expectedDocument = withoutSyncVersion(Document(doc))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            streamAndSync()
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

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("well", "shoot")
            }, null, null)
            coll.syncOne(doc1Id)
            streamAndSync()

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, remoteColl.updateOne(
                doc1Filter,
                withNewSyncVersionSet(doc1Update)).matchedCount)

            goOffline()
            val result = coll.deleteOneById(doc1Id)
            assertEquals(1, result.deletedCount)

            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedDocument.remove("hello")
            expectedDocument.remove("foo")
            expectedDocument["well"] = "shoot"
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
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

            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            val expectedDocument = withoutSyncVersion(Document(doc))
            expectedDocument["foo"] = 1
            assertNull(remoteColl.find(doc1Filter).first())
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            goOnline()
            streamAndSync()

            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
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

            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            goOnline()
            streamAndSync()
            val expectedDocument = withoutSyncVersion(Document(doc))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
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
            streamAndSync()

            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)
            val expectedDocument = withoutSyncVersion(Document(doc))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            assertEquals(1, coll.deleteOneById(doc1Id).deletedCount)
            coll.insertOneAndSync(doc)
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testRemoteDeletesLocalNoConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(failingConflictHandler, null, null)
            coll.syncOne(doc1Id)

            streamAndSync()

            assertEquals(coll.syncedIds.size, 1)

            val sem = watchForEvents(this.namespace)
            remoteColl.deleteOne(doc1Filter)
            sem.acquire()

            streamAndSync()

            assertNull(remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))

            // This should not re-sync the document
            streamAndSync()
            remoteColl.insertOne(doc)
            streamAndSync()

            assertEquals(doc, remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testRemoteDeletesLocalConflict() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("hello", "world")
            }, null, null)
            coll.syncOne(doc1Id)
            streamAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))
            assertNotNull(coll.findOneById(doc1Id))

            goOffline()
            remoteColl.deleteOne(doc1Filter)
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            goOnline()
            streamAndSync()
            assertNull(remoteColl.find(doc1Filter).first())
            assertNotNull(coll.findOneById(doc1Id))

            streamAndSync()
            assertNotNull(remoteColl.find(doc1Filter).first())
            assertNotNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testRemoteInsertsLocalUpdates() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("hello", "again")
            }, null, null)
            coll.syncOne(doc1Id)

            streamAndSync()

            assertEquals(doc, coll.findOneById(doc1Id))
            assertNotNull(coll.findOneById(doc1Id))

            val wait = watchForEvents(this.namespace, 2)
            remoteColl.deleteOne(doc1Filter)
            remoteColl.insertOne(withNewSyncVersion(doc))
            wait.acquire()

            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            streamAndSync()

            assertEquals(doc, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            val expectedDocument = Document("_id", doc1Id.value)
            expectedDocument["hello"] = "again"
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testRemoteInsertsWithVersionLocalUpdates() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(withNewSyncVersion(docToInsert))

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure(failingConflictHandler, null, null)
            coll.syncOne(doc1Id)
            streamAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))

            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            streamAndSync()
            val expectedDocument = Document(withoutSyncVersion(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testResolveConflictWithDelete() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(withNewSyncVersion(docToInsert))

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                null
            }, null, null)
            coll.syncOne(doc1Id)
            streamAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))
            assertNotNull(coll.findOneById(doc1Id))

            val sem = watchForEvents(this.namespace)
            assertEquals(1, remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 1)))).matchedCount)
            sem.acquire()

            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            streamAndSync()
            val expectedDocument = Document(withoutSyncVersion(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

            goOffline()
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            streamAndSync()
            assertNull(remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))
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

            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            powerCycleDevice()

            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            coll.syncOne(doc1Id)

            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            streamAndSync()

            val expectedDocument = Document(doc)
            var result = remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            streamAndSync() // does nothing with no conflict handler

            assertEquals(1, coll.syncedIds.size)
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
            streamAndSync() // resolves the conflict

            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testDesync() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val docToInsert = Document("hello", "world")
            coll.configure(failingConflictHandler, null, null)
            val doc1Id = coll.insertOneAndSync(docToInsert).insertedId

            assertEquals(docToInsert, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            coll.desyncOne(doc1Id)
            streamAndSync()
            assertNull(coll.findOneById(doc1Id))
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

            streamAndSync()
            val expectedDocument = Document(docToInsert)
            expectedDocument["friend"] = "welcome"
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            assertEquals(docToInsert, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testFrozenDocumentConfig() {
        testSyncInBothDirections {
            val testSync = getTestSync()
            val remoteColl = getTestCollRemote()
            var errorEmitted = false

            var conflictCounter = 0

            testSync.configure(
                    { _: BsonValue, _: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                        if (conflictCounter == 0) {
                            conflictCounter++
                            errorEmitted = true
                            throw Exception("ouch")
                        }
                        remoteEvent.fullDocument
                    },
                    { _: BsonValue, _: ChangeEvent<Document> ->
                    }, { _, _ ->
                    })

            // insert an initial doc
            val testDoc = Document("hello", "world")
            val result = testSync.insertOneAndSync(testDoc)

            // do a sync pass, synchronizing the doc
            streamAndSync()

            assertNotNull(remoteColl.find(Document("_id", testDoc.get("_id"))).first())

            // update the doc
            val expectedDoc = Document("hello", "computer")
            testSync.updateOneById(result.insertedId, Document("\$set", expectedDoc))

            // create a conflict
            var sem = watchForEvents(namespace)
            remoteColl.updateOne(Document("_id", result.insertedId), withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
            sem.acquire()

            // do a sync pass, and throw an error during the conflict resolver
            // freezing the document
            streamAndSync()
            assertTrue(errorEmitted)

            // update the doc remotely
            val nextDoc = Document("hello", "friend")

            sem = watchForEvents(namespace)
            remoteColl.updateOne(Document("_id", result.insertedId), nextDoc)
            sem.acquire()
            streamAndSync()

            // it should not have updated the local doc, as the local doc should be frozen
            assertEquals(
                withoutId(expectedDoc),
                withoutSyncVersion(withoutId(testSync.find(Document("_id", result.insertedId)).first()!!)))

            // update the local doc. this should unfreeze the config
            testSync.updateOneById(result.insertedId, Document("\$set", Document("no", "op")))

            streamAndSync()

            // this should still be the remote doc since remote wins
            assertEquals(
                withoutId(nextDoc),
                withoutSyncVersion(withoutId(testSync.find(Document("_id", result.insertedId)).first()!!)))

            // update the doc remotely
            val lastDoc = Document("good night", "computer")

            sem = watchForEvents(namespace)
            remoteColl.updateOne(
                Document("_id", result.insertedId),
                withNewSyncVersion(lastDoc)
            )
            sem.acquire()

            // now that we're sync'd and unfrozen, it should be reflected locally
            // TODO: STITCH-1958 Possible race condition here for update listening
            streamAndSync()

            assertEquals(
                withoutId(lastDoc),
                withoutSyncVersion(
                    withoutId(testSync.find(Document("_id", result.insertedId)).first()!!)))
        }
    }

    @Test
    fun testConfigure() {
        val testSync = getTestSync()
        val remoteColl = getTestCollRemote()

        val docToInsert = Document("hello", "world")
        val insertedId = testSync.insertOneAndSync(docToInsert).insertedId

        var hasConflictHandlerBeenInvoked = false
        var hasChangeEventListenerBeenInvoked = false

        testSync.configure(
            { _: BsonValue, _: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                hasConflictHandlerBeenInvoked = true
                assertEquals(remoteEvent.fullDocument["fly"], "away")
                remoteEvent.fullDocument
            },
            { _: BsonValue, _: ChangeEvent<Document> ->
                hasChangeEventListenerBeenInvoked = true
            },
            { _, _ -> }
        )

        val sem = watchForEvents(namespace)
        remoteColl.insertOne(Document("_id", insertedId).append("fly", "away"))
        sem.acquire()

        streamAndSync()

        assertTrue(hasConflictHandlerBeenInvoked)
        assertTrue(hasChangeEventListenerBeenInvoked)
    }

    private fun streamAndSync() {
        val dataSync = (mongoClient as RemoteMongoClientImpl).dataSynchronizer
        if (testNetworkMonitor.connectedState) {
            while (!dataSync.areAllStreamsOpen()) {
                println("waiting for all streams to open before doing sync pass")
                Thread.sleep(1000)
            }
        }
        dataSync.doSyncPass()
    }

    @Test
    fun testSyncVersioningScheme() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")

            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)

            val doc = coll.findOneById(insertResult.insertedId)
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            goOnline()
            streamAndSync()
            val expectedDocument = Document(doc)

            // the remote document after an initial insert should have a fresh instance ID, and a
            // version counter of 0
            val firstRemoteDoc = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(firstRemoteDoc))

            assertEquals(0, versionCounterOf(firstRemoteDoc))

            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // the remote document after a local update, but before a sync pass, should have the
            // same version as the original document, and be equivalent to the unupdated document
            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            val secondRemoteDocBeforeSyncPass = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(secondRemoteDocBeforeSyncPass))
            assertEquals(versionOf(firstRemoteDoc), versionOf(secondRemoteDocBeforeSyncPass))

            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // the remote document after a local update, and after a sync pass, should have a new
            // version with the same instance ID as the original document, a version counter
            // incremented by 1, and be equivalent to the updated document.
            streamAndSync()
            val secondRemoteDoc = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(secondRemoteDoc))
            assertEquals(instanceIdOf(firstRemoteDoc), instanceIdOf(secondRemoteDoc))
            assertEquals(1, versionCounterOf(secondRemoteDoc))

            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // the remote document after a local delete and local insert, but before a sync pass,
            // should have the same version as the previous document
            assertEquals(1, coll.deleteOneById(doc1Id).deletedCount)
            coll.insertOneAndSync(doc)

            val thirdRemoteDocBeforeSyncPass = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDocBeforeSyncPass))

            expectedDocument.remove("foo")
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)))

            // the remote document after a local delete and local insert, and after a sync pass,
            // should have the same instance ID as before and a version count, since the change
            // events are coalesced into a single update event
            streamAndSync()

            val thirdRemoteDoc = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDoc))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)))

            assertEquals(instanceIdOf(secondRemoteDoc), instanceIdOf(thirdRemoteDoc))
            assertEquals(2, versionCounterOf(thirdRemoteDoc))

            // the remote document after a local delete, a sync pass, a local insert, and after
            // another sync pass should have a new instance ID, with a version counter of zero,
            // since the change events are not coalesced
            assertEquals(1, coll.deleteOneById(doc1Id).deletedCount)
            streamAndSync()
            coll.insertOneAndSync(doc)
            streamAndSync()

            val fourthRemoteDoc = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDoc))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)))

            assertNotEquals(instanceIdOf(secondRemoteDoc), instanceIdOf(fourthRemoteDoc))
            assertEquals(0, versionCounterOf(fourthRemoteDoc))
        }
    }

    @Test
    fun testUnsupportedSpvFails() {
        val coll = getTestSync()

        val remoteColl = getTestCollRemote()

        val docToInsert = withNewUnsupportedSyncVersion(Document("hello", "world"))

        val errorEmittedSem = Semaphore(0)
        coll.configure(
                failingConflictHandler,
                null,
                ErrorListener { _, _ -> errorEmittedSem.release() })

        remoteColl.insertOne(docToInsert)

        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        coll.syncOne(doc1Id)

        assertTrue(coll.syncedIds.contains(doc1Id))

        // syncing on this document with an unsupported spv should cause the document to desync
        goOnline()
        streamAndSync()

        assertFalse(coll.syncedIds.contains(doc1Id))

        // an error should also have been emitted
        assertTrue(errorEmittedSem.tryAcquire(10, TimeUnit.SECONDS))
    }

    @Test
    fun testStaleFetchSingle() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val doc1 = Document("hello", "world")
            remoteColl.insertOne(doc1)

            // get the document
            val doc = remoteColl.find(doc1).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                throw IllegalStateException("failure")
            }, null, null)
            coll.syncOne(doc1Id)

            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))

            coll.updateOneById(doc1Id, Document("\$inc", Document("i", 1)))
            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))

            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testStaleFetchSingleDeleted() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val doc1 = Document("hello", "world")
            remoteColl.insertOne(doc1)

            // get the document
            val doc = remoteColl.find(doc1).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                throw IllegalStateException("failure")
            }, null, null)
            coll.syncOne(doc1Id)

            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))

            coll.updateOneById(doc1Id, Document("\$inc", Document("i", 1)))
            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))

            assertEquals(1, remoteColl.deleteOne(doc1Filter).deletedCount)
            powerCycleDevice()
            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                throw IllegalStateException("failure")
            }, null, null)

            streamAndSync()
            assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testStaleFetchMultiple() {
        testSyncInBothDirections {
            val coll = getTestSync()

            val remoteColl = getTestCollRemote()

            val insertResult =
                    remoteColl.insertMany(listOf(
                            Document("hello", "world"),
                            Document("hello", "friend")))

            // get the document
            val doc1Id = insertResult.insertedIds[0]
            val doc2Id = insertResult.insertedIds[1]

            coll.configure({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                throw IllegalStateException("failure")
            }, null, null)
            coll.syncOne(doc1Id)

            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))

            coll.updateOneById(doc1Id, Document("\$inc", Document("i", 1)))
            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))

            coll.syncOne(doc2Id)
            streamAndSync()
            assertNotNull(coll.findOneById(doc1Id))
            assertNotNull(coll.findOneById(doc2Id))
        }
    }

    private fun watchForEvents(
        namespace: MongoNamespace,
        n: Int = 1
    ): Semaphore {
        println("watching for $n change event(s) ns=$namespace")
        val waitFor = AtomicInteger(n)
        val sem = Semaphore(0)
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.addWatcher(namespace, object : Callback<ChangeEvent<BsonDocument>, Any> {
            override fun onComplete(result: OperationResult<ChangeEvent<BsonDocument>, Any>) {
                if (result.isSuccessful && result.geResult() != null) {
                    println("change event of operation ${result.geResult().operationType} ns=$namespace found!")
                }
                if (waitFor.decrementAndGet() == 0) {
                    (mongoClient as RemoteMongoClientImpl).dataSynchronizer.removeWatcher(namespace, this)
                    sem.release()
                }
            }
        })
        return sem
    }

    private fun powerCycleDevice() {
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.reloadConfig()
    }

    private fun goOffline() {
        println("going offline")
        testNetworkMonitor.connectedState = false
    }

    private fun goOnline() {
        println("going online")
        testNetworkMonitor.connectedState = true
    }

    private fun withoutId(document: Document): Document {
        val newDoc = Document(document)
        newDoc.remove("_id")
        return newDoc
    }

    private fun withoutSyncVersion(document: Document): Document {
        val newDoc = Document(document)
        newDoc.remove("__stitch_sync_version")
        return newDoc
    }

    private fun withNewSyncVersion(document: Document): Document {
        val newDocument = Document(java.util.HashMap(document))
        newDocument["__stitch_sync_version"] = freshSyncVersionDoc()

        return newDocument
    }

    private fun withNewSyncVersionSet(document: Document): Document {
        return appendDocumentToKey(
                "\$set",
                document,
                Document("__stitch_sync_version", freshSyncVersionDoc()))
    }

    private fun withNewUnsupportedSyncVersion(document: Document): Document {
        val newDocument = Document(java.util.HashMap(document))
        val badVersion = freshSyncVersionDoc()
        badVersion.remove("spv")
        badVersion.append("spv", 2)

        newDocument["__stitch_sync_version"] = badVersion

        return newDocument
    }

    private fun freshSyncVersionDoc(): Document {
        return Document("spv", 1).append("id", UUID.randomUUID().toString()).append("v", 0L)
    }

    private fun versionOf(document: Document): Document {
        return document["__stitch_sync_version"] as Document
    }

    private fun versionCounterOf(document: Document): Long {
        return versionOf(document)["v"] as Long
    }

    private fun instanceIdOf(document: Document): String {
        return versionOf(document)["id"] as String
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

    private val failingConflictHandler: ConflictHandler<Document> = ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
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
