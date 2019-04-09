package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoBulkWriteException
import com.mongodb.MongoWriteException
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncCountOptions
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonReader
import org.bson.BsonString
import org.bson.BsonWriter
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class CoreSyncUnitTests {
    companion object {
        data class CustomCodecConsideredHarmful(
            val consideredHarmful: Boolean,
            val author: String
        )

        class CustomCodecConsideredHarmfulCodec : Codec<CustomCodecConsideredHarmful> {
            override fun getEncoderClass(): Class<CustomCodecConsideredHarmful> {
                return CustomCodecConsideredHarmful::class.java
            }

            override fun encode(
                writer: BsonWriter?,
                value: CustomCodecConsideredHarmful?,
                encoderContext: EncoderContext?
            ) {
                if (value != null && writer != null) {
                    writer.writeStartDocument()
                    writer.writeName("consideredHarmful")
                    writer.writeBoolean(value.consideredHarmful)
                    writer.writeName("author")
                    writer.writeString(value.author)
                    writer.writeEndDocument()
                }
            }

            override fun decode(
                reader: BsonReader?,
                decoderContext: DecoderContext?
            ): CustomCodecConsideredHarmful? {
                return if (reader == null)
                    null
                else {
                    CustomCodecConsideredHarmful(reader.readBoolean(), reader.readString())
                }
            }
        }
    }
    private val harness = SyncUnitTestHarness()

    @After
    fun teardown() {
        harness.close()
        CoreRemoteClientFactory.close()
        ServerEmbeddedMongoClientFactory.getInstance().close()
    }

    @Test
    fun testSyncOne() {
        val ctx = harness.freshTestContext()
        val (coreSync, _) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)
        // assert that calling syncOne on coreSync proxies the appropriate call
        // to the data synchronizer. assert that the appropriate document is being synchronized
        coreSync.syncOne(ctx.testDocumentId)
        verify(ctx.dataSynchronizer, times(1)).syncDocumentsFromRemote(
            eq(ctx.namespace),
            eq(ctx.testDocumentId))
        assertEquals(1, ctx.dataSynchronizer.getSynchronizedDocuments(ctx.namespace).size)
        assertEquals(
            ctx.testDocumentId,
            ctx.dataSynchronizer.getSynchronizedDocuments(ctx.namespace).first().documentId)
    }

    @Test
    fun testSyncMany() {
        val ctx = harness.freshTestContext()
        val (coreSync, _) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        // assert that calling syncMany on coreSync proxies the appropriate call to the data
        // synchronizer for each document being sync'd
        coreSync.syncMany(ctx.testDocumentId, ctx.testDocumentId)
        verify(ctx.dataSynchronizer, times(1)).syncDocumentsFromRemote(
            eq(ctx.namespace),
            eq(ctx.testDocumentId),
            eq(ctx.testDocumentId))
    }

    @Test
    fun testCount() {
        val ctx = harness.freshTestContext()
        val (coreSync, _) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        val doc1 = BsonDocument("a", BsonString("b"))
        val doc2 = BsonDocument("c", BsonString("d"))

        coreSync.insertMany(listOf(doc1, doc2))

        assertEquals(2, coreSync.count())
        assertEquals(1, coreSync.count(BsonDocument("_id", doc1["_id"])))

        assertEquals(1, coreSync.count(BsonDocument(), SyncCountOptions().limit(1)))

        verify(ctx.dataSynchronizer, times(3)).count(eq(ctx.namespace), any(), any())
    }

    @Test
    fun testFindOne() {
        val ctx = harness.freshTestContext()
        val (coreSync, _) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        val doc1 = BsonDocument("hello", BsonString("world1"))
        val doc2 = BsonDocument("hello", BsonString("world2"))
        val doc3 = BsonDocument("hello", BsonString("world3"))

        // Test findOne() on empty collection with no filter and no options
        assertNull(coreSync.findOne())

        // Insert a document into the collection
        coreSync.insertOne(doc1)
        assertEquals(1, coreSync.count())

        // Test findOne() with no filter and no options
        assertEquals(doc1, SyncUnitTestHarness.withoutSyncVersion(coreSync.findOne()))

        // Test findOne() with filter and no options
        assertEquals(doc1, SyncUnitTestHarness.withoutSyncVersion(coreSync.findOne(doc1)))

        // Test findOne() with filter that does not match any documents and no options
        assertNull(coreSync.findOne(BsonDocument("hello", BsonString("worldDNE"))))

        // Insert 2 more documents into the collection
        coreSync.insertMany(listOf(doc2, doc3))
        assertEquals(3, coreSync.count())

        // test findOne() with projection and sort options
        val projection = BsonDocument("hello", BsonInt32(1))
        projection["_id"] = BsonInt32(0)
        var sort = BsonDocument("hello", BsonInt32(1))
        var options = RemoteFindOptions().limit(10).projection(projection).sort(sort)

        assertEquals(BsonString("world1"), coreSync.findOne(BsonDocument(), options)["hello"])

        sort = BsonDocument("hello", BsonInt32(-1))
        options = options.sort(sort)

        assertEquals(BsonString("world3"), coreSync.findOne(BsonDocument(), options)["hello"])
    }

    @Test
    fun testFind() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        var findIterable = coreSync.find()

        val filterDoc = BsonDocument("_id", ctx.testDocumentId)
        val sortDoc = BsonDocument("count", BsonInt32(-1))
        val projectionDoc = BsonDocument("count", BsonInt32(1))

        assertNull(findIterable.filter(filterDoc).first())
        assertNull(findIterable.sort(sortDoc).first())
        assertNull(findIterable.projection(projectionDoc).first())
        assertNull(findIterable.limit(10).first())

        ctx.insertTestDocument()

        findIterable = coreSync.find()

        val expectedRemoteFindOptions = RemoteFindOptions()
        val remoteFindCaptor = ArgumentCaptor.forClass(RemoteFindOptions::class.java)
        fun compareRemoteFindOptions(
            expectedRemoteFindOptions: RemoteFindOptions,
            actualRemoteFindOptions: RemoteFindOptions
        ) {
            assertEquals(expectedRemoteFindOptions.limit, actualRemoteFindOptions.limit)
            assertEquals(expectedRemoteFindOptions.sort, actualRemoteFindOptions.sort)
            assertEquals(expectedRemoteFindOptions.projection, actualRemoteFindOptions.projection)
        }

        assertEquals(
            ctx.testDocument,
            SyncUnitTestHarness.withoutSyncVersion(findIterable.filter(filterDoc).first()))
        verify(syncOperations, times(5)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

        expectedRemoteFindOptions.sort(sortDoc)
        assertEquals(
            ctx.testDocument,
            SyncUnitTestHarness.withoutSyncVersion(findIterable.sort(sortDoc).first()))
        verify(syncOperations, times(6)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

        expectedRemoteFindOptions.projection(projectionDoc)
        assertEquals(
            ctx.testDocument,
            SyncUnitTestHarness.withoutSyncVersion(findIterable.projection(projectionDoc).first()))
        verify(syncOperations, times(7)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

        expectedRemoteFindOptions.limit(10)
        assertEquals(
            ctx.testDocument,
            SyncUnitTestHarness.withoutSyncVersion(findIterable.limit(10).first()))
        verify(syncOperations, times(8)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)
    }

    @Test
    fun testAggregate() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        val doc1 = BsonDocument("a", BsonString("b")).append("c", BsonString("d"))
        val doc2 = BsonDocument("a", BsonString("b")).append("c", BsonString("d"))
        val doc3 = BsonDocument("a", BsonString("b")).append("c", BsonString("q"))
        val doc4 = BsonDocument("a", BsonString("b")).append("c", BsonString("d"))
        val doc5 = BsonDocument("e", BsonString("f")).append("g", BsonString("h"))

        coreSync.insertMany(listOf(doc1, doc2, doc3, doc4, doc5))

        val pipeline = listOf(
            BsonDocument(
                "\$match", BsonDocument("_id", BsonDocument("\$in", BsonArray(
                listOf(doc1["_id"], doc2["_id"], doc4["_id"])
            )))),
            BsonDocument(
                "\$project",
                BsonDocument("c", BsonInt32(0))
            ))

        val agg = coreSync.aggregate(pipeline).toList()

        assertEquals(3, agg.size)
        agg.forEach { assertFalse(it.containsKey("c")) }
        val ids = agg.map { it["_id"] }
        assertTrue(ids.contains(doc1["_id"]))
        assertTrue(ids.contains(doc2["_id"]))
        assertTrue(ids.contains(doc4["_id"]))

        verify(syncOperations, times(1)).aggregate(eq(pipeline), eq(BsonDocument::class.java))

        verify(ctx.dataSynchronizer, times(1)).aggregate(
            eq(ctx.namespace), eq(pipeline), eq(BsonDocument::class.java))
    }

    @Test
    fun testUpdateOne() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        var result = coreSync.updateOne(ctx.testDocumentFilter,
            ctx.updateDocument,
            SyncUpdateOptions().upsert(false))
        assertEquals(0, result.matchedCount)
        assertEquals(0, result.modifiedCount)
        assertNull(result.upsertedId)

        ctx.insertTestDocument()

        result = coreSync.updateOne(ctx.testDocumentFilter, ctx.updateDocument)

        assertEquals(1, result.matchedCount)
        assertEquals(1, result.modifiedCount)
        assertNull(result.upsertedId)

        verify(syncOperations, times(2)).updateOne(
            eq(ctx.testDocumentFilter), eq(ctx.updateDocument), any())

        verify(ctx.dataSynchronizer, times(2)).updateOne(
            eq(ctx.namespace), eq(ctx.testDocumentFilter), eq(ctx.updateDocument), any())
    }

    @Test
    fun testUpdateMany() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        val doc1 = BsonDocument("a", BsonString("b"))
        val doc2 = BsonDocument("c", BsonString("d"))
        val doc3 = BsonDocument("a", BsonString("r"))

        val insertResult = coreSync.insertMany(listOf(doc1, doc2, doc3))

        assertEquals(3, insertResult.insertedIds.size)

        val updateFilter = BsonDocument("a", BsonDocument("\$exists", BsonBoolean(true)))
        val updateDoc = BsonDocument("\$set", BsonDocument("a", BsonString("z")))
        val updateResult = coreSync.updateMany(updateFilter, updateDoc)

        assertEquals(2, updateResult.matchedCount)
        assertEquals(2, updateResult.modifiedCount)
        assertNull(updateResult.upsertedId)

        assertEquals(BsonDocument("a", BsonString("z")).append("_id", doc1["_id"]),
            coreSync.find(BsonDocument("_id", doc1["_id"])).first())
        assertEquals(BsonDocument("c", BsonString("d")).append("_id", doc2["_id"]),
            coreSync.find(BsonDocument("_id", doc2["_id"])).first())
        assertEquals(BsonDocument("a", BsonString("z")).append("_id", doc3["_id"]),
            coreSync.find(BsonDocument("_id", doc3["_id"])).first())

        verify(syncOperations, times(1)).updateMany(
            eq(updateFilter), eq(updateDoc), any())

        verify(ctx.dataSynchronizer, times(1)).updateMany(
            eq(ctx.namespace), eq(updateFilter), eq(updateDoc), any())
    }

    @Test
    fun testInsertOne() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        assertEquals(
            ctx.testDocumentId,
            coreSync.insertOne(ctx.testDocument).insertedId)

        try {
            coreSync.insertOne(ctx.testDocument)
            fail("should have received duplicate key error index")
        } catch (e: MongoWriteException) {
            assertTrue(e.message?.contains("E11000") ?: false)
            assertNotNull(e)
        }

        verify(syncOperations, times(2)).insertOne(
            eq(ctx.testDocument))

        verify(ctx.dataSynchronizer, times(2)).insertOne(
            eq(ctx.namespace), eq(ctx.testDocument))
    }

    @Test
    fun testInsertOneCustomCodec() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(
            ctx, CustomCodecConsideredHarmful::class.java, CustomCodecConsideredHarmfulCodec())

        val doc1 = CustomCodecConsideredHarmful(true, "Edsger Dijkstra")

        val result = coreSync.insertOne(doc1)

        val actualDoc1 = coreSync.find(Document(mapOf("consideredHarmful" to true)), BsonDocument::class.java).first()

        assertEquals(actualDoc1!!["_id"], result.insertedId)

        verify(syncOperations, times(1)).insertOne(
            eq(doc1))

        verify(ctx.dataSynchronizer, times(1)).insertOne(
            eq(ctx.namespace), eq(actualDoc1))
    }

    @Test
    fun testInsertMany() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        val doc1 = BsonDocument("a", BsonString("b"))
        val doc2 = BsonDocument("c", BsonString("d"))

        val result = coreSync.insertMany(listOf(doc1, doc2))

        assertEquals(doc1["_id"], result.insertedIds[0])
        assertEquals(doc2["_id"], result.insertedIds[1])

        try {
            coreSync.insertMany(listOf(doc1, doc2))
            fail("should have received duplicate key error index")
        } catch (e: MongoBulkWriteException) {
            assertNotNull(e.writeErrors[0])
            assertTrue(e.writeErrors[0].message.contains("E11000"))
        }

        verify(syncOperations, times(2)).insertMany(
            eq(listOf(doc1, doc2)))

        verify(ctx.dataSynchronizer, times(2)).insertMany(
            eq(ctx.namespace), eq(listOf(doc1, doc2)))
    }

    @Test
    fun testInsertManyCustomCodec() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(
            ctx, CustomCodecConsideredHarmful::class.java, CustomCodecConsideredHarmfulCodec())

        val doc1 = CustomCodecConsideredHarmful(true, "Edsger Dijkstra")
        val doc2 = CustomCodecConsideredHarmful(false, "Eric A. Meyer")

        val result = coreSync.insertMany(listOf(doc1, doc2))

        val actualDoc1 = coreSync.find(Document(mapOf("consideredHarmful" to true)), BsonDocument::class.java).first()
        val actualDoc2 = coreSync.find(Document(mapOf("consideredHarmful" to false)), BsonDocument::class.java).first()

        assertEquals(actualDoc1!!["_id"], result.insertedIds[0])
        assertEquals(actualDoc2!!["_id"], result.insertedIds[1])

        verify(syncOperations, times(1)).insertMany(
            eq(listOf(doc1, doc2)))

        verify(ctx.dataSynchronizer, times(1)).insertMany(
            eq(ctx.namespace), eq(listOf(actualDoc1, actualDoc2)))
    }

    @Test
    fun testDeleteOne() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        var deleteResult = coreSync.deleteOne(ctx.testDocumentFilter)

        assertEquals(0, deleteResult.deletedCount)

        ctx.insertTestDocument()

        deleteResult = coreSync.deleteOne(ctx.testDocumentFilter)

        assertEquals(1, deleteResult.deletedCount)

        verify(syncOperations, times(2)).deleteOne(
            eq(ctx.testDocumentFilter))

        verify(ctx.dataSynchronizer, times(2)).deleteOne(
            eq(ctx.namespace), eq(ctx.testDocumentFilter))
    }

    @Test
    fun testDeleteMany() {
        val ctx = harness.freshTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx, BsonDocument::class.java)

        val doc1 = BsonDocument("a", BsonString("b"))
        val doc2 = BsonDocument("c", BsonString("d"))
        val doc3 = BsonDocument("e", BsonString("f"))

        var deleteResult = coreSync.deleteMany(BsonDocument())
        assertEquals(0, deleteResult.deletedCount)

        val result = coreSync.insertMany(listOf(doc1, doc2, doc3))

        assertEquals(3, coreSync.count())
        deleteResult = coreSync.deleteMany(BsonDocument("_id", BsonDocument("\$in", BsonArray(result.insertedIds.map {
            it.value
        }))))

        assertEquals(3, deleteResult.deletedCount)

        verify(syncOperations, times(1)).deleteMany(
            eq(BsonDocument()))

        verify(ctx.dataSynchronizer, times(1)).deleteMany(
            eq(ctx.namespace), eq(BsonDocument()))
    }
}
