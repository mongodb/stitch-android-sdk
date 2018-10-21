package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoWriteException
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class CoreSyncUnitTests {
    private val harness = SyncHarness()

    @Test
    fun testSyncOne() {
        val ctx = harness.newTestContext()
        val (coreSync, _) = harness.createCoreSyncWithContext(ctx)
        // assert that calling syncOne on coreSync proxies the appropriate call
        // to the data synchronizer. assert that the appropriate document is being synchronized
        coreSync.syncOne(ctx.testDocumentId)
        verify(ctx.dataSynchronizer, times(1)).syncDocumentFromRemote(
            eq(ctx.namespace),
            eq(ctx.testDocumentId))
        assertEquals(1, ctx.dataSynchronizer.getSynchronizedDocuments(ctx.namespace).size)
        assertEquals(
            ctx.testDocumentId,
            ctx.dataSynchronizer.getSynchronizedDocuments(ctx.namespace).first().documentId)
    }

    @Test
    fun testSyncMany() {
        val ctx = harness.newTestContext()
        val (coreSync, _) = harness.createCoreSyncWithContext(ctx)

        // assert that calling syncMany on coreSync proxies the appropriate call to the data
        // synchronizer for each document being sync'd
        coreSync.syncMany(ctx.testDocumentId, ctx.testDocumentId)
        verify(ctx.dataSynchronizer, times(2)).syncDocumentFromRemote(
            eq(ctx.namespace),
            eq(ctx.testDocumentId))
    }

    @Test
    fun testFind() {
        val ctx = harness.newTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx)

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

        val expectedRemoteFindOptions =  RemoteFindOptions()
        val remoteFindCaptor = ArgumentCaptor.forClass(RemoteFindOptions::class.java)
        fun compareRemoteFindOptions(expectedRemoteFindOptions: RemoteFindOptions,
                                     actualRemoteFindOptions: RemoteFindOptions) {
            assertEquals(expectedRemoteFindOptions.limit, actualRemoteFindOptions.limit)
            assertEquals(expectedRemoteFindOptions.sort, actualRemoteFindOptions.sort)
            assertEquals(expectedRemoteFindOptions.projection, actualRemoteFindOptions.projection)
        }

        assertEquals(
            ctx.testDocument,
            SyncHarness.withoutVersionId(findIterable.filter(filterDoc).first()))
        verify(syncOperations, times(5)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

        expectedRemoteFindOptions.sort(sortDoc)
        assertEquals(
            ctx.testDocument,
            SyncHarness.withoutVersionId(findIterable.sort(sortDoc).first()))
        verify(syncOperations, times(6)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

        expectedRemoteFindOptions.projection(projectionDoc)
        assertEquals(
            ctx.testDocument,
            SyncHarness.withoutVersionId(findIterable.projection(projectionDoc).first()))
        verify(syncOperations, times(7)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

        expectedRemoteFindOptions.limit(10)
        assertEquals(
            ctx.testDocument,
            SyncHarness.withoutVersionId(findIterable.limit(10).first()))
        verify(syncOperations, times(8)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
        compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)
    }

    @Test
    fun testFindOneById() {
        val ctx = harness.newTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx)

        assertNull(coreSync.findOneById(ctx.testDocumentId))

        ctx.insertTestDocument()

        assertEquals(
            ctx.testDocument,
            SyncHarness.withoutVersionId(coreSync.findOneById(ctx.testDocumentId)))

        verify(syncOperations, times(2)).findOneById(
            eq(ctx.testDocumentId), eq(BsonDocument::class.java))

        verify(ctx.dataSynchronizer, times(2)).findOneById(
            eq(ctx.namespace), eq(ctx.testDocumentId), eq(BsonDocument::class.java), any()
        )
    }

    @Test
    fun testUpdateOneById() {
        val ctx = harness.newTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx)

        var result = coreSync.updateOneById(ctx.testDocumentId, ctx.updateDocument)
        assertEquals(0, result.matchedCount)
        assertEquals(0, result.modifiedCount)
        assertNull(result.upsertedId)

        ctx.insertTestDocument()

        result = coreSync.updateOneById(ctx.testDocumentId, ctx.updateDocument)

        assertEquals(1, result.matchedCount)
        assertEquals(1, result.modifiedCount)
        assertNull(result.upsertedId)

        verify(syncOperations, times(2)).updateOneById(
            eq(ctx.testDocumentId), eq(ctx.updateDocument))

        verify(ctx.dataSynchronizer, times(2)).updateOneById(
            eq(ctx.namespace), eq(ctx.testDocumentId), eq(ctx.updateDocument))
    }

    @Test
    fun testInsertOneAndSync() {
        val ctx = harness.newTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx)

        assertEquals(
            ctx.testDocumentId,
            coreSync.insertOneAndSync(ctx.testDocument).insertedId)

        try {
            coreSync.insertOneAndSync(ctx.testDocument)
            fail("should have received duplicate key error index")
        } catch (e: MongoWriteException) {
            assertNotNull(e)
        }

        verify(syncOperations, times(2)).insertOneAndSync(
            eq(ctx.testDocument))

        verify(ctx.dataSynchronizer, times(2)).insertOneAndSync(
            eq(ctx.namespace), eq(ctx.testDocument))
    }

    @Test
    fun testDeleteOneById() {
        val ctx = harness.newTestContext()
        val (coreSync, syncOperations) = harness.createCoreSyncWithContext(ctx)

        var deleteResult = coreSync.deleteOneById(ctx.testDocumentId)

        assertEquals(0, deleteResult.deletedCount)

        ctx.insertTestDocument()

        deleteResult = coreSync.deleteOneById(ctx.testDocumentId)

        assertEquals(1, deleteResult.deletedCount)

        verify(syncOperations, times(2)).deleteOneById(
            eq(ctx.testDocumentId))

        verify(ctx.dataSynchronizer, times(2)).deleteOneById(
            eq(ctx.namespace), eq(ctx.testDocumentId))
    }
}
