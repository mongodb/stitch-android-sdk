package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import org.junit.Assert
import org.junit.Test

class OperationTypeUnitTests {
    @Test
    fun testOperationTypeFromRemote() {
        Assert.assertEquals(
            OperationType.INSERT,
            OperationType.fromRemote("insert"))

        Assert.assertEquals(
            OperationType.UPDATE,
            OperationType.fromRemote("update"))

        Assert.assertEquals(
            OperationType.REPLACE,
            OperationType.fromRemote("replace"))

        Assert.assertEquals(
            OperationType.DELETE,
            OperationType.fromRemote("delete"))

        Assert.assertEquals(
            OperationType.UNKNOWN,
            OperationType.fromRemote("bad"))
    }

    @Test
    fun testOperationTypeToRemote() {
        Assert.assertEquals("insert", OperationType.INSERT.toRemote())
        Assert.assertEquals("update", OperationType.UPDATE.toRemote())
        Assert.assertEquals("replace", OperationType.REPLACE.toRemote())
        Assert.assertEquals("delete", OperationType.DELETE.toRemote())
        Assert.assertEquals("unknown", OperationType.UNKNOWN.toRemote())
    }
}
