package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.common.BsonUtils
import com.mongodb.stitch.core.internal.common.ThreadDispatcher
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncUnitTestHarness.Companion.compareEvents
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.configuration.CodecRegistries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDocumentSynchronizationConfigUnitTests {
    @After
    fun teardown() {
        ServerEmbeddedMongoClientFactory.getInstance().close()
    }

    private val namespace = MongoNamespace("foo", "bar")
    private val id = BsonObjectId()
    private val networkMonitor = object : NetworkMonitor {
        override fun removeNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }

        override fun isConnected(): Boolean {
            return true
        }

        override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }
    }

    private val authMonitor = object: AuthMonitor {
        override fun isLoggedIn(): Boolean {
            return true
        }

        override fun getActiveUserId(): String? {
            return "bound"
        }
    }
    private val localClient = SyncMongoClientFactory.getClient(
            StitchAppClientInfo(
                    "<client-app-id>",
                    System.getProperty("java.io.tmpdir"),
                    "<local-app-name>",
                    "<local-app-version>",
                    BsonUtils.DEFAULT_CODEC_REGISTRY,
                    networkMonitor,
                    authMonitor,
                    ThreadDispatcher()
            ),
            "mongodblocal",
            ServerEmbeddedMongoClientFactory.getInstance()
    )
    private val coll by lazy {
        localClient.getDatabase(namespace.databaseName)
                .withCodecRegistry(CodecRegistries.fromRegistries(
                        CodecRegistries.fromCodecs(
                                InstanceSynchronizationConfig.configCodec,
                                NamespaceSynchronizationConfig.configCodec,
                                CoreDocumentSynchronizationConfig.configCodec),
                        BsonUtils.DEFAULT_CODEC_REGISTRY))
                .getCollection(
                    namespace.collectionName,
                    CoreDocumentSynchronizationConfig::class.java)
    }

    @Test
    fun testGetDocFilter() {
        val docFilter = CoreDocumentSynchronizationConfig.getDocFilter(namespace, id)

        assertEquals(
                docFilter[CoreDocumentSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD],
                BsonString(namespace.toString()))
        assertEquals(
                docFilter[CoreDocumentSynchronizationConfig.ConfigCodec.Fields.DOCUMENT_ID_FIELD],
                id)
    }

    @Test
    fun testToBsonDocumentRoundTrip() {
        var config = CoreDocumentSynchronizationConfig(coll, namespace, id)
        val expectedTestVersion = BsonDocument("dummy", BsonString("version"))
        val expectedEvent = ChangeEvent.changeEventForLocalDelete(namespace, id, false)
        config.setSomePendingWrites(
            1,
            expectedTestVersion,
            expectedEvent)
        config.isPaused = true
        config.isStale = true

        val doc = config.toBsonDocument()

        assertEquals(id, doc[CoreDocumentSynchronizationConfig.ConfigCodec.Fields.DOCUMENT_ID_FIELD])

        assertTrue(doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE).value)
        assertTrue(doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_PAUSED).value)
        assertEquals(expectedTestVersion,
            doc[CoreDocumentSynchronizationConfig.ConfigCodec.Fields.LAST_KNOWN_REMOTE_VERSION_FIELD])
        assertEquals(
            BsonString("${namespace.databaseName}.${namespace.collectionName}"),
                doc[CoreDocumentSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD])
        assertNotNull(doc[CoreDocumentSynchronizationConfig.ConfigCodec.Fields.LAST_UNCOMMITTED_CHANGE_EVENT])

        config = CoreDocumentSynchronizationConfig.fromBsonDocument(doc)

        assertTrue(config.isPaused)
        assertEquals(namespace, config.namespace)
        assertEquals(expectedTestVersion, config.lastKnownRemoteVersion)
        compareEvents(expectedEvent, config.lastUncommittedChangeEvent)
        assertEquals(id, config.documentId)
    }
}
