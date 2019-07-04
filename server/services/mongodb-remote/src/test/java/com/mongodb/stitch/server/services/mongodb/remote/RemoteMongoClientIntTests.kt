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
import com.mongodb.stitch.server.core.StitchAppClient
import com.mongodb.stitch.server.services.mongodb.remote.internal.RemoteMongoClientImpl
import com.mongodb.stitch.server.testutils.BaseStitchServerIntTest

import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOneAndModifyOptions
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.CompactChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.HashUtils
import com.mongodb.stitch.core.testutils.CustomType

import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import org.bson.codecs.configuration.CodecConfigurationException
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        coll.insertMany(listOf(doc2, doc3))
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
    fun testFindOneAndUpdate() {
        val coll = getTestColl()

        val sampleDoc = Document("hello", "world1")
        sampleDoc["num"] = 2

        // Collection should start out empty
        // This also tests the null return format
        assertNull(coll.findOneAndUpdate(Document(), Document()))

        // Insert a sample Document
        coll.insertOne(sampleDoc)
        assertEquals(1, coll.count())

        // Sample call to findOneAndUpdate() where we get the previous document back
        var sampleUpdate = Document("\$set", Document("hello", "hellothere"))
        sampleUpdate["\$inc"] = Document("num", 1)
        assertEquals(withoutId(sampleDoc), withoutId(
                coll.findOneAndUpdate(Document("hello", "world1"), sampleUpdate)))
        assertEquals(1, coll.count())

        // Make sure the update took place
        var expectedDoc = Document("hello", "hellothere")
        expectedDoc["num"] = 3
        // TODO: Put in findOne()
        // assertEquals(withoutId(doc1), withoutId(coll.find().firstOrNull()))
        assertEquals(1, coll.count())

        // Call findOneAndUpdate() again but get the new document
        sampleUpdate.remove("\$set")
        expectedDoc["num"] = 4
        assertEquals(withoutId(expectedDoc), withoutId(coll.findOneAndUpdate(
                Document("hello", "hellothere"),
                sampleUpdate,
                RemoteFindOneAndModifyOptions().returnNewDocument(true))
        ))
        assertEquals(1, coll.count())

        // Test null behaviour again with a filter that should not match any documents
        assertNull(coll.findOneAndUpdate(Document("hello", "zzzzz"), Document()))
        assertEquals(1, coll.count())

        val doc1 = Document("hello", "world1")
        doc1["num"] = 1

        val doc2 = Document("hello", "world2")
        doc2["num"] = 2

        val doc3 = Document("hello", "world3")
        doc3["num"] = 3

        // Test the upsert option where it should not actually be invoked
        assertEquals(doc1, withoutId(coll.findOneAndUpdate(
                Document("hello", "hellothere"),
                Document("\$set", doc1),
                RemoteFindOneAndModifyOptions()
                        .returnNewDocument(true)
                        .upsert(true)
        )))
        assertEquals(1, coll.count())
        // TODO: Put in findOne()
        // assertEquals(withoutId(doc1), withoutId(coll.find().firstOrNull()))

        // Test the upsert option where the server should perform upsert and return new document
        assertEquals(doc2, withoutId(coll.findOneAndUpdate(
                Document("hello", "hellothere"),
                Document("\$set", doc2),
                RemoteFindOneAndModifyOptions()
                        .returnNewDocument(true)
                        .upsert(true)
        )))
        assertEquals(2, coll.count())

        // Test the upsert option where the server should perform upsert and return old document
        // The old document should be empty
        assertNull(coll.findOneAndUpdate(
                Document("hello", "hellothere"),
                Document("\$set", doc3),
                RemoteFindOneAndModifyOptions()
                        .upsert(true)
        ))
        assertEquals(3, coll.count())

        // Test sort and project
        assertEquals(listOf(doc1, doc2, doc3),
                withoutIds(coll.find().into(mutableListOf())))

        val sampleProject = Document("hello", 1)
        sampleProject["_id"] = 0

        assertEquals(Document("hello", "world1"), withoutId(coll.findOneAndUpdate(
                Document(),
                sampleUpdate,
                RemoteFindOneAndModifyOptions()
                        .projection(sampleProject)
                        .sort(Document("num", 1))
        )))
        assertEquals(3, coll.count())

        assertEquals(Document("hello", "world3"), withoutId(coll.findOneAndUpdate(
                Document(),
                sampleUpdate,
                RemoteFindOneAndModifyOptions()
                        .projection(sampleProject)
                        .sort(Document("num", -1))
        )))
        assertEquals(3, coll.count())

        // Test proper failure
        try {
            coll.findOneAndUpdate(Document(), Document("\$who", 1))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }

        try {
            coll.findOneAndUpdate(
                    Document(),
                    Document("\$who", 1),
                    RemoteFindOneAndModifyOptions().upsert(true))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.MONGODB_ERROR, ex.errorCode)
        }
    }

    @Test
    fun testFindOneAndReplace() {
        val coll = getTestColl()

        val sampleDoc = Document("hello", "world1")
        sampleDoc["num"] = 2

        // Collection should start out empty
        // This also tests the null return format
        assertNull(coll.findOneAndReplace(Document(), Document()))

        // Insert a sample Document
        coll.insertOne(sampleDoc)
        assertEquals(1, coll.count())

        // Sample call to findOneAndReplace() where we get the previous document back
        var sampleUpdate = Document("hello", "world2")
        sampleUpdate["num"] = 2
        assertEquals(withoutId(sampleDoc), withoutId(
                coll.findOneAndReplace(Document("hello", "world1"), sampleUpdate)))
        assertEquals(1, coll.count())

        // Make sure the update took place
        var expectedDoc = Document("hello", "world2")
        expectedDoc["num"] = 2
        // TODO: Put in findOne()
        // assertEquals(withoutId(expectedDoc), withoutId(coll.find().first()))
        assertEquals(1, coll.count())

        // Call findOneAndReplace() again but get the new document
        sampleUpdate = Document("hello", "world3")
        sampleUpdate["num"] = 3
        assertEquals(withoutId(sampleUpdate), withoutId(coll.findOneAndReplace(
                Document(),
                sampleUpdate,
                RemoteFindOneAndModifyOptions().returnNewDocument(true))
        ))
        assertEquals(1, coll.count())

        // Test null behaviour again with a filter that should not match any documents
        assertNull(coll.findOneAndReplace(Document("hello", "zzzzz"), Document()))
        assertEquals(1, coll.count())

        val doc4 = Document("hello", "world4")
        doc4["num"] = 4

        val doc5 = Document("hello", "world5")
        doc5["num"] = 5

        val doc6 = Document("hello", "world6")
        doc6["num"] = 6

        // Test the upsert option where it should not actually be invoked
        sampleUpdate = Document("hello", "world4")
        sampleUpdate["num"] = 4
        assertEquals(withoutId(doc4), withoutId(coll.findOneAndReplace(
                Document("hello", "world3"),
                doc4,
                RemoteFindOneAndModifyOptions()
                        .returnNewDocument(true)
                        .upsert(true)
        )))
        assertEquals(1, coll.count())
        // TODO: Put in findOne()
        // assertEquals(withoutId(doc4), withoutId(coll.find().first()))

        // Test the upsert option where the server should perform upsert and return new document
        assertEquals(withoutId(doc5), withoutId(coll.findOneAndReplace(
                Document("hello", "hellothere"),
                doc5,
                RemoteFindOneAndModifyOptions()
                        .returnNewDocument(true)
                        .upsert(true)
        )))
        assertEquals(2, coll.count())

        // Test the upsert option where the server should perform upsert and return old document
        // The old document should be empty
        assertNull(coll.findOneAndReplace(
                Document("hello", "hellothere"),
                doc6,
                RemoteFindOneAndModifyOptions()
                        .upsert(true)
        ))
        assertEquals(3, coll.count())

        // Test sort and project
        assertEquals(listOf(doc4, doc5, doc6),
                withoutIds(coll.find().into(mutableListOf())))

        val sampleProject = Document("hello", 1)
        sampleProject["_id"] = 0

        sampleUpdate = Document("hello", "world0")
        sampleUpdate["num"] = 0

        assertEquals(Document("hello", "world4"), withoutId(coll.findOneAndReplace(
                Document(),
                sampleUpdate,
                RemoteFindOneAndModifyOptions()
                        .projection(sampleProject)
                        .sort(Document("num", 1))
        )))
        assertEquals(3, coll.count())

        assertEquals(Document("hello", "world6"), withoutId(coll.findOneAndReplace(
                Document(),
                sampleUpdate,
                RemoteFindOneAndModifyOptions()
                        .projection(sampleProject)
                        .sort(Document("num", -1))
        )))
        assertEquals(3, coll.count())

        // Test proper failure
        try {
            coll.findOneAndReplace(Document(), Document("\$who", 1))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, ex.errorCode)
        }

        try {
            coll.findOneAndReplace(Document(), Document("\$who", 1),
                    RemoteFindOneAndModifyOptions().upsert(true))
            fail()
        } catch (ex: StitchServiceException) {
            assertEquals(StitchServiceErrorCode.INVALID_PARAMETER, ex.errorCode)
        }
    }

    @Test
    fun testFindOneAndDelete() {
        val coll = getTestColl()

        val sampleDoc = Document("hello", "world1")
        sampleDoc["num"] = 1

        // Collection should start out empty
        // This also tests the null return format
        assertNull(coll.findOneAndDelete(Document()))

        // Insert a sample Document
        coll.insertOne(sampleDoc)
        assertEquals(1, coll.count())

        // Sample call to findOneAndDelete() where we delete the only doc in the collection
        assertEquals(withoutId(sampleDoc), withoutId(
                coll.findOneAndDelete(Document())))

        // There should be no documents in the collection now
        assertEquals(0, coll.count())

        // Insert a sample Document
        coll.insertOne(sampleDoc)
        assertEquals(1, coll.count())

        // Call findOneAndDelete() again but this time with a filter
        assertEquals(withoutId(sampleDoc), withoutId(coll.findOneAndDelete(
                Document("hello", "world1"))
        ))

        // There should be no documents in the collection now
        assertEquals(0, coll.count())

        // Insert a sample Document
        coll.insertOne(sampleDoc)
        assertEquals(1, coll.count())

        // Test null behaviour again with a filter that should not match any documents
        assertNull(coll.findOneAndDelete(Document("hello", "zzzzz")))
        assertEquals(1, coll.count())

        val doc2 = Document("hello", "world2")
        doc2["num"] = 2

        val doc3 = Document("hello", "world3")
        doc3["num"] = 3

        // Insert new documents
        coll.insertMany(listOf(doc2, doc3))
        assertEquals(3, coll.count())

        // Test sort and project
        assertEquals(withoutIds(listOf(sampleDoc, doc2, doc3)),
                withoutIds(coll.find().into(mutableListOf())))

        val sampleProject = Document("hello", 1)
        sampleProject["_id"] = 0

        assertEquals(Document("hello", "world3"), withoutId(coll.findOneAndDelete(
                Document(),
                RemoteFindOneAndModifyOptions()
                        .projection(sampleProject)
                        .sort(Document("num", -1))
        )))
        assertEquals(2, coll.count())

        assertEquals(Document("hello", "world1"), withoutId(coll.findOneAndDelete(
                Document(),
                RemoteFindOneAndModifyOptions()
                        .projection(sampleProject)
                        .sort(Document("num", 1))
        )))
        assertEquals(1, coll.count())
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
    fun testWatchFullCollection() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val rawDoc1 = Document()
        rawDoc1["_id"] = 1
        rawDoc1["hello"] = "world"

        val rawDoc2 = Document()
        rawDoc2["_id"] = "foo"
        rawDoc2["happy"] = "day"

        val stream = coll.watch()

        try {
            coll.insertOne(rawDoc1)
            assertEquals(1, coll.count())

            coll.insertOne(rawDoc2)
            assertEquals(2, coll.count())
            coll.updateMany(BsonDocument(), Document().append("\$set",
                Document().append("new", "field")))

            val insertEvent1 = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent1.operationType)
            assertEquals(rawDoc1, insertEvent1.fullDocument)
            val insertEvent2 = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent2.operationType)
            assertEquals(rawDoc2, insertEvent2.fullDocument)
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
    fun testWatchWithBsonDocumentFilter() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val rawDoc1 = Document()
        rawDoc1["_id"] = 1
        rawDoc1["hello"] = "world"

        val rawDoc2 = Document()
        rawDoc2["_id"] = "foo"
        rawDoc2["happy"] = "day"

        val stream = coll.watchWithFilter(
            BsonDocument("fullDocument.happy", BsonString("day"))
        )

        try {
            coll.insertOne(rawDoc1)
            assertEquals(1, coll.count())

            coll.insertOne(rawDoc2)
            assertEquals(2, coll.count())

            coll.updateMany(BsonDocument(), Document().append("\$set",
                Document().append("new", "field")))

            // should be filtered
            // val insertEvent1 = stream.nextEvent()

            val insertEvent2 = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent2.operationType)
            assertEquals(rawDoc2, insertEvent2.fullDocument)

            // should be filtered
            // val updateEvent1 = stream.nextEvent()

            val updateEvent2 = stream.nextEvent()

            assertNotNull(updateEvent2)

            assertEquals(OperationType.UPDATE, updateEvent2.operationType)
            assertEquals(rawDoc2.append("new", "field"), updateEvent2.fullDocument)
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchWithDocumentFilter() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val rawDoc1 = Document()
        rawDoc1["_id"] = 1
        rawDoc1["hello"] = "world"

        val rawDoc2 = Document()
        rawDoc2["_id"] = "foo"
        rawDoc2["happy"] = "day"

        val stream = coll.watchWithFilter(Document("fullDocument.happy", "day"))

        try {
            coll.insertOne(rawDoc1)
            assertEquals(1, coll.count())

            coll.insertOne(rawDoc2)
            assertEquals(2, coll.count())

            coll.updateMany(BsonDocument(), Document().append("\$set",
                Document().append("new", "field")))

            // should be filtered
            // val insertEvent1 = stream.nextEvent()

            val insertEvent2 = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent2.operationType)
            assertEquals(rawDoc2, insertEvent2.fullDocument)

            // should be filtered
            // val updateEvent1 = stream.nextEvent()

            val updateEvent2 = stream.nextEvent()

            assertNotNull(updateEvent2)

            assertEquals(OperationType.UPDATE, updateEvent2.operationType)
            assertEquals(rawDoc2.append("new", "field"), updateEvent2.fullDocument)
        } finally {
            stream.close()
        }
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

    @Test
    fun testWatchCompactBsonValueIDs() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val rawDoc1 = Document()
        rawDoc1["_id"] = 1
        rawDoc1["hello"] = "world"
        rawDoc1["__stitch_sync_version"] = Document()
            .append("spv", 1)
            .append("id", ObjectId().toHexString())
            .append("v", 5L)

        val rawDoc2 = Document()
        rawDoc2["_id"] = "foo"
        rawDoc2["happy"] = "day"

        coll.insertOne(rawDoc1)
        assertEquals(1, coll.count())

        val stream = coll.watchCompact(BsonInt32(1), BsonString("foo"))

        try {
            coll.insertOne(rawDoc2)
            assertEquals(2, coll.count())
            coll.updateMany(BsonDocument(), Document().append("\$set",
                Document().append("new", "field")))

            val insertEvent = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent.operationType)
            assertEquals(rawDoc2, insertEvent.fullDocument)
            val updateEvent1 = stream.nextEvent()
            val updateEvent2 = stream.nextEvent()

            assertNotNull(updateEvent1)
            assertNotNull(updateEvent2)

            assertEquals(OperationType.UPDATE, updateEvent1.operationType)
            assertEquals(
                updateEvent1.updateDescription!!.updatedFields["new"],
                BsonString("field")
            )
            assertNull(updateEvent1.fullDocument)
            assertEquals(
                updateEvent1.stitchDocumentVersion!!.toBsonDocument(),
                rawDoc1.toBsonDocument(Document::class.java, BsonUtils.DEFAULT_CODEC_REGISTRY)
                    .getDocument("__stitch_sync_version")
            )

            rawDoc1.remove("__stitch_sync_version")
            rawDoc1.append("new", "field")
            assertEquals(
                updateEvent1.stitchDocumentHash,
                HashUtils.hash(rawDoc1.toBsonDocument(
                    Document::class.java,
                    BsonUtils.DEFAULT_CODEC_REGISTRY
                ))
            )

            assertEquals(OperationType.UPDATE, updateEvent2.operationType)
            assertEquals(
                updateEvent1.updateDescription!!.updatedFields["new"],
                BsonString("field")
            )
            assertNull(updateEvent2.stitchDocumentVersion)

            rawDoc2.append("new", "field")
            assertEquals(
                updateEvent2.stitchDocumentHash,
                HashUtils.hash(rawDoc2.toBsonDocument(
                    Document::class.java,
                    BsonUtils.DEFAULT_CODEC_REGISTRY
                ))
            )
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchCompactObjectIdIDs() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val objectId1 = ObjectId()
        val objectId2 = ObjectId()

        val rawDoc1 = Document()
        rawDoc1["_id"] = objectId1
        rawDoc1["hello"] = "world"
        rawDoc1["__stitch_sync_version"] = Document()
            .append("spv", 1)
            .append("id", ObjectId().toHexString())
            .append("v", 5L)

        val rawDoc2 = Document()
        rawDoc2["_id"] = objectId2
        rawDoc2["happy"] = "day"

        coll.insertOne(rawDoc1)
        assertEquals(1, coll.count())

        val streamTask = coll.watchCompact(objectId1, objectId2)
        val stream = streamTask

        try {
            coll.insertOne(rawDoc2)
            assertEquals(2, coll.count())
            coll.updateMany(BsonDocument(), Document().append("\$set",
                Document().append("new", "field")))

            val insertEvent = stream.nextEvent()
            assertEquals(OperationType.INSERT, insertEvent.operationType)
            assertEquals(rawDoc2, insertEvent.fullDocument)
            val updateEvent1 = stream.nextEvent()
            val updateEvent2 = stream.nextEvent()

            assertNotNull(updateEvent1)
            assertNotNull(updateEvent2)

            assertEquals(OperationType.UPDATE, updateEvent1.operationType)
            assertEquals(
                updateEvent1.updateDescription!!.updatedFields["new"],
                BsonString("field")
            )
            assertNull(updateEvent1.fullDocument)
            assertEquals(
                updateEvent1.stitchDocumentVersion!!.toBsonDocument(),
                rawDoc1.toBsonDocument(Document::class.java, BsonUtils.DEFAULT_CODEC_REGISTRY)
                    .getDocument("__stitch_sync_version")
            )

            rawDoc1.remove("__stitch_sync_version")
            rawDoc1.append("new", "field")
            assertEquals(
                updateEvent1.stitchDocumentHash,
                HashUtils.hash(rawDoc1.toBsonDocument(
                    Document::class.java, BsonUtils.DEFAULT_CODEC_REGISTRY
                ))
            )

            assertEquals(OperationType.UPDATE, updateEvent2.operationType)
            assertEquals(
                updateEvent1.updateDescription!!.updatedFields["new"],
                BsonString("field")
            )
            assertNull(updateEvent2.stitchDocumentVersion)

            rawDoc2.append("new", "field")
            assertEquals(
                updateEvent2.stitchDocumentHash,
                HashUtils.hash(rawDoc2.toBsonDocument(
                    Document::class.java, BsonUtils.DEFAULT_CODEC_REGISTRY
                ))
            )
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchListenerLambda() {
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

        coll.insertMany(listOf(rawDoc1, rawDoc2))
        assertEquals(2, coll.count())

        val stream = coll.watch(objectId1, objectId2)

        val latch = CountDownLatch(4)
        stream.addChangeEventListener { _, event ->
            assertNotNull(event.fullDocument)
            latch.countDown()
        }
        stream.addChangeEventListener { documentId, event ->
            assertNotNull(event.fullDocument)
            latch.countDown()
        }

        try {
            coll.updateMany(BsonDocument(),
                Document().append("\$set", Document().append("new", "field")))
            assert(latch.await(10, TimeUnit.SECONDS))
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchCompactListenerLambda() {
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

        coll.insertMany(listOf(rawDoc1, rawDoc2))
        assertEquals(2, coll.count())

        val stream = coll.watchCompact(objectId1, objectId2)

        val latch = CountDownLatch(4)
        stream.addChangeEventListener { _, event ->
            assertNull(event.fullDocument)
            latch.countDown()
        }
        stream.addChangeEventListener { _, event ->
            assertNull(event.fullDocument)
            latch.countDown()
        }

        try {
            coll.updateMany(BsonDocument(),
                Document().append("\$set", Document().append("new", "field")))
            assert(latch.await(10, TimeUnit.SECONDS))
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchListener() {
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

        coll.insertMany(listOf(rawDoc1, rawDoc2))
        assertEquals(2, coll.count())

        val stream = coll.watch(objectId1, objectId2)

        val latch1 = CountDownLatch(4)
        val listener1 = ChangeEventListener<Document> { _, event ->
            assertNotNull(event.fullDocument)
            latch1.countDown()
        }

        val latch2 = CountDownLatch(2)
        val listener2 = ChangeEventListener<Document> { _, event ->
            assertNotNull(event.fullDocument)
            latch2.countDown()
        }

        stream.addChangeEventListener(listener1)
        stream.addChangeEventListener(listener2)
        stream.addChangeEventListener(listener1)

        try {
            coll.updateMany(BsonDocument(),
                Document().append("\$set", Document().append("new", "field")))
            assert(latch2.await(10, TimeUnit.SECONDS))
            assertEquals(latch1.count.toInt(), 2)
            stream.removeChangeEventListener(listener2)

            coll.updateMany(BsonDocument(),
                Document().append("\$set", Document().append("new", "field2")))
            assert(latch1.await(10, TimeUnit.SECONDS))
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchCompactListener() {
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

        coll.insertMany(listOf(rawDoc1, rawDoc2))
        assertEquals(2, coll.count())

        val stream = coll.watchCompact(objectId1, objectId2)

        val latch1 = CountDownLatch(4)
        val listener1 = CompactChangeEventListener<Document> { _, event ->
            assertNull(event.fullDocument)
            latch1.countDown()
        }

        val latch2 = CountDownLatch(2)
        val listener2 = CompactChangeEventListener<Document> { _, event ->
            assertNull(event.fullDocument)
            latch2.countDown()
        }

        stream.addChangeEventListener(listener1)
        stream.addChangeEventListener(listener2)
        stream.addChangeEventListener(listener1)

        try {
            coll.updateMany(BsonDocument(),
                Document().append("\$set", Document().append("new", "field")))
            assert(latch2.await(10, TimeUnit.SECONDS))
            assertEquals(latch1.count.toInt(), 2)
            stream.removeChangeEventListener(listener2)

            coll.updateMany(BsonDocument(),
                Document().append("\$set", Document().append("new", "field2")))
            assert(latch1.await(10, TimeUnit.SECONDS))
        } finally {
            stream.close()
        }
    }

    @Test
    fun testWatchStreamClose() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val objectId1 = ObjectId()
        val rawDoc1 = Document()
        rawDoc1["_id"] = objectId1
        rawDoc1["hello"] = "world"

        coll.insertOne(rawDoc1)
        assertEquals(1, coll.count())

        val stream = coll.watch(objectId1)

        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(2)
        val listener1 = ChangeEventListener<Document> { _, event ->
            assertNotNull(event.fullDocument)
            latch1.countDown()
            latch2.countDown()
        }
        stream.addChangeEventListener(listener1)

        try {
            coll.updateMany(BsonDocument(), Document().append("\$set",
                Document().append("new", "field")))
            assert(latch1.await(10, TimeUnit.SECONDS))
        } finally {
            stream.close()
        }
        coll.updateMany(BsonDocument(), Document().append("\$set",
            Document().append("new", "field2")))
        assertFalse(latch2.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun testNextEventFailsWithWatch() {
        val coll = getTestColl()
        assertEquals(0, coll.count())

        val objectId1 = ObjectId()
        val rawDoc1 = Document()
        rawDoc1["_id"] = objectId1
        rawDoc1["hello"] = "world"

        coll.insertOne(rawDoc1)
        assertEquals(1, coll.count())

        val stream = coll.watch(objectId1)

        val listener1 = ChangeEventListener<Document> { _, _ ->
            fail()
        }
        stream.addChangeEventListener(listener1)

        try {
            stream.nextEvent()
        } catch (_: IllegalStateException) {
            return
        } catch (_: Exception) {
            fail()
        } finally {
            stream.close()
        }
        fail()
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
