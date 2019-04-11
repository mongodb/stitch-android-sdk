package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentVersionInfoUnitTests {
    @Test
    fun testGetNextVersion() {
        val emptyVersion = DocumentVersionInfo.fromVersionDoc(null)
        assertFalse(emptyVersion.hasVersion())
        assertNull(emptyVersion.versionDoc)
        assertNull(emptyVersion.filter)

        val localDoc = BsonDocument().append("_id", BsonObjectId())

        // the next version from an empty version should be a non-empty version with a version
        // counter of zero.
        val nextVersion =
                DocumentVersionInfo.fromVersionDoc(emptyVersion.getNextVersion())
        assertTrue(nextVersion.hasVersion())
        assertNotNull(nextVersion.versionDoc)
        assertNull(nextVersion.filter)

        assertEquals(1, nextVersion.version.syncProtocolVersion)
        assertEquals(0, nextVersion.version.versionCounter)

        // the next version from a non-empty version should be the same version, but with the
        // version counter incremented by one
        val incrementedVersion =
                DocumentVersionInfo.fromVersionDoc(nextVersion.getNextVersion())
        assertTrue(nextVersion.hasVersion())
        assertNotNull(incrementedVersion .versionDoc)
        assertNull(incrementedVersion.filter)

        assertEquals(1, incrementedVersion.version.syncProtocolVersion)
        assertEquals(1, incrementedVersion.version.versionCounter)
        assertEquals(nextVersion.version.instanceId, incrementedVersion.version.instanceId)
    }
}
