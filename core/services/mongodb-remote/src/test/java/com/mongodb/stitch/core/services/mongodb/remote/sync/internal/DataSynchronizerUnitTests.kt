package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterableImpl
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncUnitTestHarness.Companion.withoutSyncVersion
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.Document
import org.junit.After

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.lang.Exception
import java.util.Collections

class DataSynchronizerUnitTests {
    companion object {
        private fun setupPendingReplace(
            ctx: DataSynchronizerTestContext,
            shouldConflictBeResolvedByRemote: Boolean = false,
            shouldWaitForError: Boolean = false
        ) {
            ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))
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
            ctx.shouldConflictBeResolvedByRemote = shouldConflictBeResolvedByRemote

            ctx.doSyncPass()

            if (shouldWaitForError) {
                ctx.waitForError()
            } else {
                ctx.waitForEvents()
            }

            ctx.verifyChangeEventListenerCalledForActiveDoc(
                if (shouldWaitForError) 0 else 1)
            ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
            ctx.verifyErrorListenerCalledForActiveDoc(times = if (shouldWaitForError) 1 else 0,
                error = if (shouldWaitForError) ctx.exceptionToThrowDuringConflict else null)
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
    fun testNew() {
        val ctx = harness.freshTestContext(shouldPreconfigure = false)

        // a fresh, non-configured dataSynchronizer should not be running.
        assertFalse(ctx.dataSynchronizer.isRunning)
    }

    @Test
    fun testOnNetworkStateChanged() {
        val ctx = harness.freshTestContext()

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
        val ctx = harness.freshTestContext(shouldPreconfigure = false)
        assertFalse(ctx.dataSynchronizer.isRunning)
        ctx.reconfigure()

        // with a configuration, we should be running
        assertTrue(ctx.dataSynchronizer.isRunning)

        ctx.dataSynchronizer.stop()
        assertFalse(ctx.dataSynchronizer.isRunning)
    }

    @Test
    fun testSuccessfulInsert() {
        val ctx = harness.freshTestContext()

        // insert the doc, wait, sync, and assert that the expected change events are emitted
        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalInsert(
                ctx.namespace,
                ctx.testDocument,
                true))
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalInsert(
                ctx.namespace,
                ctx.testDocument,
                false))

        // verify the appropriate doc was inserted
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock, times(1)).insertOne(docCaptor.capture())
        assertEquals(ctx.testDocument, withoutSyncVersion(docCaptor.value))
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
        // verify the conflict and error handlers not called
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
    }

    @Test
    fun testConflictedInsert() {
        val duplicateInsertException = StitchServiceException("E11000", StitchServiceErrorCode.MONGODB_ERROR)
        var ctx = harness.freshTestContext()
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
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false))
        ctx.verifyConflictHandlerCalledForActiveDoc(
            times = 1,
            expectedLocalConflictEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true),
            expectedRemoteConflictEvent = ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false))
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // 2: Insert -> Conflict -> Insert (local wins)
        // reset
        ctx = harness.freshTestContext()
        ctx.mockInsertException(duplicateInsertException)
        ctx.insertTestDocument()

        // accept the local event this time, which will insert the local doc.
        // assert that the local doc has been inserted
        ctx.shouldConflictBeResolvedByRemote = false
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true))
        ctx.verifyConflictHandlerCalledForActiveDoc(
            times = 1,
            expectedLocalConflictEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true),
            expectedRemoteConflictEvent = ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false))
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // 3: Insert -> Conflict -> Exception -> Freeze
        // reset
        ctx = harness.freshTestContext()
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

        // assert that the local doc is the same. this is paused now
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        ctx.exceptionToThrowDuringConflict = null
        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // 4: Unknown -> Delete
        ctx = harness.freshTestContext()
        ctx.mockInsertException(duplicateInsertException)
        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.queueConsumableRemoteUnknownEvent()
        ctx.doSyncPass()
        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testFailedInsert() {
        val ctx = harness.freshTestContext()
        // prepare the exceptionToThrow
        val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
        ctx.mockInsertException(expectedException)

        // insert the document, prepare for an error
        ctx.insertTestDocument()
        ctx.waitForEvents()

        // sync, verifying that the expected exceptionToThrow was emitted, pausing the document
        ctx.doSyncPass()
        ctx.waitForError()
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 0)
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 1, error = expectedException)
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // prepare a remote delete event, sync, and assert that nothing was affecting
        // (since we're paused)
        ctx.queueConsumableRemoteDeleteEvent()
        ctx.doSyncPass()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testSuccessfulReplace() {
        val ctx = harness.freshTestContext()
        val expectedDocument = BsonDocument("_id", ctx.testDocumentId).append("count", BsonInt32(3))
        setupPendingReplace(ctx)
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalUpdate(
                    ctx.namespace,
                    ctx.testDocumentId,
                    ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(3)), Collections.emptyList()),
                    expectedDocument,
                    false
            )
        )
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
    }

    @Test
    fun testConflictedReplace() {
        var ctx = harness.freshTestContext()
        var expectedLocalDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)

        // 1: Replace -> Conflict -> Replace (local wins)
        setupPendingReplace(
            ctx,
            shouldConflictBeResolvedByRemote = false)

        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertEquals(expectedLocalDoc, ctx.findTestDocumentFromLocalCollection())

        // 2: Replace -> Conflict -> Replace (remote wins)
        ctx = harness.freshTestContext()
        val expectedRemoteDoc = BsonDocument("_id", ctx.testDocumentId).append("count", BsonInt32(1))
        setupPendingReplace(
            ctx,
            shouldConflictBeResolvedByRemote = true)

        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertEquals(expectedRemoteDoc, ctx.findTestDocumentFromLocalCollection())

        // 3: Replace -> Conflict -> Exception -> Freeze
        ctx = harness.freshTestContext()
        expectedLocalDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        ctx.exceptionToThrowDuringConflict = Exception("bad")
        // verify that, though the conflict handler was called, the exceptionToThrow was emitted
        // by the dataSynchronizer
        setupPendingReplace(ctx, shouldWaitForError = true)
        assertEquals(expectedLocalDoc, ctx.findTestDocumentFromLocalCollection())

        // clear issues. open a path for a delete.
        // do another sync pass. the doc should remain the same as it is paused
        ctx.exceptionToThrowDuringConflict = null
        ctx.shouldConflictBeResolvedByRemote = false
        ctx.doSyncPass()
        assertEquals(expectedLocalDoc, ctx.findTestDocumentFromLocalCollection())
        expectedLocalDoc = BsonDocument("count", BsonInt32(5)).append("_id", ctx.testDocumentId)

        // replace the doc locally (with an update), unfreezing it, and syncing it
        setupPendingReplace(ctx)
        ctx.doSyncPass()
        assertEquals(expectedLocalDoc, ctx.findTestDocumentFromLocalCollection())

        // 4: Unknown -> Freeze
        ctx = harness.freshTestContext()
        expectedLocalDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        ctx.queueConsumableRemoteUnknownEvent()
        setupPendingReplace(ctx)

        ctx.queueConsumableRemoteUpdateEvent()
        ctx.doSyncPass()
        assertEquals(expectedLocalDoc, ctx.findTestDocumentFromLocalCollection())

        // should be paused since the operation type was unknown
        ctx.queueConsumableRemoteUnknownEvent()
        ctx.doSyncPass()
        assertEquals(expectedLocalDoc, ctx.findTestDocumentFromLocalCollection())

        ctx.queueConsumableRemoteDeleteEvent()
        ctx.doSyncPass()
        assertEquals(expectedLocalDoc, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testSuccessfulUpdate() {
        val ctx = harness.freshTestContext()
        // setup our expectations
        val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", ctx.testDocumentId)

        // insert, sync the doc, update, and verify that the change event was emitted
        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1,
            ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))
        ctx.updateTestDocument()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1,
            ChangeEvent.changeEventForLocalUpdate(
                ctx.namespace,
                ctx.testDocumentId,
                ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()),
                docAfterUpdate,
                true
            ))

        // mock a successful update, sync the update. verify that the update
        // was of the correct doc, and that no conflicts or errors occured
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalUpdate(
            ctx.namespace,
            ctx.testDocumentId,
            ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()),
            docAfterUpdate,
            false
        ))
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock, times(1)).updateOne(any(), docCaptor.capture())

        // create what we expect the diff to look like
        val expectedDiff = ChangeEvent.UpdateDescription.diff(
            BsonDocument.parse(ctx.testDocument.toJson()),
            docAfterUpdate).toUpdateDocument()
        expectedDiff.remove("\$unset")

        // get the actual diff. remove the versioning info
        val actualDiff = docCaptor.value
        actualDiff.getDocument("\$set").remove(DataSynchronizer.DOCUMENT_VERSION_FIELD)

        assertEquals(expectedDiff, actualDiff)
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)

        // verify the doc update was maintained locally
        assertEquals(
            docAfterUpdate,
            ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testConflictedUpdate() {
        var ctx = harness.freshTestContext()
        // setup our expectations
        var docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", ctx.testDocumentId)
        var expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
            ctx.namespace,
            ctx.testDocumentId,
            ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()),
            docAfterUpdate,
            true)

        // 1: Update -> Conflict -> Delete (remote wins)
        // insert a new document, and sync.
        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.doSyncPass()

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedLocalEvent)

        // create conflict here by claiming there is no remote doc to update
        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))

        // do a sync pass, addressing the conflict
        ctx.doSyncPass()
        ctx.waitForEvents(1)
        // verify that a change event has been emitted, a conflict has been handled,
        // and no errors were emitted
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1)
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)

        // since we've accepted the remote result, this doc will have been deleted
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // 2: Update -> Conflict -> Update (local wins)
        // reset (delete, insert, sync)
        ctx = harness.freshTestContext()
        docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", ctx.testDocumentId)
        expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
            ctx.namespace,
            ctx.testDocumentId,
            ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()),
            docAfterUpdate,
            true)
        var expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false)

        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))
        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvents()

        // do a sync pass, addressing the conflict. let local win
        ctx.shouldConflictBeResolvedByRemote = false

        ctx.doSyncPass()
        ctx.waitForEvents()

        // verify that a change event has been emitted, a conflict has been handled,
        // and no errors were emitted
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalInsert(ctx.namespace, docAfterUpdate, true))
        ctx.verifyConflictHandlerCalledForActiveDoc(1, expectedLocalEvent, expectedRemoteEvent)
        ctx.verifyErrorListenerCalledForActiveDoc(0)

        // since we've accepted the local result, this doc will have been updated remotely
        // and sync'd locally
        assertEquals(
            docAfterUpdate,
            ctx.findTestDocumentFromLocalCollection())

        // 3: Update -> Conflict -> Exception -> Freeze
        // reset (delete, insert, sync)
        ctx = harness.freshTestContext()
        docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", ctx.testDocumentId)
        expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
            ctx.namespace,
            ctx.testDocumentId,
            ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()),
            docAfterUpdate,
            true)
        expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false)

        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))

        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1, ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))
        ctx.doSyncPass()

        // update the reset doc
        ctx.updateTestDocument()
        ctx.waitForEvents()

        // prepare an exceptionToThrow to be thrown, and sync
        ctx.exceptionToThrowDuringConflict = Exception("bad")
        ctx.doSyncPass()
        ctx.waitForError()

        // verify that, though the conflict handler was called, the exceptionToThrow was emitted
        // by the dataSynchronizer
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 0)
        ctx.verifyConflictHandlerCalledForActiveDoc(1, expectedLocalEvent, expectedRemoteEvent)
        ctx.verifyErrorListenerCalledForActiveDoc(1, ctx.exceptionToThrowDuringConflict)

        // assert that this document is still the locally updated doc. this is paused now
        assertEquals(docAfterUpdate, ctx.findTestDocumentFromLocalCollection())

        // clear issues. open a path for a delete.
        // do another sync pass. the doc should remain the same as it is paused
        ctx.exceptionToThrowDuringConflict = null
        ctx.shouldConflictBeResolvedByRemote = false

        ctx.doSyncPass()
        assertEquals(docAfterUpdate, ctx.findTestDocumentFromLocalCollection())

        // update the doc locally, unfreezing it, and syncing it
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        assertEquals(1L, ctx.updateTestDocument().matchedCount)
        ctx.doSyncPass()

        // 4: Unknown -> Freeze
        ctx = harness.freshTestContext()
        ctx.insertTestDocument()
        ctx.queueConsumableRemoteUnknownEvent()
        ctx.doSyncPass()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // should be paused since the operation type was unknown
        ctx.queueConsumableRemoteUpdateEvent()
        ctx.doSyncPass()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testFailedUpdate() {
        val ctx = harness.freshTestContext()
        // set up expectations and insert
        val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", ctx.testDocumentId)
        val expectedEvent = ChangeEvent.changeEventForLocalUpdate(
            ctx.namespace,
            ctx.testDocument["_id"],
            ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()),
            docAfterUpdate,
            true
        )
        ctx.insertTestDocument()
        ctx.doSyncPass()
        ctx.waitForEvents()

        // update the inserted doc, and prepare our exceptionToThrow
        ctx.updateTestDocument()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedEvent)
        val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
        ctx.mockUpdateException(expectedException)

        // sync, and verify that we attempted to update with the correct document,
        // but the expected exceptionToThrow was called
        ctx.doSyncPass()
        ctx.waitForError()
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock, times(1)).updateOne(any(), docCaptor.capture())

        // create what we expect the diff to look like
        val expectedDiff = ChangeEvent.UpdateDescription.diff(
            BsonDocument.parse(ctx.testDocument.toJson()),
            expectedEvent.fullDocument).toUpdateDocument()
        expectedDiff.remove("\$unset")

        // get the actual diff. remove the versioning info
        val actualDiff = docCaptor.value
        actualDiff.getDocument("\$set").remove(DataSynchronizer.DOCUMENT_VERSION_FIELD)

        assertEquals(expectedDiff, actualDiff)
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 0)
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 1, error = expectedException)
        assertEquals(
            docAfterUpdate,
            ctx.findTestDocumentFromLocalCollection())

        // prepare a remote delete event, sync, and assert that nothing was affecting
        // (since we're paused)
        ctx.queueConsumableRemoteDeleteEvent()
        ctx.doSyncPass()
        assertEquals(docAfterUpdate, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testSuccessfulDelete() {
        val ctx = harness.freshTestContext()

        // insert a new document. assert that the correct change events
        // have been reflected w/ and w/o pending writes
        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true))
        ctx.doSyncPass()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))

        // delete the document and wait
        ctx.deleteTestDocument()
        ctx.waitForEvents()

        // verify a delete event with pending writes is called
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalDelete(
            ctx.namespace,
            ctx.testDocument["_id"],
            true))
        ctx.mockDeleteResult(RemoteDeleteResult(1))

        // sync. verify the correct doc was deleted and that a change event
        // with no pending writes was emitted
        ctx.doSyncPass()
        ctx.waitForEvents()
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock, times(1)).deleteOne(docCaptor.capture())
        assertEquals(ctx.testDocument["_id"], docCaptor.value["_id"])
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalDelete(
            ctx.namespace,
            ctx.testDocument["_id"],
            false))
        ctx.verifyConflictHandlerCalledForActiveDoc(0)
        ctx.verifyErrorListenerCalledForActiveDoc(0)
        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testConflictedDelete() {
        var ctx = harness.freshTestContext()

        var expectedLocalEvent = ChangeEvent.changeEventForLocalDelete(
            ctx.namespace,
            ctx.testDocument["_id"],
            true
        )

        ctx.insertTestDocument()
        ctx.doSyncPass()
        ctx.waitForEvents()

        ctx.deleteTestDocument()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedLocalEvent)

        // create conflict here
        // 1: Remote wins
        `when`(ctx.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(0))
        ctx.queueConsumableRemoteUpdateEvent()

        ctx.doSyncPass()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalReplace(
            ctx.namespace,
            ctx.testDocumentId,
            ctx.testDocument,
            false
        ))
        ctx.verifyConflictHandlerCalledForActiveDoc(1, expectedLocalEvent,
            ChangeEvent.changeEventForLocalUpdate(ctx.namespace, ctx.testDocumentId, null, ctx.testDocument, false))
        ctx.verifyErrorListenerCalledForActiveDoc(0)

        assertEquals(
            ctx.testDocument,
            ctx.findTestDocumentFromLocalCollection())

        // 2: Local wins
        ctx = harness.freshTestContext()

        expectedLocalEvent = ChangeEvent.changeEventForLocalDelete(
            ctx.namespace,
            ctx.testDocument["_id"],
            true
        )

        ctx.insertTestDocument()
        ctx.doSyncPass()
        ctx.waitForEvents()

        ctx.deleteTestDocument()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedLocalEvent)

        // create conflict here
        `when`(ctx.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(0))
        ctx.queueConsumableRemoteUpdateEvent()
        ctx.shouldConflictBeResolvedByRemote = false
        ctx.doSyncPass()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalDelete(
            ctx.namespace,
            ctx.testDocumentId,
            true
        ))
        ctx.verifyConflictHandlerCalledForActiveDoc(1,
            ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, true),
            ChangeEvent.changeEventForLocalUpdate(
                ctx.namespace, ctx.testDocumentId, null, ctx.testDocument, false
            ))
        ctx.verifyErrorListenerCalledForActiveDoc(0)

        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testFailedDelete() {
        val ctx = harness.freshTestContext()

        val expectedEvent = ChangeEvent.changeEventForLocalDelete(
            ctx.namespace,
            ctx.testDocument["_id"],
            true
        )

        ctx.insertTestDocument()
        ctx.waitForEvents()

        ctx.doSyncPass()

        ctx.deleteTestDocument()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedEvent)
        val expectedException = StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN)
        ctx.mockDeleteException(expectedException)

        ctx.doSyncPass()
        ctx.waitForError()
        // verify we have deleted the correct doc
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock, times(1)).deleteOne(docCaptor.capture())
        assertEquals(
            BsonDocument("_id", ctx.testDocument["_id"]!!.asObjectId()),
            withoutSyncVersion(docCaptor.value))
        ctx.verifyChangeEventListenerCalledForActiveDoc(0)
        ctx.verifyConflictHandlerCalledForActiveDoc(0)
        ctx.verifyErrorListenerCalledForActiveDoc(1, expectedException)

        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testCount() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        assertEquals(0, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))

        val doc1 = BsonDocument("hello", BsonString("world"))
        val doc2 = BsonDocument("goodbye", BsonString("computer"))

        ctx.dataSynchronizer.insertMany(ctx.namespace, listOf(doc1, doc2))

        assertEquals(2, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))

        assertEquals(1, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument(), CountOptions().limit(1)))

        assertEquals(1, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument("_id", doc1["_id"])))

        ctx.dataSynchronizer.deleteMany(ctx.namespace, BsonDocument())

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        assertEquals(0, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))
    }

    @Test
    fun testAggregate() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        assertEquals(0, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))

        val doc1 = BsonDocument("hello", BsonString("world")).append("a", BsonString("b"))
        val doc2 = BsonDocument("hello", BsonString("computer")).append("a", BsonString("b"))

        ctx.dataSynchronizer.insertMany(ctx.namespace, listOf(doc1, doc2))

        val iterable = ctx.dataSynchronizer.aggregate(ctx.namespace,
                listOf(
                    BsonDocument(
                        "\$project",
                        BsonDocument("_id", BsonInt32(0))
                            .append("a", BsonInt32(0))
                    ),
                    BsonDocument(
                        "\$match",
                        BsonDocument("hello", BsonString("computer"))
                    )))

        assertEquals(2, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))
        assertEquals(1, iterable.count())

        val actualDoc = iterable.first()!!

        assertNull(actualDoc["a"])
        assertNull(actualDoc["_id"])
        assertEquals(BsonString("computer"), actualDoc["hello"])
    }

    @Test
    fun testInsertOne() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()

        val expectedEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true)

        ctx.deleteTestDocument()

        ctx.insertTestDocument()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedEvent)

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testInsertMany() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        val doc1 = BsonDocument("hello", BsonString("world"))
        val doc2 = BsonDocument("goodbye", BsonString("computer"))

        ctx.dataSynchronizer.insertMany(ctx.namespace, listOf(doc1, doc2))

        val expectedEvent1 = ChangeEvent.changeEventForLocalInsert(ctx.namespace, doc1, true)
        val expectedEvent2 = ChangeEvent.changeEventForLocalInsert(ctx.namespace, doc2, true)

        ctx.waitForEvents(amount = 2)

        ctx.verifyChangeEventListenerCalledForActiveDoc(2, expectedEvent1, expectedEvent2)

        assertEquals(
            doc1,
            ctx.dataSynchronizer.find(
                ctx.namespace,
                BsonDocument("_id", doc1["_id"]),
                0,
                null,
                null,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec())
            ).firstOrNull())
        assertEquals(
            doc2,
            ctx.dataSynchronizer.find(
                ctx.namespace,
                BsonDocument("_id", doc2["_id"]),
                0,
                null,
                null,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec())
            ).firstOrNull())
    }

    @Test
    fun testUpdateOne() {
        val ctx = harness.freshTestContext()
        val expectedDocumentAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", ctx.testDocumentId)
        // assert this doc does not exist
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // update the non-existent document...
        var updateResult = ctx.updateTestDocument()
        // ...which should continue to not exist...
        assertNull(ctx.findTestDocumentFromLocalCollection())
        // ...and result in an "empty" UpdateResult
        assertEquals(0, updateResult.matchedCount)
        assertEquals(0, updateResult.modifiedCount)
        assertNull(updateResult.upsertedId)
        assertTrue(updateResult.wasAcknowledged())

        // insert the initial document
        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1)

        // do the actual update
        updateResult = ctx.updateTestDocument()
        ctx.waitForEvents()

        // assert the UpdateResult is non-zero
        assertEquals(1, updateResult.matchedCount)
        assertEquals(1, updateResult.modifiedCount)
        assertNull(updateResult.upsertedId)
        assertTrue(updateResult.wasAcknowledged())
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalUpdate(
                ctx.namespace, ctx.testDocumentId, ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()), expectedDocumentAfterUpdate, true))
        // assert that the updated document equals what we've expected
        assertEquals(ctx.testDocument["_id"], ctx.findTestDocumentFromLocalCollection()?.get("_id"))
        assertEquals(expectedDocumentAfterUpdate, ctx.findTestDocumentFromLocalCollection()!!)
    }

    @Test
    fun testUpsertOne() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        val doc1 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(1))

        val result = ctx.dataSynchronizer.updateOne(
            ctx.namespace,
            BsonDocument("name", BsonString("philip")),
            BsonDocument("\$inc", BsonDocument("count", BsonInt32(1))),
            UpdateOptions().upsert(true))

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        assertEquals(1, result.matchedCount)
        assertEquals(1, result.modifiedCount)
        assertNotNull(result.upsertedId)

        val expectedEvent1 = ChangeEvent.changeEventForLocalInsert(ctx.namespace,
            doc1.append("_id", result.upsertedId), true)

        ctx.waitForEvents(amount = 1)

        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            expectedEvent1)

        assertEquals(
            doc1,
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", doc1["_id"])).first())

        // assert that the stream was opened
        ctx.verifyWatchFunctionCalled(1, expectedArgs =
        Document(mapOf(
            "database" to ctx.namespace.databaseName,
            "collection" to ctx.namespace.collectionName,
            "ids" to setOf(result.upsertedId)
        )))

        ctx.dataSynchronizer.updateOne(
            ctx.namespace,
            BsonDocument("name", BsonString("philip")),
            BsonDocument("\$inc", BsonDocument("count", BsonInt32(1))))

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        ctx.waitForEvents(amount = 2)

        val expectedDocAfterUpdate1 = BsonDocument("name", BsonString("philip"))
            .append("count", BsonInt32(2)).append("_id", doc1["_id"])

        assertEquals(
            expectedDocAfterUpdate1,
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", doc1["_id"])).first())
    }

    @Test
    fun testUpdateMany() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        val doc1 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(1))
        val doc2 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(1))
        val doc3 = BsonDocument("name", BsonString("timothy")).append("count", BsonInt32(1))

        ctx.dataSynchronizer.insertMany(ctx.namespace, listOf(doc1, doc2, doc3))

        val expectedEvent1 = ChangeEvent.changeEventForLocalInsert(ctx.namespace, doc1, true)
        val expectedEvent2 = ChangeEvent.changeEventForLocalInsert(ctx.namespace, doc2, true)
        val expectedEvent3 = ChangeEvent.changeEventForLocalInsert(ctx.namespace, doc3, true)

        ctx.waitForEvents(amount = 3)

        ctx.verifyChangeEventListenerCalledForActiveDoc(
            3,
            expectedEvent1,
            expectedEvent2,
            expectedEvent3)

        val result = ctx.dataSynchronizer.updateMany(
            ctx.namespace,
            BsonDocument("name", BsonString("philip")),
            BsonDocument("\$set", BsonDocument("count", BsonInt32(2))),
            UpdateOptions().upsert(true)) // ensure there wasn't an unnecessary insert

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        ctx.findTestDocumentFromLocalCollection()
        assertEquals(2, result.modifiedCount)
        assertEquals(2, result.matchedCount)
        assertNull(result.upsertedId)

        ctx.waitForEvents(amount = 2)

        val expectedDocAfterUpdate1 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(2)).append("_id", doc1["_id"])
        val expectedDocAfterUpdate2 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(2)).append("_id", doc2["_id"])

        ctx.verifyChangeEventListenerCalledForActiveDoc(
            5,
            expectedEvent1,
            expectedEvent2,
            expectedEvent3,
            ChangeEvent.changeEventForLocalUpdate(
                ctx.namespace,
                doc1["_id"],
                ChangeEvent.UpdateDescription(
                    BsonDocument("count", BsonInt32(2)),
                    listOf()
                ),
                expectedDocAfterUpdate1,
                true),
            ChangeEvent.changeEventForLocalUpdate(
                ctx.namespace,
                doc2["_id"],
                ChangeEvent.UpdateDescription(
                    BsonDocument("count", BsonInt32(2)),
                    listOf()
                ),
                expectedDocAfterUpdate2,
                true))

        assertEquals(
            expectedDocAfterUpdate1,
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", doc1["_id"])).first())
        assertEquals(
            expectedDocAfterUpdate2,
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", doc2["_id"])).first())
        assertEquals(
            doc3,
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", doc3["_id"])).first())
    }

    @Test
    fun testUpsertMany() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        val doc1 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(2))

        var result = ctx.dataSynchronizer.updateMany(
            ctx.namespace,
            BsonDocument("name", BsonString("philip")),
            BsonDocument("\$set", BsonDocument("count", BsonInt32(2))),
            UpdateOptions().upsert(true))

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        assertEquals(0, result.matchedCount)
        assertEquals(0, result.modifiedCount)
        assertNotNull(result.upsertedId)

        val expectedEvent1 = ChangeEvent.changeEventForLocalInsert(
            ctx.namespace,
            doc1.append("_id", result.upsertedId), true)

        ctx.waitForEvents(amount = 1)

        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            expectedEvent1)

        assertEquals(
            doc1,
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", result.upsertedId)).first())

        // assert that the stream was opened
        ctx.verifyWatchFunctionCalled(1, expectedArgs =
        Document(mapOf(
            "database" to ctx.namespace.databaseName,
            "collection" to ctx.namespace.collectionName,
            "ids" to setOf(result.upsertedId)
        )))

        ctx.doSyncPass()

        ctx.queueConsumableRemoteUpdateEvent(
            id = result.upsertedId!!,
            document = BsonDocument(
                "name",
                BsonString("philip")
            ).append("count", BsonInt32(3)).append("_id", result.upsertedId))
        ctx.doSyncPass()
        assertEquals(
            BsonDocument("name", BsonString("philip")).append("count", BsonInt32(3)).append("_id", result.upsertedId),
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", result.upsertedId)).first())

        val doc2 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(1))

        ctx.dataSynchronizer.insertOne(ctx.namespace, doc2)

        result = ctx.dataSynchronizer.updateMany(
            ctx.namespace,
            BsonDocument("name", BsonString("philip")),
            BsonDocument("\$set", BsonDocument("count", BsonInt32(3))),
            UpdateOptions().upsert(true))

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        assertEquals(2, result.matchedCount)
        assertEquals(1, result.modifiedCount)
        assertNull(result.upsertedId)

        // there should only be 2 events instead of 3 since only 1 document was modified
        ctx.waitForEvents(amount = 2)

        val expectedDocAfterUpdate1 = BsonDocument("name", BsonString("philip")).append("count", BsonInt32(3)).append("_id", doc1["_id"])

        assertEquals(
            expectedDocAfterUpdate1,
            ctx.dataSynchronizer.find(ctx.namespace, BsonDocument("_id", doc1["_id"])).first())

        assertTrue(ctx.dataSynchronizer.areAllStreamsOpen())
    }

    @Test
    fun testDeleteOne() {
        val ctx = harness.freshTestContext()

        // 0: Pre-checks
        // assert this doc does not exist
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // delete the non-existent document...
        var deleteResult = ctx.deleteTestDocument()
        // ...which should continue to not exist...
        assertNull(ctx.findTestDocumentFromLocalCollection())
        // ...and result in an "empty" DeleteResult
        assertEquals(0, deleteResult.deletedCount)
        assertTrue(deleteResult.wasAcknowledged())

        // 1: Insert -> Delete -> Coalescence
        // insert the initial document
        ctx.insertTestDocument()
        ctx.waitForEvents()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1)

        // do the actual delete
        deleteResult = ctx.deleteTestDocument()
        // assert the DeleteResult is non-zero, and that a (new) change event was not
        // called (coalescence). verify desync was called
        assertEquals(1, deleteResult.deletedCount)
        assertTrue(deleteResult.wasAcknowledged())
        verify(ctx.dataSynchronizer).desyncDocumentFromRemote(eq(ctx.namespace), eq(ctx.testDocumentId))
        // assert that the updated document equals what we've expected
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // 2: Insert -> Update -> Delete -> Event Emission
        // insert the initial document
        ctx.insertTestDocument()
        ctx.doSyncPass()

        // do the actual delete
        deleteResult = ctx.deleteTestDocument()
        ctx.waitForEvents()

        // assert the UpdateResult is non-zero
        assertEquals(1, deleteResult.deletedCount)
        assertTrue(deleteResult.wasAcknowledged())
        ctx.verifyChangeEventListenerCalledForActiveDoc(1,
            ChangeEvent.changeEventForLocalDelete(
                ctx.namespace, ctx.testDocumentId, true
            ))
        // assert that the updated document equals what we've expected
        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testDeleteMany() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        var result = ctx.dataSynchronizer.deleteMany(ctx.namespace, BsonDocument())

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        assertEquals(0, result.deletedCount)
        assertEquals(0, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))

        val doc1 = BsonDocument("hello", BsonString("world"))
        val doc2 = BsonDocument("goodbye", BsonString("computer"))

        ctx.dataSynchronizer.insertMany(ctx.namespace, listOf(doc1, doc2))

        assertEquals(2, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))

        result = ctx.dataSynchronizer.deleteMany(ctx.namespace, BsonDocument())

        // verify that we didn't accidentally leak any documents that will be "recovered" later
        ctx.verifyUndoCollectionEmpty()

        assertEquals(2, result.deletedCount)
        assertEquals(0, ctx.dataSynchronizer.count(ctx.namespace, BsonDocument()))
        assertNull(ctx.dataSynchronizer.find(ctx.namespace, BsonDocument()).firstOrNull())
    }

    @Test
    fun testConfigure() {
        val ctx = harness.freshTestContext(false)
        ctx.verifyStartCalled(0)

        // without a configuration it should not be
        // configured or running
        assertFalse(ctx.dataSynchronizer.isRunning)

        // this call will configure the data synchronizer
        ctx.insertTestDocument()

        ctx.verifyStartCalled(1)

        ctx.deleteTestDocument()

        ctx.insertTestDocument()
        ctx.waitForEvents()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalInsert(
            ctx.namespace, ctx.testDocument, true))
        assertTrue(ctx.dataSynchronizer.isRunning)
    }

    @Test
    fun testResumeSyncForDocument() {
        val ctx = harness.freshTestContext()

        // assert that resume returns false for a doc that doesn't exist yet
        assertFalse(ctx.dataSynchronizer.resumeSyncForDocument(ctx.namespace, ctx.testDocumentId))

        // insert and sync
        ctx.insertTestDocument()
        ctx.doSyncPass()

        // throw and exception on the next sync pass, pausing the
        // document config
        ctx.exceptionToThrowDuringConflict = Exception("intentional")
        ctx.queueConsumableRemoteUnknownEvent()
        ctx.doSyncPass()

        // assert that the doc is paused
        assertEquals(
            ctx.testDocumentId,
            ctx.dataSynchronizer.getPausedDocumentIds(ctx.namespace).firstOrNull())

        // attempt a remote delete, which should fail
        ctx.queueConsumableRemoteDeleteEvent()
        ctx.doSyncPass()
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        // assert that resume returns true for our paused doc
        assertTrue(ctx.dataSynchronizer.resumeSyncForDocument(ctx.namespace, ctx.testDocumentId))
        assertTrue(ctx.dataSynchronizer.getPausedDocumentIds(ctx.namespace).isEmpty())

        // queue another remote delete, one that should work
        // now that the document is no longer paused
        ctx.queueConsumableRemoteDeleteEvent()
        ctx.doSyncPass()
        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testSyncVersionConflictedUpdateRemoteWins() {
        val ctx = harness.freshTestContext()
        // setup our expectations

        // 1: Update -> Conflict -> Delete (remote wins)
        // insert a new document, and sync.
        ctx.insertTestDocument()
        ctx.waitForEvents(1)
        ctx.doSyncPass()

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvents(1)

        // create conflict here by claiming there is no remote doc to update
        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))

        // do a sync pass, addressing the conflict
        ctx.doSyncPass()
        ctx.waitForEvents(1)
        val captor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock).insertOne(captor.capture())

        // verify that a conflict has been handled
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)

        // since we've accepted the remote result, this doc will have been deleted
        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testSyncVersionConflictedUpdateLocalWins() {
        // 1: Update -> Conflict -> Update (local wins)
        // reset (delete, insert, sync)
        val ctx = harness.freshTestContext()

        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))
        ctx.insertTestDocument()
        ctx.waitForEvents(1)
        ctx.doSyncPass()
        ctx.waitForEvents(1)
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1,
            ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvents(1)

        // do a sync pass, addressing the conflict. let local win
        ctx.shouldConflictBeResolvedByRemote = false

        ctx.doSyncPass()
        ctx.waitForEvents(1)

        val insertOneCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock).insertOne(insertOneCaptor.capture())
        val previousVersion =
            DocumentVersionInfo.fromVersionDoc(insertOneCaptor.value["__stitch_sync_version"]!!.asDocument())

        assertEquals(0, previousVersion.version.versionCounter)
        // verify that a conflict has been handled,
        ctx.verifyConflictHandlerCalledForActiveDoc(1)

        // since we've accepted the local result, this doc will have been updated remotely
        // and sync'd locally
        ctx.updateTestDocument()
        ctx.doSyncPass()

        val filterCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        val updateVersionCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock).updateOne(filterCaptor.capture(), updateVersionCaptor.capture())

        val filterVersion = DocumentVersionInfo.fromVersionDoc(
            updateVersionCaptor.value["\$set"]!!.asDocument()["__stitch_sync_version"]!!.asDocument())
        val nextVersionAfterUpdate = DocumentVersionInfo.fromVersionDoc(
            filterCaptor.value["__stitch_sync_version"]!!.asDocument())
        assertEquals(
            filterVersion.version.versionCounter,
            nextVersionAfterUpdate.version.versionCounter + 1)
        assertEquals(filterVersion.version.instanceId, nextVersionAfterUpdate.version.instanceId)
        assertEquals(previousVersion.version.instanceId, nextVersionAfterUpdate.version.instanceId)
    }

    @Test
    fun testRemoteUpdateLocalAndRemoteEmptyVersion() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote
        ctx.insertTestDocument()
        ctx.doSyncPass()

        // update the doc locally and queue a fake update remotely.
        // neither of these will have versions.
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NONE)

        ctx.shouldConflictBeResolvedByRemote = true
        // sync, creating a conflict. because they have the same empty version,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote.
        ctx.doSyncPass()

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalOrRemoteEmptyVersion() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote.
        // this time, add a version to the local doc
        ctx.insertTestDocument()
        ctx.doSyncPass()
        // update the doc locally and queue a fake update remotely.
        // neither of these will have versions.
        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NONE)

        ctx.shouldConflictBeResolvedByRemote = true
        // sync, creating a conflict. because remote has an empty version,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote.
        ctx.doSyncPass()

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalVersionEqual() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote.
        // this time, add a version to the local doc
        ctx.insertTestDocument()
        ctx.doSyncPass()
        // update the doc locally and queue a fake update remotely.
        // local and remote will have equal versions
        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.SAME)

        ctx.shouldConflictBeResolvedByRemote = false
        // sync, creating a conflict. because remote and local have equal versions,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote.
        ctx.doSyncPass()

        assertEquals(BsonDocument("_id", ctx.testDocumentId).append("count", BsonInt32(2)),
            ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalVersionHigher() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote.
        // this time, add a version to the local doc
        ctx.insertTestDocument()
        ctx.doSyncPass()
        // update the doc locally and queue a fake update remotely.
        // the local version will be higher than the remote version
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NEXT)

        ctx.shouldConflictBeResolvedByRemote = true
        // sync, creating a conflict. because local version has a higher version,
        // the update will not have gone through
        ctx.doSyncPass()

        ctx.updateTestDocument()

        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument.append("oh", BsonString("joy")),
            TestVersionState.PREVIOUS)

        ctx.doSyncPass()

        // the update should not have gone through
        assertEquals(
            BsonDocument("count", BsonInt32(2))
                .append("hello", BsonString("dolly"))
                .append("_id", ctx.testDocumentId),
            ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateRemoteVersionHigher() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote.
        // this time, add a version to the local doc
        ctx.insertTestDocument()
        ctx.doSyncPass()
        // update the doc locally and queue a fake update remotely.
        // the remote doc will have a higher version than the local
        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NEXT)

        ctx.shouldConflictBeResolvedByRemote = true
        // sync, creating a conflict. because remote has a higher version,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote.
        ctx.doSyncPass()

        // the update will go through
        assertEquals(withoutSyncVersion(pseudoUpdatedDocument),
            ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateDifferentGUIDs() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote.
        // this time, add a version to the local doc
        ctx.insertTestDocument()
        ctx.doSyncPass()
        // update the doc locally and queue a fake update remotely.
        // neither of these will have versions.
        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NEW)

        ctx.shouldConflictBeResolvedByRemote = true
        // sync, creating a conflict. because remote has an empty version,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote. however, this will be resolved as a delete event due
        // to the different guids and lack of a (mocked) remote document
        ctx.doSyncPass()

        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateDifferentGUIDsNewDoc() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote.
        // this time, add a version to the local doc
        ctx.insertTestDocument()
        ctx.doSyncPass()
        // update the doc locally and queue a fake update remotely.
        // these docs will contain different GUIDs for their versions
        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NEW)

        ctx.shouldConflictBeResolvedByRemote = true

        // The remote event is stale (but has a document with a new version GUID),
        // but the remote collection itself no longer has the document,
        // so the conflict is using the latest remote document
        // which doesn't exist to resolve the conflict
        val findMock = mock(CoreRemoteFindIterableImpl::class.java)
        `when`(findMock.first()).thenReturn(pseudoUpdatedDocument)
        `when`(ctx.collectionMock.find(any())).thenReturn(findMock as CoreRemoteFindIterable<BsonDocument>)
        // sync, creating a conflict. because remote has an empty version,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote. however, this will be resolved as a REPLACE since
        // now we also have a new (mocked) doc with the new guid
        ctx.doSyncPass()

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRecoverUpdateNoPendingWrite() {
        val origCtx = harness.freshTestContext()

        origCtx.insertTestDocument()
        origCtx.doSyncPass()

        val testDocumentId = origCtx.testDocumentId
        val originalTestDocument = origCtx.testDocument

        origCtx.dataSynchronizer.stop()

        // simulate a failure case where an update started, but did not get pending writes set
        origCtx.localCollection
                .updateOne(
                        BsonDocument("_id", testDocumentId),
                        BsonDocument("\$set", BsonDocument("oops", BsonBoolean(true)))
                )

        val ctx = harness.testContextFromExistingContext(
                origCtx, Collections.singletonList(originalTestDocument)
        )

        ctx.verifyUndoCollectionEmpty()

        // assert that the update got rolled back
        assertEquals(originalTestDocument,
                ctx.dataSynchronizer.find(
                        ctx.namespace,
                        BsonDocument("_id", testDocumentId)
                ).firstOrNull()
        )
    }

    @Test
    fun testRecoverUpdateWithPendingWrite() {
        val origCtx = harness.freshTestContext()

        origCtx.insertTestDocument()
        origCtx.doSyncPass()

        val testDocumentId = origCtx.testDocumentId
        val originalTestDocument = origCtx.testDocument

        origCtx.dataSynchronizer.stop()

        // simulate a failure case where an update started and got pending writes set, but the undo
        // document still exists
        origCtx.localCollection
                .updateOne(
                        BsonDocument("_id", testDocumentId),
                        BsonDocument("\$set", BsonDocument("oops", BsonBoolean(true)))
                )

        val expectedNewDocument = originalTestDocument.append("oops", BsonBoolean(true))

        origCtx.setPendingWritesForDocId(
                testDocumentId,
                ChangeEvent.changeEventForLocalUpdate(
                        origCtx.namespace,
                        testDocumentId,
                        ChangeEvent.UpdateDescription.diff(originalTestDocument, expectedNewDocument),
                        expectedNewDocument,
                        true
                ))

        val ctx = harness.testContextFromExistingContext(
                origCtx, Collections.singletonList(originalTestDocument)
        )

        ctx.verifyUndoCollectionEmpty()

        // assert that the update did not get rolled back, since we set pending writes
        assertEquals(expectedNewDocument,
                ctx.dataSynchronizer.find(
                        ctx.namespace,
                        BsonDocument("_id", testDocumentId)
                ).firstOrNull()
        )
    }

    @Test
    fun testRecoverDeleteNoPendingWrite() {
        val origCtx = harness.freshTestContext()

        origCtx.insertTestDocument()
        origCtx.doSyncPass()

        val testDocumentId = origCtx.testDocumentId
        val originalTestDocument = origCtx.testDocument

        origCtx.dataSynchronizer.stop()

        // simulate a failure case where a delete started, but did not get pending writes set
        origCtx.localCollection
                .deleteOne(
                        BsonDocument("_id", testDocumentId)
                )

        val ctx = harness.testContextFromExistingContext(
                origCtx, Collections.singletonList(originalTestDocument)
        )

        ctx.verifyUndoCollectionEmpty()

        // assert that the delete got rolled back
        assertEquals(originalTestDocument,
                ctx.dataSynchronizer.find(
                        ctx.namespace,
                        BsonDocument("_id", testDocumentId)
                ).firstOrNull()
        )
    }

    @Test
    fun testRecoverDeleteWithPendingWrite() {
        val origCtx = harness.freshTestContext()

        origCtx.insertTestDocument()
        origCtx.doSyncPass()

        val testDocumentId = origCtx.testDocumentId
        val originalTestDocument = origCtx.testDocument

        origCtx.dataSynchronizer.stop()

        // simulate a failure case where a delete started and got pending writes, but the undo
        // document still exists
        origCtx.localCollection
                .deleteOne(
                        BsonDocument("_id", testDocumentId)
                )

        origCtx.setPendingWritesForDocId(
                testDocumentId,
                ChangeEvent.changeEventForLocalDelete(
                        origCtx.namespace,
                        testDocumentId,
                        true
                ))

        val ctx = harness.testContextFromExistingContext(
                origCtx, Collections.singletonList(originalTestDocument)
        )

        ctx.verifyUndoCollectionEmpty()

        // assert that the delete did not get rolled back, since we already set pending writes
        assertNull(
                ctx.dataSynchronizer.find(
                        ctx.namespace,
                        BsonDocument("_id", testDocumentId)
                ).firstOrNull()
        )
    }

    @Test
    fun testRecoverUpdateOldPendingWrite() {
        val origCtx = harness.freshTestContext()

        origCtx.insertTestDocument()
        origCtx.doSyncPass()

        val testDocumentId = origCtx.testDocumentId

        origCtx.dataSynchronizer.stop()

        // simulate a failure case where an update started, but did not get pending writes set, and
        // a previous completed update event is pending but uncommitted
        origCtx.updateTestDocument()

        val expectedTestDocument = origCtx.testDocument.clone()

        expectedTestDocument["count"] = BsonInt32(2)

        origCtx.localCollection
                .updateOne(
                        BsonDocument("_id", testDocumentId),
                        BsonDocument("\$set", BsonDocument("oops", BsonBoolean(true)))
                )

        val ctx = harness.testContextFromExistingContext(
                origCtx, Collections.singletonList(expectedTestDocument)
        )

        ctx.verifyUndoCollectionEmpty()

        // assert that the update got rolled back to the state of the previous completed update
        // that had uncommitted pending writes
        assertEquals(expectedTestDocument,
                ctx.dataSynchronizer.find(
                        ctx.namespace,
                        BsonDocument("_id", testDocumentId)
                ).firstOrNull()
        )
    }

    @Test
    fun testRecoverUnsychronizedDocument() {
        val origCtx = harness.freshTestContext()

        origCtx.insertTestDocument()
        origCtx.doSyncPass()

        val testDocumentId = origCtx.testDocumentId

        origCtx.dataSynchronizer.stop()

        origCtx.localCollection
                .updateOne(
                        BsonDocument("_id", testDocumentId),
                        BsonDocument("\$set", BsonDocument("oops", BsonBoolean(true)))
                )

        // simulate a pathological case where a user tries to insert arbitrary documents into the
        // undo collection
        val fakeRecoveryDocumentId = BsonObjectId()
        val fakeRecoveryDocument = BsonDocument()
                .append("_id", fakeRecoveryDocumentId)
                .append("hello collection", BsonString("my old friend"))

        val ctx = harness.testContextFromExistingContext(
                origCtx, Collections.singletonList(fakeRecoveryDocument)
        )

        ctx.verifyUndoCollectionEmpty()

        // ensure that undo documents that represent unsynchronized documents don't exist in the
        // local collection after a recovery pass
        assertNull(
                ctx.dataSynchronizer.find(
                        ctx.namespace,
                        BsonDocument("_id", fakeRecoveryDocumentId)
                ).firstOrNull()
        )
    }

    @Test
    fun testReinitialize() {
        val ctx = harness.freshTestContext(false)

        ctx.dataSynchronizer.reinitialize(ctx.localClient)

        ctx.verifyStopCalled(1)

        ctx.verifyStartCalled(1)

        // without a configuration it should not be
        // configured or running
        assertFalse(ctx.dataSynchronizer.isRunning)
    }
}
