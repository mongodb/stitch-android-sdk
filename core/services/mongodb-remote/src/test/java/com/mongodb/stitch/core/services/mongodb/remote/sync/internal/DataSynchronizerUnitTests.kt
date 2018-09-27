package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal .common.AuthMonitor
import com.mongodb.stitch.core.internal .net.NetworkMonitor
import com.mongodb.stitch.core.services.internal .CoreStitchServiceClient
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.internal.*
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import junit.framework.Assert.*
import org.bson.*
import org.bson.codecs.BsonDocumentCodec

import org.bson.codecs.configuration.CodecRegistries
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.*

class DataSynchronizerUnitTests {
    private val testDb = "testDb"
    private val testColl = "testColl"

    private val namespace = MongoNamespace(testDb, testColl)

    private var dataSynchronizer: DataSynchronizer? = null
    private val dataSpy: DataSynchronizer by lazy { spy(dataSynchronizer!!) }
    private val remoteClient: CoreRemoteMongoClient by lazy {
        Mockito.spy(
                CoreRemoteMongoClientImpl(
                        Mockito.mock(CoreStitchServiceClient::class.java),
                        "instanceKey",
                        localClient,
                        networkMonitor,
                        authMonitor
                )
        )
    }
    private val coreStitchServiceClient = Mockito.mock(CoreStitchServiceClient::class.java)
    private var insertedId: BsonValue? = null
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

    @Before
    fun setup() {
        dataSynchronizer = DataSynchronizer(
                "testInstanceKey",
                coreStitchServiceClient,
                localClient,
                remoteClient,
                networkMonitor,
                authMonitor
        )
    }

    @After
    fun teardown() {
        dataSpy.deleteOneById(namespace, insertedId)
    }

    @Test
    fun testInsertOneAndSync() {
        val doc = BsonDocument("foo", BsonString("bar"))

        val size = dataSpy.getSynchronizedDocuments(namespace).size

        insertOneAndSync(dataSpy, doc)

        val databaseSpy = Mockito.mock(CoreRemoteMongoDatabaseImpl::class.java)
        val collectionSpy = Mockito.mock(CoreRemoteMongoCollectionImpl::class.java)
                as CoreRemoteMongoCollectionImpl<BsonDocument>

        Mockito.`when`(remoteClient.getDatabase(namespace.databaseName)).thenReturn(databaseSpy)
        Mockito.`when`(databaseSpy.getCollection(namespace.collectionName, BsonDocument::class.java)).thenReturn(collectionSpy)

        assertEquals(dataSpy.getSynchronizedDocuments(namespace).last().documentId, insertedId)
        assertEquals(dataSpy.getSynchronizedDocuments(namespace).size, size + 1)

        assertTrue(dataSpy.doSyncPass())

        val docArg = ArgumentCaptor.forClass(BsonDocument::class.java)

        verify(collectionSpy).insertOne(docArg.capture())

        assertEquals(doc["foo"], docArg.value["foo"])
        assertEquals(insertedId, docArg.value["_id"])
    }

    @Test
    fun testFindOneById() {
        val doc = BsonDocument("foo", BsonString("bar"))

        insertOneAndSync(dataSpy, doc)

        val doc2 = dataSpy.findOneById(
                namespace,
                insertedId,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec())
        )

        assertEquals(insertedId, doc2["_id"])
        assertEquals(doc2["foo"], doc["foo"])
    }

    @Test
    fun testDeleteOneById() {
        val size = dataSpy.getSynchronizedDocuments(namespace).size

        val doc = BsonDocument("foo", BsonString("bar"))
        val databaseSpy = Mockito.mock(CoreRemoteMongoDatabaseImpl::class.java)
        val collectionSpy = Mockito.mock(CoreRemoteMongoCollectionImpl::class.java)
                as CoreRemoteMongoCollectionImpl<BsonDocument>

        Mockito.`when`(remoteClient.getDatabase(namespace.databaseName)).thenReturn(databaseSpy)
        Mockito.`when`(databaseSpy.getCollection(namespace.collectionName, BsonDocument::class.java)).thenReturn(collectionSpy)

        Mockito.`when`(collectionSpy.deleteOne(ArgumentMatchers.any())).thenReturn(RemoteDeleteResult(1))

        insertOneAndSync(dataSpy, doc)

        assertEquals(dataSpy.getSynchronizedDocuments(namespace).size, size + 1)

        assertTrue(dataSpy.doSyncPass())

        dataSpy.deleteOneById(namespace, insertedId)

        assertTrue(dataSpy.doSyncPass())

        val docArg = ArgumentCaptor.forClass(BsonDocument::class.java)
        verify(collectionSpy).deleteOne(docArg.capture())

        assertEquals(dataSpy.getSynchronizedDocuments(namespace).size, size)

        val namespaceArg = ArgumentCaptor.forClass(MongoNamespace::class.java)
        val idArg = ArgumentCaptor.forClass(BsonValue::class.java)

        assertTrue(dataSpy.doSyncPass())

        verify(dataSpy).desyncDocumentFromRemote(namespaceArg.capture(), idArg.capture())

        assertEquals(namespaceArg.value, namespace)
        assertEquals(idArg.value, insertedId)
    }

    private fun insertOneAndSync(dataSpy: DataSynchronizer,
                                 doc: BsonDocument,
                                 conflictHandler: ConflictHandler<BsonDocument> = ConflictHandler { _, _, _ ->
                                     doc
                                 },
                                 changeEventListener: ChangeEventListener<BsonDocument> = ChangeEventListener { _, _: ChangeEvent<BsonDocument> ->
                                 }): BsonValue {
        dataSpy.insertOneAndSync(
            namespace,
            doc
        )

        assertTrue(dataSpy.getSynchronizedDocuments(namespace).size > 0)

        insertedId = dataSpy.getSynchronizedDocumentIds(namespace).first()
        return insertedId!!
    }
}
