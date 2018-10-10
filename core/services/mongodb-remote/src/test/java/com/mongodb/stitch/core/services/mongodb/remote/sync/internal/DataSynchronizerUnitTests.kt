package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.common.BsonUtils
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl
import com.mongodb.stitch.core.services.internal.StitchServiceRoutes
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollectionImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoDatabaseImpl
import com.mongodb.stitch.core.services.mongodb.remote.internal.TestUtils
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonObjectId

import org.bson.codecs.BsonDocumentCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.Random

class DataSynchronizerUnitTests {
    @After
    fun teardown() {
        CoreRemoteClientFactory.close()
        ServerEmbeddedMongoClientFactory.getInstance().close()
    }

    private val namespace = MongoNamespace("foo", "bar")
    private val networkMonitor = object : NetworkMonitor {
        override fun removeNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }

        override fun isConnected(): Boolean {
            return true
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
    private val service = spy(
        CoreStitchServiceClientImpl(
            Mockito.mock(StitchAuthRequestClient::class.java),
            StitchServiceRoutes("foo"),
            BsonUtils.DEFAULT_CODEC_REGISTRY)
    )

    private val remoteClient = spy(CoreRemoteMongoClientImpl(
        service,
        instanceKey,
        localClient,
        networkMonitor,
        authMonitor
    ))

    @Before
    fun setup() {
        remoteClient.dataSynchronizer.stop()
    }

    @Test
    fun testCoreDocumentSynchronizationConfigIsFrozenCheck() {
        // create a datasynchronizer with an injected remote client
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
        assertFalse(dataSynchronizer.isConfigured)
        assertFalse(dataSynchronizer.isRunning)

        // mock the necessary config args
        val conflictHandler = mock(ConflictHandler::class.java) as ConflictHandler<BsonDocument>
        val changeEventListener = mock(ChangeEventListener::class.java) as ChangeEventListener<BsonDocument>
        val errorListener = mock(ErrorListener::class.java)
        val bsonCodec = BsonDocumentCodec()

        // fetch a new namespace, creating a new config
        val nsConfig: NamespaceSynchronizationConfig = dataSynchronizer.getNamespaceConfig(namespace)
        // add a synchronized document to establish the namespace
        nsConfig.addSynchronizedDocument(namespace, BsonObjectId())
        assertNull(nsConfig.namespaceListenerConfig)

        // configure the dataSynchronizer,
        // which should pass down the configuration to the namespace config
        dataSynchronizer.configure(namespace, conflictHandler, changeEventListener, errorListener, bsonCodec)

        // make dummy calls on the conflict handler and event listener, asserting these are the
        // same as our original handlers and listeners
        nsConfig.conflictHandler.resolveConflict(null, null, null)
        nsConfig.namespaceListenerConfig.eventListener.onEvent(null, null)

        // verify the appropriate methods have been called on our config
        verify(conflictHandler, times(1)).resolveConflict(any(), any(), any())
        verify(changeEventListener, times(1)).onEvent(any(), any())
        assertEquals(nsConfig.namespaceListenerConfig.documentCodec, bsonCodec)

        // verify that the data synchronizer has triggered the namespace
        // and has started itself
        verify(dataSynchronizer, times(1)).triggerListeningToNamespace(any())
        verify(dataSynchronizer, times(1)).start()

        // assert that the dataSynchronizer is concretely running and configured
        assertTrue(dataSynchronizer.isRunning)
        assertTrue(dataSynchronizer.isConfigured)

        // configuring again, verifying that the data synchronizer does NOT
        // trigger the namespace or start up a second time
        dataSynchronizer.configure(namespace, conflictHandler, changeEventListener, errorListener, bsonCodec)

        verify(dataSynchronizer, times(1)).triggerListeningToNamespace(any())
        verify(dataSynchronizer, times(1)).start()

        // assert that nothing has changed about our state
        assertTrue(dataSynchronizer.isRunning)
        assertTrue(dataSynchronizer.isConfigured)
    }
}
