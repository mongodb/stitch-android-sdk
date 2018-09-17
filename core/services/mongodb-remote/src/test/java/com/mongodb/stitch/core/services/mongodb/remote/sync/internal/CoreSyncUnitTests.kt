package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.*
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.codecs.configuration.CodecRegistry
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import junit.framework.Assert.*
import org.bson.BsonString
import org.mockito.ArgumentMatchers

class CoreSyncUnitTests {
    private val dataSyncMock: DataSynchronizer = mock(DataSynchronizer::class.java)
    private val syncOpsMock: SyncOperations<BsonDocument> =
            mock(SyncOperations::class.java) as SyncOperations<BsonDocument>

    private val changeEventListener: ChangeEventListener<BsonDocument> =
            ChangeEventListener { _, _ -> }
    private val conflictHandler: ConflictHandler<BsonDocument> = ConflictHandler { documentId, localEvent, remoteEvent -> null }

    private val coreSync: CoreSync<BsonDocument> by lazy {
        spy(CoreSyncImpl<BsonDocument>(
                MongoNamespace("foo", "bar"),
                BsonDocument::class.java,
                dataSyncMock,
                spy(CoreStitchServiceClient::class.java),
                syncOpsMock,
                mock(CodecRegistry::class.java)))
    }

    @Before
    fun setup() {
        coreSync.configure(conflictHandler, changeEventListener)
    }

    @Test
    fun testSyncOne() {
        val oid = BsonObjectId()

        coreSync.syncOne(oid)

        val oidCaptor = ArgumentCaptor.forClass(BsonObjectId::class.java)

        verify(dataSyncMock).syncDocumentFromRemote<BsonDocument>(
                any(), oidCaptor.capture(), any(), any())

        assertEquals(oidCaptor.value, oid)

        verify(dataSyncMock).watchDocument<BsonDocument>(any(), oidCaptor.capture(), any(), any())

        assertEquals(oidCaptor.value, oid)
    }

    @Test
    fun testSyncMany() {
        val oid1 = BsonObjectId()
        val oid2 = BsonObjectId()

        coreSync.syncMany(oid1, oid2)

        val oidCaptor = ArgumentCaptor.forClass(BsonObjectId::class.java)

        verify(coreSync, times(2)).syncOne(oidCaptor.capture())

        val oids = oidCaptor.allValues

        assertEquals(oid1, oids[0])
        assertEquals(oid2, oids[1])
    }

    @Test
    fun testSyncedIds() {
        coreSync.syncedIds

        verify(dataSyncMock).getSynchronizedDocumentIds(ArgumentMatchers.any())
    }

    @Test
    fun testDesyncOne() {
        val oid = BsonObjectId()

        coreSync.desyncOne(oid)

        val oidCaptor = ArgumentCaptor.forClass(BsonObjectId::class.java)

        verify(dataSyncMock).desyncDocumentFromRemote(
                any(), oidCaptor.capture())

        assertEquals(oidCaptor.value, oid)
    }

    @Test
    fun testDesyncMany() {
        val oid1 = BsonObjectId()
        val oid2 = BsonObjectId()

        coreSync.desyncMany(oid1, oid2)

        val oidCaptor = ArgumentCaptor.forClass(BsonObjectId::class.java)

        verify(coreSync, times(2)).desyncOne(oidCaptor.capture())

        val oids = oidCaptor.allValues

        assertEquals(oid1, oids[0])
        assertEquals(oid2, oids[1])
    }

    @Test
    fun testFindOneById() {
        val doc = BsonDocument("foo", BsonString("bar"))

        `when`(syncOpsMock.findOneById<BsonDocument>(any(), any())).thenReturn(
                mock(FindOneByIdOperation::class.java) as FindOneByIdOperation<BsonDocument>
        )

        coreSync.findOneById(doc)

        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)

        verify(syncOpsMock).findOneById<BsonDocument>(docCaptor.capture(), any())

        assertEquals(doc, docCaptor.value)
    }

    @Test
    fun testUpdateOneById() {
        val doc = BsonDocument("foo", BsonString("bar"))
        val oid = BsonObjectId()

        `when`(syncOpsMock.updateOneById(any(), any())).thenReturn(
                mock(UpdateOneByIdOperation::class.java) as UpdateOneByIdOperation<BsonDocument>
        )

        coreSync.updateOneById(oid, doc)

        val oidCaptor = ArgumentCaptor.forClass(BsonObjectId::class.java)
        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)

        verify(syncOpsMock).updateOneById(oidCaptor.capture(), docCaptor.capture())

        assertEquals(oid, oidCaptor.value)
        assertEquals(doc, docCaptor.value)
    }

    @Test
    fun testInsertOneAndSync() {
        val doc = BsonDocument("foo", BsonString("bar"))

        `when`(syncOpsMock.insertOneAndSync(any(), any(), any())).thenReturn(
                mock(InsertOneAndSyncOperation::class.java) as InsertOneAndSyncOperation<BsonDocument>
        )

        coreSync.insertOneAndSync(doc)

        val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)

        verify(syncOpsMock).insertOneAndSync(docCaptor.capture(), any(), any())

        assertEquals(doc, docCaptor.value)
    }

    @Test
    fun testDeleteOneById() {
        val oid = BsonObjectId()

        `when`(syncOpsMock.deleteOneById(any())).thenReturn(
                mock(DeleteOneByIdOperation::class.java)
        )

        coreSync.deleteOneById(oid)

        val oidCaptor = ArgumentCaptor.forClass(BsonObjectId::class.java)

        verify(syncOpsMock).deleteOneById(oidCaptor.capture())

        assertEquals(oid, oidCaptor.value)
    }
}
