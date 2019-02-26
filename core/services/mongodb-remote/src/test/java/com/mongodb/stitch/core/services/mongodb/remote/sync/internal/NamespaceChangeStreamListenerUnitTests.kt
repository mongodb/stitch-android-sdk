package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.internal.net.Event
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory
import org.bson.BsonObjectId
import org.bson.Document
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class NamespaceChangeStreamListenerUnitTests {
    private val harness = SyncUnitTestHarness()

    @After
    fun teardown() {
        CoreRemoteClientFactory.close()
        ServerEmbeddedMongoClientFactory.getInstance().close()
    }

    @Test
    fun testOpenStream() {
        val ctx = harness.freshTestContext()
        val (namespaceChangeStreamListener, nsConfigMock) = harness.createNamespaceChangeStreamListenerWithContext(ctx)

        // assert the stream does not open since we are offline
        ctx.isOnline = false
        ctx.isLoggedIn = false
        assertFalse(namespaceChangeStreamListener.openStream())

        // assert the stream does not open since we are not logged in
        ctx.isOnline = true
        assertFalse(namespaceChangeStreamListener.openStream())

        // assert the stream does not open since we have no document ids
        ctx.isLoggedIn = true
        assertFalse(namespaceChangeStreamListener.openStream())
        verify(nsConfigMock, times(3)).synchronizedDocumentIds

        // assert and verify that our stream has opened, and that the streamFunction
        // method has been called with the appropriate arguments. verify that we have
        // set the nsConfig to stale
        `when`(nsConfigMock.synchronizedDocumentIds).thenReturn(setOf(BsonObjectId()))
        assertTrue(namespaceChangeStreamListener.openStream())
        val expectedArgs = Document(mapOf(
            "database" to ctx.namespace.databaseName,
            "collection" to ctx.namespace.collectionName,
            "ids" to nsConfigMock.synchronizedDocumentIds
        ))
        ctx.verifyWatchFunctionCalled(times = 1, expectedArgs = expectedArgs)
        verify(nsConfigMock).setStale(eq(true))
    }

    @Test
    fun testStoreEvent() {
        val ctx = harness.freshTestContext()
        val (namespaceChangeStreamListener, nsConfigMock) = harness.createNamespaceChangeStreamListenerWithContext(ctx)
        // assert nothing happens when we try to store events on a closed stream
        assertFalse(namespaceChangeStreamListener.isOpen)
        namespaceChangeStreamListener.storeNextEvent()

        // open the stream. assert that, with an injected error Event, the stream closes
        `when`(nsConfigMock.synchronizedDocumentIds).thenReturn(setOf(BsonObjectId()))
        assertTrue(namespaceChangeStreamListener.openStream())
        ctx.nextStreamEvent = Event.Builder().withEventName("error").withData(
            """{"error": "bad", "error_code": "Unknown"}"""
        ).build()
        namespaceChangeStreamListener.storeNextEvent()
        assertFalse(namespaceChangeStreamListener.isOpen)

        // re-open the stream. assert that, with an injected null message Event,
        // the stream does not close, but nothing else should occur
        assertTrue(namespaceChangeStreamListener.openStream())
        ctx.nextStreamEvent = Event.Builder().withEventName("message").build()
        namespaceChangeStreamListener.storeNextEvent()
        assertEquals(0, namespaceChangeStreamListener.events.size)
        assertTrue(namespaceChangeStreamListener.isOpen)

        // assert that, with an expected ChangeEvent, the event is stored
        // and the stream remains open
        val expectedChangeEvent = ChangeEvents.changeEventForLocalInsert(ctx.namespace, ctx.testDocument, true)
        ctx.nextStreamEvent = Event.Builder().withEventName("message").withData(
            expectedChangeEvent.toBsonDocument().toJson()
        ).build()
        namespaceChangeStreamListener.storeNextEvent()
        assertTrue(namespaceChangeStreamListener.isOpen)

        // assert that the consumed event equals the expected event.
        // assert that the events have been drained from the event map
        val actualEvents = namespaceChangeStreamListener.events
        assertEquals(1, actualEvents.size)
        SyncUnitTestHarness.compareEvents(expectedChangeEvent, actualEvents.values.first())
        assertEquals(0, namespaceChangeStreamListener.events.size)
    }
}
