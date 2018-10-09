package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.common.BsonUtils
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.configuration.CodecRegistries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val authMonitor = AuthMonitor { true }
    private val localClient = SyncMongoClientFactory.getClient(
            StitchAppClientInfo(
                    "<client-app-id>",
                    System.getProperty("java.io.tmpdir"),
                    "<local-app-name>",
                    "<local-app-version>",
                    BsonUtils.DEFAULT_CODEC_REGISTRY,
                    networkMonitor,
                    authMonitor
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
    fun testStaleAndFrozen() {
        var config = CoreDocumentSynchronizationConfig(coll, namespace, id)
        coll.insertOne(config)

        assertFalse(config.isStale)

        config.isStale = true
        config.isFrozen = true

        var doc = config.toBsonDocument()

        assertTrue(doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE).value)
        assertTrue(doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_FROZEN).value)

        config = CoreDocumentSynchronizationConfig(
                coll, CoreDocumentSynchronizationConfig.fromBsonDocument(doc))

        assertTrue(config.isStale)

        config.isStale = false
        config.setSomePendingWrites(
            1,
            ChangeEvent.changeEventForLocalInsert(
                coll.namespace, BsonDocument("_id", BsonObjectId()), true))

        doc = config.toBsonDocument()
        // should be stale from set some pending writes
        assertTrue(
            doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE).value)
        assertFalse(
            doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_FROZEN).value)
    }
}
