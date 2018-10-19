package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.net.Event
import com.mongodb.stitch.core.internal.net.EventStream
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.internal.net.Stream
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollectionImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoDatabaseImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.TestUtils
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
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
import org.junit.Assert
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import java.lang.Exception
import java.util.*
import java.util.concurrent.Semaphore

class SyncHarness(withConfiguration: Boolean = true) {
    companion object {
        open class TestConflictHandler(private val expectedLocalEvent: ChangeEvent<BsonDocument>?,
                                       private val expectedRemoteEvent: ChangeEvent<BsonDocument>?,
                                       var acceptRemote: Boolean,
                                       var exception: Exception? = null): ConflictHandler<BsonDocument> {
            override fun resolveConflict(documentId: BsonValue?, localEvent: ChangeEvent<BsonDocument>?, remoteEvent: ChangeEvent<BsonDocument>?): BsonDocument? {
                // assert that our actualEvent is correct
                if (expectedLocalEvent != null) {
                    compareEvents(expectedLocalEvent, localEvent!!)
                }

                if (expectedRemoteEvent != null) {
                    compareEvents(expectedRemoteEvent, remoteEvent!!)
                }

                if (exception != null) {
                    throw exception!!
                }
                return if (acceptRemote) remoteEvent?.fullDocument else localEvent?.fullDocument
            }
        }

        internal fun newDoc(key: String = "hello", value: BsonValue = BsonString("world")): BsonDocument {
            return BsonDocument("_id", BsonObjectId()).append(key, value)
        }
        
        fun withoutVersionId(document: BsonDocument?): BsonDocument? {
            if (document == null) {
                return null
            }
            val newDoc = BsonDocument.parse(document.toJson())
            newDoc.remove("__stitch_sync_version")
            return newDoc
        }

        fun withoutId(document: BsonDocument): BsonDocument {
            val newDoc = BsonDocument.parse(document.toJson())
            newDoc.remove("_id")
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
                    try {
                        if (expectedDocumentId != null) {
                            Assert.assertEquals(expectedDocumentId, actualDocumentId)
                        }
                    } finally {
                        emitErrorSemaphore?.release()
                    }
                }
            }
            return Mockito.spy(TestErrorListener())
        }

        private fun newConflictHandler(expectedLocalEvent: ChangeEvent<BsonDocument>? = null,
                                       expectedRemoteEvent: ChangeEvent<BsonDocument>? = null,
                                       acceptRemote: Boolean = true): TestConflictHandler {
            return Mockito.spy(TestConflictHandler(expectedLocalEvent, expectedRemoteEvent, acceptRemote))
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

    val namespace = MongoNamespace("foo", "bar")

    var isOnline = true
    private open class TestNetworkMonitor(val syncHarness: SyncHarness): NetworkMonitor {
        override fun removeNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }

        override fun isConnected(): Boolean {
            return syncHarness.isOnline
        }

        override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }
    }
    val networkMonitor: NetworkMonitor = spy(TestNetworkMonitor(this))

    var isLoggedIn = true
    private open class TestAuthMonitor(val syncHarness: SyncHarness): AuthMonitor {
        override fun isLoggedIn(): Boolean {
            return syncHarness.isLoggedIn
        }
    }
    val authMonitor: AuthMonitor = spy(TestAuthMonitor(this))

    private val localClient by lazy {
        SyncMongoClientFactory.getClient(
            TestUtils.getClientInfo(),
            "mongodblocal",
            ServerEmbeddedMongoClientFactory.getInstance()
        )
    }

    private val instanceKey = "${Random().nextInt()}"
    val service: CoreStitchServiceClient by lazy {
        val service = Mockito.mock(CoreStitchServiceClientImpl::class.java)
        `when`(service.codecRegistry).thenReturn(CodecRegistries.fromCodecs(BsonDocumentCodec()))
        service
    }

    private val remoteClient = Mockito.mock(CoreRemoteMongoClientImpl::class.java)
    val dataSynchronizer: DataSynchronizer = Mockito.spy(DataSynchronizer(
        instanceKey,
        service,
        localClient,
        remoteClient,
        networkMonitor,
        authMonitor
    ))
    val syncOperations: SyncOperations<BsonDocument> = Mockito.spy(SyncOperations(
        namespace,
        BsonDocument::class.java,
        dataSynchronizer,
        CodecRegistries.fromCodecs(BsonDocumentCodec())))
    val coreSync = CoreSyncImpl(
        namespace,
        BsonDocument::class.java,
        dataSynchronizer,
        service,
        syncOperations)

    val activeDoc = newDoc("count", BsonInt32(1))
    val activeDocId: BsonObjectId by lazy { activeDoc["_id"] as BsonObjectId }
    val updateDoc: BsonDocument = BsonDocument("\$inc", BsonDocument("count", BsonInt32(1)))

    class SemaphoreResultHolder<T>(var emitEventSemaphore: Semaphore? = null,
                                   var emitErrorSemaphore: Semaphore? = null,
                                   var result: T? = null) {
        fun withEventSemaphore(emitEventSemaphore: Semaphore?): SemaphoreResultHolder<T> {
            this.emitEventSemaphore = emitEventSemaphore
            return this
        }

        fun withErrorSemaphore(emitErrorSemaphore: Semaphore?): SemaphoreResultHolder<T> {
            this.emitErrorSemaphore = emitErrorSemaphore
            return this
        }

        fun withResult(result: T): SemaphoreResultHolder<T> {
            this.result = result
            return this
        }
    }

    private var changeEventListener = newChangeEventListener()
    var conflictHandler = newConflictHandler()
        private set
    private var errorListener = newErrorListener()
    private val bsonDocumentCodec = BsonDocumentCodec()

    val collectionMock: CoreRemoteMongoCollection<BsonDocument>

    var nextStreamEvent: Event = Event.Builder().withEventName("MOCK").build()
    private class TestEventStream(val harness: SyncHarness): EventStream {
        override fun nextEvent(): Event {
            return harness.nextStreamEvent
        }

        override fun isOpen(): Boolean {
            return true
        }

        override fun close() {
        }

        override fun cancel() {
        }
    }
    val streamMock = Stream(TestEventStream(this), ChangeEvent.changeEventCoder)
    var shouldExpectError: Boolean = false

    init {
        if (withConfiguration) {
            dataSynchronizer.disableSyncThread()

            dataSynchronizer.stop()

            dataSynchronizer.configure(
                namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                bsonDocumentCodec)
        }

        Mockito.`when`(service.streamFunction(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyList<Any>(),
            ArgumentMatchers.eq(ChangeEvent.changeEventCoder))
        ).thenReturn(streamMock)

        val databaseSpy = Mockito.mock(CoreRemoteMongoDatabaseImpl::class.java)
        Mockito.`when`(remoteClient.getDatabase(ArgumentMatchers.eq(namespace.databaseName))).thenReturn(databaseSpy)
        collectionMock = Mockito.mock(CoreRemoteMongoCollectionImpl::class.java) as CoreRemoteMongoCollectionImpl<BsonDocument>
        Mockito.`when`(databaseSpy.getCollection(ArgumentMatchers.eq(namespace.collectionName), ArgumentMatchers.eq(BsonDocument::class.java))).thenReturn(collectionMock)

        Mockito.`when`(collectionMock.namespace).thenReturn(namespace)
        val remoteFindIterable = Mockito.mock(CoreRemoteFindIterable::class.java) as CoreRemoteFindIterable<BsonDocument>
        Mockito.`when`(collectionMock.find(ArgumentMatchers.any())).thenReturn(remoteFindIterable)
        Mockito.`when`(remoteFindIterable.into<HashSet<BsonDocument>>(ArgumentMatchers.any())).thenReturn(HashSet())

        Mockito.verifyZeroInteractions(collectionMock)
    }

    fun reconfigure() {
        dataSynchronizer.configure(namespace,
            conflictHandler,
            changeEventListener,
            errorListener,
            bsonDocumentCodec)
    }

    fun insertOne(expectedChangeEvent: ChangeEvent<BsonDocument>? = null,
                  expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                  expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null,
                  withConfiguration: Boolean = true): SemaphoreResultHolder<Unit> {
        val semaphoreHolder = SemaphoreResultHolder<Unit>()

        if (withConfiguration) {
            semaphoreHolder.withEventSemaphore(configureNewChangeEventListener(expectedChangeEvent))
            semaphoreHolder.withErrorSemaphore(configureNewErrorListener())
            configureNewConflictHandler(expectedLocalConflictEvent, expectedRemoteConflictEvent)
        }

        dataSynchronizer.insertOneAndSync(namespace, activeDoc)

        return semaphoreHolder
    }

    fun insertOneAndWait(expectedChangeEvent: ChangeEvent<BsonDocument> = ChangeEvent.changeEventForLocalInsert(namespace, activeDoc, false),
                         expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                         expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null): SemaphoreResultHolder<Unit> {
        val semaphoreHolder = this.insertOne(expectedChangeEvent, expectedLocalConflictEvent, expectedRemoteConflictEvent)

        semaphoreHolder.emitEventSemaphore?.acquire()

        if (shouldExpectError) {
            semaphoreHolder.emitErrorSemaphore?.acquire()
        }

        return semaphoreHolder
    }

    fun updateOne(expectedChangeEvent: ChangeEvent<BsonDocument>? = null,
                  expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                  expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null): SemaphoreResultHolder<UpdateResult> {
        val semaphoreHolder = SemaphoreResultHolder<UpdateResult>(
            configureNewChangeEventListener(expectedChangeEvent),
            configureNewErrorListener())
        configureNewConflictHandler(expectedLocalConflictEvent, expectedRemoteConflictEvent)

        return semaphoreHolder.withResult(dataSynchronizer.updateOneById(namespace, activeDoc["_id"], updateDoc))
    }

    fun updateOneAndWait(expectedChangeEvent: ChangeEvent<BsonDocument>? = null,
                         expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                         expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null): SemaphoreResultHolder<UpdateResult> {
        val semaphoreHolder = this.updateOne(expectedChangeEvent, expectedLocalConflictEvent, expectedRemoteConflictEvent)

        semaphoreHolder.emitEventSemaphore?.acquire()

        if (shouldExpectError) {
            semaphoreHolder.emitErrorSemaphore?.acquire()
        }

        return semaphoreHolder
    }

    fun deleteOne(expectedChangeEvent: ChangeEvent<BsonDocument>? = null,
                  expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                  expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null,
                  withConfiguration: Boolean = true): SemaphoreResultHolder<DeleteResult> {
        val semaphoreHolder = SemaphoreResultHolder<DeleteResult>()
        if (withConfiguration) {
            semaphoreHolder.withEventSemaphore(configureNewChangeEventListener(expectedChangeEvent))
            semaphoreHolder.withErrorSemaphore(configureNewErrorListener())
            configureNewConflictHandler(expectedLocalConflictEvent, expectedRemoteConflictEvent)
        }

        return semaphoreHolder.withResult(dataSynchronizer.deleteOneById(namespace, activeDoc["_id"]))
    }

    fun deleteOneAndWait(expectedChangeEvent: ChangeEvent<BsonDocument>? = ChangeEvent.changeEventForLocalDelete(namespace, activeDocId, false),
                         expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                         expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null): SemaphoreResultHolder<DeleteResult> {
        val semaphoreHolder = deleteOne(expectedChangeEvent, expectedLocalConflictEvent, expectedRemoteConflictEvent)

        semaphoreHolder.emitEventSemaphore?.acquire()

        if (shouldExpectError) {
            semaphoreHolder.emitErrorSemaphore?.acquire()
        }

        return semaphoreHolder
    }

    fun findActiveDocFromLocal(): BsonDocument? {
        return withoutVersionId(
            dataSynchronizer.findOneById(
                namespace,
                activeDoc["_id"],
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec())))
    }

    fun doSyncPass(expectedChangeEvent: ChangeEvent<BsonDocument>? = null) {
        val emitErrorSemaphore = configureNewErrorListener()
        var emitEventSemaphore: Semaphore? = null

        if (expectedChangeEvent != null) {
            emitEventSemaphore = configureNewChangeEventListener(expectedChangeEvent)
        }

        dataSynchronizer.doSyncPass()

        emitEventSemaphore?.acquire()

        if (shouldExpectError) {
            emitErrorSemaphore.acquire()
        }
    }

    fun start() {
        dataSynchronizer.start()
    }

    fun stop() {
        dataSynchronizer.stop()
    }

    fun onNetworkStateChanged() {
        dataSynchronizer.onNetworkStateChanged()
    }

    fun isRunning(): Boolean {
        return dataSynchronizer.isRunning
    }

    internal fun getSynchronizedDocuments(): Set<CoreDocumentSynchronizationConfig> {
        return this.dataSynchronizer.getSynchronizedDocuments(namespace)
    }

    fun verifyChangeEventListenerCalledForActiveDoc() {
        Mockito.verify(changeEventListener, times(1)).onEvent(eq(activeDoc["_id"]), ArgumentMatchers.any())
    }

    fun verifyErrorListenerCalledForActiveDoc(times: Int, error: Exception? = null) {
        Mockito.verify(errorListener, times(times)).onError(eq(activeDoc["_id"]), eq(error))
    }

    fun verifyConflictHandlerCalledForActiveDoc(times: Int) {
        Mockito.verify(conflictHandler, times(times)).resolveConflict(eq(activeDoc["_id"]), ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    fun verifyStartCalled(times: Int) {
        Mockito.verify(dataSynchronizer, times(times)).start()
    }

    fun verifyStopCalled(times: Int) {
        Mockito.verify(dataSynchronizer, Mockito.times(times)).stop()
    }

    fun verifyStreamFunctionCalled(times: Int) {
        Mockito.verify(service, Mockito.times(times)).streamFunction(ArgumentMatchers.anyString(), ArgumentMatchers.anyList<Document>(), ArgumentMatchers.eq(ChangeEvent.changeEventCoder))
    }

    private fun configureNewErrorListener(): Semaphore {
        val emitErrorSemaphore = Semaphore(0)
        this.errorListener = newErrorListener(emitErrorSemaphore)
        dataSynchronizer.configure(namespace,
            conflictHandler,
            changeEventListener,
            errorListener,
            bsonDocumentCodec)
        return emitErrorSemaphore
    }

    private fun configureNewChangeEventListener(expectedChangeEvent: ChangeEvent<BsonDocument>? = null): Semaphore? {
        if (expectedChangeEvent != null) {
            val emitEventSemaphore = Semaphore(0)
            this.changeEventListener = newChangeEventListener(emitEventSemaphore, expectedChangeEvent)
            dataSynchronizer.configure(namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                bsonDocumentCodec)
            return emitEventSemaphore
        }

        return null
    }

    private fun configureNewConflictHandler(expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
                                            expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null) {
        if (expectedLocalConflictEvent != null || expectedRemoteConflictEvent != null) {
            this.conflictHandler = newConflictHandler(expectedLocalConflictEvent, expectedRemoteConflictEvent)
            dataSynchronizer.configure(namespace,
                conflictHandler,
                changeEventListener,
                errorListener,
                bsonDocumentCodec)
        }
    }
}

fun withSyncHarness(withConfiguration: Boolean = true,
                    work: (harness: SyncHarness) -> Unit) {
    work(SyncHarness(withConfiguration))
}