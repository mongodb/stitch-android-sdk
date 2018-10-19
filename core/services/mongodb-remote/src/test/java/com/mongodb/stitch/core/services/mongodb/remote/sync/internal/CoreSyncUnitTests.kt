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
    @Test
    fun testSyncOne() {
        withSyncHarness { harness ->
            // assert that calling syncOne on coreSync proxies the appropriate call
            // to the data synchronizer. assert that the appropriate document is being synchronized
            harness.coreSync.syncOne(harness.activeDoc["_id"])
            verify(harness.dataSynchronizer, times(1)).syncDocumentFromRemote(
                eq(harness.namespace),
                eq(harness.activeDoc["_id"]))
            assertEquals(1, harness.dataSynchronizer.getSynchronizedDocuments(harness.namespace).size)
            assertEquals(
                harness.activeDoc["_id"],
                harness.dataSynchronizer.getSynchronizedDocuments(harness.namespace).first().documentId)
        }
    }

    @Test
    fun testSyncMany() {
        withSyncHarness { harness ->
            // assert that calling syncMany on coreSync proxies the appropriate call to the data
            // synchronizer for each document being sync'd
            harness.coreSync.syncMany(harness.activeDoc["_id"], harness.activeDoc["_id"])
            verify(harness.dataSynchronizer, times(2)).syncDocumentFromRemote(
                eq(harness.namespace),
                eq(harness.activeDoc["_id"]))
        }
    }

    @Test
    fun testFind() {
        withSyncHarness { harness ->
            var findIterable = harness.coreSync.find()

            val filterDoc = BsonDocument("_id", harness.activeDoc["_id"])
            val sortDoc = BsonDocument("count", BsonInt32(-1))
            val projectionDoc = BsonDocument("count", BsonInt32(1))

            assertNull(findIterable.filter(filterDoc).first())
            assertNull(findIterable.sort(sortDoc).first())
            assertNull(findIterable.projection(projectionDoc).first())
            assertNull(findIterable.limit(10).first())

            harness.insertOne()

            findIterable = harness.coreSync.find()

            val expectedRemoteFindOptions =  RemoteFindOptions()
            val remoteFindCaptor = ArgumentCaptor.forClass(RemoteFindOptions::class.java)
            fun compareRemoteFindOptions(expectedRemoteFindOptions: RemoteFindOptions,
                                         actualRemoteFindOptions: RemoteFindOptions) {
                assertEquals(expectedRemoteFindOptions.limit, actualRemoteFindOptions.limit)
                assertEquals(expectedRemoteFindOptions.sort, actualRemoteFindOptions.sort)
                assertEquals(expectedRemoteFindOptions.projection, actualRemoteFindOptions.projection)
            }

            assertEquals(
                harness.activeDoc,
                SyncHarness.withoutVersionId(findIterable.filter(filterDoc).first()))
            verify(harness.syncOperations, times(5)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
            compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

            expectedRemoteFindOptions.sort(sortDoc)
            assertEquals(
                harness.activeDoc,
                SyncHarness.withoutVersionId(findIterable.sort(sortDoc).first()))
            verify(harness.syncOperations, times(6)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
            compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

            expectedRemoteFindOptions.projection(projectionDoc)
            assertEquals(
                harness.activeDoc,
                SyncHarness.withoutVersionId(findIterable.projection(projectionDoc).first()))
            verify(harness.syncOperations, times(7)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
            compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)

            expectedRemoteFindOptions.limit(10)
            assertEquals(
                harness.activeDoc,
                SyncHarness.withoutVersionId(findIterable.limit(10).first()))
            verify(harness.syncOperations, times(8)).findFirst(eq(filterDoc), eq(BsonDocument::class.java), remoteFindCaptor.capture())
            compareRemoteFindOptions(expectedRemoteFindOptions, remoteFindCaptor.value)
        }
    }

    @Test
    fun testFindOneById() {
        withSyncHarness { harness ->
            assertNull(harness.coreSync.findOneById(harness.activeDoc["_id"]))

            harness.insertOne()

            assertEquals(
                harness.activeDoc,
                SyncHarness.withoutVersionId(harness.coreSync.findOneById(harness.activeDoc["_id"])))

            verify(harness.syncOperations, times(2)).findOneById(
                eq(harness.activeDoc["_id"]), eq(BsonDocument::class.java))

            verify(harness.dataSynchronizer, times(2)).findOneById(
                eq(harness.namespace), eq(harness.activeDoc["_id"]), eq(BsonDocument::class.java), any()
            )
        }
    }

    @Test
    fun testUpdateOneById() {
        withSyncHarness { harness ->
            var result = harness.coreSync.updateOneById(harness.activeDoc["_id"], harness.updateDoc)
            assertEquals(0, result.matchedCount)
            assertEquals(0, result.modifiedCount)
            assertNull(result.upsertedId)

            harness.insertOne()

            result = harness.coreSync.updateOneById(harness.activeDoc["_id"], harness.updateDoc)

            assertEquals(1, result.matchedCount)
            assertEquals(1, result.modifiedCount)
            assertNull(result.upsertedId)

            verify(harness.syncOperations, times(2)).updateOneById(
                eq(harness.activeDoc["_id"]), eq(harness.updateDoc))

            verify(harness.dataSynchronizer, times(2)).updateOneById(
                eq(harness.namespace), eq(harness.activeDoc["_id"]), eq(harness.updateDoc))
        }
    }

    @Test
    fun testInsertOneAndSync() {
        withSyncHarness { harness ->
            assertEquals(
                harness.activeDoc["_id"],
                harness.coreSync.insertOneAndSync(harness.activeDoc).insertedId)

            try {
                harness.coreSync.insertOneAndSync(harness.activeDoc)
                fail("should have received duplicate key error index")
            } catch (e: MongoWriteException) {
                assertNotNull(e)
            }

            verify(harness.syncOperations, times(2)).insertOneAndSync(
                eq(harness.activeDoc))

            verify(harness.dataSynchronizer, times(2)).insertOneAndSync(
                eq(harness.namespace), eq(harness.activeDoc))
        }
    }

    @Test
    fun testDeleteOneById() {
        withSyncHarness { harness ->
            var deleteResult = harness.coreSync.deleteOneById(harness.activeDoc["_id"])

            assertEquals(0, deleteResult.deletedCount)

            harness.insertOne()

            deleteResult = harness.coreSync.deleteOneById(harness.activeDoc["_id"])

            assertEquals(1, deleteResult.deletedCount)

            verify(harness.syncOperations, times(2)).deleteOneById(
                eq(harness.activeDoc["_id"]))

            verify(harness.dataSynchronizer, times(2)).deleteOneById(
                eq(harness.namespace), eq(harness.activeDoc["_id"]))
        }
    }
}
