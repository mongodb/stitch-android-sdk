package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.stitch.core.internal.net.Event
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollectionImpl
import org.bson.BsonDocument
import org.bson.BsonValue
import java.lang.Exception

interface DataSynchronizerTestContext {
    val namespace: MongoNamespace
    val testDocument: BsonDocument
    val testDocumentId: BsonValue
    var updateDocument: BsonDocument

    val collectionMock: CoreRemoteMongoCollectionImpl<BsonDocument>
    var shouldConflictBeResolvedByRemote: Boolean
    var exceptionToThrowDuringConflict: Exception?
    var isOnline: Boolean
    var isLoggedIn: Boolean
    var nextStreamEvent: Event
    val dataSynchronizer: DataSynchronizer

    fun reconfigure()

    fun waitForError()

    fun waitForEvent()

    fun insertTestDocument()

    fun updateTestDocument(): UpdateResult

    fun deleteTestDocument(): DeleteResult

    fun doSyncPass()

    fun findTestDocumentFromLocalCollection(): BsonDocument?

    fun verifyChangeEventListenerCalledForActiveDoc(times: Int, expectedChangeEvent: ChangeEvent<BsonDocument>? = null)

    fun verifyErrorListenerCalledForActiveDoc(times: Int, error: Exception? = null)

    fun verifyConflictHandlerCalledForActiveDoc(
        times: Int,
        expectedLocalConflictEvent: ChangeEvent<BsonDocument>? = null,
        expectedRemoteConflictEvent: ChangeEvent<BsonDocument>? = null
    )

    fun verifyStreamFunctionCalled(times: Int, expectedArgs: List<Any>)

    fun verifyStartCalled(times: Int)

    fun verifyStopCalled(times: Int)

    fun queueConsumableRemoteInsertEvent()

    fun queueConsumableRemoteUpdateEvent()

    fun queueConsumableRemoteDeleteEvent()

    fun queueConsumableRemoteUnknownEvent()

    fun mockInsertException(exception: Exception)

    fun mockUpdateResult(remoteUpdateResult: RemoteUpdateResult)

    fun mockUpdateException(exception: Exception)

    fun mockDeleteResult(remoteDeleteResult: RemoteDeleteResult)

    fun mockDeleteException(exception: Exception)
}
