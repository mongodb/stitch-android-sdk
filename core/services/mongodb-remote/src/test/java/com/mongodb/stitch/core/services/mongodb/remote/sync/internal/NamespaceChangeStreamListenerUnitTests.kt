package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.internal.net.Event
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonObjectId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.*

class NamespaceChangeStreamListenerUnitTests {
    @After
    fun teardown() {
        CoreRemoteClientFactory.close()
        ServerEmbeddedMongoClientFactory.getInstance().close()
    }

    @Test
    fun testOpenStream() {
        withNewNamespaceChangeStreamListener { harness, namespaceChangeStreamListener, nsConfigMock ->
            // assert the stream does not open since we are offline
            harness.isOnline = false
            harness.isLoggedIn = false
            assertFalse(namespaceChangeStreamListener.openStream())
            verify(harness.authMonitor, times(0)).isLoggedIn

            // assert the stream does not open since we are not logged in
            harness.isOnline = true
            assertFalse(namespaceChangeStreamListener.openStream())
            verify(harness.authMonitor, times(1)).isLoggedIn

            // assert the stream does not open since we have no document ids
            harness.isLoggedIn = true
            assertFalse(namespaceChangeStreamListener.openStream())
            verify(nsConfigMock, times(1)).synchronizedDocumentIds

            // assert and verify that our stream has opened, and that the streamFunction
            // method has been called with the appropriate arguments. verify that we have
            // set the nsConfig to stale
            `when`(nsConfigMock.synchronizedDocumentIds).thenReturn(setOf(BsonObjectId()))
            assertTrue(namespaceChangeStreamListener.openStream())
            val expectedArgs = Collections.singletonList(mapOf(
                "database" to harness.namespace.databaseName,
                "collection" to harness.namespace.collectionName,
                "ids" to nsConfigMock.synchronizedDocumentIds
            ))
            verify(harness.service).streamFunction(eq("watch"), eq(expectedArgs), eq(ChangeEvent.changeEventCoder))
            verify(nsConfigMock).setStale(eq(true))
        }
    }

    @Test
    fun testStoreEvent() {
        withNewNamespaceChangeStreamListener { harness, namespaceChangeStreamListener, nsConfigMock ->
            // assert nothing happens when we try to store events on a closed stream
            assertFalse(namespaceChangeStreamListener.isOpen)
            namespaceChangeStreamListener.storeNextEvent()

            // open the stream. assert that, with an injected error Event, the stream closes
            `when`(nsConfigMock.synchronizedDocumentIds).thenReturn(setOf(BsonObjectId()))
            assertTrue(namespaceChangeStreamListener.openStream())
            harness.nextStreamEvent = Event.Builder().withEventName("error").withData(
                """{"error": "bad", "error_code": "Unknown"}"""
            ).build()
            namespaceChangeStreamListener.storeNextEvent()
            assertFalse(namespaceChangeStreamListener.isOpen)

            // re-open the stream. assert that, with an injected null message Event,
            // the stream does not close, but nothing else should occur
            assertTrue(namespaceChangeStreamListener.openStream())
            harness.nextStreamEvent = Event.Builder().withEventName("message").build()
            namespaceChangeStreamListener.storeNextEvent()
            assertTrue(namespaceChangeStreamListener.isOpen)

            // assert that, with an expected ChangeEvent, the event is stored
            // and the stream remains open
            val expectedChangeEvent = ChangeEvent.changeEventForLocalInsert(harness.namespace, harness.activeDoc, true)
            harness.nextStreamEvent = Event.Builder().withEventName("message").withData(
                ChangeEvent.toBsonDocument(expectedChangeEvent).toJson()
            ).build()
            namespaceChangeStreamListener.storeNextEvent()
            assertTrue(namespaceChangeStreamListener.isOpen)

            // assert that the consumed event equals the expected event.
            // assert that the events have been drained from the event map
            val actualEvents = namespaceChangeStreamListener.events
            assertEquals(1, actualEvents.size)
            SyncHarness.compareEvents(expectedChangeEvent, actualEvents.values.first())
            assertEquals(0, namespaceChangeStreamListener.events.size)
        }
    }

    companion object {
        /**
         * Run a test block with the context of a SyncHarness with an associated
         * NamespaceChangeStreamListener and NamespaceSynchronizationConfig mock.
         */
        private fun withNewNamespaceChangeStreamListener(block: (harness: SyncHarness,
                                                                 namespaceChangeStreamListener: NamespaceChangeStreamListener,
                                                                 nsConfigMock: NamespaceSynchronizationConfig) -> Unit) {
            val harness = SyncHarness()
            val nsConfigMock = mock(NamespaceSynchronizationConfig::class.java)
            block(harness, NamespaceChangeStreamListener(
                harness.namespace,
                nsConfigMock,
                harness.service,
                harness.networkMonitor,
                harness.authMonitor), nsConfigMock)
        }
    }
}
