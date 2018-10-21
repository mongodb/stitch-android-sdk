package com.mongodb.stitch.core.testutils.sync

import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import org.bson.BsonValue
import org.bson.Document
import org.bson.conversions.Bson

/**
 * A set of platform independent methods related to
 * [com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync].
 */
interface CoreSyncMethods {
    /**
     * Set the conflict handler and and change event listener on this collection.
     * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
     *                         and remote events.
     * @param changeEventListener the event listener to invoke when a change event happens for the
     *                         document.
     * @param errorListener the error listener to invoke when an irrecoverable error occurs
     */
    fun configure(conflictResolver: ConflictHandler<Document?>,
                  changeEventListener: ChangeEventListener<Document>?,
                  errorListener: ErrorListener?)

    /**
     * Requests that the given document _id be synchronized.
     * @param id the document _id to synchronize.
     */
    fun syncOne(id: BsonValue)

    /**
     * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
     *
     * @param id the _id of the document to desynchronize.
     */
    fun desyncOne(id: BsonValue)

    /**
     * Returns the set of synchronized document ids in a namespace.
     *
     * @return the set of synchronized document ids in a namespace.
     */
    fun getSyncedIds(): Set<BsonValue>

    /**
     * Finds all documents in the collection.
     *
     * @param filter the query filter
     * @return the find iterable interface
     */
    fun find(filter: Bson): Iterable<Document?>

    /**
     * Finds a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param id the _id of the document to search for.
     * @return the document if found locally or remotely.
     */
    fun findOneById(id: BsonValue): Document?

    /**
     * Updates a document by the given id. It is first searched for in the local synchronized cache
     * and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @param update the update specifier.
     * @return the result of the local or remote update.
     */
    fun updateOneById(documentId: BsonValue, update: Bson): RemoteUpdateResult

    /**
     * Inserts a single document and begins to synchronize it.
     *
     * @param document the document to insert and synchronize.
     * @return the result of the insertion.
     */
    fun insertOneAndSync(document: Document): RemoteInsertOneResult

    /**
     * Deletes a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @return the result of the local or remote update.
     */
    fun deleteOneById(documentId: BsonValue): RemoteDeleteResult
}
