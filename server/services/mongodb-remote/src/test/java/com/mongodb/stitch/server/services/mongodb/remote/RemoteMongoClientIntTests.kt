package com.mongodb.stitch.server.services.mongodb.remote

import com.mongodb.Block
import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.internal.common.BsonUtils
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions
import com.mongodb.stitch.core.testutils.CustomType
import com.mongodb.stitch.server.core.StitchAppClient
import com.mongodb.stitch.server.services.mongodb.remote.internal.RemoteMongoClientImpl
import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import org.bson.codecs.configuration.CodecConfigurationException
import org.bson.codecs.configuration.CodecRegistries
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
import java.util.*

class RemoteMongoClientIntTests : BaseStitchServerIntTest() {

    private val mongodbUriProp = "test.stitch.mongodbURI"

    private var mongoClientOpt: RemoteMongoClient? = null
    private val mongoClient: RemoteMongoClient by lazy {
        mongoClientOpt!!
    }
    private val dbName = ObjectId().toHexString()
    private val collName = ObjectId().toHexString()

    private fun getMongoDbUri(): String {
        return System.getProperty(mongodbUriProp, "mongodb://localhost:26000")
    }

    @Before
    override fun setup() {
        Assume.assumeTrue("no MongoDB URI in properties; skipping test", getMongoDbUri().isNotEmpty())
        super.setup()

        val rule = RuleCreator.MongoDb(
                database = dbName,
                collection = collName,
                roles = listOf(RuleCreator.MongoDb.Role(
                        read = true, write = true
                )),
                schema = RuleCreator.MongoDb.Schema().copy(properties = Document()))
        val client = createStitchClientForAppWithRule(rule)

        mongoClientOpt = client.getServiceClient(RemoteMongoClient.factory, "mongodb1")
    }

    private fun createStitchClientForAppWithRule(rule: RuleCreator): StitchAppClient {
        val app = createApp()
        addProvider(app.second, ProviderConfigs.Anon)
        val svc = addService(
                app.second,
                "mongodb",
                "mongodb1",
                ServiceConfigs.Mongo(getMongoDbUri()))
        addRule(svc.second, rule)

        val client = getAppClient(app.first)
        client.auth.loginWithCredential(AnonymousCredential())

        return client
    }

    @After
    override fun teardown() {
        (mongoClient as RemoteMongoClientImpl).dataSynchronizer.close()
        super.teardown()
    }

    private fun getTestColl(): RemoteMongoCollection<Document> {
        val db = mongoClient.getDatabase(dbName)
        assertEquals(dbName, db.name)
        val coll = db.getCollection(collName)
        assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return coll
    }

    private fun <ResultT> getTestColl(resultClass: Class<ResultT>): RemoteMongoCollection<ResultT> {
        val db = mongoClient.getDatabase(dbName)
        assertEquals(dbName, db.name)
        val coll = db.getCollection(collName, resultClass)
        assertEquals(MongoNamespace(dbName, collName), coll.namespace)
        return coll
    }

    @Test
    fun testCount() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val rawDoc = Document("hello", "world")
        val doc1 = Document(rawDoc)
        val doc2 = Document(rawDoc)
        coll.insertOne(doc1)
        assertEquals(1, coll.count())
        coll.insertOne(doc2)
        assertEquals(2, coll.count())

        assertEquals(2, coll.count(rawDoc))
        assertEquals(0, coll.count(Document("hello", "Friend")))
        assertEquals(1, coll.count(rawDoc, RemoteCountOptions().limit(1)))

        try {
            coll.count(Document("\$who", 1))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun findOne() {
        val coll = getTestColl()

        val doc1 = Document("hello", "world1")
        val doc2 = Document("hello", "world2")
        val doc3 = Document("hello", "world3")

        // Test findOne() on empty collection with no filter and no options
        assertNull(coll.findOne())

        // Insert a document into the collection
        coll.insertOne(doc1)
        assertEquals(1, coll.count())

        // Test findOne() with no filter and no options
        assertEquals(withoutId(coll.findOne()), withoutId(doc1))

        // Test findOne() with filter and no options
        val result = coll.findOne(Document("hello", "world1"))
        assertEquals(withoutId(result), withoutId(doc1))

        // Test findOne() with filter that does not match any documents and no options
        assertNull(coll.findOne(Document("hello", "worldDNE")))

        // Insert 2 more documents into the collection
       coll.insertMany(Arrays.asList(doc2, doc3))
        assertEquals(3, coll.count())

        // test findOne() with projection and sort options
        val projection = Document("hello", 1)
        projection["_id"] = 0
        val result2 = coll.findOne(Document(), RemoteFindOptions()
                .limit(2)
                .projection(projection)
                .sort(Document("hello", 1)))
        assertEquals(result2, withoutId(doc1))

        val result3 = coll.findOne(Document(), RemoteFindOptions()
                .limit(2)
                .projection(projection)
                .sort(Document("hello", -1)))
        assertEquals(result3, withoutId(doc3))

        // test findOne() properly fails
        try {
            coll.findOne(Document("\$who", 1))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testFind() {
        val coll = getTestColl()
        var iter = coll.find()
        assertFalse(iter.iterator().hasNext())
        assertNull(iter.first())

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        doc2["proj"] = "field"
        coll.insertMany(listOf(doc1, doc2))
        assertTrue(iter.iterator().hasNext())
        assertEquals(withoutId(doc1), withoutId(iter.first()!!))
        assertEquals(withoutId(doc2), withoutId(iter
                .limit(1)
                .sort(Document("_id", -1)).iterator().next()))

        iter = coll.find(doc1)
        assertTrue(iter.iterator().hasNext())
        assertEquals(withoutId(doc1), withoutId(iter.iterator().next()))

        iter = coll.find().filter(doc1)
        assertTrue(iter.iterator().hasNext())
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
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testAggregate() {
        val coll = getTestColl()
        var iter = coll.aggregate(listOf())
        assertFalse(iter.iterator().hasNext())
        assertNull(iter.first())

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        coll.insertMany(listOf(doc1, doc2))
        assertTrue(iter.iterator().hasNext())
        assertEquals(withoutId(doc1), withoutId(iter.first()!!))

        iter = coll.aggregate(listOf(Document("\$sort", Document("_id", -1)), Document("\$limit", 1)))
        assertEquals(withoutId(doc2), withoutId(iter.iterator().next()))

        iter = coll.aggregate(listOf(Document("\$match", doc1)))
        assertTrue(iter.iterator().hasNext())
        assertEquals(withoutId(doc1), withoutId(iter.iterator().next()))

        try {
            coll.aggregate(listOf(Document("\$who", 1))).first()
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testInsertOne() {
        val coll = getTestColl()
        val doc = Document("hello", "world")
        doc["_id"] = ObjectId()

        assertEquals(doc.getObjectId("_id"), coll.insertOne(doc).insertedId.asObjectId().value)
        try {
            coll.insertOne(doc)
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
            assertTrue(ex.message!!.contains("duplicate"))
        }

        val doc2 = Document("hello", "world")
        assertNotEquals(doc.getObjectId("_id"), coll.insertOne(doc2).insertedId.asObjectId().value)
    }

    @Test
    fun testInsertMany() {
        val coll = getTestColl()
        val doc1 = Document("hello", "world")
        doc1["_id"] = ObjectId()

        assertEquals(doc1.getObjectId("_id"), coll.insertMany(listOf(doc1)).insertedIds[0]!!.asObjectId().value)
        try {
            coll.insertMany(listOf(doc1))
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
            assertTrue(ex.message!!.contains("duplicate"))
        }

        val doc2 = Document("hello", "world")
        assertNotEquals(doc1.getObjectId("_id"), coll.insertMany(listOf(doc2)).insertedIds[0]!!.asObjectId().value)

        val doc3 = Document("one", "two")
        val doc4 = Document("three", 4)

        coll.insertMany(listOf(doc3, doc4))
        assertEquals(withoutIds(listOf(doc1, doc2, doc3, doc4)), withoutIds(coll.find().toMutableList()))
    }

    @Test
    fun testDeleteOne() {
        val coll = getTestColl()
        assertEquals(0, coll.deleteOne(Document()).deletedCount)
        assertEquals(0, coll.deleteOne(Document("hello", "world")).deletedCount)

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        coll.insertMany(listOf(doc1, doc2))

        assertEquals(1, coll.deleteOne(Document()).deletedCount)
        assertEquals(1, coll.deleteOne(Document()).deletedCount)
        assertEquals(0, coll.deleteOne(Document()).deletedCount)

        coll.insertMany(listOf(doc1, doc2))
        assertEquals(1, coll.deleteOne(doc1).deletedCount)
        assertEquals(0, coll.deleteOne(doc1).deletedCount)
        assertEquals(1, coll.count())
        assertEquals(0, coll.count(doc1))

        try {
            coll.deleteOne(Document("\$who", 1))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testDeleteMany() {
        val coll = getTestColl()
        assertEquals(0, coll.deleteMany(Document()).deletedCount)
        assertEquals(0, coll.deleteMany(Document("hello", "world")).deletedCount)

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        coll.insertMany(listOf(doc1, doc2))

        assertEquals(2, coll.deleteMany(Document()).deletedCount)
        assertEquals(0, coll.deleteMany(Document()).deletedCount)

        coll.insertMany(listOf(doc1, doc2))
        assertEquals(1, coll.deleteMany(doc1).deletedCount)
        assertEquals(0, coll.deleteMany(doc1).deletedCount)
        assertEquals(1, coll.count())
        assertEquals(0, coll.count(doc1))

        try {
            coll.deleteMany(Document("\$who", 1))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testUpdateOne() {
        val coll = getTestColl()
        val doc1 = Document("hello", "world")
        var result = coll.updateOne(Document(), doc1)
        assertEquals(0, result.matchedCount)
        assertEquals(0, result.modifiedCount)
        assertNull(result.upsertedId)

        result = coll.updateOne(Document(), doc1, RemoteUpdateOptions().upsert(true))
        assertEquals(0, result.matchedCount)
        assertEquals(0, result.modifiedCount)
        assertNotNull(result.upsertedId)
        result = coll.updateOne(Document(), Document("\$set", Document("woof", "meow")))
        assertEquals(1, result.matchedCount)
        assertEquals(1, result.modifiedCount)
        assertNull(result.upsertedId)
        val expectedDoc = Document("hello", "world")
        expectedDoc["woof"] = "meow"
        assertEquals(expectedDoc, withoutId(coll.find(Document()).first()!!))

        try {
            coll.updateOne(Document("\$who", 1), Document())
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testUpdateMany() {
        val coll = getTestColl()
        val doc1 = Document("hello", "world")
        var result = coll.updateMany(Document(), doc1)
        assertEquals(0, result.matchedCount)
        assertEquals(0, result.modifiedCount)
        assertNull(result.upsertedId)

        result = coll.updateMany(Document(), doc1, RemoteUpdateOptions().upsert(true))
        assertEquals(0, result.matchedCount)
        assertEquals(0, result.modifiedCount)
        assertNotNull(result.upsertedId)
        result = coll.updateMany(Document(), Document("\$set", Document("woof", "meow")))
        assertEquals(1, result.matchedCount)
        assertEquals(1, result.modifiedCount)
        assertNull(result.upsertedId)

        coll.insertOne(Document())
        result = coll.updateMany(Document(), Document("\$set", Document("woof", "meow")))
        assertEquals(2, result.matchedCount)
        assertEquals(2, result.modifiedCount)

        val expectedDoc1 = Document("hello", "world")
        expectedDoc1["woof"] = "meow"
        val expectedDoc2 = Document("woof", "meow")
        assertEquals(listOf(expectedDoc1, expectedDoc2), withoutIds(coll.find(Document()).toMutableList()))

        try {
            coll.updateMany(Document("\$who", 1), Document())
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testWithDocument() {
        var coll = getTestColl().withDocumentClass(CustomType::class.java)
        assertEquals(CustomType::class.java, coll.documentClass)

        val expected = CustomType(ObjectId(), 42)

        try {
            coll.insertOne(expected)
        } catch (ex: CodecConfigurationException) {
            // Conversion to a BsonDocument happens before it gets into the core Stitch libraries
            // which means this exception isn't wrapped in anyway which closely resembles the
            // Java driver.
        }

        assertEquals(BsonUtils.DEFAULT_CODEC_REGISTRY, coll.codecRegistry)
        coll = coll.withCodecRegistry(CodecRegistries.fromCodecs(CustomType.Codec()))
        assertEquals(expected.id, coll.insertOne(expected).insertedId.asObjectId().value)
        assertEquals(expected, coll.find().first()!!)

        coll = getTestColl(CustomType::class.java)
                .withCodecRegistry(CodecRegistries.fromCodecs(CustomType.Codec()))
        val expected2 = CustomType(null, 42)
        val result = coll.insertOne(expected2)
        assertNotNull(expected2.id)
        assertEquals(expected2.id, result.insertedId.asObjectId().value)
        assertNotEquals(expected.id, result.insertedId.asObjectId().value)

        var actual = coll.find().first()!!
        assertEquals(expected2.intValue, actual.intValue)
        assertNotNull(expected.id)

        val coll2 = getTestColl().withCodecRegistry(
                CodecRegistries.fromRegistries(
                        BsonUtils.DEFAULT_CODEC_REGISTRY,
                        CodecRegistries.fromCodecs(CustomType.Codec())))
        actual = coll2.find(Document(), CustomType::class.java).first()!!
        assertEquals(expected2.intValue, actual.intValue)
        assertNotNull(expected.id)

        val iter = coll2.aggregate(
                listOf(Document("\$match", Document())), CustomType::class.java)
        assertTrue(iter.iterator().hasNext())
        assertEquals(expected, iter.iterator().next())
    }

    @Test
    fun testWatchBsonValueIDs() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val rawDoc1 = Document()
        rawDoc1["_id"] = 1
        rawDoc1["hello"] = "world"

        val rawDoc2 = Document()
        rawDoc2["_id"] = "foo"
        rawDoc2["happy"] = "day"

        coll.insertOne(rawDoc1)
        assertEquals(1, coll.count())

        val stream = coll.watch(BsonInt32(1), BsonString("foo"))

        try {
            coll.insertOne(rawDoc2)
            assertEquals(2, coll.count())
            coll.updateMany(BsonDocument(), Document().append("\$set",
                    BsonDocument().append("new", BsonString("field"))))

            val insertEvent = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent.operationType)
            assertEquals(rawDoc2, insertEvent.fullDocument)
            val updateEvent1 = stream.nextEvent()
            val updateEvent2 = stream.nextEvent()

            assertNotNull(updateEvent1)
            assertNotNull(updateEvent2)

            assertEquals(OperationType.UPDATE, updateEvent1.operationType)
            assertEquals(rawDoc1.append("new", "field"), updateEvent1.fullDocument)
            assertEquals(OperationType.UPDATE, updateEvent2.operationType)
            assertEquals(rawDoc2.append("new", "field"), updateEvent2.fullDocument)
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchObjectIdIDs() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val objectId1 = ObjectId()
        val objectId2 = ObjectId()

        val rawDoc1 = Document()
        rawDoc1["_id"] = objectId1
        rawDoc1["hello"] = "world"

        val rawDoc2 = Document()
        rawDoc2["_id"] = objectId2
        rawDoc2["happy"] = "day"

        coll.insertOne(rawDoc1)
        assertEquals(1, coll.count())

        val stream = coll.watch(objectId1, objectId2)

        try {
            coll.insertOne(rawDoc2)
            assertEquals(2, coll.count())
            coll.updateMany(BsonDocument(), Document().append("\$set",
                    BsonDocument().append("new", BsonString("field"))))

            val insertEvent = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent.operationType)
            assertEquals(rawDoc2, insertEvent.fullDocument)
            val updateEvent1 = stream.nextEvent()
            val updateEvent2 = stream.nextEvent()

            assertNotNull(updateEvent1)
            assertNotNull(updateEvent2)

            assertEquals(OperationType.UPDATE, updateEvent1.operationType)
            assertEquals(rawDoc1.append("new", "field"), updateEvent1.fullDocument)
            assertEquals(OperationType.UPDATE, updateEvent2.operationType)
            assertEquals(rawDoc2.append("new", "field"), updateEvent2.fullDocument)
        } finally {
            stream.close()
        }
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
}
