package com.mongodb.stitch.server.services.mongodb.sync

import com.mongodb.Block
import com.mongodb.MongoNamespace
import com.mongodb.MongoQueryException
import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.mongodb.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver
import com.mongodb.stitch.core.services.mongodb.sync.internal.ChangeEvent
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.server.services.mongodb.sync.internal.SyncMongoClientImpl
import org.bson.BsonObjectId
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SyncMongoClientIntTests : BaseStitchServerIntTest() {

    private val mongodbUriProp = "test.stitch.mongodbURI"

    private var remoteMongoClientOpt: RemoteMongoClient? = null
    private val remoteMongoClient: RemoteMongoClient
        get() = remoteMongoClientOpt!!
    private var mongoClientOpt: SyncMongoClient? = null
    private val mongoClient: SyncMongoClient
        get() = mongoClientOpt!!
    private var dbName = ObjectId().toHexString()
    private var collName = ObjectId().toHexString()

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
        addRule(svc.second, RuleCreator.MongoDb("$dbName.$collName", rule))
        addRule(svc2.second, RuleCreator.MongoDb("$dbName.$collName", rule))

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())
        mongoClientOpt = client.getServiceClient(SyncMongoClient.Factory, "mongodb1")
        (mongoClient as SyncMongoClientImpl).dataSynchronizer.stop()
        remoteMongoClientOpt = client.getServiceClient(RemoteMongoClient.Factory, "mongodb1")
        goOnline()
    }

    @After
    override fun teardown() {
        mongoClientOpt?.close()
        super.teardown()
    }

    private fun getTestColl(): SyncMongoCollection<Document> {
        val db = mongoClient.getDatabase(dbName)
        assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return coll
    }

    private fun getTestCollRemote(): RemoteMongoCollection<Document> {
        val db = remoteMongoClient.getDatabase(dbName)
        assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return coll
    }

    @Test
    fun testFind() {
        val coll = getTestColl()
        var iter = coll.find()
        Assert.assertFalse(iter.iterator().hasNext())
        assertNull(iter.first())

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        doc2["proj"] = "field"
        coll.insertMany(listOf(doc1, doc2))
        Assert.assertTrue(iter.iterator().hasNext())
        assertEquals(withoutId(doc1), withoutId(iter.first()!!))
        assertEquals(withoutId(doc2), withoutId(iter
                .limit(1)
                .sort(Document("_id", -1)).iterator().next()))

        iter = coll.find(doc1)
        Assert.assertTrue(iter.iterator().hasNext())
        assertEquals(withoutId(doc1), withoutId(iter.iterator().next()))

        iter = coll.find().filter(doc1)
        Assert.assertTrue(iter.iterator().hasNext())
        assertEquals(withoutId(doc1), withoutId(iter.iterator().next()))

        assertEquals(Document("proj", "field"),
                withoutId(coll.find(doc2).projection(Document("proj", 1)).iterator().next()))

        var count = 0
        coll.find().forEach(Block {
            count++
        })
        assertEquals(2, count)

        assertEquals(true, coll.find().map {
            it == doc1
        }.first())

        assertEquals(listOf(doc1, doc2), coll.find().into(mutableListOf()))

        try {
            coll.find(Document("\$who", 1)).first()
            fail()
        } catch (ex: MongoQueryException) {
        }
    }

    @Test
    fun testWatch() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val doc1 = Document("hello", "world")
            val doc2 = Document("hello", "friend")
            doc2["proj"] = "field"
            coll.insertMany(listOf(doc1, doc2))

            // get the document
            val doc = coll.find(doc1).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            // start watching it and always set the value to hello world in a conflict
            coll.sync(doc1Id, {
                _: BsonValue, localEvent: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                val merged = localEvent.fullDocument.getInteger("foo") +
                        remoteEvent.fullDocument.getInteger("foo")
                val newDocument = Document(HashMap<String, Any>(remoteEvent.fullDocument))
                newDocument["foo"] = merged
                newDocument
            })
            listenAndSync()

            // 1. updating a document remotely should not be reflected until coming back online.
            goOffline()
            val doc1Update = Document("\$inc", Document("foo", 1))
            val result = remoteColl.updateOne(
                    doc1Filter,
                    doc1Update)
            assertEquals(1, result.matchedCount)
            listenAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))
            goOnline()
            listenAndSync()
            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // 2. insertOneAndSync should work offline and then sync the document when online.
            goOffline()
            val doc3 = Document("so", "syncy")
            val insResult = coll.insertOneAndSync(doc3, {
                _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("hello", "world")
            })
            assertEquals(doc3, withoutVersionId(coll.findOneById(insResult.insertedId)!!))
            listenAndSync()
            assertNull(remoteColl.find(Document("_id", doc3["_id"])).first())
            goOnline()
            listenAndSync()
            assertEquals(doc3, withoutVersionId(remoteColl.find(Document("_id", doc3["_id"])).first()!!))

            // 3. updating a document locally that has been updated remotely should invoke the conflict
            // resolver.
            val result2 = remoteColl.updateOne(
                    doc1Filter,
                    withNewVersionIdSet(doc1Update))
            assertEquals(1, result2.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            val result3 = coll.updateOneById(
                    doc1Id,
                    doc1Update)
            assertEquals(1, result3.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            // first pass will invoke the conflict handler and update locally but not remotely yet
            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 4
            expectedDocument.remove("fooOps")
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            // second pass will update with the ack'd version id
            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
        })
    }

    @Test
    fun testUpdateConflicts() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, { _: BsonValue, localEvent: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                val merged = Document(localEvent.fullDocument)
                remoteEvent.fullDocument.forEach {
                    if (localEvent.fullDocument.containsKey(it.key)) {
                        return@forEach
                    }
                    merged[it.key] = it.value
                }
                merged
            })
            listenAndSync()

            // Update remote
            val remoteUpdate = withNewVersionIdSet(Document("\$set", Document("remote", "update")))
            var result = remoteColl.updateOne(doc1Filter, remoteUpdate)
            assertEquals(1, result.matchedCount)
            val expectedRemoteDocument = Document(doc)
            expectedRemoteDocument["remote"] = "update"
            assertEquals(expectedRemoteDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))

            // Update local
            val localUpdate = Document("\$set", Document("local", "updateWow"))
            result = coll.updateOneById(doc1Id, localUpdate)
            assertEquals(1, result.matchedCount)
            val expectedLocalDocument = Document(doc)
            expectedLocalDocument["local"] = "updateWow"
            assertEquals(expectedLocalDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            // first pass will invoke the conflict handler and update locally but not remotely yet
            listenAndSync()
            assertEquals(expectedRemoteDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            expectedLocalDocument["remote"] = "update"
            assertEquals(expectedLocalDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            // second pass will update with the ack'd version id
            listenAndSync()
            assertEquals(expectedLocalDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            assertEquals(expectedLocalDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
        })
    }

    @Test
    fun testUpdateRemoteWins() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            docToInsert["foo"] = 1
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, DefaultSyncConflictResolvers.remoteWins())
            listenAndSync()

            val expectedDocument = Document(doc)
            var result = remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 2))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            listenAndSync()
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
        })
    }

    @Test
    fun testUpdateLocalWins() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            docToInsert["foo"] = 1
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, DefaultSyncConflictResolvers.localWins())
            listenAndSync()

            val expectedDocument = Document(doc)
            var result = remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 2))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            listenAndSync()
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
        })
    }

    @Test
    fun testDeleteOneByIdNoConflict() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, failingConflictResolver)
            listenAndSync()

            goOffline()
            val result = coll.deleteOneById(doc1Id)
            assertEquals(1, result.deletedCount)

            val expectedDocument = withoutVersionId(Document(doc))
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            listenAndSync()
            assertNull(remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))
        })
    }

    @Test
    fun testDeleteOneByIdConflict() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("well", "shoot")
            })
            listenAndSync()

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, remoteColl.updateOne(
                    doc1Filter,
                    withNewVersionIdSet(doc1Update)).matchedCount)

            goOffline()
            val result = coll.deleteOneById(doc1Id)
            assertEquals(1, result.deletedCount)

            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            expectedDocument.remove("hello")
            expectedDocument.remove("foo")
            expectedDocument["well"] = "shoot"
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
        })
    }

    @Test
    fun testInsertThenUpdateThenSync() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            val insertResult = coll.insertOneAndSync(docToInsert, failingConflictResolver)

            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            val expectedDocument = withoutVersionId(Document(doc))
            expectedDocument["foo"] = 1
            assertNull(remoteColl.find(doc1Filter).first())
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            goOnline()
            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
        })
    }
    @Test
    fun testInsertThenSyncUpdateThenUpdate() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            val insertResult = coll.insertOneAndSync(docToInsert, failingConflictResolver)

            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            goOnline()
            listenAndSync()
            val expectedDocument = withoutVersionId(Document(doc))
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
        })
    }

    @Test
    fun testInsertThenSyncThenRemoveThenInsertThenUpdate() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            val insertResult = coll.insertOneAndSync(docToInsert, failingConflictResolver)
            listenAndSync()

            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)
            val expectedDocument = withoutVersionId(Document(doc))
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            assertEquals(1, coll.deleteOneById(doc1Id).deletedCount)
            coll.insertOneAndSync(doc, failingConflictResolver)
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
        })
    }

    @Test
    fun testRemoteDeletesLocalNoConflict() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, failingConflictResolver)
            listenAndSync()

            remoteColl.deleteOne(doc1Filter)

            listenAndSync()
            assertNull(remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))

            // This should not desync the document
            remoteColl.insertOne(doc)
            listenAndSync()
            assertEquals(doc, remoteColl.find(doc1Filter).first())
            assertEquals(doc, coll.findOneById(doc1Id))
        })
    }

    @Test
    fun testRemoteDeletesLocalConflict() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("hello", "world")
            })
            listenAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))
            assertNotNull(coll.findOneById(doc1Id))

            goOffline()
            remoteColl.deleteOne(doc1Filter)
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            goOnline()
            listenAndSync()
            assertNull(remoteColl.find(doc1Filter).first())
            assertNotNull(coll.findOneById(doc1Id))

            listenAndSync()
            assertNotNull(remoteColl.find(doc1Filter).first())
            assertNotNull(coll.findOneById(doc1Id))
        })
    }

    @Test
    fun testRemoteInsertsLocalUpdates() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("hello", "again")
            })
            listenAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))
            assertNotNull(coll.findOneById(doc1Id))

            remoteColl.deleteOne(doc1Filter)
            remoteColl.insertOne(withNewVersionId(doc))
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            goOnline()
            listenAndSync()
            assertEquals(doc, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            val expectedDocument = Document("_id", doc1Id.value)
            expectedDocument["hello"] = "again"
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
        })
    }

    @Test
    fun testRemoteInsertsWithVersionLocalUpdates() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(withNewVersionId(docToInsert))

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, failingConflictResolver)
            listenAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))

            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            listenAndSync()
            val expectedDocument = Document(withoutVersionId(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
        })
    }

    @Test
    fun testResolveConflictWithDelete() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            coll.insertOne(withNewVersionId(docToInsert))

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            coll.sync(doc1Id, { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                null
            })
            listenAndSync()
            assertEquals(doc, coll.findOneById(doc1Id))
            assertNotNull(coll.findOneById(doc1Id))

            assertEquals(1, remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 1)))).matchedCount)
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            listenAndSync()
            val expectedDocument = Document(withoutVersionId(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))

            goOffline()
            assertNull(coll.findOneById(doc1Id))

            goOnline()
            listenAndSync()
            assertNull(remoteColl.find(doc1Filter).first())
            assertNull(coll.findOneById(doc1Id))
        })
    }

    @Test
    fun testTurnDeviceOffAndOn() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val remoteColl = getTestCollRemote()

            val docToInsert = Document("hello", "world")
            docToInsert["foo"] = 1
            coll.insertOne(docToInsert)

            val doc = coll.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            powerCycleDevice()
            coll.sync(doc1Id, DefaultSyncConflictResolvers.localWins())
            powerCycleDevice()
            listenAndSync() // does nothing

            coll.sync(doc1Id, DefaultSyncConflictResolvers.localWins())
            listenAndSync() // syncs this time

            val expectedDocument = Document(doc)
            var result = remoteColl.updateOne(doc1Filter, withNewVersionIdSet(Document("\$inc", Document("foo", 2))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
            powerCycleDevice()
            coll.sync(doc1Id, DefaultSyncConflictResolvers.localWins())
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))

            powerCycleDevice()
            listenAndSync() // does nothing with no conflict handler

            assertEquals(1, coll.synchronizedDocuments.size)
            assertNull(coll.synchronizedDocuments.toTypedArray()[0].conflictResolver)
            coll.sync(coll.synchronizedDocuments.toTypedArray()[0].documentId, DefaultSyncConflictResolvers.localWins())
            listenAndSync() // resolves the conflict

            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            powerCycleDevice()
            coll.sync(doc1Id, DefaultSyncConflictResolvers.localWins())
            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
        })
    }

    @Test
    fun testDesync() {
        testSyncInBothDirections({
            val coll = getTestColl()

            val docToInsert = Document("hello", "world")
            val doc1Id = coll.insertOneAndSync(docToInsert, failingConflictResolver).insertedId

            assertEquals(docToInsert, withoutVersionId(coll.findOneById(doc1Id)!!))
            coll.desync(doc1Id)
            listenAndSync()
            assertNull(coll.findOneById(doc1Id))
        })
    }

    @Test
    fun testInsertInsertConflict() {
        testSyncInBothDirections({
            val coll = getTestColl()
            val remoteColl = getTestCollRemote()

            val docToInsert = Document("_id", "hello")
            remoteColl.insertOne(docToInsert)

            val doc1Id = coll.insertOneAndSync(docToInsert, { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("friend", "welcome")
            }).insertedId

            val doc1Filter = Document("_id", doc1Id)

            listenAndSync()
            val expectedDocument = Document(docToInsert)
            expectedDocument["friend"] = "welcome"
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            assertEquals(docToInsert, withoutVersionId(remoteColl.find(doc1Filter).first()!!))

            listenAndSync()
            assertEquals(expectedDocument, withoutVersionId(coll.findOneById(doc1Id)!!))
            assertEquals(expectedDocument, withoutVersionId(remoteColl.find(doc1Filter).first()!!))
        })
    }

    private fun listenAndSync() {
        val dataSync = (mongoClient as SyncMongoClientImpl).dataSynchronizer
        dataSync.doListenerSweep()
        dataSync.doSyncPass()
    }

    private fun powerCycleDevice() {
        (mongoClient as SyncMongoClientImpl).dataSynchronizer.reloadConfig()
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

    private val failingConflictResolver: SyncConflictResolver<Document> = SyncConflictResolver({ _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
        fail("did not expect a conflict")
        throw IllegalStateException("unreachable")
    })

    private fun testSyncInBothDirections(testFun: () -> Unit) {
        val dataSync = (mongoClient as SyncMongoClientImpl).dataSynchronizer
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
