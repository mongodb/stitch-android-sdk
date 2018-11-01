package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.client.MongoClient
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.common.ThreadDispatcher
import com.mongodb.stitch.core.internal.net.Event
import com.mongodb.stitch.core.internal.net.EventStream
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.internal.net.Stream
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollectionImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoDatabaseImpl
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.Document
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.Codec
import org.bson.codecs.DocumentCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import java.io.Closeable
import java.lang.Exception
import java.util.Collections
import java.util.Random
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class SyncUnitTestHarness : Closeable {
    companion object {
        /**
         * Conflict handler used for testing purposes.
         *
         * @param shouldConflictBeResolvedByRemote whether or not to resolve using the remote document or the local
         *                     document
         * @param exceptionToThrow if set, will throw an exceptionToThrow after comparing the events
         */
        open class TestConflictHandler(
            var shouldConflictBeResolvedByRemote: Boolean,
            var exceptionToThrow: Exception? = null
        ) : ConflictHandler<BsonDocument> {
            override fun resolveConflict(
                documentId: BsonValue?,
                localEvent: ChangeEvent<BsonDocument>?,
                remoteEvent: ChangeEvent<BsonDocument>?
            ): BsonDocument? {
                if (exceptionToThrow != null) {
                    throw exceptionToThrow!!
                }
                return if (shouldConflictBeResolvedByRemote) remoteEvent?.fullDocument else localEvent?.fullDocument
            }
        }

        /**
         * Network monitor used for testing purposes.
         * Can be switched online or offline via the syncHarness.
         */
        open class TestNetworkMonitor : NetworkMonitor {
            private val networkStateListeners = mutableListOf<NetworkMonitor.StateListener>()
            var isOnline: Boolean = true
                set(value) {
                    if (field != value) {
                        field = value
                        networkStateListeners.forEach { it.onNetworkStateChanged() }
                    }
                }

            override fun removeNetworkStateListener(listener: NetworkMonitor.StateListener) {
                networkStateListeners.remove(listener)
            }

            override fun isConnected(): Boolean {
                return isOnline
            }

            override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {
                networkStateListeners.add(listener)
            }
        }

        /**
         * Auth monitor used for testing purposes.
         * Can be logged on or off via the syncHarness.
         */
        open class TestAuthMonitor : AuthMonitor {
            var isAuthed = true
            override fun isLoggedIn(): Boolean {
                return isAuthed
            }
        }

        /**
         * Test event stream that can passed on injected events.
         */
        private class TestEventStream(private val testContext: DataSynchronizerTestContext) : EventStream {
            override fun nextEvent(): Event {
                return testContext.nextStreamEvent
            }

            override fun isOpen(): Boolean {
                return true
            }

            override fun close() {
            }

            override fun cancel() {
            }
        }

        val waitLock = ReentrantLock()

        fun newDoc(key: String = "hello", value: BsonValue = BsonString("world")): BsonDocument {
            return BsonDocument("_id", BsonObjectId()).append(key, value)
        }

        fun newNamespace(): MongoNamespace {
            return MongoNamespace(
                BsonObjectId().value.toHexString(),
                BsonObjectId().value.toHexString())
        }

        fun withoutSyncVersion(document: BsonDocument?): BsonDocument? {
            if (document == null) {
                return null
            }
            val newDoc = BsonDocument.parse(document.toJson())
            newDoc.remove("__stitch_sync_version")
            return newDoc
        }

        /**
         * Compare the properties of given events
         *
         * @param expectedEvent event we are expecting to see
         * @Param actualEvent actual event generated
         */
        fun compareEvents(expectedEvent: ChangeEvent<BsonDocument>, actualEvent: ChangeEvent<BsonDocument>) {
            // assert that our actualEvent is correct
            Assert.assertEquals(expectedEvent.operationType, actualEvent.operationType)
            Assert.assertEquals(expectedEvent.documentKey, actualEvent.documentKey)

            if (actualEvent.fullDocument == null) {
                Assert.assertNull(expectedEvent.fullDocument)
            } else if (expectedEvent.fullDocument == null) {
                Assert.assertNull(actualEvent.fullDocument)
            } else {
                Assert.assertEquals(expectedEvent.fullDocument, withoutSyncVersion(actualEvent.fullDocument))
            }
            Assert.assertEquals(expectedEvent.id, actualEvent.id)
            Assert.assertEquals(expectedEvent.namespace, actualEvent.namespace)
            Assert.assertEquals(expectedEvent.updateDescription.removedFields, actualEvent.updateDescription.removedFields)
            Assert.assertEquals(expectedEvent.updateDescription.updatedFields, actualEvent.updateDescription.updatedFields)

            Assert.assertEquals(expectedEvent.hasUncommittedWrites(), actualEvent.hasUncommittedWrites())
        }

        private fun newErrorListener(
            emitErrorSemaphore: Semaphore? = null,
            expectedDocumentId: BsonValue? = null
        ): ErrorListener {
            open class TestErrorListener : ErrorListener {
                override fun onError(actualDocumentId: BsonValue?, error: Exception?) {
                    if (expectedDocumentId != null) {
                        Assert.assertEquals(expectedDocumentId, actualDocumentId)
                    }

                    emitErrorSemaphore?.release()
                }
            }
            return Mockito.spy(TestErrorListener())
        }

        private fun newConflictHandler(
            shouldConflictBeResolvedByRemote: Boolean = true,
            exceptionToThrow: Exception? = null
        ): TestConflictHandler {
            return Mockito.spy(
                TestConflictHandler(
                    shouldConflictBeResolvedByRemote = shouldConflictBeResolvedByRemote,
                    exceptionToThrow = exceptionToThrow))
        }

        private fun newChangeEventListener(
            emitEventSemaphore: Semaphore? = null,
            expectedEvent: ChangeEvent<BsonDocument>? = null
        ): DataSynchronizerTestContextImpl.TestChangeEventListener {
            return Mockito.spy(DataSynchronizerTestContextImpl.TestChangeEventListener(expectedEvent, emitEventSemaphore))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class DataSynchronizerTestContextImpl(shouldPreconfigure: Boolean = true) : DataSynchronizerTestContext {
        open class TestChangeEventListener(
            private val expectedEvent: ChangeEvent<BsonDocument>?,
            var emitEventSemaphore: Semaphore?
        ) : ChangeEventListener<BsonDocument> {
            val eventAccumulator = mutableListOf<ChangeEvent<BsonDocument>>()
            var totalEventsToAccumulate = 0

            override fun onEvent(documentId: BsonValue?, actualEvent: ChangeEvent<BsonDocument>?) {
                waitLock.lock()
                try {
                    eventAccumulator.add(actualEvent!!)
                    if (expectedEvent != null) {
                        compareEvents(expectedEvent, actualEvent)
                        Assert.assertEquals(expectedEvent.id, documentId)
                    }
                } finally {
                    if (eventAccumulator.size >= totalEventsToAccumulate) {
                        emitEventSemaphore?.release()
                    }
                    waitLock.unlock()
                }
            }
        }

        override val collectionMock: CoreRemoteMongoCollectionImpl<BsonDocument> =
            Mockito.mock(CoreRemoteMongoCollectionImpl::class.java) as CoreRemoteMongoCollectionImpl<BsonDocument>

        override var nextStreamEvent: Event = Event.Builder().withEventName("MOCK").build()
        private val streamMock = Stream(TestEventStream(this), ChangeEvent.changeEventCoder)
        override val testDocument = newDoc("count", BsonInt32(1))
        override val testDocumentId: BsonObjectId by lazy { testDocument["_id"] as BsonObjectId }
        override val testDocumentFilter by lazy { BsonDocument("_id", testDocumentId) }
        override var updateDocument: BsonDocument = BsonDocument("\$inc", BsonDocument("count", BsonInt32(1)))
        private val bsonDocumentCodec = BsonDocumentCodec()

        override var isOnline = true
            set(value) {
                this.networkMonitor.isOnline = value
                field = value
            }
        override var isLoggedIn = true
            set(value) {
                this.authMonitor.isAuthed = value
                field = value
            }
        override var shouldConflictBeResolvedByRemote: Boolean = true
            set(value) {
                this.conflictHandler.shouldConflictBeResolvedByRemote = value
                field = value
            }
        override var exceptionToThrowDuringConflict: Exception? = null
            set(value) {
                this.conflictHandler.exceptionToThrow = value
                field = value
            }

        var changeEventListener = newChangeEventListener()
            private set
        var conflictHandler = newConflictHandler()
            private set
        var errorListener = newErrorListener()
            private set

        override val namespace = newNamespace()
        val networkMonitor: TestNetworkMonitor = spy(TestNetworkMonitor())
        val authMonitor: TestAuthMonitor = spy(TestAuthMonitor())

        override val localClient: MongoClient by lazy {
            val clientKey = ObjectId().toHexString()
            SyncMongoClientFactory.getClient(
                StitchAppClientInfo(
                    clientKey,
                    String.format("%s/%s", System.getProperty("java.io.tmpdir"), clientKey),
                    ObjectId().toHexString(),
                    ObjectId().toHexString(),
                    CodecRegistries.fromCodecs(bsonDocumentCodec),
                    networkMonitor,
                    authMonitor,
                    ThreadDispatcher()
                ),
                "local",
                ServerEmbeddedMongoClientFactory.getInstance()
            )
        }

        val service: CoreStitchServiceClient by lazy {
            val service = Mockito.mock(CoreStitchServiceClientImpl::class.java)
            `when`(service.codecRegistry).thenReturn(CodecRegistries.fromCodecs(BsonDocumentCodec()))
            service
        }
        private val remoteClient = Mockito.mock(CoreRemoteMongoClientImpl::class.java)
        private val instanceKey = "${Random().nextInt()}"

        override val dataSynchronizer: DataSynchronizer =
            Mockito.spy(DataSynchronizer(
                instanceKey,
                service,
                localClient,
                remoteClient,
                networkMonitor,
                authMonitor,
                ThreadDispatcher()
            ))

        private var eventSemaphore: Semaphore? = null
        private var errorSemaphore: Semaphore? = null

        init {
            if (shouldPreconfigure) {
                // this needs to be done since the spied dataSynchronizer does not
                // re-add itself to the network monitor
                networkMonitor.addNetworkStateListener(dataSynchronizer)

                dataSynchronizer.disableSyncThread()

                dataSynchronizer.stop()

                Mockito.`when`(service.streamFunction(
                    ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyList<Any>(),
                    ArgumentMatchers.eq(ChangeEvent.changeEventCoder))
                ).thenReturn(streamMock)

                val databaseSpy = Mockito.mock(CoreRemoteMongoDatabaseImpl::class.java)
                Mockito.`when`(remoteClient.getDatabase(ArgumentMatchers.eq(namespace.databaseName))).thenReturn(databaseSpy)
                Mockito.`when`(
                    databaseSpy.getCollection(ArgumentMatchers.eq(namespace.collectionName),
                        ArgumentMatchers.eq(BsonDocument::class.java))).thenReturn(collectionMock)

                Mockito.`when`(collectionMock.namespace).thenReturn(namespace)
                val remoteFindIterable = Mockito.mock(CoreRemoteFindIterable::class.java) as CoreRemoteFindIterable<BsonDocument>
                Mockito.`when`(collectionMock.find(ArgumentMatchers.any())).thenReturn(remoteFindIterable)
                Mockito.`when`(remoteFindIterable.into<HashSet<BsonDocument>>(ArgumentMatchers.any())).thenReturn(HashSet())

                Mockito.verifyZeroInteractions(collectionMock)
            }
        }

        /**
         * Reconfigure the internal dataSynchronizer with
         * the current conflictHandler, changeEventListener, and
         * errorListener.
         */
        override fun reconfigure() {
            dataSynchronizer.configure(
                namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                bsonDocumentCodec)
        }

        override fun waitForEvents(amount: Int) {
            waitLock.lock()
            changeEventListener.totalEventsToAccumulate = amount
            if (changeEventListener.totalEventsToAccumulate > changeEventListener.eventAccumulator.size) {
                // means sem has been called and we need to wait for more events
                eventSemaphore = Semaphore(0)
                changeEventListener.emitEventSemaphore = eventSemaphore
            }
            waitLock.unlock()
            assertTrue(changeEventListener.emitEventSemaphore?.tryAcquire(10, TimeUnit.SECONDS) ?: true)
        }

        override fun waitForError() {
            assertTrue(errorSemaphore?.tryAcquire(10, TimeUnit.SECONDS) ?: true)
        }

        /**
         * Insert the current test document.
         */
        override fun insertTestDocument() {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            dataSynchronizer.insertOneAndSync(namespace, testDocument)
        }

        override fun updateTestDocument(): UpdateResult {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            return dataSynchronizer.updateOne(
                namespace,
                BsonDocument("_id", testDocumentId),
                updateDocument
            )
        }

        override fun deleteTestDocument(): DeleteResult {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            return dataSynchronizer.deleteOne(namespace, BsonDocument("_id", testDocumentId))
        }

        override fun doSyncPass() {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            dataSynchronizer.doSyncPass()
        }

        override fun queueConsumableRemoteInsertEvent() {
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(testDocument to ChangeEvent.changeEventForLocalInsert(namespace, testDocument, true)),
                mapOf())
        }

        fun getVersionForTestDocument(): BsonDocument? {
            return dataSynchronizer.getSynchronizedDocuments(namespace)
                .find { it.documentId == testDocumentId }?.lastKnownRemoteVersion
        }

        override fun queueConsumableRemoteUpdateEvent(
            id: BsonValue,
            document: BsonDocument,
            versionState: TestVersionState
        ) {
            val fakeUpdateDoc = document.clone()
            val cachedVersion = fakeUpdateDoc["__stitch_sync_version"] ?: getVersionForTestDocument()

            if (cachedVersion != null) {
                val documentVersionInfo = DocumentVersionInfo.fromVersionDoc(cachedVersion.asDocument())
                when (versionState) {
                    TestVersionState.NONE ->
                        fakeUpdateDoc.remove("__stitch_sync_version")
                    TestVersionState.PREVIOUS ->
                        fakeUpdateDoc["__stitch_sync_version"] =
                            documentVersionInfo.versionDoc?.append("v", BsonInt64(documentVersionInfo.version.versionCounter - 1))
                    TestVersionState.SAME ->
                        fakeUpdateDoc["__stitch_sync_version"] = documentVersionInfo.versionDoc
                    TestVersionState.NEXT ->
                        fakeUpdateDoc["__stitch_sync_version"] = documentVersionInfo.nextVersion
                    TestVersionState.NEW ->
                        fakeUpdateDoc["__stitch_sync_version"] = DocumentVersionInfo.getFreshVersionDocument()
                }
            }
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(document to ChangeEvent.changeEventForLocalUpdate(
                    namespace, id, null, fakeUpdateDoc, false)),
                mapOf())
        }

        override fun queueConsumableRemoteDeleteEvent() {
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(testDocument to ChangeEvent.changeEventForLocalDelete(namespace, testDocumentId, true)),
                mapOf())
        }

        override fun queueConsumableRemoteUnknownEvent() {
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(testDocument to ChangeEvent(
                    BsonDocument("_id", testDocumentId),
                    ChangeEvent.OperationType.UNKNOWN,
                    testDocument,
                    namespace,
                    BsonDocument("_id", testDocumentId),
                    null,
                    true)), mapOf())
        }

        override fun findTestDocumentFromLocalCollection(): BsonDocument? {
            // TODO: this may be rendered unnecessary with STITCH-1972
            return withoutSyncVersion(
                dataSynchronizer.find(
                    namespace,
                    BsonDocument("_id", testDocumentId),
                    10,
                    null,
                    null,
                    BsonDocument::class.java,
                    CodecRegistries.fromCodecs(bsonDocumentCodec)).firstOrNull())
        }

        override fun verifyChangeEventListenerCalledForActiveDoc(
            times: Int,
            vararg expectedChangeEvents: ChangeEvent<BsonDocument>
        ) {
            val changeEventArgumentCaptor = ArgumentCaptor.forClass(ChangeEvent::class.java)
            Mockito.verify(changeEventListener, times(times)).onEvent(
                any(),
                changeEventArgumentCaptor.capture() as ChangeEvent<BsonDocument>?)

            if (expectedChangeEvents.isNotEmpty()) {
                changeEventArgumentCaptor.allValues.forEachIndexed { i, actualChangeEvent ->
                    compareEvents(
                        expectedChangeEvents[i],
                        actualChangeEvent as ChangeEvent<BsonDocument>)
                }
            }
        }

        override fun verifyErrorListenerCalledForActiveDoc(times: Int, error: Exception?) {
            Mockito.verify(errorListener, times(times)).onError(eq(testDocumentId), eq(error))
        }

        override fun verifyConflictHandlerCalledForActiveDoc(
            times: Int,
            expectedLocalConflictEvent: ChangeEvent<BsonDocument>?,
            expectedRemoteConflictEvent: ChangeEvent<BsonDocument>?
        ) {
            val localChangeEventArgumentCaptor = ArgumentCaptor.forClass(ChangeEvent::class.java)
            val remoteChangeEventArgumentCaptor = ArgumentCaptor.forClass(ChangeEvent::class.java)

            Mockito.verify(conflictHandler, times(times)).resolveConflict(
                eq(testDocumentId),
                localChangeEventArgumentCaptor.capture() as ChangeEvent<BsonDocument>?,
                remoteChangeEventArgumentCaptor.capture() as ChangeEvent<BsonDocument>?)

            if (expectedLocalConflictEvent != null) {
                compareEvents(expectedLocalConflictEvent, localChangeEventArgumentCaptor.value as ChangeEvent<BsonDocument>)
            }

            if (expectedRemoteConflictEvent != null) {
                compareEvents(expectedRemoteConflictEvent, remoteChangeEventArgumentCaptor.value as ChangeEvent<BsonDocument>)
            }
        }

        override fun verifyWatchFunctionCalled(times: Int, expectedArgs: Document) {
            Mockito.verify(service, times(times)).streamFunction(
                eq("watch"), eq(Collections.singletonList(expectedArgs)), eq(ChangeEvent.changeEventCoder))
        }

        override fun verifyStartCalled(times: Int) {
            Mockito.verify(dataSynchronizer, times(times)).start()
        }

        override fun verifyStopCalled(times: Int) {
            Mockito.verify(dataSynchronizer, times(times)).stop()
        }

        override fun mockInsertException(exception: Exception) {
            `when`(collectionMock.insertOne(any())).thenThrow(exception)
        }

        override fun mockUpdateResult(remoteUpdateResult: RemoteUpdateResult) {
            `when`(collectionMock.updateOne(any(), any())).thenReturn(remoteUpdateResult)
        }

        override fun mockUpdateException(exception: Exception) {
            `when`(collectionMock.updateOne(any(), any())).thenAnswer {
                throw exception
            }
        }

        override fun mockDeleteResult(remoteDeleteResult: RemoteDeleteResult) {
            `when`(collectionMock.deleteOne(any())).thenReturn(remoteDeleteResult)
        }

        override fun mockDeleteException(exception: Exception) {
            `when`(collectionMock.deleteOne(any())).thenAnswer {
                throw exception
            }
        }

        override fun close() {
            dataSynchronizer.close()
        }

        private fun configureNewErrorListener() {
            val emitErrorSemaphore = Semaphore(0)
            this.errorSemaphore?.release()
            this.errorListener = newErrorListener(emitErrorSemaphore)
            this.reconfigure()
            this.errorSemaphore = emitErrorSemaphore
        }

        private fun configureNewChangeEventListener(expectedChangeEvent: ChangeEvent<BsonDocument>? = null) {
            val emitEventSemaphore = Semaphore(0)
            this.eventSemaphore?.release()
            this.changeEventListener = newChangeEventListener(emitEventSemaphore, expectedChangeEvent)
            this.reconfigure()
            this.eventSemaphore = emitEventSemaphore
        }

        private fun configureNewConflictHandler() {
            this.conflictHandler = newConflictHandler(shouldConflictBeResolvedByRemote, exceptionToThrowDuringConflict)
            this.reconfigure()
        }
    }

    private var latestCtx: DataSynchronizerTestContext? = null

    override fun close() {
        latestCtx?.dataSynchronizer?.close()
    }

    internal fun freshTestContext(shouldPreconfigure: Boolean = true): DataSynchronizerTestContext {
        latestCtx?.dataSynchronizer?.close()
        latestCtx = DataSynchronizerTestContextImpl(shouldPreconfigure)
        return latestCtx!!
    }

    internal fun createNamespaceChangeStreamListenerWithContext(context: DataSynchronizerTestContext): Pair<NamespaceChangeStreamListener, NamespaceSynchronizationConfig> {
        val nsConfigMock = Mockito.mock(NamespaceSynchronizationConfig::class.java)
        val namespaceChangeStreamListener = NamespaceChangeStreamListener(
            context.namespace,
            nsConfigMock,
            (context as DataSynchronizerTestContextImpl).service,
            context.networkMonitor,
            context.authMonitor)

        return namespaceChangeStreamListener to nsConfigMock
    }

    internal fun <T> createCoreSyncWithContext(
        context: DataSynchronizerTestContext,
        resultClass: Class<T>,
        codec: Codec<T>? = null
    ):
        Pair<CoreSync<T>, SyncOperations<T>> {
        val syncOperations = Mockito.spy(SyncOperations(
            context.namespace,
            resultClass,
            context.dataSynchronizer,
            CodecRegistries.fromCodecs(codec ?: BsonDocumentCodec(), DocumentCodec())))
        val coreSync = CoreSyncImpl(
            context.namespace,
            resultClass,
            context.dataSynchronizer,
            (context as DataSynchronizerTestContextImpl).service,
            syncOperations)

        return coreSync to syncOperations
    }
}