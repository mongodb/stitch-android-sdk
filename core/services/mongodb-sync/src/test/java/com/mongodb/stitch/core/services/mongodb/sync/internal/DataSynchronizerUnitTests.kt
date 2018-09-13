package com.mongodb.stitch.core.services.mongodb.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.StitchAppClientInfo
import com.mongodb.stitch.core.internal .common.AuthMonitor
import com.mongodb.stitch.core.internal .net.NetworkMonitor
import com.mongodb.stitch.core.services.internal .CoreStitchServiceClient
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver
import junit.framework.Assert.*
import org.bson.*
import org.bson.codecs.BsonDocumentCodec

import org.bson.codecs.configuration.CodecRegistries
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.verify

class DataSynchronizerUnitTests {
    private val testDb = "testDb"
    private val testColl = "testColl"

    private val namespace = MongoNamespace(testDb, testColl)

    private var dataSynchronizer: DataSynchronizer? = null
    private var insertedId: BsonValue? = null

    @Before
    fun setup() {
        val networkMonitor = object : NetworkMonitor {
            override fun isConnected(): Boolean {
                return true
            }

            override fun addNetworkStateListener(listener: NetworkMonitor.StateListener) {

            }
        }
        val authMonitor = AuthMonitor { true }
        dataSynchronizer = DataSynchronizer(
                "testInstanceKey",
                Mockito.mock(CoreStitchServiceClient::class.java),
                LocalClientFactory.getClient(
                        StitchAppClientInfo(
                                "<client-app-id>",
                                "__store__",
                                "<local-app-name>",
                                "<local-app-version>", null,
                                networkMonitor,
                                authMonitor
                        ),
                        "local"
                ),
                Mockito.spy(CoreRemoteMongoClientImpl(Mockito.mock(CoreStitchServiceClient::class.java))),
                networkMonitor,
                authMonitor
        )
    }

    @After
    fun teardown() {
        val dataSpy = Mockito.spy(dataSynchronizer!!)
        dataSpy.deleteOneById(namespace, insertedId)
    }

    @Test
    fun testInsertOneAndSync() {
        val dataSpy = Mockito.spy(dataSynchronizer!!)

        insertOneAndSync(dataSpy, BsonDocument())

        assertEquals(dataSpy.getSynchronizedDocuments(namespace).first().documentId, insertedId)
        assertEquals(dataSpy.getSynchronizedDocuments(namespace).size, 1)
    }

    @Test
    fun testFindOneById() {
        val dataSpy = Mockito.spy(dataSynchronizer!!)

        insertOneAndSync(dataSpy, BsonDocument())

        val doc2 = dataSpy.findOneById(
                namespace,
                insertedId,
                BsonDocument::class.java,
                CodecRegistries.fromCodecs(BsonDocumentCodec())
        )

        assertEquals(insertedId, doc2["_id"])
    }

    @Test
    fun testDeleteOneById() {
        val dataSpy = Mockito.spy(dataSynchronizer!!)

        insertOneAndSync(dataSpy, BsonDocument())

        assertEquals(dataSpy.getSynchronizedDocuments(namespace).size, 1)

        dataSpy.deleteOneById(namespace, insertedId)

        assertEquals(dataSpy.getSynchronizedDocuments(namespace).size, 0)

        val namespaceArg = ArgumentCaptor.forClass(MongoNamespace::class.java)
        val idArg = ArgumentCaptor.forClass(BsonValue::class.java)

        verify(dataSpy).desyncDocumentFromRemote(namespaceArg.capture(), idArg.capture())

        assertEquals(namespaceArg.value, namespace)
        assertEquals(idArg.value, insertedId)
    }

    private fun insertOneAndSync(dataSpy: DataSynchronizer,
                                 doc: BsonDocument,
                                 conflictResolver: SyncConflictResolver<BsonDocument> = SyncConflictResolver {
                                     _, _, _ ->
                                     doc
                                 },
                                 changeEventListener: ChangeEventListener<BsonDocument> = ChangeEventListener {
                                     _, _: ChangeEvent<BsonDocument> ->
                                 }): BsonValue {
        dataSpy.insertOneAndSync(
                namespace,
                doc,
                conflictResolver,
                changeEventListener,
                BsonDocumentCodec()
        )

        assertTrue(dataSpy.getSynchronizedDocuments(namespace).size > 0)

        val namespaceArg = ArgumentCaptor.forClass(MongoNamespace::class.java)
        val idArg = ArgumentCaptor.forClass(BsonValue::class.java)
        val eventListenerArg = ArgumentCaptor.forClass(changeEventListener::class.java)
        val documentCodecArg = ArgumentCaptor.forClass(BsonDocumentCodec::class.java)

        verify(dataSpy).watchDocument(
                namespaceArg.capture(),
                idArg.capture(),
                eventListenerArg.capture(),
                documentCodecArg.capture()
        )

        insertedId = idArg.value
        return insertedId!!
    }
}
