package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.common.Dispatcher
import com.mongodb.stitch.core.internal.common.ThreadDispatcher
import com.mongodb.stitch.core.internal.net.Event
import com.mongodb.stitch.core.internal.net.EventStream
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.internal.net.Stream
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.BaseChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.CompactChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollectionImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoDatabaseImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.ResultDecoders
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictResolution
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
import java.lang.IllegalStateException
import java.util.Collections
import java.util.Random
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock

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
                remoteEvent: CompactChangeEvent<BsonDocument>?
            ): ConflictResolution {
                if (exceptionToThrow != null) {
                    throw exceptionToThrow!!
                }
                return when (shouldConflictBeResolvedByRemote) {
                    true -> ConflictResolution.fromRemote()
                    false -> ConflictResolution.fromLocal()
                }
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
            var userId = "bound"

            override fun isLoggedIn(): Boolean {
                return isAuthed
            }

            override fun tryIsLoggedIn(): Boolean {
                return isAuthed
            }

            override fun getActiveUserId(): String? {
                return userId
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
            compareBaseEvents(expectedEvent, actualEvent)

            Assert.assertEquals(expectedEvent.id, actualEvent.id)
            Assert.assertEquals(expectedEvent.namespace, actualEvent.namespace)
        }

        fun compareEvents(expectedEvent: CompactChangeEvent<BsonDocument>, actualEvent: CompactChangeEvent<BsonDocument>) {
            compareBaseEvents(expectedEvent, actualEvent)

            Assert.assertEquals(expectedEvent.stitchDocumentVersion, actualEvent.stitchDocumentVersion)
            Assert.assertEquals(expectedEvent.stitchDocumentHash, actualEvent.stitchDocumentHash)
        }

        private fun compareBaseEvents(expectedEvent: BaseChangeEvent<BsonDocument>, actualEvent: BaseChangeEvent<BsonDocument>) {
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

            Assert.assertEquals(expectedEvent.updateDescription!!.removedFields, actualEvent.updateDescription!!.removedFields)
            Assert.assertEquals(expectedEvent.updateDescription!!.updatedFields, actualEvent.updateDescription!!.updatedFields)

            Assert.assertEquals(expectedEvent.hasUncommittedWrites(), actualEvent.hasUncommittedWrites())
        }

        private fun newErrorListener(
            emitErrorSemaphore: Semaphore? = null,
            expectedDocumentId: BsonValue? = null
        ): ExceptionListener {
            open class TestExceptionListener : ExceptionListener {
                override fun onError(actualDocumentId: BsonValue?, error: Exception?) {
                    if (expectedDocumentId != null) {
                        Assert.assertEquals(expectedDocumentId, actualDocumentId)
                    }
                    emitErrorSemaphore?.release()
                }
            }
            return Mockito.spy(TestExceptionListener())
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
    private class DataSynchronizerTestContextImpl(
        shouldPreconfigure: Boolean = true,
        undoDocuments: List<BsonDocument> = ArrayList(),
        override val namespace: MongoNamespace = newNamespace(),
        override val clientKey: String = ObjectId().toHexString(),
        override val instanceKey: String = "${Random().nextInt()}"
    ) : DataSynchronizerTestContext {
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
        private val streamMock = Stream(TestEventStream(this), ResultDecoders.compactChangeEventDecoder(BsonDocumentCodec()))
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

        override val testDocumentVersion: BsonDocument?
            get() =
                this.testDocument.getDocument("__stitch_sync_version", null)
                    ?: getVersionForTestDocument()

        override val testDocumentHash: Long
            get() = HashUtils.hash(DataSynchronizer.sanitizeDocument(this.testDocument))

        var changeEventListener = newChangeEventListener()
            private set
        var conflictHandler = newConflictHandler()
            private set
        var errorListener = newErrorListener()
            private set

        override val localCollection: MongoCollection<BsonDocument> by lazy {
            localClient
                    .getDatabase(namespace.databaseName)
                    .getCollection(namespace.collectionName, BsonDocument::class.java)
        }

        val undoCollection: MongoCollection<BsonDocument> by lazy {
            localClient
                    .getDatabase(String.format("sync_undo_%s", namespace.databaseName))
                    .getCollection(namespace.collectionName, BsonDocument::class.java)
        }

        val networkMonitor: TestNetworkMonitor = spy(TestNetworkMonitor())
        val authMonitor: TestAuthMonitor = spy(TestAuthMonitor())

        override val localClient: MongoClient by lazy {
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
                UnitTestEmbeddedMongoClientFactory.getInstance()
            )
        }

        val service: CoreStitchServiceClient by lazy {
            val service = Mockito.mock(CoreStitchServiceClientImpl::class.java)
            `when`(service.codecRegistry).thenReturn(CodecRegistries.fromCodecs(BsonDocumentCodec()))
            service
        }
        private val remoteClient = Mockito.mock(CoreRemoteMongoClientImpl::class.java)

        override val dispatcher: Dispatcher = ThreadDispatcher()

        override val dataSynchronizer: DataSynchronizer by lazy {
            // Insert any documents that we want to be recovered by the recovery sequence.
            if (!undoDocuments.isEmpty()) {
                for (doc in undoDocuments) {
                    undoCollection.insertOne(doc)
                }
            }

            var ds = DataSynchronizer(
                instanceKey,
                service,
                localClient,
                remoteClient,
                networkMonitor,
                authMonitor,
                dispatcher
            )
            ds.waitUntilInitialized()
            ds = Mockito.spy(ds)

            ds.waitUntilInitialized()
            ds
        }

        private var eventSemaphore: Semaphore? = null
        private var errorSemaphore: Semaphore? = null

        init {
            if (shouldPreconfigure) {
                // this needs to be done since the spied dataSynchronizer does not
                // re-add itself to the network monitor
                networkMonitor.addNetworkStateListener(dataSynchronizer)

                dataSynchronizer.disableSyncThread()
                dataSynchronizer.disableListeners()

                dataSynchronizer.stop()
            }

            Mockito.`when`(service.streamFunction(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyList<Any>(),
                ArgumentMatchers.eq(ResultDecoders.compactChangeEventDecoder(BsonDocumentCodec())))
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

        /**
         * Reconfigure the internal dataSynchronizer with
         * the current conflictHandler, changeEventListener,
         * errorListener, and syncFrequency.
         */
        override fun reconfigure() {
            val syncConfig = SyncConfiguration.Builder()
                .withChangeEventListener(changeEventListener)
                .withConflictHandler(conflictHandler)
                .withExceptionListener(errorListener)
                .withCodec(bsonDocumentCodec)
                .withSyncFrequency(SyncFrequency.reactive())
                .build()
            this.dataSynchronizer.configure(namespace, syncConfig)
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

        override fun waitForDataSynchronizerStreams() {
            waitLock.lock()
            var counter = 0
            while (!dataSynchronizer.areAllStreamsOpen()) {
                counter += 1
                Thread.sleep(500)

                if (counter > 10000 /*ms*/ / 500 /*ms/tick*/) {
                    throw TimeoutException("DataSynchronizer streams never opened after 10 seconds")
                }
            }
            waitLock.unlock()
            assertTrue(dataSynchronizer.areAllStreamsOpen())
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

            dataSynchronizer.insertOne(namespace, testDocument)
        }

        override fun updateTestDocument(): UpdateResult {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            val updateResult = dataSynchronizer.updateOne(
                namespace,
                BsonDocument("_id", testDocumentId),
                updateDocument
            )

            // verify that we didn't accidentally leak any documents that will be "recovered" later
            verifyUndoCollectionEmpty()

            return updateResult
        }

        override fun deleteTestDocument(): DeleteResult {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            val deleteResult =
                    dataSynchronizer.deleteOne(namespace, BsonDocument("_id", testDocumentId))

            // verify that we didn't accidentally leak any documents that will be "recovered" later
            verifyUndoCollectionEmpty()

            return deleteResult
        }

        override fun doSyncPass() {
            configureNewChangeEventListener()
            configureNewErrorListener()
            configureNewConflictHandler()

            dataSynchronizer.doSyncPass()

            // verify that the undo collection is empty after all conflict resolutions so that we
            // know we're not leaking any documents will be unnecessarily "recovered" later
            verifyUndoCollectionEmpty()
        }

        override fun queueConsumableRemoteInsertEvent() {
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(testDocument to ChangeEvents.compactChangeEventForLocalInsert(testDocument, false)),
                mapOf())
        }

        fun getVersionForTestDocument(): BsonDocument? {
            return dataSynchronizer.getSynchronizedDocuments(namespace)
                .find { it.documentId == testDocumentId }?.lastKnownRemoteVersion
        }

        override fun setPendingWritesForDocId(documentId: BsonValue, event: ChangeEvent<BsonDocument>) {
            // use reflection to get access to the private sync config so we can set pending writes
            val scField = DataSynchronizer::class.java.getDeclaredField("syncConfig")
            scField.isAccessible = true
            val syncConfig = scField.get(dataSynchronizer) as InstanceSynchronizationConfig

            val ltField = DataSynchronizer::class.java.getDeclaredField("logicalT")
            ltField.isAccessible = true
            val logicalT = ltField.get(dataSynchronizer) as Long

            val docConfig = syncConfig.getSynchronizedDocument(namespace, documentId)

            docConfig.setSomePendingWritesAndSave(logicalT, event)
        }

        override fun queueConsumableRemoteUpdateEvent(
            id: BsonValue,
            previousVersion: BsonDocument?,
            expectedNewHash: Long,
            remoteUpdate: UpdateDescription,
            versionState: TestVersionState
        ) {
            val newVersion: BsonDocument?

            if (previousVersion != null) {
                val documentVersionInfo = DocumentVersionInfo.fromVersionDoc(previousVersion.asDocument())
                newVersion = when (versionState) {
                    TestVersionState.NONE ->
                        null
                    TestVersionState.PREVIOUS -> {
                        if (documentVersionInfo.version.versionCounter <= 0) {
                            throw IllegalStateException("Version cannot be less than zero")
                        }
                        documentVersionInfo
                            .versionDoc?.append(
                            "v",
                            BsonInt64(documentVersionInfo.version.versionCounter - 1)
                        )
                    }
                    TestVersionState.SAME ->
                        documentVersionInfo.versionDoc
                    TestVersionState.NEXT ->
                        documentVersionInfo.nextVersion
                    TestVersionState.NEW ->
                        DocumentVersionInfo.getFreshVersionDocument()
                }
            } else {
                if (versionState == TestVersionState.NEW) {
                    newVersion = DocumentVersionInfo.getFreshVersionDocument()
                } else {
                    newVersion = null
                }
            }

            if (newVersion == null) {
                remoteUpdate.removedFields.add("__stitch_sync_version")
            } else {
                remoteUpdate.updatedFields.append("__stitch_sync_version", newVersion)
            }

            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(id to ChangeEvents.compactChangeEventForLocalUpdate(
                    id,
                    remoteUpdate,
                    newVersion,
                    expectedNewHash,
                    false
                )),
                mapOf())
        }

        override fun queueConsumableRemoteUpdateEvent(
            fromExpectedDoc: BsonDocument,
            versionState: TestVersionState
        ) {
            queueConsumableRemoteUpdateEvent(
                id = fromExpectedDoc.get("_id")!!,
                previousVersion = testDocumentVersion,
                expectedNewHash = HashUtils.hash(DataSynchronizer.sanitizeDocument(fromExpectedDoc)),
                remoteUpdate = UpdateDescription.diff(testDocument, fromExpectedDoc),
                versionState = versionState
            )
        }

        override fun queueConsumableRemoteReplaceEvent(
            fromExpectedDoc: BsonDocument,
            versionState: TestVersionState
        ) {
            val previousVersion =
                fromExpectedDoc.getDocument("__stitch_sync_version", null)
            val newVersion: BsonDocument?
            val newDoc = fromExpectedDoc.clone()

            if (previousVersion != null) {
                val documentVersionInfo = DocumentVersionInfo.fromVersionDoc(previousVersion.asDocument())
                newVersion = when (versionState) {
                    TestVersionState.NONE ->
                        null
                    TestVersionState.PREVIOUS -> {
                        if (documentVersionInfo.version.versionCounter <= 0) {
                            throw IllegalStateException("Version cannot be less than zero")
                        }
                        documentVersionInfo
                            .versionDoc?.append(
                            "v",
                            BsonInt64(documentVersionInfo.version.versionCounter - 1)
                        )
                    }
                    TestVersionState.SAME ->
                        documentVersionInfo.versionDoc
                    TestVersionState.NEXT ->
                        documentVersionInfo.nextVersion
                    TestVersionState.NEW ->
                        DocumentVersionInfo.getFreshVersionDocument()
                }
            } else {
                if (versionState == TestVersionState.NEW) {
                    newVersion = DocumentVersionInfo.getFreshVersionDocument()
                } else {
                    newVersion = null
                }
            }

            if (newVersion == null) {
                newDoc.remove("__stitch_sync_version")
            } else {
                newDoc.append("__stitch_sync_version", newVersion)
            }

            val id = fromExpectedDoc["_id"]

            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(id to ChangeEvents.compactChangeEventForLocalReplace(
                    id,
                    newDoc,
                    false
                )),
                mapOf())
        }

        override fun queueConsumableRemoteDeleteEvent() {
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(testDocument to ChangeEvents.compactChangeEventForLocalDelete(
                    testDocumentId, false)),
                mapOf())
        }

        override fun queueConsumableRemoteUnknownEvent() {
            `when`(dataSynchronizer.getEventsForNamespace(any())).thenReturn(
                mapOf(testDocument to CompactChangeEvent(
                        OperationType.UNKNOWN,
                        testDocument,
                        BsonDocument("_id", testDocumentId),
                        null,
                        null,
                        null,
                        true)), mapOf())
        }

        override fun findTestDocumentFromLocalCollection(): BsonDocument? {
            return dataSynchronizer.find(
                namespace,
                BsonDocument("_id", testDocumentId),
                10,
                null,
                null,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(bsonDocumentCodec)).firstOrNull()
        }

        override fun findTestNamespaceConfig(): NamespaceSynchronizationConfig? {
            return dataSynchronizer.syncConfig.getNamespaceConfig(namespace)
        }

        override fun findTestDocumentConfig(): CoreDocumentSynchronizationConfig? {
            return dataSynchronizer.syncConfig.getNamespaceConfig(namespace).docsColl.find(
                    BsonDocument("document_id", testDocumentId)).firstOrNull()
        }

        override fun setNamespaceStale(isStale: Boolean) {
            return dataSynchronizer.syncConfig.getNamespaceConfig(namespace).setStale(isStale)
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
                assertEquals(times, expectedChangeEvents.size)
                val capturedEvents = mutableMapOf<BsonValue, MutableList<ChangeEvent<BsonDocument>>>()
                changeEventArgumentCaptor.allValues.forEach { actualChangeEvent ->
                    val capturedId = actualChangeEvent.documentKey["_id"] as BsonValue
                    if (!capturedEvents.containsKey(capturedId)) {
                        capturedEvents[capturedId] = mutableListOf()
                    }
                    capturedEvents[capturedId]!!.add(actualChangeEvent as ChangeEvent<BsonDocument>)
                }
                expectedChangeEvents.forEach {
                    val capturedEventsForId = capturedEvents[it.documentKey["_id"] as BsonValue]
                    if (capturedEventsForId == null || capturedEventsForId.size == 0) {
                        fail("expected to capture event for " + it.documentKey["_id"].toString())
                    }
                    compareEvents(it, capturedEventsForId!!.removeAt(0))
                }
            }
        }

        override fun verifyErrorListenerCalledForActiveDoc(times: Int, error: Exception?) {
            Mockito.verify(errorListener, times(times)).onError(eq(testDocumentId), eq(error))
        }

        override fun verifyConflictHandlerCalledForActiveDoc(
            times: Int,
            expectedLocalConflictEvent: ChangeEvent<BsonDocument>?,
            expectedRemoteConflictEvent: CompactChangeEvent<BsonDocument>?
        ) {
            val localChangeEventArgumentCaptor = ArgumentCaptor.forClass(ChangeEvent::class.java)
            val remoteChangeEventArgumentCaptor = ArgumentCaptor.forClass(CompactChangeEvent::class.java)

            Mockito.verify(conflictHandler, times(times)).resolveConflict(
                eq(testDocumentId),
                localChangeEventArgumentCaptor.capture() as ChangeEvent<BsonDocument>?,
                remoteChangeEventArgumentCaptor.capture() as CompactChangeEvent<BsonDocument>?)

            if (expectedLocalConflictEvent != null) {
                compareEvents(expectedLocalConflictEvent, localChangeEventArgumentCaptor.value as ChangeEvent<BsonDocument>)
            }

            if (expectedRemoteConflictEvent != null) {
                compareEvents(expectedRemoteConflictEvent, remoteChangeEventArgumentCaptor.value as CompactChangeEvent<BsonDocument>)
            }
        }

        override fun verifyWatchFunctionCalled(times: Int, expectedArgs: Document) {
            Mockito.verify(service, times(times)).streamFunction(
                eq("watch"),
                eq(Collections.singletonList(expectedArgs.append("useCompactEvents", true))),
                eq(ResultDecoders.compactChangeEventDecoder(BsonDocumentCodec())))
        }

        override fun verifyStartCalled(times: Int) {
            Mockito.verify(dataSynchronizer, times(times)).start()
        }

        override fun verifyStopCalled(times: Int) {
            Mockito.verify(dataSynchronizer, times(times)).stop()
        }

        override fun verifyUndoCollectionEmpty() {
            Assert.assertEquals(0, undoCollection.countDocuments())
        }

        override fun mockInsertException(exception: Exception) {
            `when`(collectionMock.insertOne(any())).thenThrow(exception)
        }

        override fun mockUpdateResult(remoteUpdateResult: RemoteUpdateResult) {
            `when`(collectionMock.updateOne(any(), any())).thenReturn(remoteUpdateResult)
            `when`(collectionMock.updateMany(any(), any())).thenReturn(remoteUpdateResult)
        }

        override fun mockUpdateException(exception: Exception) {
            `when`(collectionMock.updateOne(any(), any())).thenAnswer {
                throw exception
            }
            `when`(collectionMock.updateMany(any(), any())).thenAnswer {
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

        override fun mockFindOneResult(doc: BsonDocument?) {
            `when`(collectionMock.findOne(any())).thenReturn(doc)
        }

        override fun mockFindResult(vararg docs: BsonDocument?) {
            val remoteFindIterable = Mockito.mock(CoreRemoteFindIterable::class.java) as CoreRemoteFindIterable<BsonDocument>
            Mockito.`when`(collectionMock.find(ArgumentMatchers.any())).thenReturn(remoteFindIterable)

            val iterableSet = HashSet<BsonDocument>()
            for (doc in docs) {
                if (doc != null) {
                    iterableSet.add(doc)
                }
            }

            Mockito.`when`(remoteFindIterable.into<HashSet<BsonDocument>>(ArgumentMatchers.any())).thenReturn(iterableSet)
            Mockito.`when`(remoteFindIterable.first()).thenReturn(docs[0])
            this.mockFindOneResult(docs[0])
        }

        override fun clearFindOneMock() {
            `when`(collectionMock.findOne(any())).thenReturn(null)
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
        latestCtx?.localClient?.listDatabaseNames()?.forEach {
            if (it != "admin") {
                latestCtx?.localClient?.getDatabase(it)?.drop()
            }
        }

        latestCtx?.dataSynchronizer?.close()

        unclosedDataSynchronizers.forEach {
            it?.close()
        }
        unclosedDataSynchronizers.clear()
    }

    private val unclosedDataSynchronizers: HashSet<DataSynchronizer?> = HashSet()

    internal fun freshTestContext(shouldPreconfigure: Boolean = true): DataSynchronizerTestContext {
        unclosedDataSynchronizers.forEach {
            it?.close()
        }
        unclosedDataSynchronizers.clear()

        latestCtx?.dataSynchronizer?.close()
        latestCtx = DataSynchronizerTestContextImpl(shouldPreconfigure)
        return latestCtx!!
    }

    internal fun testContextFromExistingContext(
        existingCtx: DataSynchronizerTestContext,
        undoDocuments: List<BsonDocument> = ArrayList()
    ): DataSynchronizerTestContext {
        // don't close the underlying synchronizer yet since that would release the local client
        // needed for the next test context. We will close this data synchronizer when we make a
        // fresh test context
        unclosedDataSynchronizers.add(latestCtx?.dataSynchronizer)

        latestCtx = DataSynchronizerTestContextImpl(
                true,
                undoDocuments,
                existingCtx.namespace,
                existingCtx.clientKey,
                existingCtx.instanceKey
        )

        // perform a no-op write so that we wait for the recovery pass to complete. This works
        // since the recovery routine write-locks all namespaces until recovery is done.
        latestCtx!!.dataSynchronizer.updateOne(
                latestCtx!!.namespace,
                BsonDocument("_id", BsonString("nonexistent")),
                BsonDocument("\$set", BsonDocument("a", BsonInt32(1)))
        )

        return latestCtx!!
    }

    internal fun createNamespaceChangeStreamListenerWithContext(context: DataSynchronizerTestContext): Pair<NamespaceChangeStreamListener, NamespaceSynchronizationConfig> {
        val nsConfigMock = Mockito.mock(NamespaceSynchronizationConfig::class.java)
        val namespaceChangeStreamListener = NamespaceChangeStreamListener(
            context.namespace,
            nsConfigMock,
            (context as DataSynchronizerTestContextImpl).service,
            context.networkMonitor,
            context.authMonitor,
            ReentrantReadWriteLock())

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
