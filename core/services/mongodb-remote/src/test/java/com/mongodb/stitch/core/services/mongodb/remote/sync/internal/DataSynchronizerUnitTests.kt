package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.net.Event
import com.mongodb.stitch.core.internal.net.EventStream
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.internal.net.Stream
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollectionImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoDatabaseImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.TestUtils
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.Document

import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.configuration.CodecRegistries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import java.lang.Exception
import java.util.Random
import java.util.concurrent.Semaphore

class DataSynchronizerUnitTests {
    @After
    fun teardown() {
        CoreRemoteClientFactory.close()
        ServerEmbeddedMongoClientFactory.getInstance().close()
    }

    private val namespace = MongoNamespace("foo", "bar")

    private val networkMonitor = object : NetworkMonitor {
        var isOnline = true

        override fun removeNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }

        override fun isConnected(): Boolean {
            return this.isOnline
        }

        override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }
    }
    private val authMonitor = AuthMonitor { true }

    private val localClient by lazy {
        SyncMongoClientFactory.getClient(
            TestUtils.getClientInfo(),
            "mongodblocal",
            ServerEmbeddedMongoClientFactory.getInstance()
        )
    }

    private val instanceKey = "${Random().nextInt()}"
    private val service = mock(CoreStitchServiceClientImpl::class.java)

    private val remoteClient = spy(CoreRemoteMongoClientImpl(
        service,
        instanceKey,
        localClient,
        networkMonitor,
        authMonitor
    ))

    private val dataSynchronizer = spy(DataSynchronizer(
        instanceKey,
        service,
        localClient,
        remoteClient,
        networkMonitor,
        authMonitor
    ))

    @Before
    fun setup() {
        remoteClient.dataSynchronizer.stop()
        `when`(service.streamFunction(
            anyString(),
            anyList<Any>(),
            eq(ChangeEvent.changeEventCoder))
        ).thenReturn(
            Stream(object : EventStream {
                override fun nextEvent(): Event {
                    return Event.Builder().withEventName("MOCK").build()
                }

                override fun isOpen(): Boolean {
                    return true
                }

                override fun close() {
                }

                override fun cancel() {
                }
            }, ChangeEvent.changeEventCoder)
        )
    }

    @Test
    fun testNew() {
        assertFalse(dataSynchronizer.isRunning)
    }

    @Test
    fun testOnNetworkStateChanged() {
        dataSynchronizer.onNetworkStateChanged()

        verify(dataSynchronizer, times(1)).start()
        verify(dataSynchronizer, times(0)).stop()

        networkMonitor.isOnline = false

        dataSynchronizer.onNetworkStateChanged()

        verify(dataSynchronizer, times(1)).start()
        verify(dataSynchronizer, times(1)).stop()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testStart() {
        dataSynchronizer.start()

        // without a configuration, we should not be running
        assertFalse(dataSynchronizer.isRunning)

        dataSynchronizer.configure(
            namespace,
            mock(ConflictHandler::class.java) as ConflictHandler<BsonDocument>,
            mock(ChangeEventListener::class.java) as ChangeEventListener<BsonDocument>,
            mock(ErrorListener::class.java),
            BsonDocumentCodec())

        assertTrue(dataSynchronizer.isRunning)
    }

    @Test
    fun testDisableSyncThread() {
        dataSynchronizer.start()

        assertTrue(dataSynchronizer.isRunning)

        dataSynchronizer.stop()

        dataSynchronizer.disableSyncThread()

        dataSynchronizer.start()

        assertFalse(dataSynchronizer.isRunning)
    }

    @Test
    fun testStop() {
        dataSynchronizer.start()

        assertTrue(dataSynchronizer.isRunning)

        dataSynchronizer.stop()

        assertFalse(dataSynchronizer.isRunning)
    }

    @Test
    fun testClose() {
        fail()
    }

    @Test
    fun testSuccessfulInsert() {
        withNewDataSynchronizer { dataSynchronizer, coreRemoteMongoCollectionMock ->
            val document1 = newDoc()
            val emitEventSemaphore = Semaphore(0)
            val emitErrorSemaphore = Semaphore(0)

            val expectedEvent = ChangeEvent.changeEventForLocalInsert(namespace, document1, false)

            val changeEventListener = changeEventListener(emitEventSemaphore, expectedEvent)
            val conflictHandler = conflictHandler(expectedEvent, expectedEvent)
            val errorListener = errorListener(emitErrorSemaphore, document1["_id"]!!)

            dataSynchronizer.configure(namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                BsonDocumentCodec())

            dataSynchronizer.insertOneAndSync(namespace, document1)

            dataSynchronizer.doSyncPass()

            emitEventSemaphore.acquire()

            // verify we are inserting!
            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
            verify(coreRemoteMongoCollectionMock, times(1)).insertOne(docCaptor.capture())
            assertEquals(expectedEvent.fullDocument, withoutVersionId(docCaptor.value))

            verify(changeEventListener, times(2)).onEvent(eq(document1["_id"]), any())
            verify(conflictHandler, times(0)).resolveConflict(eq(document1["_id"]), any(), any())
            verify(errorListener, times(0)).onError(eq(document1["_id"]), any())

            assertEquals(
                document1,
                withoutVersionId(
                    dataSynchronizer.findOneById(
                        namespace,
                        document1["_id"],
                        BsonDocument::class.java,
                        CodecRegistries.fromCodecs(BsonDocumentCodec()))))
        }
    }

    @Test
    fun testConflictedInsert() {
        withNewDataSynchronizer { dataSynchronizer, coreRemoteMongoCollectionMock ->
            val document2 = newDoc()
            val emitEventSemaphore = Semaphore(0)
            val emitErrorSemaphore = Semaphore(0)

            val expectedLocalEvent = ChangeEvent.changeEventForLocalInsert(namespace, document2, true)
            val expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(namespace, document2["_id"], false)

            val changeEventListener = changeEventListener(emitEventSemaphore, expectedRemoteEvent)
            val conflictHandler = conflictHandler(expectedLocalEvent, expectedRemoteEvent)
            val errorListener = errorListener(emitErrorSemaphore, document2["_id"]!!)

            dataSynchronizer.configure(namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                BsonDocumentCodec())

            // force a conflict
            `when`(coreRemoteMongoCollectionMock.insertOne(any())).thenThrow(
                StitchServiceException("E11000", StitchServiceErrorCode.MONGODB_ERROR)
            )

            dataSynchronizer.insertOneAndSync(namespace, document2)

            assertEquals(
                document2,
                withoutVersionId(
                    dataSynchronizer.findOneById(
                        namespace,
                        document2["_id"],
                        BsonDocument::class.java,
                        CodecRegistries.fromCodecs(BsonDocumentCodec()))))

            dataSynchronizer.doSyncPass()

            emitEventSemaphore.acquire()

            verify(changeEventListener, times(2)).onEvent(eq(document2["_id"]), any())
            verify(conflictHandler, times(1)).resolveConflict(eq(document2["_id"]), any(), any())
            verify(errorListener, times(0)).onError(eq(document2["_id"]), any())

            assertNull(
                dataSynchronizer.findOneById(namespace, document2["_id"], BsonDocument::class.java, CodecRegistries.fromCodecs(BsonDocumentCodec()))
            )
        }
    }

    @Test
    fun testFailedInsert() {
        withNewDataSynchronizer { dataSynchronizer, coreRemoteMongoCollectionMock ->
            val document2 = newDoc()
            val emitEventSemaphore = Semaphore(0)
            val emitErrorSemaphore = Semaphore(0)

            val expectedLocalEvent = ChangeEvent.changeEventForLocalInsert(namespace, document2, true)
            val expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(namespace, document2["_id"], false)

            val changeEventListener = changeEventListener(emitEventSemaphore, expectedRemoteEvent)
            val conflictHandler = conflictHandler(expectedLocalEvent, expectedRemoteEvent)
            val errorListener = errorListener(emitErrorSemaphore, document2["_id"]!!)

            dataSynchronizer.configure(namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                BsonDocumentCodec())

            // force a conflict
            `when`(coreRemoteMongoCollectionMock.insertOne(any())).thenThrow(StitchServiceException("bad", StitchServiceErrorCode.UNKNOWN))

            dataSynchronizer.insertOneAndSync(namespace, document2)

            dataSynchronizer.doSyncPass()

            emitErrorSemaphore.acquire()

            verify(changeEventListener, times(1)).onEvent(eq(document2["_id"]), any())
            verify(conflictHandler, times(0)).resolveConflict(eq(document2["_id"]), any(), any())
            verify(errorListener, times(1)).onError(eq(document2["_id"]), any())

            assertEquals(
                document2,
                withoutVersionId(
                    dataSynchronizer.findOneById(
                        namespace,
                        document2["_id"],
                        BsonDocument::class.java,
                        CodecRegistries.fromCodecs(BsonDocumentCodec()))))
        }

    }

    @Test
    fun testSuccessfulUpdate() {
        withNewDataSynchronizer { dataSynchronizer, coreRemoteMongoCollectionMock ->
            val document1 = newDoc("count", BsonInt32(1))
            val update1 = BsonDocument("\$inc", BsonDocument("count", BsonInt32(1)))
            val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", document1["_id"])

            val emitEventSemaphore = Semaphore(2)
            val emitErrorSemaphore = Semaphore(0)

            val expectedEvent = ChangeEvent.changeEventForLocalUpdate(
                namespace,
                document1["_id"],
                update1,
                docAfterUpdate,
                false
            )

            val changeEventListener = changeEventListener(emitEventSemaphore, expectedEvent)
            val conflictHandler = conflictHandler(expectedEvent, expectedEvent)
            val errorListener = errorListener(emitErrorSemaphore, document1["_id"]!!)

            dataSynchronizer.configure(namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                BsonDocumentCodec())

            dataSynchronizer.insertOneAndSync(namespace, document1)

            dataSynchronizer.doSyncPass()

            emitEventSemaphore.acquire()

            verify(changeEventListener, times(2)).onEvent(eq(document1["_id"]), any())

            dataSynchronizer.updateOneById(namespace, document1["_id"], update1)

            val remoteUpdateResult = RemoteUpdateResult(1, 1, null)
            `when`(coreRemoteMongoCollectionMock.updateOne(any(), any())).thenReturn(remoteUpdateResult)

            dataSynchronizer.doSyncPass()

            emitEventSemaphore.acquire()

            // verify we are inserting!
            val docCaptor = ArgumentCaptor.forClass(BsonDocument::class.java)
            verify(coreRemoteMongoCollectionMock, times(1)).updateOne(any(), docCaptor.capture())
            assertEquals(expectedEvent.fullDocument, withoutVersionId(docCaptor.value))

            // the four calls should be:
            // insertOne, syncLocalToRemote, updateOne, syncLocalToRemote
            verify(changeEventListener, times(4)).onEvent(eq(document1["_id"]), any())
            verify(conflictHandler, times(0)).resolveConflict(eq(document1["_id"]), any(), any())
            verify(errorListener, times(0)).onError(eq(document1["_id"]), any())

            assertEquals(
                docAfterUpdate,
                withoutVersionId(
                    dataSynchronizer.findOneById(
                        namespace,
                        document1["_id"],
                        BsonDocument::class.java,
                        CodecRegistries.fromCodecs(BsonDocumentCodec()))))
        }
    }

    @Test
    fun testConflictedUpdate() {
        withNewDataSynchronizer { dataSynchronizer, coreRemoteMongoCollectionMock ->
            val document1 = newDoc("count", BsonInt32(1))
            val update1 = BsonDocument("\$inc", BsonDocument("count", BsonInt32(1)))
            val docAfterUpdate = BsonDocument("count", BsonInt32(2)).append("_id", document1["_id"])

            val emitEventSemaphore = Semaphore(2)
            val emitErrorSemaphore = Semaphore(0)

            val expectedLocalEvent = ChangeEvent.changeEventForLocalUpdate(
                namespace,
                document1["_id"],
                update1,
                docAfterUpdate,
                true
            )
            val expectedRemoteEvent = ChangeEvent.changeEventForLocalDelete(namespace, document1["_id"], false)

            val changeEventListener = changeEventListener(emitEventSemaphore, expectedLocalEvent)
            val conflictHandler = conflictHandler(expectedLocalEvent, expectedRemoteEvent)
            val errorListener = errorListener(emitErrorSemaphore, document1["_id"]!!)

            dataSynchronizer.configure(namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                BsonDocumentCodec())

            dataSynchronizer.insertOneAndSync(namespace, document1)

            dataSynchronizer.doSyncPass()

            emitEventSemaphore.acquire()

            verify(changeEventListener, times(2)).onEvent(eq(document1["_id"]), any())

            dataSynchronizer.updateOneById(namespace, document1["_id"], update1)

            // create conflict here
            val remoteUpdateResult = RemoteUpdateResult(0, 0, null)
            `when`(coreRemoteMongoCollectionMock.updateOne(any(), any())).thenReturn(remoteUpdateResult)

            dataSynchronizer.doSyncPass()

            emitEventSemaphore.acquire()

            verify(changeEventListener, times(4)).onEvent(eq(document1["_id"]), any())
            verify(conflictHandler, times(1)).resolveConflict(eq(document1["_id"]), any(), any())
            verify(errorListener, times(0)).onError(eq(document1["_id"]), any())

            // verify we are inserting!
            assertNull(
                dataSynchronizer.findOneById(namespace, document1["_id"],
                    BsonDocument::class.java,
                    CodecRegistries.fromCodecs(BsonDocumentCodec()))
            )
        }
    }

    @Test
    fun testFind() {
        throw NotImplementedError()
    }

    @Test
    fun testInsertOneAndSync() {
        // disable syncing
        dataSynchronizer.disableSyncThread()

        val docId1 = BsonObjectId()
        val document1 = BsonDocument("_id", docId1)

        val docId2 = BsonObjectId()
        val document2 = BsonDocument("_id", docId2)

        // insert new document
        dataSynchronizer.insertOneAndSync(namespace, document1)

        // verify triggerListeningToNamespace has been called
        verify(dataSynchronizer, times(1)).triggerListeningToNamespace(namespace)
        // verify streamFunction has not been called yet
        verify(service, times(0)).streamFunction(anyString(), anyList<Document>(), eq(ChangeEvent.changeEventCoder))

        // our expected event should be for DOCUMENT 2. the change event for document1 should not
        // be emitted to our ChangeEventListener since it has not been configured yet
        val expectedEvent = ChangeEvent.changeEventForLocalInsert(namespace, document2, true)

        val emitEventSemaphore = Semaphore(0)

        val changeEventListener = changeEventListener(emitEventSemaphore, expectedEvent)

        // configure dataSynchronizer
        dataSynchronizer.configure(namespace,
            DefaultSyncConflictResolvers.remoteWins(),
            changeEventListener,
            null,
            BsonDocumentCodec())

        // configuring should open the stream
        while (!dataSynchronizer.areAllStreamsOpen()) {}
        // verify stream has been called
        verify(service, times(1)).streamFunction(anyString(), anyList<Document>(), eq(ChangeEvent.changeEventCoder))

        // insert another document
        dataSynchronizer.insertOneAndSync(namespace, document2)

        // verify trigger is called again
        verify(dataSynchronizer, times(3)).triggerListeningToNamespace(namespace)
        // verify stream is not called again, since the stream was already started
        // when we configured the namespace
        verify(service, times(1)).streamFunction(anyString(), anyList<Document>(), eq(ChangeEvent.changeEventCoder))

        // acquire the event semaphore. see the ChangeEventListener above
        emitEventSemaphore.acquire()

        // assert the event was emitted to the ChangeEventListener successfully
        verify(changeEventListener, times(1)).onEvent(eq(docId2), any())

        assertEquals(
            docId1,
            dataSynchronizer.findOneById(
                namespace,
                docId1,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec()))["_id"]
        )
        assertEquals(
            docId2,
            dataSynchronizer.findOneById(
                namespace,
                docId2,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec()))["_id"]
        )
    }

    @Test
    fun testUpdateOneById() {
        // disable syncing
        dataSynchronizer.disableSyncThread()

        val docId1 = BsonObjectId()
        val document1 = BsonDocument("_id", docId1).append("count", BsonInt32(1))
        val updateDoc = BsonDocument("\$inc", BsonDocument("count", BsonInt32(1)))

        val expectedDocumentAfterUpdate = BsonDocument("count", BsonInt32(2))

        // assert this doc does not exist
        assertNull(dataSynchronizer.findOneById(namespace, docId1, BsonDocument::class.java, CodecRegistries.fromCodecs(BsonDocumentCodec())))

        // update the non-existent document...
        var updateResult = dataSynchronizer.updateOneById(namespace, docId1, updateDoc)
        // ...which should continue to not exist...
        assertNull(dataSynchronizer.findOneById(namespace, docId1, BsonDocument::class.java, CodecRegistries.fromCodecs(BsonDocumentCodec())))
        // ...and result in an "empty" UpdateResult
        assertEquals(0, updateResult.matchedCount)
        assertEquals(0, updateResult.modifiedCount)
        assertNull(updateResult.upsertedId)
        assertTrue(updateResult.wasAcknowledged())

        // insert the initial document
        dataSynchronizer.insertOneAndSync(namespace, document1)
        // assert this doc exists
        assertEquals(
            docId1,
            dataSynchronizer.findOneById(namespace, docId1, BsonDocument::class.java, CodecRegistries.fromCodecs(BsonDocumentCodec()))["_id"])

        // configure the dataSynchronizer with a ChangeEventListener that expects
        // a local update event
        val eventSemaphore = Semaphore(0)
        val expectedEvent = ChangeEvent.changeEventForLocalUpdate(namespace, docId1, updateDoc, expectedDocumentAfterUpdate, true)
        dataSynchronizer.configure(namespace,
            DefaultSyncConflictResolvers.remoteWins(),
            changeEventListener(eventSemaphore, expectedEvent),
            null,
            BsonDocumentCodec())

        // do the actual update
        updateResult = dataSynchronizer.updateOneById(namespace, docId1, updateDoc)
        // assert the UpdateResult is non-zero
        assertEquals(1, updateResult.matchedCount)
        assertEquals(1, updateResult.modifiedCount)
        assertNull(updateResult.upsertedId)
        assertTrue(updateResult.wasAcknowledged())

        // acquire the event semaphore. a check will/will have happened
        // asynchronously, asserting that our actual emitted event is
        // equal to our expected event
        eventSemaphore.acquire()

        // verify trigger is called, once for the insert, once for the configure
        verify(dataSynchronizer, times(2)).triggerListeningToNamespace(namespace)
        // verify stream is only opened on the configure, not the update
        verify(service, times(1)).streamFunction(anyString(), anyList<Document>(), eq(ChangeEvent.changeEventCoder))

        // assert that the updated document equals what we've expected
        assertEquals(
            docId1,
            dataSynchronizer.findOneById(
                namespace,
                docId1,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec()))["_id"])

        assertEquals(
            expectedDocumentAfterUpdate,
            withoutVersionId(
                withoutId(
                    dataSynchronizer.findOneById(
                        namespace,
                        docId1,
                        BsonDocument::class.java,
                        CodecRegistries.fromCodecs(BsonDocumentCodec())))))
    }

    @Test
    fun testDeleteOneById() {
        throw NotImplementedError()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testCoreDocumentSynchronizationConfigIsFrozenCheck() {
        // create a dataSynchronizer with an injected remote client
        val id1 = BsonObjectId()

        val dataSynchronizer = spy(DataSynchronizer(
            instanceKey,
            mock(CoreStitchServiceClient::class.java),
            localClient,
            remoteClient,
            networkMonitor,
            authMonitor
        ))

        // insert a new doc. the details of the doc do not matter
        val doc1 = BsonDocument("_id", id1)
        dataSynchronizer.insertOneAndSync(namespace, doc1)

        // set the doc to frozen and reload the configs
        dataSynchronizer.getSynchronizedDocuments(namespace).forEach {
            it.isFrozen = true
        }
        dataSynchronizer.reloadConfig()

        // spy on the remote client
        val remoteMongoDatabase = mock(CoreRemoteMongoDatabaseImpl::class.java)
        `when`(remoteClient.getDatabase(namespace.databaseName)).thenReturn(remoteMongoDatabase)

        val remoteMongoCollection = mock(CoreRemoteMongoCollectionImpl::class.java)
            as CoreRemoteMongoCollectionImpl<BsonDocument>
        `when`(remoteMongoDatabase.getCollection(namespace.collectionName, BsonDocument::class.java))
            .thenReturn(remoteMongoCollection)

        // ensure that no remote inserts are made during this sync pass
        dataSynchronizer.doSyncPass()

        verify(remoteMongoCollection, times(0)).insertOne(any())

        // unfreeze the configs and reload
        dataSynchronizer.getSynchronizedDocuments(namespace).forEach {
            it.isFrozen = false
        }
        dataSynchronizer.reloadConfig()

        // this time ensure that the remote insert has been called
        dataSynchronizer.doSyncPass()

        verify(remoteMongoCollection, times(1)).insertOne(any())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testConfigure() {
        // spy a new DataSynchronizer
        val dataSynchronizer = spy(DataSynchronizer(
            instanceKey,
            service,
            localClient,
            remoteClient,
            networkMonitor,
            authMonitor
        ))

        // without a configuration it should not be
        // configured or running
        assertFalse(dataSynchronizer.isRunning)

        // mock the necessary config args
        val conflictHandler = mock(ConflictHandler::class.java) as ConflictHandler<BsonDocument>
        val changeEventListener = mock(ChangeEventListener::class.java) as ChangeEventListener<BsonDocument>
        val errorListener = mock(ErrorListener::class.java)
        val bsonCodec = BsonDocumentCodec()

        // insert a pseudo doc
        dataSynchronizer.insertOneAndSync(namespace, BsonDocument())
        // verify that, though triggerListeningToNamespace was called,
        // it was short circuited and never attempted to open the stream
        verify(dataSynchronizer, times(1)).triggerListeningToNamespace(any())

        // configure the dataSynchronizer,
        // which should pass down the configuration to the namespace config
        // this should also trigger listening to the namespace And attempt to open the stream
        dataSynchronizer.configure(namespace, conflictHandler, changeEventListener, errorListener, bsonCodec)

        // verify that the data synchronizer has triggered the namespace,
        // has started itself, and has attempted to open the stream for the namespace
        verify(dataSynchronizer, times(2)).triggerListeningToNamespace(any())
        verify(dataSynchronizer, times(1)).start()

        // assert that the dataSynchronizer is concretely running
        assertTrue(dataSynchronizer.isRunning)

        // configuring again, verifying that the data synchronizer does NOT
        // trigger the namespace or start up a second time
        dataSynchronizer.configure(namespace, conflictHandler, changeEventListener, errorListener, bsonCodec)

        verify(dataSynchronizer, times(2)).triggerListeningToNamespace(any())
        verify(dataSynchronizer, times(1)).start()

        // assert that nothing has changed about our state
        assertTrue(dataSynchronizer.isRunning)
    }

    @Suppress("UNCHECKED_CAST")
    private fun withNewDataSynchronizer(work: (dataSynchronizer: DataSynchronizer,
                                               coreRemoteMongoCollectionMock: CoreRemoteMongoCollection<BsonDocument>) -> Unit) {
        val remoteClient = mock(CoreRemoteMongoClientImpl::class.java)

        val dataSynchronizer = spy(DataSynchronizer(
            instanceKey,
            service,
            localClient,
            remoteClient,
            networkMonitor,
            authMonitor
        ))

        dataSynchronizer.disableSyncThread()

        val databaseSpy = mock(CoreRemoteMongoDatabaseImpl::class.java)
        `when`(remoteClient.getDatabase(eq(namespace.databaseName))).thenReturn(databaseSpy)
        val collectionMock = mock(CoreRemoteMongoCollectionImpl::class.java) as CoreRemoteMongoCollectionImpl<BsonDocument>
        `when`(databaseSpy.getCollection(eq(namespace.collectionName), eq(BsonDocument::class.java))).thenReturn(collectionMock)

        `when`(collectionMock.namespace).thenReturn(namespace)
        val remoteFindIterable = mock(CoreRemoteFindIterable::class.java) as CoreRemoteFindIterable<BsonDocument>
        `when`(collectionMock.find(any())).thenReturn(remoteFindIterable)
        `when`(remoteFindIterable.into<HashSet<BsonDocument>>(any())).thenReturn(HashSet())

        dataSynchronizer.doSyncPass()

        verifyZeroInteractions(collectionMock)

        work(dataSynchronizer, collectionMock)
    }

    companion object {
        private fun newDoc(key: String = "hello", value: BsonValue = BsonString("world")): BsonDocument {
            return BsonDocument("_id", BsonObjectId()).append(key, value)
        }

        private fun withoutVersionId(document: BsonDocument?): BsonDocument? {
            if (document == null) {
                return null
            }
            val newDoc = BsonDocument.parse(document.toJson())
            newDoc.remove("__stitch_sync_version")
            return newDoc
        }

        private fun withoutId(document: BsonDocument): BsonDocument {
            val newDoc = BsonDocument.parse(document.toJson())
            newDoc.remove("_id")
            return newDoc
        }

        private fun compareEvents(expectedEvent: ChangeEvent<BsonDocument>,
                                  actualEvent: ChangeEvent<BsonDocument>) {
            // assert that our actualEvent is correct
            assertEquals(expectedEvent.operationType, actualEvent.operationType)
            assertEquals(expectedEvent.documentKey, actualEvent.documentKey)

            if (actualEvent.fullDocument == null) {
                assertNull(expectedEvent.fullDocument)
            } else if (expectedEvent.fullDocument == null) {
                assertNull(actualEvent.fullDocument)
            } else {
                assertEquals(expectedEvent.fullDocument, withoutVersionId(actualEvent.fullDocument))
            }
            assertEquals(expectedEvent.id, actualEvent.id)
            assertEquals(expectedEvent.namespace, actualEvent.namespace)
            assertEquals(expectedEvent.updateDescription.removedFields, actualEvent.updateDescription.removedFields)
            assertEquals(expectedEvent.updateDescription.updatedFields, actualEvent.updateDescription.updatedFields)

            assertEquals(expectedEvent.hasUncommittedWrites(), actualEvent.hasUncommittedWrites())
        }

        private fun errorListener(emitErrorSemaphore: Semaphore, expectedDocumentId: BsonValue): ErrorListener {
            open class TestErrorListener: ErrorListener {
                override fun onError(actualDocumentId: BsonValue?, error: Exception?) {
                    try {
                        assertEquals(expectedDocumentId, actualDocumentId)
                    } finally {
                        emitErrorSemaphore.release()
                    }
                }
            }
            return spy(TestErrorListener())
        }

        private fun conflictHandler(expectedLocalEvent: ChangeEvent<BsonDocument>,
                                    expectedRemoteEvent: ChangeEvent<BsonDocument>): ConflictHandler<BsonDocument> {
            open class TestConflictHandler: ConflictHandler<BsonDocument> {
                override fun resolveConflict(documentId: BsonValue?, localEvent: ChangeEvent<BsonDocument>?, remoteEvent: ChangeEvent<BsonDocument>?): BsonDocument? {
                    // assert that our actualEvent is correct
                    compareEvents(expectedLocalEvent, localEvent!!)
                    compareEvents(expectedRemoteEvent, remoteEvent!!)
                    return remoteEvent.fullDocument
                }
            }
            return spy(TestConflictHandler())
        }

        private fun changeEventListener(emitEventSemaphore: Semaphore,
                                        expectedEvent: ChangeEvent<BsonDocument>): ChangeEventListener<BsonDocument> {
            open class TestChangeEventListener: ChangeEventListener<BsonDocument> {
                override fun onEvent(documentId: BsonValue?, actualEvent: ChangeEvent<BsonDocument>?) {
                    try {
                        // assert that our actualEvent is correct
                        compareEvents(expectedEvent, actualEvent!!)
                        assertEquals(expectedEvent.id, documentId)
                        // if we've reached here, all was successful
                    } finally {
                        emitEventSemaphore.release()
                    }
                }
            }
            return spy(TestChangeEventListener())
        }
    }
}
