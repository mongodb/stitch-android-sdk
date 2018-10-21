package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
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
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollectionImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoDatabaseImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.TestUtils
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.configuration.CodecRegistries
import org.junit.Assert
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import java.lang.Exception
import java.util.*
import java.util.concurrent.Semaphore

class SyncHarness {
    companion object {
        /**
         * Conflict handler used for testing purposes.
         *
         * @param shouldConflictBeResolvedByRemote whether or not to resolve using the remote document or the local
         *                     document
         * @param exceptionToThrow if set, will throw an exceptionToThrow after comparing the events
         */
        open class TestConflictHandler(var shouldConflictBeResolvedByRemote: Boolean,
                                       var exceptionToThrow: Exception? = null): ConflictHandler<BsonDocument> {
            override fun resolveConflict(documentId: BsonValue?,
                                         localEvent: ChangeEvent<BsonDocument>?,
                                         remoteEvent: ChangeEvent<BsonDocument>?): BsonDocument? {
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
        open class TestNetworkMonitor: NetworkMonitor {
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
        open class TestAuthMonitor: AuthMonitor {
            var isAuthed = true
            override fun isLoggedIn(): Boolean {
                return isAuthed
            }
        }

        private class TestEventStream(private val testContext: TestContext): EventStream {
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

        fun newDoc(key: String = "hello", value: BsonValue = BsonString("world")): BsonDocument {
            return BsonDocument("_id", BsonObjectId()).append(key, value)
        }

        fun newNamespace(): MongoNamespace {
            return MongoNamespace(
                BsonObjectId().value.toHexString(),
                BsonObjectId().value.toHexString())
        }

        fun withoutVersionId(document: BsonDocument?): BsonDocument? {
            if (document == null) {
                return null
            }
            val newDoc = BsonDocument.parse(document.toJson())
            newDoc.remove("__stitch_sync_version")
            return newDoc
        }

        fun compareEvents(expectedEvent: ChangeEvent<BsonDocument>,
                          actualEvent: ChangeEvent<BsonDocument>) {
            // assert that our actualEvent is correct
            Assert.assertEquals(expectedEvent.operationType, actualEvent.operationType)
            Assert.assertEquals(expectedEvent.documentKey, actualEvent.documentKey)

            if (actualEvent.fullDocument == null) {
                Assert.assertNull(expectedEvent.fullDocument)
            } else if (expectedEvent.fullDocument == null) {
                Assert.assertNull(actualEvent.fullDocument)
            } else {
                Assert.assertEquals(expectedEvent.fullDocument, withoutVersionId(actualEvent.fullDocument))
            }
            Assert.assertEquals(expectedEvent.id, actualEvent.id)
            Assert.assertEquals(expectedEvent.namespace, actualEvent.namespace)
            Assert.assertEquals(expectedEvent.updateDescription.removedFields, actualEvent.updateDescription.removedFields)
            Assert.assertEquals(expectedEvent.updateDescription.updatedFields, actualEvent.updateDescription.updatedFields)

            Assert.assertEquals(expectedEvent.hasUncommittedWrites(), actualEvent.hasUncommittedWrites())
        }
        
        private fun newErrorListener(emitErrorSemaphore: Semaphore? = null,
                                     expectedDocumentId: BsonValue? = null): ErrorListener {
            open class TestErrorListener: ErrorListener {
                override fun onError(actualDocumentId: BsonValue?, error: Exception?) {
                    if (expectedDocumentId != null) {
                        Assert.assertEquals(expectedDocumentId, actualDocumentId)
                    }

                    emitErrorSemaphore?.release()
                }
            }
            return Mockito.spy(TestErrorListener())
        }

        private fun newConflictHandler(shouldConflictBeResolvedByRemote: Boolean = true,
                                       exceptionToThrow: Exception? = null): TestConflictHandler {
            return Mockito.spy(
                TestConflictHandler(
                    shouldConflictBeResolvedByRemote = shouldConflictBeResolvedByRemote,
                    exceptionToThrow = exceptionToThrow))
        }

        private fun newChangeEventListener(emitEventSemaphore: Semaphore? = null,
                                           expectedEvent: ChangeEvent<BsonDocument>? = null): ChangeEventListener<BsonDocument> {
            open class TestChangeEventListener: ChangeEventListener<BsonDocument> {
                override fun onEvent(documentId: BsonValue?, actualEvent: ChangeEvent<BsonDocument>?) {
                    try {
                        if (expectedEvent != null) {
                            compareEvents(expectedEvent, actualEvent!!)
                            Assert.assertEquals(expectedEvent.id, documentId)
                        }
                    } finally {
                        emitEventSemaphore?.release()
                    }
                }
            }
            return Mockito.spy(TestChangeEventListener())
        }
    }

    interface TestContext {
        val namespace: MongoNamespace
        val testDocument: BsonDocument
        val testDocumentId: BsonValue
        var updateDocument: BsonDocument

        val collectionMock: CoreRemoteMongoCollectionImpl<BsonDocument>
        var shouldConflictBeResolvedByRemote: Boolean
        var exceptionToThrowDuringConflict: Exception?
        var isOnline: Boolean
        var isLoggedIn: Boolean
        var nextStreamEvent: Event
        val dataSynchronizer: DataSynchronizer

        fun reconfigure()

        fun waitForError()

        fun waitForEvent()

        fun insertTestDocument()

        fun updateTestDocument()

        fun deleteTestDocument()

        fun doSyncPass()

        fun findTestDocumentFromLocalCollection(): BsonDocument?

        fun verifyChangeEventListenerCalledForActiveDoc(times: Int, expectedChangeEvent: ChangeEvent<BsonDocument>? = null)

        fun verifyErrorListenerCalledForActiveDoc(times: Int, error: Exception? = null)

        fun verifyConflictHandlerCalledForActiveDoc(times: Int,
                                                    expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                                                    expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null)

        fun verifyStreamFunctionCalled(times: Int, expectedArgs: List<Any>)

        fun verifyStartCalled(times: Int)

        fun verifyStopCalled(times: Int)

        fun queueConsumableRemoteInsertEvent()

        fun queueConsumableRemoteUpdateEvent()

        fun queueConsumableRemoteDeleteEvent()

        fun queueConsumableRemoteUnknownEvent()

        fun mockInsertException(exception: Exception)

        fun mockUpdateResult(remoteUpdateResult: RemoteUpdateResult)
    }

    @Suppress("UNCHECKED_CAST")
    private class TestContextImpl(shouldPreconfigure: Boolean = true): TestContext {
        override val collectionMock: CoreRemoteMongoCollectionImpl<BsonDocument> =
            Mockito.mock(CoreRemoteMongoCollectionImpl::class.java) as CoreRemoteMongoCollectionImpl<BsonDocument>

        override var nextStreamEvent: Event = Event.Builder().withEventName("MOCK").build()
        private val streamMock = Stream(TestEventStream(this), ChangeEvent.changeEventCoder)
        override val testDocument = newDoc("count", BsonInt32(1))
        override val testDocumentId: BsonObjectId by lazy { testDocument["_id"] as BsonObjectId }
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

        private val localClient by lazy {
            SyncMongoClientFactory.getClient(
                TestUtils.getClientInfo(),
                "mongodblocal",
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
                authMonitor
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

        override fun waitForEvent() {
            eventSemaphore?.acquire()
        }

        override fun waitForError() {
            errorSemaphore?.acquire()
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

        override fun updateTestDocument() {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            dataSynchronizer.updateOneById(namespace, testDocumentId, updateDocument)
        }

        override fun deleteTestDocument() {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            dataSynchronizer.deleteOneById(namespace, testDocumentId)
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

        override fun queueConsumableRemoteUpdateEvent() {
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(testDocument to ChangeEvent.changeEventForLocalUpdate(namespace, testDocumentId, updateDocument, testDocument, false)),
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
            return withoutVersionId(
                dataSynchronizer.findOneById(
                    namespace,
                    testDocumentId,
                    BsonDocument::class.java,
                    CodecRegistries.fromCodecs(bsonDocumentCodec)))
        }

        override fun verifyChangeEventListenerCalledForActiveDoc(times: Int, expectedChangeEvent: ChangeEvent<BsonDocument>?) {
            val changeEventArgumentCaptor = ArgumentCaptor.forClass(ChangeEvent::class.java)
            Mockito.verify(changeEventListener, times(times)).onEvent(
                eq(testDocument["_id"]),
                changeEventArgumentCaptor.capture() as ChangeEvent<BsonDocument>?)

            if (expectedChangeEvent != null) {
                compareEvents(expectedChangeEvent, changeEventArgumentCaptor.value as ChangeEvent<BsonDocument>)
            }
        }

        override fun verifyErrorListenerCalledForActiveDoc(times: Int, error: Exception?) {
            Mockito.verify(errorListener, times(times)).onError(eq(testDocumentId), eq(error))
        }

        override fun verifyConflictHandlerCalledForActiveDoc(times: Int,
                                                             expectedLocalConflictEvent: ChangeEvent<BsonDocument>?,
                                                             expectedRemoteConflictEvent: ChangeEvent<BsonDocument>?) {
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

        override fun verifyStreamFunctionCalled(times: Int, expectedArgs: List<Any>) {
            Mockito.verify(service, times(times)).streamFunction(eq("watch"), eq(expectedArgs), eq(ChangeEvent.changeEventCoder))
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

        private fun configureNewErrorListener() {
            val emitErrorSemaphore = Semaphore(0)
            this.errorListener = newErrorListener(emitErrorSemaphore)
            this.reconfigure()
            this.errorSemaphore?.release()
            this.errorSemaphore = emitErrorSemaphore
        }

        private fun configureNewChangeEventListener(expectedChangeEvent: ChangeEvent<BsonDocument>? = null) {
            val emitEventSemaphore = Semaphore(0)
            this.changeEventListener = newChangeEventListener(emitEventSemaphore, expectedChangeEvent)
            this.reconfigure()
            this.eventSemaphore?.release()
            this.eventSemaphore = emitEventSemaphore
        }

        private fun configureNewConflictHandler() {
            this.conflictHandler = newConflictHandler(shouldConflictBeResolvedByRemote, exceptionToThrowDuringConflict)
            this.reconfigure()
        }
    }

    internal fun newTestContext(shouldPreconfigure: Boolean = true): TestContext {
        return TestContextImpl(shouldPreconfigure)
    }

    internal fun createNamespaceChangeStreamListenerWithContext(context: TestContext): Pair<NamespaceChangeStreamListener, NamespaceSynchronizationConfig> {
        val nsConfigMock = Mockito.mock(NamespaceSynchronizationConfig::class.java)
        val namespaceChangeStreamListener = NamespaceChangeStreamListener(
            context.namespace,
            nsConfigMock,
            (context as TestContextImpl).service,
            context.networkMonitor,
            context.authMonitor)

        return namespaceChangeStreamListener to nsConfigMock
    }

    internal fun createCoreSyncWithContext(context: TestContext): Pair<CoreSync<BsonDocument>, SyncOperations<BsonDocument>> {
        val syncOperations = Mockito.spy(SyncOperations(
            context.namespace,
            BsonDocument::class.java,
            context.dataSynchronizer,
            CodecRegistries.fromCodecs(BsonDocumentCodec())))
        val coreSync = CoreSyncImpl(
            context.namespace,
            BsonDocument::class.java,
            context.dataSynchronizer,
            (context as TestContextImpl).service,
            syncOperations)

        return coreSync to syncOperations
    }
//
//    fun deleteTestDocument(expectedChangeEvent: ChangeEvent<BsonDocument>? = null,
//                           expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
//                           expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null,
//                           withConfiguration: Boolean = true): SemaphoreResultHolder<DeleteResult> {
//        val semaphoreHolder = SemaphoreResultHolder<DeleteResult>()
//        if (withConfiguration) {
//            semaphoreHolder.withEventSemaphore(configureNewChangeEventListener(expectedChangeEvent))
//            semaphoreHolder.withErrorSemaphore(configureNewErrorListener())
//            configureNewConflictHandler(expectedLocalConflictEvent, expectedRemoteConflictEvent)
//        }
//
//        return semaphoreHolder.withResult(dataSynchronizer.deleteOneById(namespace, testDocument["_id"]))
//    }
//
//    fun deleteTestDocumentAndWait(expectedChangeEvent: ChangeEvent<BsonDocument>? = ChangeEvent.changeEventForLocalDelete(namespace, testDocumentId, false),
//                                  expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
//                                  expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null): SemaphoreResultHolder<DeleteResult> {
//        val semaphoreHolder = deleteTestDocument(expectedChangeEvent, expectedLocalConflictEvent, expectedRemoteConflictEvent)
//
//        semaphoreHolder.emitEventSemaphore?.acquire()
//
//        if (shouldExpectError) {
//            semaphoreHolder.emitErrorSemaphore?.acquire()
//        }
//
//        return semaphoreHolder
//    }

//    fun start() {
//        dataSynchronizer.start()
//    }
//
//    fun stop() {
//        dataSynchronizer.stop()
//    }
//
//    fun onNetworkStateChanged() {
//        dataSynchronizer.onNetworkStateChanged()
//    }
//
//    fun isRunning(): Boolean {
//        return dataSynchronizer.isRunning
//    }
//
//    internal fun getSynchronizedDocuments(): Set<CoreDocumentSynchronizationConfig> {
//        return this.dataSynchronizer.getSynchronizedDocuments(namespace)
//    }
//
}
