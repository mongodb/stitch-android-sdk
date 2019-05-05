package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterableImpl
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncUnitTestHarness.Companion.withoutSyncVersion
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory

import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString

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
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.lang.Exception
import java.util.Collections

class DataSynchronizerUnitTests {
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

        ctx.verifyChangeEventListenerCalledForActiveDoc(1, ChangeEvents.changeEventForLocalInsert(
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
        ctx.shouldConflictBeResolvedByRemote = true
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
            ChangeEvents.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, false))

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
    fun testLastKnownRemoteVersionHashIsPersisted() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        val docConfig = ctx.findTestDocumentConfig()

        assertNotNull(docConfig?.lastKnownRemoteVersion)

        assertNotNull(docConfig?.lastKnownHash)
        assertTrue(docConfig?.lastKnownHash != 0L)
    }

    @Test
    fun testPendingWriteRemoteUpdateLocalAndRemoteEmptyVersionHashDifferent() {
        val ctx = harness.freshTestContext()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        ctx.insertTestDocument()
        ctx.setPendingWritesForDocId(ctx.testDocumentId,
                ChangeEvents.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true))

        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NONE)

        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(1)

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testPendingWriteRemoteUpdateLocalAndRemoteEmptyVersionHashSame() {
        val ctx = harness.freshTestContext()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        ctx.insertTestDocument()
        ctx.setPendingWritesForDocId(ctx.testDocumentId,
                ChangeEvents.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true))

        val pseudoUpdatedDocument = ctx.testDocument.clone()
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NONE)

        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testPendingWriteRemoteUpdateRemoteEmptyVersion() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NONE)

        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(1)

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testPendingWriteRemoteUpdateLocalEmptyVersion() {
        val ctx = harness.freshTestContext()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)
        ctx.setPendingWritesForDocId(ctx.testDocumentId,
                ChangeEvents.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true))

        val remoteVersion = DocumentVersionInfo.getFreshVersionDocument()
        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append(
                        DataSynchronizer.DOCUMENT_VERSION_FIELD,
                        remoteVersion
                )
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEW)

        ctx.doSyncPass()

        ctx.shouldConflictBeResolvedByRemote = true
        ctx.verifyConflictHandlerCalledForActiveDoc(1)

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testPendingWriteRemoteUpdateLocalVersionEqualHashSame() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone()
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.SAME)

        ctx.doSyncPass()
        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(ctx.testDocument.append("count", BsonInt32(2)),
                ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testPendingWriteRemoteUpdateLocalVersionEqualHashDifferentRemoteEventSameDevice() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.SAME)

        ctx.shouldConflictBeResolvedByRemote = true

        ctx.doSyncPass()
        ctx.verifyConflictHandlerCalledForActiveDoc(1)

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testPendingWriteRemoteUpdateLocalVersionEqualHashDifferentRemoteEventDifferentDevice() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.updateTestDocument()
        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone()
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEW)

        val findMock = mock(CoreRemoteFindIterableImpl::class.java)
        `when`(findMock.first()).thenReturn(pseudoUpdatedDocument)
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any())).thenReturn(findMock as CoreRemoteFindIterable<BsonDocument>)

        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()
        ctx.verifyConflictHandlerCalledForActiveDoc(1)

        assertEquals(ctx.testDocument.append("count", BsonInt32(1)),
                ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalVersionEqualHashDifferentRemoteEventDifferentDevice() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEW)

        val findMock = mock(CoreRemoteFindIterableImpl::class.java)
        `when`(findMock.first()).thenReturn(pseudoUpdatedDocument)
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any())).thenReturn(findMock as CoreRemoteFindIterable<BsonDocument>)

        ctx.doSyncPass()
        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateVersionDifferentDeviceDeletesLocalDocumentIfFindOperationReturnsNull() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()
        val pseudoUpdatedDocument = ctx.testDocument.clone()

        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEW)

        val findMock = mock(CoreRemoteFindIterableImpl::class.java)
        `when`(findMock.first()).thenReturn(null)
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any())).thenReturn(findMock as CoreRemoteFindIterable<BsonDocument>)

        ctx.doSyncPass()
        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertNull(ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalAndRemoteEmptyVersion() {
        val ctx = harness.freshTestContext()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NONE)

        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        val lastKnownVersion = ctx.findTestDocumentConfig()?.lastKnownRemoteVersion

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
        assertNotNull(lastKnownVersion)
        assertEquals(0, DocumentVersionInfo.fromVersionDoc(lastKnownVersion)
                .version.versionCounter)
        verify(ctx.collectionMock).updateOne(eq(ctx.testDocumentFilter),
                eq(pseudoUpdatedDocument.append(DataSynchronizer.DOCUMENT_VERSION_FIELD,
                        lastKnownVersion)))
    }

    @Test
    fun testRemoteUpdateLocalEmptyVersionHashSame() {
        val ctx = harness.freshTestContext()

        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val remoteVersion = DocumentVersionInfo.getFreshVersionDocument()
        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append(
                        DataSynchronizer.DOCUMENT_VERSION_FIELD,
                        remoteVersion
                )

        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.SAME)

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalEmptyVersionHashDifferent() {
        val ctx = harness.freshTestContext()

        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val remoteVersion = DocumentVersionInfo.getFreshVersionDocument()
        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append("hello", BsonString("dolly"))
                .append(
                        DataSynchronizer.DOCUMENT_VERSION_FIELD,
                        remoteVersion
                )
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.SAME)

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(withoutSyncVersion(pseudoUpdatedDocument),
                ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateRemoteEmptyVersionHashSame() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone()
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NONE)

        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateRemoteEmptyVersionHashDifferent() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        ctx.mockUpdateResult(RemoteUpdateResult(1, 1, null))
        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NONE)

        ctx.shouldConflictBeResolvedByRemote = true
        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalVersionEqualHashSame() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()
        val pseudoUpdatedDocument = ctx.testDocument.clone()

        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.SAME)

        ctx.doSyncPass()
        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalVersionEqualHashSameRemoteEventDifferentDevice() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()
        val pseudoUpdatedDocument = ctx.testDocument.clone()

        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEW)

        val findMock = mock(CoreRemoteFindIterableImpl::class.java)
        `when`(findMock.first()).thenReturn(pseudoUpdatedDocument)
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any())).thenReturn(findMock as CoreRemoteFindIterable<BsonDocument>)

        ctx.doSyncPass()
        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(withoutSyncVersion(pseudoUpdatedDocument),
                ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalVersionHigherRemoteEventDifferentDevice() {
        val ctx = harness.freshTestContext()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEXT)

        ctx.doSyncPass()

        val findMock = mock(CoreRemoteFindIterableImpl::class.java)
        `when`(findMock.first()).thenReturn(pseudoUpdatedDocument)
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any())).thenReturn(findMock as CoreRemoteFindIterable<BsonDocument>)

        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEW)

        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)
        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateLocalVersionHigherRemoteEventSameDevice() {
        val ctx = harness.freshTestContext()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        val pseudoUpdatedDocument = ctx.testDocument.clone()
                .append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.NEXT)

        ctx.doSyncPass()

        ctx.queueConsumableRemoteUpdateEvent(
                ctx.testDocumentId,
                pseudoUpdatedDocument,
                TestVersionState.SAME)

        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)
        assertEquals(pseudoUpdatedDocument, ctx.findTestDocumentFromLocalCollection())
    }

    @Test
    fun testRemoteUpdateRemoteVersionHigher() {
        val ctx = harness.freshTestContext()

        ctx.insertTestDocument()
        ctx.doSyncPass()

        val pseudoUpdatedDocument = ctx.testDocument.clone().append("hello", BsonString("dolly"))
        ctx.queueConsumableRemoteUpdateEvent(
            ctx.testDocumentId,
            pseudoUpdatedDocument,
            TestVersionState.NEXT)

        ctx.doSyncPass()

        ctx.verifyConflictHandlerCalledForActiveDoc(0)

        assertEquals(withoutSyncVersion(pseudoUpdatedDocument),
            ctx.findTestDocumentFromLocalCollection())
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
        @Suppress("UNCHECKED_CAST")
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
                ChangeEvents.changeEventForLocalUpdate(
                        origCtx.namespace,
                        testDocumentId,
                        UpdateDescription.diff(originalTestDocument, expectedNewDocument),
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
                ChangeEvents.changeEventForLocalDelete(
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
        ctx.dataSynchronizer.waitUntilInitialized()

        ctx.verifyStopCalled(1)

        ctx.verifyStartCalled(1)

        // without a configuration it should not be
        // configured or running
        assertFalse(ctx.dataSynchronizer.isRunning)
    }

    @Test
    fun testMissingDocument() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        ctx.dataSynchronizer.stop()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        ctx.waitForDataSynchronizerStreams()

        ctx.doSyncPass()

        val mockEmptyFindResult = mock(CoreRemoteFindIterableImpl::class.java)
        @Suppress("UNCHECKED_CAST")
        `when`(mockEmptyFindResult
                .into(any(MutableCollection::class.java as Class<MutableCollection<Any>>)))
                .thenReturn(HashSet())
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any()))
                .thenReturn(mockEmptyFindResult as CoreRemoteFindIterable<BsonDocument>)

        verify(ctx.collectionMock, times(1)).find(any())

        ctx.doSyncPass()

        verify(ctx.collectionMock, times(1)).find(any())
    }

    @Test
    fun testMissingDocumentThatAppearsLaterAsInsertEvent() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        ctx.dataSynchronizer.stop()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        ctx.waitForDataSynchronizerStreams()

        ctx.doSyncPass()

        val mockEmptyFindResult = mock(CoreRemoteFindIterableImpl::class.java)
        @Suppress("UNCHECKED_CAST")
        `when`(mockEmptyFindResult
                .into(any(MutableCollection::class.java as Class<MutableCollection<Any>>)))
                .thenReturn(HashSet())
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any()))
                .thenReturn(mockEmptyFindResult as CoreRemoteFindIterable<BsonDocument>)

        ctx.queueConsumableRemoteInsertEvent()
        ctx.doSyncPass()
        ctx.waitForEvents()

        val localDoc = ctx.dataSynchronizer
                .find(ctx.namespace, BsonDocument().append("_id", ctx.testDocumentId)).firstOrNull()
        assertEquals(ctx.testDocumentId, localDoc?.get("_id"))
    }

    @Test
    fun testMissingDocumentThatAppearsLaterAsUpdateEvent() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        ctx.dataSynchronizer.stop()

        ctx.dataSynchronizer.syncDocumentsFromRemote(ctx.namespace, ctx.testDocumentId)

        ctx.doSyncPass()

        val mockEmptyFindResult = mock(CoreRemoteFindIterableImpl::class.java)
        @Suppress("UNCHECKED_CAST")
        `when`(mockEmptyFindResult
                .into(any(MutableCollection::class.java as Class<MutableCollection<Any>>)))
                .thenReturn(HashSet())
        @Suppress("UNCHECKED_CAST")
        `when`(ctx.collectionMock.find(any()))
                .thenReturn(mockEmptyFindResult as CoreRemoteFindIterable<BsonDocument>)

        ctx.queueConsumableRemoteUpdateEvent()
        ctx.doSyncPass()
        ctx.waitForEvents()

        val localDoc = ctx.dataSynchronizer
                .find(ctx.namespace, BsonDocument().append("_id", ctx.testDocumentId)).firstOrNull()
        assertEquals(ctx.testDocumentId, localDoc?.get("_id"))
    }

    @Test
    fun testRecovery() {
        val ctx = harness.freshTestContext()

        ctx.reconfigure()

        ctx.dataSynchronizer.stop()

        val doc = BsonDocument()
        ctx.dataSynchronizer.insertOne(ctx.namespace, doc)

        val id = doc["_id"]
        val filter = BsonDocument("_id", id)
        val batchOps = LocalSyncWriteModelContainer(
                ctx.dataSynchronizer.syncConfig.getNamespaceConfig(ctx.namespace),
                ctx.dataSynchronizer.getLocalCollection(ctx.namespace),
                ctx.collectionMock,
                ctx.dataSynchronizer.getUndoCollection(ctx.namespace),
                EventDispatcher(ctx.instanceKey, ctx.dispatcher))
        batchOps.addDocIDs(id)

        // cause the batching to fail after the undo docs have been inserted
        try {
            batchOps.wrapForRecovery {
                throw Exception()
            }
        } catch (e: Exception) {
            println(e)
        }

        // go underneath the DataSynchronizer and delete the document to see if it is readded after
        // recovery
        assertEquals(
            1,
            ctx.dataSynchronizer.getLocalCollection(ctx.namespace).deleteOne(filter).deletedCount)

        assertNull(ctx.dataSynchronizer.find(ctx.namespace, filter).firstOrNull())

        ctx.dataSynchronizer.recover()

        assertNotNull(ctx.dataSynchronizer.find(ctx.namespace, filter).firstOrNull())
    }

    @Test
    fun testRecoveryIsAttemptedOnNetworkReconnect() {
        val ctx = harness.freshTestContext()

        // disable all non-deliberate syncing
        ctx.reconfigure()
        ctx.dataSynchronizer.stop()
        ctx.dataSynchronizer.disableSyncThread()

        // insert a local document and sync on it
        ctx.dataSynchronizer.insertOne(ctx.namespace, ctx.testDocument)

        // ensure change is committed
        ctx.waitForEvents(1)

        ctx.dataSynchronizer.doSyncPass()

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        val modifiedDocument = ctx.testDocument.clone().append("goodbye",
                BsonString("universe"))

        // stage an update without reactivating the sync thread
        val changeEvent = ChangeEvents
                .changeEventForLocalUpdate(ctx.namespace, ctx.testDocumentId,
                        UpdateDescription(BsonDocument("goodbye",
                                BsonString("universe")), HashSet<String>()),
                        modifiedDocument, false)
        `when`(ctx.dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(ctx.testDocument to changeEvent),
                mapOf())
        ctx.dataSynchronizer.doSyncPass()
        // make sure event was applied
        assertEquals(modifiedDocument, ctx.findTestDocumentFromLocalCollection())

        // simulate a failed update by adding to the undo collection
        ctx.verifyUndoCollectionEmpty()
        ctx.dataSynchronizer.getUndoCollection(ctx.namespace).insertOne(ctx.testDocument)

        // simulate a reconnect by triggering network change event without
        // changing the status of the network monitor
        ctx.dataSynchronizer.onNetworkStateChanged()
        // verify that if we did another sync pass now and the event had successfully cleared,
        // the update would be reverted
        ctx.dataSynchronizer.doSyncPass()
        // document restored in original state
        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())
        // confirm we're still syncing on that ID
        assertTrue(ctx.dataSynchronizer.getSynchronizedDocumentIds(ctx.namespace).contains(ctx.testDocumentId))
        // confirm that we've cleared the undo collection after applying it
        ctx.verifyUndoCollectionEmpty()
    }

    @Test
    fun testRecoveryIsNotAttemptedWithoutNetworkReconnect() {
        val ctx = harness.freshTestContext()

        // disable all non-deliberate syncing
        ctx.reconfigure()
        ctx.dataSynchronizer.stop()
        ctx.dataSynchronizer.disableSyncThread()

        // insert a local document and sync on it
        ctx.dataSynchronizer.insertOne(ctx.namespace, ctx.testDocument)

        // ensure change is committed
        ctx.waitForEvents(1)

        ctx.dataSynchronizer.doSyncPass()

        assertEquals(ctx.testDocument, ctx.findTestDocumentFromLocalCollection())

        val modifiedDocument = ctx.testDocument.clone().append("goodbye",
                BsonString("universe"))

        // stage an update without reactivating the sync thread
        val changeEvent = ChangeEvents
                .changeEventForLocalUpdate(ctx.namespace, ctx.testDocumentId,
                        UpdateDescription(BsonDocument("goodbye",
                                BsonString("universe")), HashSet<String>()),
                        modifiedDocument, false)
        `when`(ctx.dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(ctx.testDocument to changeEvent),
                mapOf())
        ctx.dataSynchronizer.doSyncPass()
        // make sure event was applied
        assertEquals(modifiedDocument, ctx.findTestDocumentFromLocalCollection())

        // simulate a failed update by adding to the undo collection
        ctx.verifyUndoCollectionEmpty()
        ctx.dataSynchronizer.getUndoCollection(ctx.namespace).insertOne(ctx.testDocument)

        // verify that if we did another sync pass now and the event had successfully cleared,
        // the update would be reverted
        ctx.dataSynchronizer.doSyncPass()
        // document NOT restored in original state
        assertEquals(modifiedDocument, ctx.findTestDocumentFromLocalCollection())
        // confirm we're still syncing on that ID
        assertTrue(ctx.dataSynchronizer.getSynchronizedDocumentIds(ctx.namespace).contains(ctx.testDocumentId))
        // clear the test undo
        ctx.dataSynchronizer.getUndoCollection(ctx.namespace).deleteOne(ctx.testDocumentFilter)
        // confirm that we've cleared the undo collection
        ctx.verifyUndoCollectionEmpty()
    }

}
