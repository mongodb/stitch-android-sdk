package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.common.BsonUtils
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.configuration.CodecRegistries
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreDocumentSynchronizationConfigUnitTests {
    private val namespace = MongoNamespace("foo", "bar")
    private val id = BsonObjectId()
    private val networkMonitor = object : NetworkMonitor {
        override fun isConnected(): Boolean {
            return true
        }

        override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {
        }
    }
    private val authMonitor = AuthMonitor { true }
    private val localClient = LocalClientFactory.getClient(
            StitchAppClientInfo(
                    "<client-app-id>",
                    "__store__",
                    "<local-app-name>",
                    "<local-app-version>", null,
                    networkMonitor,
                    authMonitor
            ),
            "mongodblocal"
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
    fun testStale() {
        var config = CoreDocumentSynchronizationConfig(coll, namespace, id)

        assert(!config.isStale)

        config.isStale = true

        var doc = config.toBsonDocument()
        assert(doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE).value)

        config = CoreDocumentSynchronizationConfig(
                coll, CoreDocumentSynchronizationConfig.fromBsonDocument(doc))

        assert(config.isStale)

        config.isStale = false
        doc = config.toBsonDocument()
        assert(!doc.getBoolean(CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE).value)

        config = CoreDocumentSynchronizationConfig.fromBsonDocument(doc)
        assert(!config.isStale)
    }
}
