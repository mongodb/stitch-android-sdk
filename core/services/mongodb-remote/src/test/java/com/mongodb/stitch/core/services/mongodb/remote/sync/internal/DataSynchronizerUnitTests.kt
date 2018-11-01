package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncUnitTestHarness.Companion.withoutSyncVersion
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.junit.After

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.lang.Exception

class DataSynchronizerUnitTests {
    companion object {
        private fun setupPendingReplace(
            ctx: DataSynchronizerTestContext,
            expectedDocument: BsonDocument,
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
                ctx.waitForEvent()
            }

            val expectedChangeEvent = if (shouldConflictBeResolvedByRemote)
                ChangeEvent.changeEventForLocalDelete(ctx.namespace, ctx.testDocumentId, false)
            else ChangeEvent.changeEventForLocalInsert(ctx.namespace, expectedDocument, true)
            ctx.verifyChangeEventListenerCalledForActiveDoc(
                times = if (shouldWaitForError) 0 else 1,
                expectedChangeEvent = if (shouldWaitForError) null else expectedChangeEvent)
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
        ctx = harness.freshTestContext()
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
        ctx.waitForEvent()

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
        setupPendingReplace(ctx, expectedDocument)

        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))

        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(
                ctx.namespace, expectedDocument, false))
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
    }

    @Test
    fun testConflictedReplace() {
        var ctx = harness.freshTestContext()
        var expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)

        // 1: Replace -> Conflict -> Replace (local wins)
        setupPendingReplace(
            ctx,
            shouldConflictBeResolvedByRemote = false,
            expectedDocument = expectedDoc)

        // do a sync pass, addressing the conflict
        ctx.doSyncPass()
        ctx.waitForEvent()
        // verify that a change event has been emitted. the conflict will have been handled
        // in setupPendingReplace
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(
                ctx.namespace, expectedDoc, false
            ))
        ctx.verifyConflictHandlerCalledForActiveDoc(times = 0)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)

        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        // 2: Replace -> Conflict -> Delete (remote wins)
        ctx = harness.freshTestContext()
        expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        setupPendingReplace(ctx, expectedDoc, shouldConflictBeResolvedByRemote = true)

        ctx.verifyConflictHandlerCalledForActiveDoc(times = 1)
        ctx.verifyErrorListenerCalledForActiveDoc(times = 0)
        assertNull(ctx.findTestDocumentFromLocalCollection())

        // 3: Replace -> Conflict -> Exception -> Freeze
        ctx = harness.freshTestContext()
        expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        ctx.exceptionToThrowDuringConflict = Exception("bad")
        // verify that, though the conflict handler was called, the exceptionToThrow was emitted
        // by the dataSynchronizer
        setupPendingReplace(ctx, expectedDoc, shouldWaitForError = true)
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        // clear issues. open a path for a delete.
        // do another sync pass. the doc should remain the same as it is paused
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
        ctx = harness.freshTestContext()
        expectedDoc = BsonDocument("count", BsonInt32(3)).append("_id", ctx.testDocumentId)
        ctx.queueConsumableRemoteUnknownEvent()
        setupPendingReplace(ctx, expectedDoc)

        ctx.queueConsumableRemoteUpdateEvent()
        ctx.doSyncPass()
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        // should be paused since the operation type was unknown
        ctx.queueConsumableRemoteUnknownEvent()
        ctx.doSyncPass()
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())

        ctx.queueConsumableRemoteDeleteEvent()
        ctx.doSyncPass()
        assertEquals(expectedDoc, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testSuccessfulUpdate() {
        val ctx = harness.freshTestContext()
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
                ChangeEvent.UpdateDescription(BsonDocument("count", BsonInt32(2)), listOf()),
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
        ctx.waitForEvent()
        ctx.doSyncPass()

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvent()

        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1,
            expectedChangeEvent = expectedLocalEvent)

        // create conflict here by claiming there is no remote doc to update
        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))

        // do a sync pass, addressing the conflict
        ctx.doSyncPass()
        ctx.waitForEvent()

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
        ctx.waitForEvent()
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1, expectedChangeEvent =
        ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvent()

        // do a sync pass, addressing the conflict. let local win
        ctx.shouldConflictBeResolvedByRemote = false

        ctx.doSyncPass()
        ctx.waitForEvent()

        // verify that a change event has been emitted, a conflict has been handled,
        // and no errors were emitted
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            times = 1,
            expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, docAfterUpdate, true))
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
        ctx.waitForEvent()
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(
            1, ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))
        ctx.doSyncPass()

        // update the reset doc
        ctx.updateTestDocument()
        ctx.waitForEvent()

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
        ctx.waitForEvent()

        // update the inserted doc, and prepare our exceptionToThrow
        ctx.updateTestDocument()
        ctx.waitForEvent()

        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1, expectedChangeEvent = expectedEvent)
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
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true))
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))

        // delete the document and wait
        ctx.deleteTestDocument()
        ctx.waitForEvent()

        // verify a delete event with pending writes is called
        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvent.changeEventForLocalDelete(
            ctx.namespace,
            ctx.testDocument["_id"],
            true))
        ctx.mockDeleteResult(RemoteDeleteResult(1))

        // sync. verify the correct doc was deleted and that a change event
        // with no pending writes was emitted
        ctx.doSyncPass()
        ctx.waitForEvent()
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
        ctx.waitForEvent()

        ctx.deleteTestDocument()
        ctx.waitForEvent()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedLocalEvent)

        // create conflict here
        // 1: Remote wins
        `when`(ctx.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(0))
        ctx.queueConsumableRemoteUpdateEvent()

        ctx.doSyncPass()
        ctx.waitForEvent()

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
        ctx.waitForEvent()

        ctx.deleteTestDocument()
        ctx.waitForEvent()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedLocalEvent)

        // create conflict here
        `when`(ctx.collectionMock.deleteOne(any())).thenReturn(RemoteDeleteResult(0))
        ctx.queueConsumableRemoteUpdateEvent()
        ctx.shouldConflictBeResolvedByRemote = false
        ctx.doSyncPass()
        ctx.waitForEvent()

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
        ctx.waitForEvent()

        ctx.doSyncPass()

        ctx.deleteTestDocument()
        ctx.waitForEvent()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedChangeEvent = expectedEvent)
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
    fun testInsertOneAndSync() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()

        val expectedEvent = ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true)

        ctx.deleteTestDocument()

        ctx.insertTestDocument()
        ctx.waitForEvent()

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, expectedEvent)

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testUpdateOneById() {
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
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(1)

        // do the actual update
        updateResult = ctx.updateTestDocument()
        ctx.waitForEvent()

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
    fun testDeleteOneById() {
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
        ctx.waitForEvent()
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
        ctx.waitForEvent()

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
        ctx.waitForEvent()

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
        ctx.waitForEvent()
        ctx.doSyncPass()

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvent()

        // create conflict here by claiming there is no remote doc to update
        ctx.mockUpdateResult(RemoteUpdateResult(0, 0, null))

        // do a sync pass, addressing the conflict
        ctx.doSyncPass()
        ctx.waitForEvent()
        val captor = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(ctx.collectionMock).insertOne(captor.capture())
        val versionDoc =
            DocumentVersionInfo.fromVersionDoc(captor.value["__stitch_sync_version"]!!.asDocument())
        println(versionDoc)

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
        ctx.waitForEvent()
        ctx.doSyncPass()
        ctx.waitForEvent()
        ctx.verifyChangeEventListenerCalledForActiveDoc(times = 1, expectedChangeEvent =
        ChangeEvent.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))

        // update the document and wait for the local update event
        ctx.updateTestDocument()
        ctx.waitForEvent()

        // do a sync pass, addressing the conflict. let local win
        ctx.shouldConflictBeResolvedByRemote = false

        ctx.doSyncPass()
        ctx.waitForEvent()

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
    fun testRemoteUpdateNoConflict() {

    }

    @Test
    fun testRemoteUpdateLocalAndRemoteEmptyVersion() {
        val ctx = harness.freshTestContext()

        // insert a new document and sync it to the remote
        ctx.insertTestDocument()
//        ctx.dataSynchronizer.swapSyncDirection(true)
        ctx.doSyncPass()
//
//        // update the doc locally and queue a fake update remotely.
//        // neither of these will have versions.
//        ctx.updateTestDocument()
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            DataSynchronizerTestContext.TestVersionState.NONE,
            pseudoUpdatedDocument)

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
            DataSynchronizerTestContext.TestVersionState.NONE,
            pseudoUpdatedDocument)

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
        ctx.dataSynchronizer.swapSyncDirection(true)
        // update the doc locally and queue a fake update remotely.
        // neither of these will have versions.
        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            DataSynchronizerTestContext.TestVersionState.SAME,
            pseudoUpdatedDocument)

        ctx.shouldConflictBeResolvedByRemote = false
        // sync, creating a conflict. because remote has an empty version,
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
        // neither of these will have versions.
        ctx.addVersionInfoToTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            DataSynchronizerTestContext.TestVersionState.NEXT,
            pseudoUpdatedDocument)

        ctx.shouldConflictBeResolvedByRemote = true
        // sync, creating a conflict. because remote has an empty version,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote.
        ctx.doSyncPass()

        ctx.updateTestDocument()

        ctx.queueConsumableRemoteUpdateEvent(
            DataSynchronizerTestContext.TestVersionState.PREVIOUS,
            pseudoUpdatedDocument.append("oh", BsonString("joy")))

        ctx.doSyncPass()

        assertEquals(withoutSyncVersion(pseudoUpdatedDocument),
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
        // neither of these will have versions.
        ctx.updateTestDocument()
        ctx.addVersionInfoToTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            DataSynchronizerTestContext.TestVersionState.NEXT,
            pseudoUpdatedDocument)

        ctx.shouldConflictBeResolvedByRemote = true
        // sync, creating a conflict. because remote has an empty version,
        // there will be a conflict on the next L2R pass that we will resolve
        // with remote.
        ctx.doSyncPass()

        assertEquals(withoutSyncVersion(pseudoUpdatedDocument),
            ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateDifferentGUIDs() {

    }

    @Test
    fun testRemoteUpdateLocalVersionNotEqual() {

    }
}
