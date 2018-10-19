package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncHarness.Companion.newDoc
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncHarness.Companion.withoutId
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncHarness.Companion.withoutVersionId
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.Document

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.lang.Exception
import java.util.HashSet

class DataSynchronizerUnitTests {
    companion object {
        @AfterClass
        fun teardown() {
            CoreRemoteClientFactory.close()
            ServerEmbeddedMongoClientFactory.getInstance().close()
        }
    }

    @Test
    fun testNew() {
        withSyncHarness(withConfiguration = false) { harness ->
            assertFalse(harness.isRunning())
        }
    }

    @Test
    fun testOnNetworkStateChanged() {
        withSyncHarness(withConfiguration = false) { harness ->
            // verify that, since we are online, the dataSync has started
            harness.onNetworkStateChanged()
            harness.verifyStartCalled(1)
            harness.verifyStopCalled(0)

            // verify that, since we are offline, start has not been called again
            harness.isOnline = false
            harness.onNetworkStateChanged()
            harness.verifyStartCalled(1)
            harness.verifyStopCalled(1)
        }
    }

    @Test
    fun testStart() {
        withSyncHarness(withConfiguration = false) { harness ->
            // without a configuration, we should not be running
            harness.start()
            assertFalse(harness.isRunning())
            // with a configuration, we should be running
            harness.reconfigure()
            assertTrue(harness.isRunning())
        }
    }

    @Test
    fun testStop() {
        withSyncHarness(withConfiguration = false) { harness ->
            // assert stop stops the runner
            harness.reconfigure()
            harness.start()
            assertTrue(harness.isRunning())
            harness.stop()
            assertFalse(harness.isRunning())
        }
    }

    @Test
    fun testSuccessfulInsert() {
        withSyncHarness { harness: SyncHarness ->
            // setup our expectations
            val expectedEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false)

            // insert the doc, wait, sync, and assert that the change events are emitted
            harness.insertOneAndWait()
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.doSyncPass(expectedEvent)
            harness.verifyChangeEventListenerCalledForActiveDoc()

            // verify the appropriate doc was inserted and
            // the conflict and error handlers not called
            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
            verify(harness.collectionMock, times(1)).insertOne(docCaptor.capture())
            assertEquals(expectedEvent.fullDocument, withoutVersionId(docCaptor.value))
            harness.verifyConflictHandlerCalledForActiveDoc(0)
            harness.verifyErrorListenerCalledForActiveDoc(0)
            assertEquals(
                harness.activeDoc,
                harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testConflictedInsert() {
        withSyncHarness { harness ->
            // setup our expectations
            val expectedLocalEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, true)
            val expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(harness.namespace, harness.activeDocId, false)
            `when`(harness.collectionMock.insertOne(any())).thenThrow(
                StitchServiceException("E11000", StitchServiceErrorCode.MONGODB_ERROR)
            )

            // 1: Insert -> Conflict -> Delete (remote wins)
            // insert the expected doc, waiting for the change event
            // assert we inserted it properly
            harness.insertOneAndWait(
                expectedChangeEvent = expectedLocalEvent,
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)
            assertEquals(
                harness.activeDoc,
                harness.findActiveDocFromLocal())

            // sync and assert that the conflict handler was called,
            // accepting the remote delete, nullifying the document
            harness.doSyncPass(expectedRemoteEvent)
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(1)
            harness.verifyErrorListenerCalledForActiveDoc(0)
            assertNull(harness.findActiveDocFromLocal())

            // 1: Insert -> Conflict -> Insert (local wins)
            // reset
            harness.deleteOne()
            harness.insertOneAndWait(
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)

            // accept the local event this time, which will insert the local doc.
            // assert that the local doc has been inserted
            harness.conflictHandler.acceptRemote = false
            harness.doSyncPass(expectedRemoteEvent)
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(1)
            harness.verifyErrorListenerCalledForActiveDoc(0)
            assertEquals(harness.activeDoc, harness.findActiveDocFromLocal())

            // 3: Insert -> Conflict -> Exception -> Freeze
            // reset
            harness.deleteOne()
            harness.insertOneAndWait(
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)

            // prepare an exception to be thrown, and sync
            harness.conflictHandler.exception = Exception("bad")
            harness.shouldExpectError = true
            harness.doSyncPass()

            // verify that, though the conflict handler was called, the exception was emitted
            // by the dataSynchronizer
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(1)
            harness.verifyErrorListenerCalledForActiveDoc(1, harness.conflictHandler.exception!!)

            // assert that the local doc is the same. this is frozen now
            assertEquals(harness.activeDoc, harness.findActiveDocFromLocal())

            harness.conflictHandler.exception = null
            harness.conflictHandler.acceptRemote = true
            harness.shouldExpectError = false
            harness.doSyncPass()
            assertEquals(harness.activeDoc, harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testFailedInsert() {
        withSyncHarness { harness ->
            val expectedLocalEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, true)
            val expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(harness.namespace, harness.activeDocId, false)

            // force an error
            val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
            `when`(harness.collectionMock.insertOne(any())).thenThrow(expectedException)

            harness.insertOneAndWait(
                expectedChangeEvent = expectedLocalEvent,
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)

            harness.shouldExpectError = true

            harness.doSyncPass()

            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(0)
            harness.verifyErrorListenerCalledForActiveDoc(1, expectedException)

            assertEquals(harness.activeDoc, harness.findActiveDocFromLocal())

            `when`(harness.dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(harness.activeDoc to ChangeEvent.changeEventForLocalDelete(harness.namespace, harness.activeDocId, true)))
            harness.doSyncPass()

            harness.doSyncPass()

            assertEquals(harness.activeDoc, harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testSuccessfulUpdate() {
        withSyncHarness { harness: SyncHarness ->
            // setup our expectations
            val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", harness.activeDoc["_id"])
            val expectedEvent = ChangeEvent.changeEventForLocalUpdate(
                harness.namespace,
                harness.activeDoc["_id"],
                harness.updateDoc,
                docAfterUpdate,
                false
            )

            // insert, sync the doc, update, and verify that the change event was emitted
            harness.insertOneAndWait()
            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false))
            harness.updateOneAndWait(expectedChangeEvent = expectedEvent)
            harness.verifyChangeEventListenerCalledForActiveDoc()

            // mock a successful update, sync the update. verify that the update
            // was of the correct doc, and that no conflicts or errors occured
            `when`(harness.collectionMock.updateOne(any(), any())).thenReturn(RemoteUpdateResult(1, 1, null))
            harness.doSyncPass(expectedEvent)
            harness.verifyChangeEventListenerCalledForActiveDoc()
            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
            verify(harness.collectionMock, times(1)).updateOne(any(), docCaptor.capture())
            assertEquals(expectedEvent.fullDocument, withoutVersionId(docCaptor.value))
            harness.verifyConflictHandlerCalledForActiveDoc(0)
            harness.verifyErrorListenerCalledForActiveDoc(0)

            // verify the doc update was maintained locally
            assertEquals(
                docAfterUpdate,
                harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testConflictedUpdate() {
        withSyncHarness { harness ->
            // setup our expectations
            var docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", harness.activeDoc["_id"])
            var expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
                harness.namespace,
                harness.activeDoc["_id"],
                harness.updateDoc,
                docAfterUpdate,
                true)
            val expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(harness.namespace, harness.activeDocId, false)

            // 1: Update -> Conflict -> Delete (remote wins)
            // insert a new document, and sync it, waiting for the local insert change event
            harness.insertOneAndWait()
            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false))

            // update the document and wait for the local update event
            harness.updateOneAndWait(
                expectedChangeEvent = expectedLocalEvent,
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)

            // create conflict here by claiming there is no remote doc to update
            `when`(harness.collectionMock.updateOne(any(), any())).thenReturn(RemoteUpdateResult(0, 0, null))

            // do a sync pass, addressing the conflict
            harness.doSyncPass(expectedLocalEvent)

            // verify that a change event has been emitted, a conflict has been handled,
            // and no errors were emitted
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(1)
            harness.verifyErrorListenerCalledForActiveDoc(0)

            // since we've accepted the remote result, this doc will have been deleted
            assertNull(harness.findActiveDocFromLocal())

            // 2: Update -> Conflict -> Update (local wins)
            // reset (delete, insert, sync)
            harness.deleteOne()
            harness.insertOneAndWait()
            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false))

            // update the document and wait for the local update event
            harness.updateOneAndWait(
                expectedChangeEvent = expectedLocalEvent,
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)

            // do a sync pass, addressing the conflict. let local win
            harness.conflictHandler.acceptRemote = false
            harness.doSyncPass(expectedLocalEvent)

            // verify that a change event has been emitted, a conflict has been handled,
            // and no errors were emitted
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(1)
            harness.verifyErrorListenerCalledForActiveDoc(0)

            // since we've accepted the local result, this doc will have been updated remotely
            // and sync'd locally
            assertEquals(
                docAfterUpdate,
                harness.findActiveDocFromLocal())

            // 3: Update -> Conflict -> Exception -> Freeze
            // reset (delete, insert, sync)
            harness.deleteOne()
            harness.insertOneAndWait()
            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false))

            // update the reset doc
            harness.updateOneAndWait(
                expectedChangeEvent = expectedLocalEvent,
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)

            // prepare an exception to be thrown, and sync
            harness.conflictHandler.exception = Exception("bad")
            harness.shouldExpectError = true
            harness.doSyncPass()

            // verify that, though the conflict handler was called, the exception was emitted
            // by the dataSynchronizer
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(1)
            harness.verifyErrorListenerCalledForActiveDoc(1, harness.conflictHandler.exception)

            // assert that this document is still the locally updated doc. this is frozen now
            assertEquals(docAfterUpdate, harness.findActiveDocFromLocal())

            // clear issues. open a path for a delete.
            // do another sync pass. the doc should remain the same as it is frozen
            harness.conflictHandler.exception = null
            harness.conflictHandler.acceptRemote = false
            harness.shouldExpectError = false
            harness.doSyncPass()
            assertEquals(docAfterUpdate, harness.findActiveDocFromLocal())

            // update the doc locally, unfreezing it, and syncing it
            docAfterUpdate = BsonDocument("count", BsonInt32(3)).append("_id", harness.activeDoc["_id"])
            expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
                harness.namespace, harness.activeDocId, harness.updateDoc, docAfterUpdate, true)
            `when`(harness.collectionMock.updateOne(any(), any())).thenReturn(RemoteUpdateResult(1, 1, null))
            assertEquals(1L, harness.updateOneAndWait(expectedLocalEvent, expectedLocalEvent).result?.matchedCount)
            harness.doSyncPass()

            // 4: Unknown -> Freeze
            `when`(harness.dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(harness.activeDoc to ChangeEvent(
                    BsonDocument("_id", harness.activeDocId),
                    ChangeEvent.OperationType.UNKNOWN,
                    docAfterUpdate,
                    harness.namespace,
                    BsonDocument("_id", harness.activeDocId),
                    null,
                    true)))
            harness.doSyncPass()
            assertEquals(docAfterUpdate, harness.findActiveDocFromLocal())

            // should be frozen since the operation type was unknown
            `when`(harness.dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(harness.activeDoc to
                    ChangeEvent.changeEventForLocalUpdate(harness.namespace, harness.activeDocId, harness.updateDoc, docAfterUpdate, false)))

            harness.doSyncPass()
            assertEquals(docAfterUpdate, harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testFailedUpdate() {
        withSyncHarness { harness ->
            val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", harness.activeDoc["_id"])

            val expectedEvent = ChangeEvent.changeEventForLocalUpdate(
                harness.namespace,
                harness.activeDoc["_id"],
                harness.updateDoc,
                docAfterUpdate,
                false
            )

            harness.insertOneAndWait()
            harness.doSyncPass()

            harness.updateOneAndWait(expectedChangeEvent = expectedEvent)

            val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
            `when`(harness.collectionMock.updateOne(any(), any())).thenAnswer {
                throw expectedException
            }

            harness.shouldExpectError = true
            harness.doSyncPass()

            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
            verify(harness.collectionMock, times(1)).updateOne(any(), docCaptor.capture())
            assertEquals(expectedEvent.fullDocument, withoutVersionId(docCaptor.value))

            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(0)
            harness.verifyErrorListenerCalledForActiveDoc(1, expectedException)

            assertEquals(
                docAfterUpdate,
                harness.findActiveDocFromLocal())

            assertTrue(harness.getSynchronizedDocuments().all { it.isFrozen })
        }
    }

    @Test
    fun testSuccessfulDelete() {
        withSyncHarness { harness ->
            val expectedEvent = ChangeEvent.changeEventForLocalDelete(
                harness.namespace,
                harness.activeDoc["_id"],
                false
            )

            val expectedInsertEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false)
            harness.insertOneAndWait(expectedInsertEvent)
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.doSyncPass(expectedInsertEvent)
            harness.verifyChangeEventListenerCalledForActiveDoc()

            harness.deleteOneAndWait(expectedChangeEvent = expectedEvent)

            `when`(harness.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(1))

            harness.doSyncPass(expectedEvent)

            // verify we are inserting!
            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
            verify(harness.collectionMock, times(1)).deleteOne(docCaptor.capture())
            assertEquals(harness.activeDoc["_id"], docCaptor.value["_id"])

            // the four calls should be:
            // insertOne, syncLocalToRemote, updateOne, syncLocalToRemote
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(0)
            harness.verifyErrorListenerCalledForActiveDoc(0)

            assertNull(harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testConflictedDelete() {
        withSyncHarness { harness ->
            val conflictDocument = newDoc("count", BsonInt32(2)).append("_id", harness.activeDoc["_id"])

            val expectedLocalEvent = ChangeEvent.changeEventForLocalDelete(
                harness.namespace,
                harness.activeDoc["_id"],
                true
            )
            val expectedRemoteEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, conflictDocument, false)

            harness.insertOneAndWait()
            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false))

            @Suppress("UNCHECKED_CAST")
            val remoteFindIterable = mock(CoreRemoteFindIterable::class.java) as CoreRemoteFindIterable<BsonDocument>
            `when`(remoteFindIterable.first()).thenReturn(conflictDocument)
            `when`(harness.collectionMock.find(any())).thenReturn(remoteFindIterable)

            harness.deleteOneAndWait(
                expectedChangeEvent = expectedLocalEvent,
                expectedLocalConflictEvent = expectedLocalEvent,
                expectedRemoteConflictEvent = expectedRemoteEvent)

            // create conflict here
            `when`(harness.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(0))

            harness.doSyncPass(ChangeEvent.changeEventForLocalDelete(harness.namespace, harness.activeDocId, false))

            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(1)
            harness.verifyErrorListenerCalledForActiveDoc(0)

            assertEquals(
                conflictDocument,
                harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testFailedDelete() {
        withSyncHarness { harness ->
            val expectedEvent = ChangeEvent.changeEventForLocalDelete(
                harness.namespace,
                harness.activeDoc["_id"],
                false
            )

            harness.insertOneAndWait()
            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false))

            harness.deleteOneAndWait(expectedChangeEvent = expectedEvent)

            val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
            `when`(harness.collectionMock.deleteOne(any())).thenAnswer {
                throw expectedException
            }

            harness.shouldExpectError = true
            harness.doSyncPass()

            // verify we are inserting!
            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
            verify(harness.collectionMock, times(1)).deleteOne(docCaptor.capture())
            assertEquals(
                BsonDocument("_id", harness.activeDoc["_id"]!!.asObjectId()),
                withoutVersionId(docCaptor.value))

            // the four calls should be:
            // insertOne, syncLocalToRemote, updateOne, syncLocalToRemote
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyConflictHandlerCalledForActiveDoc(0)
            harness.verifyErrorListenerCalledForActiveDoc(1, expectedException)

            assertNull(harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testInsertOneAndSync() {
        withSyncHarness(withConfiguration = false) { harness ->
            harness.insertOne(withConfiguration = false)

            harness.verifyStreamFunctionCalled(0)

            val expectedEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, true)

            harness.deleteOne(withConfiguration = false)

            harness.insertOneAndWait(expectedEvent)

            harness.verifyStreamFunctionCalled(1)

            assertEquals(harness.activeDoc, harness.findActiveDocFromLocal())
        }
    }

    @Test
    fun testUpdateOneById() {
        withSyncHarness { harness ->
            val expectedDocumentAfterUpdate = BsonDocument("count", BsonInt32(2))
            // assert this doc does not exist
            assertNull(harness.findActiveDocFromLocal())

            // update the non-existent document...
            var updateResult = harness.updateOne()
            // ...which should continue to not exist...
            assertNull(harness.findActiveDocFromLocal())
            // ...and result in an "empty" UpdateResult
            assertEquals(0, updateResult.result!!.matchedCount)
            assertEquals(0, updateResult.result!!.modifiedCount)
            assertNull(updateResult.result!!.upsertedId)
            assertTrue(updateResult.result!!.wasAcknowledged())

            // insert the initial document
            harness.insertOneAndWait(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, false))
            harness.verifyChangeEventListenerCalledForActiveDoc()

            // do the actual update
            updateResult = harness.updateOneAndWait(
                ChangeEvent.changeEventForLocalUpdate(
                    harness.namespace, harness.activeDocId, harness.updateDoc, harness.activeDoc, false))
            // assert the UpdateResult is non-zero
            assertEquals(1, updateResult.result!!.matchedCount)
            assertEquals(1, updateResult.result!!.modifiedCount)
            assertNull(updateResult.result!!.upsertedId)
            assertTrue(updateResult.result!!.wasAcknowledged())
            harness.verifyChangeEventListenerCalledForActiveDoc()
            harness.verifyStreamFunctionCalled(1)
            // assert that the updated document equals what we've expected
            assertEquals(harness.activeDoc["_id"], harness.findActiveDocFromLocal()?.get("_id"))
            assertEquals(expectedDocumentAfterUpdate, withoutId(harness.findActiveDocFromLocal()!!))
        }
    }

    @Test
    fun testDeleteOneById() {
        throw NotImplementedError()
    }

    @Test
    fun testConfigure() {
        withSyncHarness(withConfiguration = false) { harness ->
            val expectedEvent = ChangeEvent.changeEventForLocalInsert(
                harness.namespace, harness.activeDoc, false)
            // without a configuration it should not be
            // configured or running
            assertFalse(harness.isRunning())

            harness.insertOne(withConfiguration = false)

            harness.verifyStreamFunctionCalled(0)
            harness.verifyStartCalled(0)

            harness.deleteOne()

            harness.insertOneAndWait(expectedEvent)

            // TODO: there is a race here as we are both configuring the namespace,
            // TODO: and inserting. this will trigger stream twice, which is not correct
            // harness.verifyStreamFunctionCalled(1)
            // TODO: for now, verify that we have started and are running
            harness.verifyStartCalled(1)
            assertTrue(harness.isRunning())
        }
    }
}
