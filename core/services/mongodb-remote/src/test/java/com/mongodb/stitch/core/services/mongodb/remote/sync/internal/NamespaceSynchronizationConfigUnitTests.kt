package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NamespaceSynchronizationConfigUnitTests {
    @Test
    fun testToBsonDocumentRoundTrip() {
        withNewNamespaceSynchronizationConfig { harness, nsConfig ->
            val configBsonDocument = nsConfig.toBsonDocument()

            assertEquals(BsonString(harness.namespace.toString()), configBsonDocument["namespace"])
            assertEquals(BsonInt32(1), configBsonDocument["schema_version"])

            val roundTrippedNsConfig = NamespaceSynchronizationConfig.fromBsonDocument(configBsonDocument)

            assertEquals(nsConfig.namespace, roundTrippedNsConfig.namespace)
        }
    }

    companion object {
        private fun withNewNamespaceSynchronizationConfig(block: (harness: SyncHarness,
                                                                  nsConfig: NamespaceSynchronizationConfig) -> Unit) {
            val harness = SyncHarness()
            val docsColl = mock(MongoCollection::class.java) as MongoCollection<CoreDocumentSynchronizationConfig>
            val findIterable = mock(FindIterable::class.java) as FindIterable<CoreDocumentSynchronizationConfig>

            `when`(docsColl.find(any(BsonDocument::class.java))).thenReturn(findIterable)

            block(harness,  NamespaceSynchronizationConfig(
                mock(MongoCollection::class.java) as MongoCollection<NamespaceSynchronizationConfig>,
                docsColl,
                harness.namespace))
        }
    }
}
