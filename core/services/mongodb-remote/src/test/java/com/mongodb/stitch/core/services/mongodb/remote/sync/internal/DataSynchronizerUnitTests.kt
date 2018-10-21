package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncHarness.Companion.withoutVersionId
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonInt32

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.lang.Exception

class DataSynchronizerUnitTests {
    private val harness = SyncHarness()

    companion object {
        @AfterClass
        fun teardown() {
            CoreRemoteClientFactory.close()
            ServerEmbeddedMongoClientFactory.getInstance().close()
        }

        private fun setupPendingReplace(ctx: SyncHarness.TestContext, expectedDocument: BsonDocument) {
            ctx.queueConsumableRemoteInsertEvent()
            ctx.dataSynchronizer.syncDocumentFromRemote(ctx.namespace, ctx.testDocumentId)
            ctx.doSyncPass()

            // prepare a remote update and a local update.
            // do a sync pass, accepting the local doc. this will create
            // a pending replace to be sync'd on the next pass
            ctx.queueConsumableRemoteUpdateEvent()
            // set a different update doc than the remote
            ctx.updateDocument = BsonDocument("\$inc", BsonDocument("count", BsonInt32(2)))
            ctx.updateTestDocument()
            // set it back
            ctx.updateDocument = BsonDocument("\$inc", BsonDocument("count", BsonInt32(1)))
            ctx.shouldConflictBeResolvedByRemote = false
            ctx.doSyncPass()
            ctx.waitForEvent()
            ctx.verifyChangeEventListenerCalledForActiveDoc(
                times = 1,
                expectedChangeEvent = ChangeEvent.changeEventForLocalReplace(
                    ctx.namespace, ctx.testDocumentId, expectedDocument, true
                ))
            ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
            ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        }
    }

    @Test
    fun testNew() {
        val ctx = harness.newTestContext(shouldPreconfigure = false)

        // a fresh, non-configured dataSynchronizer should not be running.
        assertFalse(ctx.dataSynchronizer.isRunning)
    }

    @Test
    fun testOnNetworkStateChanged() {
        val ctx = harness.newTestContext()

        // verify that, since we are offline, start has not been called
        ctx.isOnline = false
        assertFalse(ctx.dataSynchronizer.isRunning)
        ctx.verifyStartCalled(0)
        ctx.verifyStopCalled(2)

        // verify that, since we are online, the dataSync has started
        ctx.isOnline = true
        ctx.verifyStartCalled(1)
        ctx.verifyStopCalled(2)
    }

    @Test
    fun testStartAndStop() {
        val ctx = harness.newTestContext(shouldPreconfigure = false)
        assertFalse(ctx.dataSynchronizer.isRunning)
        ctx.reconfigure()

        // with a configuration, we should be running
        assertTrue(ctx.dataSynchronizer.isRunning)

        ctx.dataSynchronizer.stop()
        assertFalse(ctx.dataSynchronizer.isRunning)
    }

    @Test
    fun testSuccessfulInsert() {
        val ctx = harness.newTestContext()

        // insert the doc, wait, sync, and assert that the expected change events are emitted
        ctx.insertTestDocument()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(
                ctx.namespace,
                ctx.testDocument,
                true))
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(
                ctx.namespace,
                ctx.testDocument,
                false))

        // verify the appropriate doc was inserted
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock, times(1)).insertOne(docCaptor.capture())
        assertEquals(ctx.testDocument, withoutVersionId(docCaptor.value))
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
        // verify the conflict and error handlers not called
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
    }

    @Test
    fun testConflictedInsert() {
        val duplicateInsertException = StitchServiceException("E11000", StitchServiceErrorCode.MONGODB_ERROR)
        var ctx = harness.newTestContext()
        // setup our expectations
        ctx.mockInsertException(duplicateInsertException)

        // 1: Insert -> Conflict -> Delete (remote wins)
        // insert the expected doc, waiting for the change event
        // assert we inserted it properly
        ctx.insertTestDocument()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // sync and assert that the conflict handler was called,
        // accepting the remote delete, nullifying the document
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false))
        ctx.verifyConflictHandlerCalledForActiveDoc(
            times = 1,
            expectedLocalConflictEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true),
            expectedRemoteConflictEvent = ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false))
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // 2: Insert -> Conflict -> Insert (local wins)
        // reset
        ctx = harness.newTestContext()
        ctx.mockInsertException(duplicateInsertException)
        ctx.insertTestDocument()

        // accept the local event this time, which will insert the local doc.
        // assert that the local doc has been inserted
        ctx.shouldConflictBeResolvedByRemote = false
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true))
        ctx.verifyConflictHandlerCalledForActiveDoc(
            times = 1,
            expectedLocalConflictEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true),
            expectedRemoteConflictEvent = ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false))
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // 3: Insert -> Conflict -> Exception -> Freeze
        // reset
        ctx = harness.newTestContext()
        ctx.mockInsertException(duplicateInsertException)
        ctx.insertTestDocument()

        // prepare an exceptionToThrow to be thrown, and sync
        ctx.exceptionToThrowDuringConflict = Exception("bad")
        ctx.doSyncPass()
        ctx.waitForError()

        // verify that, though the conflict handler was called, the exceptionToThrow was emitted
        // by the dataSynchronizer
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 0)
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 1, error = ctx.exceptionToThrowDuringConflict)

        // assert that the local doc is the same. this is frozen now
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        ctx.exceptionToThrowDuringConflict = null
        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // 4: Unknown -> Delete
        ctx = harness.newTestContext()
        ctx.mockInsertException(duplicateInsertException)
        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.queueConsumableRemoteUnknownEvent()
        ctx.doSyncPass()
        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testFailedInsert() {
        val ctx = harness.newTestContext()
        // prepare the exceptionToThrow
        val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
        ctx.mockInsertException(expectedException)

        // insert the document, prepare for an error
        ctx.insertTestDocument()
        ctx.waitForEvent()

        // sync, verifying that the expected exceptionToThrow was emitted, freezing the document
        ctx.doSyncPass()
        ctx.waitForError()
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 0)
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 1, error = expectedException)
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // prepare a remote delete event, sync, and assert that nothing was affecting
        // (since we're frozen)
        ctx.queueConsumableRemoteDeleteEvent()
        ctx.doSyncPass()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testSuccessfulReplace() {
        val ctx = harness.newTestContext()
        val expectedDocument = BsonDocument("_id", ctx.testDocumentId).append("count", BsonInt32(3))
        setupPendingReplace(ctx, expectedDocument)

        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))

        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalReplace(
                ctx.namespace, ctx.testDocumentId, expectedDocument, false))
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
    }

    @Test
    fun testConflictedReplace() {
        var ctx = harness.newTestContext()
        var expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)

        // 1: Replace -> Conflict -> Replace (local wins)
        setupPendingReplace(ctx, expectedDoc)

        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))
        // do a sync pass, addressing the conflict
        ctx.doSyncPass()
        ctx.waitForEvent()
        // verify that a change event has been emitted, a conflict has been handled (also one
        // from our prepareLocalReplace), and no errors were emitted
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(
                ctx.namespace, expectedDoc, true
            ))
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)

        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(
                ctx.namespace, expectedDoc, false
            ))
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        // 2: Replace -> Conflict -> Delete (remote wins)
        ctx = harness.newTestContext()
        expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        setupPendingReplace(ctx, expectedDoc)
        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))
        // do a sync pass, addressing the conflict
        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()
        ctx.waitForEvent()
        // verify that a change event has been emitted, a conflict has been handled (also one
        // from our prepareLocalReplace), and no errors were emitted
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalDelete(
                ctx.namespace, ctx.testDocumentId, false
            ))

        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // 3: Replace -> Conflict -> Exception -> Freeze
        ctx = harness.newTestContext()
        expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        setupPendingReplace(ctx, expectedDoc)
        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))
        expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        ctx.exceptionToThrowDuringConflict = Exception("bad")

        ctx.doSyncPass()
        ctx.waitForError()
        // verify that, though the conflict handler was called, the exceptionToThrow was emitted
        // by the dataSynchronizer
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 1,
            error = ctx.exceptionToThrowDuringConflict)
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        // clear issues. open a path for a delete.
        // do another sync pass. the doc should remain the same as it is frozen
        ctx.exceptionToThrowDuringConflict = null
        ctx.shouldConflictBeResolvedByRemote = false
        ctx.doSyncPass()
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())
        expectedDoc = BsonDocument("count", BsonInt32(5)).append("_id", ctx.testDocumentId)

        // replace the doc locally (with an update), unfreezing it, and syncing it
        setupPendingReplace(ctx, expectedDoc)
        ctx.doSyncPass()
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        // 4: Unknown -> Freeze
        ctx = harness.newTestContext()
        expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        setupPendingReplace(ctx, expectedDoc)
        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))
        ctx.queueConsumableRemoteUnknownEvent()
        ctx.doSyncPass()
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        // should be frozen since the operation type was unknown
        ctx.queueConsumableRemoteUpdateEvent()
        ctx.doSyncPass()
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testFailedReplace() {
        throw NotImplementedError()
    }

    @Test
    fun testSuccessfulUpdate() {
        val ctx = harness.newTestContext()
        // setup our expectations
        val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", ctx.testDocumentId)

        // insert, sync the doc, update, and verify that the change event was emitted
        ctx.insertTestDocument()
        ctx.waitForEvent()
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))
        ctx.updateTestDocument()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalUpdate(
                ctx.namespace,
                ctx.testDocumentId,
                ctx.updateDocument,
                docAfterUpdate,
                true
            ))

        // mock a successful update, sync the update. verify that the update
        // was of the correct doc, and that no conflicts or errors occured
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1, expectedChangeEvent = ChangeEvent.changeEventForLocalUpdate(
            ctx.namespace,
            ctx.testDocumentId,
            ctx.updateDocument,
            docAfterUpdate,
            false
        ))
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock, times(1)).updateOne(any(), docCaptor.capture())
        assertEquals(docAfterUpdate, withoutVersionId(docCaptor.value))
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)

        // verify the doc update was maintained locally
        assertEquals(
            docAfterUpdate,
            ctx.findTestDocumentFromLocalCollection())
    }
//
//    @Test
//    fun testConflictedUpdate() {
//        withSyncHarness { harness ->
//            // setup our expectations
//            var docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", harness.testDocument["_id"])
//            var expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
//                harness.namespace,
//                harness.testDocument["_id"],
//                harness.updateDoc,
//                docAfterUpdate,
//                true)
//            val expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(harness.namespace, harness.testDocumentId, false)
//
//            // 1: Update -> Conflict -> Delete (remote wins)
//            // insert a new document, and sync it, waiting for the local insert change event
//            harness.insertTestDocumentAndWait()
//            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, false))
//
//            // update the document and wait for the local update event
//            harness.updateTestDocumentAndWait(
//                expectedChangeEvent = expectedLocalEvent,
//                expectedLocalConflictEvent = expectedLocalEvent,
//                expectedRemoteConflictEvent = expectedRemoteEvent)
//
//            // create conflict here by claiming there is no remote doc to update
//            `when`(harness.collectionMock.updateOne(any(), any())).thenReturn(RemoteUpdateResult(0, 0, null))
//
//            // do a sync pass, addressing the conflict
//            harness.doSyncPass(expectedLocalEvent)
//
//            // verify that a change event has been emitted, a conflict has been handled,
//            // and no errors were emitted
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyConflictHandlerCalledForActiveDoc(1)
//            harness.verifyErrorListenerCalledForActiveDoc(0)
//
//            // since we've accepted the remote result, this doc will have been deleted
//            assertNull(harness.findTestDocumentFromLocal())
//
//            // 2: Update -> Conflict -> Update (local wins)
//            // reset (delete, insert, sync)
//            harness.deleteTestDocument()
//            harness.insertTestDocumentAndWait()
//            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, false))
//
//            // update the document and wait for the local update event
//            harness.updateTestDocumentAndWait(
//                expectedChangeEvent = expectedLocalEvent,
//                expectedLocalConflictEvent = expectedLocalEvent,
//                expectedRemoteConflictEvent = expectedRemoteEvent)
//
//            // do a sync pass, addressing the conflict. let local win
//            harness.conflictHandler.shouldConflictBeResolvedByRemote = false
//            harness.doSyncPass(expectedLocalEvent)
//
//            // verify that a change event has been emitted, a conflict has been handled,
//            // and no errors were emitted
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyConflictHandlerCalledForActiveDoc(1)
//            harness.verifyErrorListenerCalledForActiveDoc(0)
//
//            // since we've accepted the local result, this doc will have been updated remotely
//            // and sync'd locally
//            assertEquals(
//                docAfterUpdate,
//                harness.findTestDocumentFromLocal())
//
//            // 3: Update -> Conflict -> Exception -> Freeze
//            // reset (delete, insert, sync)
//            harness.deleteTestDocument()
//            harness.insertTestDocumentAndWait()
//            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, false))
//
//            // update the reset doc
//            harness.updateTestDocumentAndWait(
//                expectedChangeEvent = expectedLocalEvent,
//                expectedLocalConflictEvent = expectedLocalEvent,
//                expectedRemoteConflictEvent = expectedRemoteEvent)
//
//            // prepare an exceptionToThrow to be thrown, and sync
//            harness.conflictHandler.exceptionToThrow = Exception("bad")
//            harness.shouldExpectError = true
//            harness.doSyncPass()
//
//            // verify that, though the conflict handler was called, the exceptionToThrow was emitted
//            // by the dataSynchronizer
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyConflictHandlerCalledForActiveDoc(1)
//            harness.verifyErrorListenerCalledForActiveDoc(1, harness.conflictHandler.exceptionToThrow)
//
//            // assert that this document is still the locally updated doc. this is frozen now
//            assertEquals(docAfterUpdate, harness.findTestDocumentFromLocal())
//
//            // clear issues. open a path for a delete.
//            // do another sync pass. the doc should remain the same as it is frozen
//            harness.conflictHandler.exceptionToThrow = null
//            harness.conflictHandler.shouldConflictBeResolvedByRemote = false
//            harness.shouldExpectError = false
//            harness.doSyncPass()
//            assertEquals(docAfterUpdate, harness.findTestDocumentFromLocal())
//
//            // update the doc locally, unfreezing it, and syncing it
//            docAfterUpdate = BsonDocument("count", BsonInt32(3)).append("_id", harness.testDocument["_id"])
//            expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
//                harness.namespace, harness.testDocumentId, harness.updateDoc, docAfterUpdate, true)
//            `when`(harness.collectionMock.updateOne(any(), any())).thenReturn(RemoteUpdateResult(1, 1, null))
//            assertEquals(1L, harness.updateTestDocumentAndWait(expectedLocalEvent, expectedLocalEvent).result?.matchedCount)
//            harness.doSyncPass()
//
//            // 4: Unknown -> Freeze
//            harness.prepareRemoteUnknown()
//            harness.doSyncPass()
//            assertEquals(docAfterUpdate, harness.findTestDocumentFromLocal())
//
//            // should be frozen since the operation type was unknown
//            harness.prepareRemoteUpdate()
//            harness.doSyncPass()
//            assertEquals(docAfterUpdate, harness.findTestDocumentFromLocal())
//        }
//    }
//
//    @Test
//    fun testFailedUpdate() {
//        withSyncHarness { harness ->
//            // set up expectations and insert
//            val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", harness.testDocument["_id"])
//            val expectedEvent = ChangeEvent.changeEventForLocalUpdate(
//                harness.namespace,
//                harness.testDocument["_id"],
//                harness.updateDoc,
//                docAfterUpdate,
//                false
//            )
//            harness.insertTestDocumentAndWait()
//            harness.doSyncPass()
//
//            // update the inserted doc, and prepare our exceptionToThrow
//            harness.updateTestDocumentAndWait(expectedChangeEvent = expectedEvent)
//            val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
//            `when`(harness.collectionMock.updateOne(any(), any())).thenAnswer {
//                throw expectedException
//            }
//            harness.shouldExpectError = true
//
//            // sync, and verify that we attempted to update with the correct document,
//            // but the expected exceptionToThrow was called
//            harness.doSyncPass()
//            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
//            verify(harness.collectionMock, times(1)).updateOne(any(), docCaptor.capture())
//            assertEquals(expectedEvent.fullDocument, withoutVersionId(docCaptor.value))
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyConflictHandlerCalledForActiveDoc(0)
//            harness.verifyErrorListenerCalledForActiveDoc(1, expectedException)
//            assertEquals(
//                docAfterUpdate,
//                harness.findTestDocumentFromLocal())
//
//            // prepare a remote delete event, sync, and assert that nothing was affecting
//            // (since we're frozen)
//            harness.prepareRemoteDelete()
//            harness.shouldExpectError = false
//            harness.doSyncPass()
//            assertEquals(docAfterUpdate, harness.findTestDocumentFromLocal())
//        }
//    }
//
//    @Test
//    fun testSuccessfulDelete() {
//        withSyncHarness { harness ->
//            val expectedEvent = ChangeEvent.changeEventForLocalDelete(
//                harness.namespace,
//                harness.testDocument["_id"],
//                false)
//
//            val expectedInsertEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, false)
//            harness.insertTestDocumentAndWait(expectedInsertEvent)
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.doSyncPass(expectedInsertEvent)
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//
//            harness.deleteTestDocumentAndWait(expectedChangeEvent = expectedEvent)
//
//            `when`(harness.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(1))
//
//            harness.doSyncPass(expectedEvent)
//
//            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
//            verify(harness.collectionMock, times(1)).deleteOne(docCaptor.capture())
//            assertEquals(harness.testDocument["_id"], docCaptor.value["_id"])
//
//            // the four calls should be:
//            // insertTestDocument, syncLocalToRemote, updateTestDocument, syncLocalToRemote
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyConflictHandlerCalledForActiveDoc(0)
//            harness.verifyErrorListenerCalledForActiveDoc(0)
//
//            assertNull(harness.findTestDocumentFromLocal())
//        }
//    }
//
//    @Test
//    fun testConflictedDelete() {
//        withSyncHarness { harness ->
//            val conflictDocument = newDoc("count", BsonInt32(2))
//                .append("_id", harness.testDocument["_id"])
//
//            val expectedLocalEvent = ChangeEvent.changeEventForLocalDelete(
//                harness.namespace,
//                harness.testDocument["_id"],
//                true
//            )
//            val expectedRemoteEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, conflictDocument, false)
//
//            harness.insertTestDocumentAndWait()
//            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, false))
//
//            @Suppress("UNCHECKED_CAST")
//            val remoteFindIterable = mock(CoreRemoteFindIterable::class.java) as CoreRemoteFindIterable<BsonDocument>
//            `when`(remoteFindIterable.first()).thenReturn(conflictDocument)
//            `when`(harness.collectionMock.find(any())).thenReturn(remoteFindIterable)
//
//            harness.deleteTestDocumentAndWait(
//                expectedChangeEvent = expectedLocalEvent,
//                expectedLocalConflictEvent = expectedLocalEvent,
//                expectedRemoteConflictEvent = expectedRemoteEvent)
//
//            // create conflict here
//            `when`(harness.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(0))
//
//            harness.doSyncPass(ChangeEvent.changeEventForLocalDelete(harness.namespace, harness.testDocumentId, false))
//
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyConflictHandlerCalledForActiveDoc(1)
//            harness.verifyErrorListenerCalledForActiveDoc(0)
//
//            assertEquals(
//                conflictDocument,
//                harness.findTestDocumentFromLocal())
//        }
//    }
//
//    @Test
//    fun testFailedDelete() {
//        withSyncHarness { harness ->
//            val expectedEvent = ChangeEvent.changeEventForLocalDelete(
//                harness.namespace,
//                harness.testDocument["_id"],
//                false
//            )
//
//            harness.insertTestDocumentAndWait()
//            harness.doSyncPass(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, false))
//
//            harness.deleteTestDocumentAndWait(expectedChangeEvent = expectedEvent)
//
//            val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
//            `when`(harness.collectionMock.deleteOne(any())).thenAnswer {
//                throw expectedException
//            }
//
//            harness.shouldExpectError = true
//            harness.doSyncPass()
//
//            // verify we are inserting!
//            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
//            verify(harness.collectionMock, times(1)).deleteOne(docCaptor.capture())
//            assertEquals(
//                BsonDocument("_id", harness.testDocument["_id"]!!.asObjectId()),
//                withoutVersionId(docCaptor.value))
//
//            // the four calls should be:
//            // insertTestDocument, syncLocalToRemote, updateTestDocument, syncLocalToRemote
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyConflictHandlerCalledForActiveDoc(0)
//            harness.verifyErrorListenerCalledForActiveDoc(1, expectedException)
//
//            assertNull(harness.findTestDocumentFromLocal())
//        }
//    }
//
//    @Test
//    fun testInsertOneAndSync() {
//        withSyncHarness(withConfiguration = false) { harness ->
//            harness.insertTestDocument(withConfiguration = false)
//
//            harness.verifyStreamFunctionCalled(0)
//
//            val expectedEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, true)
//
//            harness.deleteTestDocument(withConfiguration = false)
//
//            harness.insertTestDocumentAndWait(expectedEvent)
//
//            harness.verifyStreamFunctionCalled(1)
//
//            assertEquals(harness.testDocument, harness.findTestDocumentFromLocal())
//        }
//    }
//
//    @Test
//    fun testUpdateOneById() {
//        withSyncHarness { harness ->
//            val expectedDocumentAfterUpdate = BsonDocument("count", BsonInt32(2))
//            // assert this doc does not exist
//            assertNull(harness.findTestDocumentFromLocal())
//
//            // update the non-existent document...
//            var updateResult = harness.updateTestDocument()
//            // ...which should continue to not exist...
//            assertNull(harness.findTestDocumentFromLocal())
//            // ...and result in an "empty" UpdateResult
//            assertEquals(0, updateResult.result!!.matchedCount)
//            assertEquals(0, updateResult.result!!.modifiedCount)
//            assertNull(updateResult.result!!.upsertedId)
//            assertTrue(updateResult.result!!.wasAcknowledged())
//
//            // insert the initial document
//            harness.insertTestDocumentAndWait(ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.testDocument, false))
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//
//            // do the actual update
//            updateResult = harness.updateTestDocumentAndWait(
//                ChangeEvent.changeEventForLocalUpdate(
//                    harness.namespace, harness.testDocumentId, harness.updateDoc, harness.testDocument, false))
//            // assert the UpdateResult is non-zero
//            assertEquals(1, updateResult.result!!.matchedCount)
//            assertEquals(1, updateResult.result!!.modifiedCount)
//            assertNull(updateResult.result!!.upsertedId)
//            assertTrue(updateResult.result!!.wasAcknowledged())
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//            harness.verifyStreamFunctionCalled(1)
//            // assert that the updated document equals what we've expected
//            assertEquals(harness.testDocument["_id"], harness.findTestDocumentFromLocal()?.get("_id"))
//            assertEquals(expectedDocumentAfterUpdate, withoutId(harness.findTestDocumentFromLocal()!!))
//        }
//    }
//
//    @Test
//    fun testDeleteOneById() {
//        withSyncHarness { harness ->
//            // 0: Pre-checks
//            // assert this doc does not exist
//            assertNull(harness.findTestDocumentFromLocal())
//
//            // update the non-existent document...
//            var deleteResult = harness.deleteTestDocument()
//            // ...which should continue to not exist...
//            assertNull(harness.findTestDocumentFromLocal())
//            // ...and result in an "empty" DeleteResult
//            assertEquals(0, deleteResult.result!!.deletedCount)
//            assertTrue(deleteResult.result!!.wasAcknowledged())
//
//            // 1: Insert -> Delete -> Coalescence
//            // insert the initial document
//            harness.insertTestDocumentAndWait()
//            harness.verifyChangeEventListenerCalledForActiveDoc()
//
//            // do the actual delete
//            deleteResult = harness.deleteTestDocument()
//            // assert the DeleteResult is non-zero, and that a (new) change event was not
//            // called (coalescence). verify desync was called
//            assertEquals(1, deleteResult.result!!.deletedCount)
//            assertTrue(deleteResult.result!!.wasAcknowledged())
//            harness.verifyChangeEventListenerCalledForActiveDoc(1)
//            harness.verifyStreamFunctionCalled(1)
//            verify(harness.dataSynchronizer).desyncDocumentFromRemote(eq(harness.namespace), eq(harness.testDocumentId))
//            // assert that the updated document equals what we've expected
//            assertNull(harness.findTestDocumentFromLocal())
//
//            // 2: Insert -> Update -> Delete -> Event Emission
//            // insert the initial document
//            harness.insertTestDocumentAndWait()
//            harness.doSyncPass()
//            // do the actual delete
//            deleteResult = harness.deleteTestDocumentAndWait(ChangeEvent.changeEventForLocalDelete(
//                harness.namespace, harness.testDocumentId, true
//            ))
//            // assert the UpdateResult is non-zero
//            assertEquals(1, deleteResult.result!!.deletedCount)
//            assertTrue(deleteResult.result!!.wasAcknowledged())
//            harness.verifyChangeEventListenerCalledForActiveDoc(1)
//            harness.verifyStreamFunctionCalled(2)
//            // assert that the updated document equals what we've expected
//            assertNull(harness.findTestDocumentFromLocal())
//        }
//    }
//
//    @Test
//    fun testConfigure() {
//        withSyncHarness(withConfiguration = false) { harness ->
//            val expectedEvent = ChangeEvent.changeEventForLocalInsert(
//                harness.namespace, harness.testDocument, false)
//            // without a configuration it should not be
//            // configured or running
//            assertFalse(harness.isRunning())
//
//            harness.insertTestDocument(withConfiguration = false)
//
//            harness.verifyStreamFunctionCalled(0)
//            harness.verifyStartCalled(0)
//
//            harness.deleteTestDocument()
//
//            harness.insertTestDocumentAndWait(expectedEvent)
//
//            // TODO: there is a race here as we are both configuring the namespace,
//            // TODO: and inserting. this will trigger stream twice, which is not correct
//            // harness.verifyStreamFunctionCalled(1)
//            // TODO: for now, verify that we have started and are running
//            harness.verifyStartCalled(1)
//            assertTrue(harness.isRunning())
//        }
//    }
}
