package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal.common.AuthMonitor
import com.mongodb.stitch.core.internal.common.BsonUtils
import com.mongodb.stitch.core.internal.common.ThreadDispatcher
import com.mongodb.stitch.core.internal.net.NetworkMonitor
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncUnitTestHarness.Companion.compareEvents
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
        UnitTestEmbeddedMongoClientFactory.getInstance().close()
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

    private val authMonitor = object : AuthMonitor {
        override fun isLoggedIn(): Boolean {
            return true
        }

        override fun getActiveUserId(): String? {
            return "bound"
        }

        override fun tryIsLoggedIn(): Boolean {
            return true
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
            UnitTestEmbeddedMongoClientFactory.getInstance()
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
        val expectedEvent = ChangeEvents.changeEventForLocalDelete(namespace, id, false)
        val expectedHash = 12345L
        config.setSomePendingWrites(
            1,
            expectedTestVersion,
            expectedHash,
            expectedEvent)
        coll.replaceOne(CoreDocumentSynchronizationConfig.getDocFilter(namespace, id), config)

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
        assertEquals(expectedHash, config.lastKnownHash)
        assertEquals(id, config.documentId)
    }

    @Test
    fun testPendingWritesCoalescenceUpdateUpdate() {
        val config = CoreDocumentSynchronizationConfig(coll, namespace, id)
        val ud = UpdateDescription(BsonDocument("foo", BsonString("bar")), hashSetOf("baz"))
        val updateEvent1 = ChangeEvents.changeEventForLocalUpdate(
            namespace,
            id,
            UpdateDescription(BsonDocument("foo", BsonString("bar")), hashSetOf("baz")),
            null,
            true)
        config.setSomePendingWritesAndSave(0, updateEvent1)

        assertEquals(ud, config.lastUncommittedChangeEvent.updateDescription)

        val ud2 = UpdateDescription(BsonDocument("qux", BsonString("quux")), hashSetOf("corge"))
        val updateEvent2 = ChangeEvents.changeEventForLocalUpdate(
            namespace,
            id,
            ud2,
            null,
            true)
        config.setSomePendingWritesAndSave(0, updateEvent2)
        assertEquals(ud.merge(ud2), config.lastUncommittedChangeEvent.updateDescription)
    }

    @Test
    fun testPendingWritesCoalescenceUpdateReplace() {
        val config = CoreDocumentSynchronizationConfig(coll, namespace, id)
        val ud = UpdateDescription(BsonDocument("foo", BsonString("bar")), hashSetOf("baz"))
        val updateEvent1 = ChangeEvents.changeEventForLocalUpdate(
            namespace,
            id,
            UpdateDescription(BsonDocument("foo", BsonString("bar")), hashSetOf("baz")),
            null,
            true)
        config.setSomePendingWritesAndSave(0, updateEvent1)

        assertEquals(ud, config.lastUncommittedChangeEvent.updateDescription)

        val fullDoc = BsonDocument("grault", BsonString("garply"))
        val replaceEvent = ChangeEvents.changeEventForLocalReplace(
            namespace,
            id,
            fullDoc,
            true)
        config.setSomePendingWritesAndSave(0, replaceEvent)
        assertEquals(OperationType.REPLACE, config.lastUncommittedChangeEvent.operationType)
        assertEquals(fullDoc, config.lastUncommittedChangeEvent.fullDocument)
    }

    @Test
    fun testPendingWritesCoalescenceReplaceUpdate() {
        val config = CoreDocumentSynchronizationConfig(coll, namespace, id)
        val replaceEvent = ChangeEvents.changeEventForLocalReplace(
            namespace,
            id,
            BsonDocument("grault", BsonString("garply")),
            true)
        config.setSomePendingWritesAndSave(0, replaceEvent)

        val ud2 = UpdateDescription(BsonDocument("qux", BsonString("quux")), hashSetOf("corge"))
        val fullDoc = BsonDocument()
            .append("grault", BsonString("garply"))
            .append("qux", BsonString("quux"))
        val updateEvent2 = ChangeEvents.changeEventForLocalUpdate(
            namespace,
            id,
            ud2,
            fullDoc,
            true)
        config.setSomePendingWritesAndSave(0, updateEvent2)
        assertEquals(OperationType.REPLACE, config.lastUncommittedChangeEvent.operationType)
        assertEquals(fullDoc, config.lastUncommittedChangeEvent.fullDocument)
    }
}
