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
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.junit.After
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

    @Test
    fun testCoreDocumentSynchronizationConfigIsFrozenCheck() {
        // create a datasynchronizer with an injected remote client
        val instanceKey = "${Random().nextInt()}"

        val routes = StitchServiceRoutes("foo")
        val requestClient = Mockito.mock(StitchAuthRequestClient::class.java)
        val remoteClient = CoreRemoteMongoClientImpl(
            spy(CoreStitchServiceClientImpl(
                requestClient,
                routes,
                BsonUtils.DEFAULT_CODEC_REGISTRY)
            ),
            instanceKey,
            localClient,
            networkMonitor,
            authMonitor
        )
        remoteClient.dataSynchronizer.stop()
        val rmSpy = spy(remoteClient)

        val id1 = BsonObjectId()

        var ds = DataSynchronizer(
            instanceKey,
            mock(CoreStitchServiceClient::class.java),
            localClient,
            rmSpy,
            networkMonitor,
            authMonitor
        )

        ds.stop()
        ds = spy(ds)

        // insert a new doc. the details of the doc do not matter
        val doc1 = BsonDocument("_id", id1)
        ds.insertOneAndSync(namespace, doc1)

        // set the doc to frozen and reload the configs
        ds.getSynchronizedDocuments(namespace).forEach {
            it.isFrozen = true
        }
        ds.reloadConfig()

        // spy on the remote client
        val dbSpy = mock(CoreRemoteMongoDatabaseImpl::class.java)
        `when`(rmSpy.getDatabase(namespace.databaseName)).thenReturn(dbSpy)

        val collSpy = mock(CoreRemoteMongoCollectionImpl::class.java)
            as CoreRemoteMongoCollectionImpl<BsonDocument>
        `when`(dbSpy.getCollection(namespace.collectionName, BsonDocument::class.java))
            .thenReturn(collSpy)

        // ensure that no remote inserts are made during this sync pass
        ds.doSyncPass()

        verify(collSpy, times(0)).insertOne(any())

        // unfreeze the configs and reload
        ds.getSynchronizedDocuments(namespace).forEach {
            it.isFrozen = false
        }
        ds.reloadConfig()

        // this time ensure that the remote insert has been called
        ds.doSyncPass()

        verify(collSpy, times(1)).insertOne(any())
    }
}
