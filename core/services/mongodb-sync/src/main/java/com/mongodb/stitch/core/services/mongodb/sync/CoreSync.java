package com.mongodb.stitch.core.services.mongodb.sync;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable;

import org.bson.BsonValue;
import org.bson.conversions.Bson;

import java.util.Set;

public interface CoreSync<DocumentT> {
    /**
     * Set the conflict resolver and and change event listener on this collection.
     * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
     *                         and remote events.
     * @param changeEventListener the event listener to invoke when a a change event happens for the
     *                         document.
     */
    void configure(SyncConflictResolver<DocumentT> conflictResolver,
                   ChangeEventListener<DocumentT> changeEventListener);

    /**
     * Requests that the given document _id be synchronized.
     * @param id the document _id to synchronize.
     */
    void syncOne(BsonValue id);

    /**
     * Requests that the given document _ids be synchronized.
     * @param ids the document _ids to synchronize.
     */
    void syncMany(BsonValue... ids);

    /**
     * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
     *
     * @param id the _id of the document to desynchronize.
     */
    void desyncOne(BsonValue id);

    /**
     * Stops synchronizing the given document _ids. Any uncommitted writes will be lost.
     *
     * @param ids the _ids of the documents to desynchronize.
     */
    void desyncMany(BsonValue... ids);

    /**
     * Returns the set of synchronized document ids in a namespace.
     *
     * @return the set of synchronized document ids in a namespace.
     */
    Set<BsonValue> getSyncedIds();

    /**
     * Finds all documents in the collection.
     *
     * @return the find iterable interface
     */
    CoreRemoteFindIterable<DocumentT> find();

    /**
     * Finds all documents in the collection.
     *
     * @param resultClass the class to decode each document into
     * @param <ResultT>   the target document type of the iterable.
     * @return the find iterable interface
     */
    <ResultT> CoreRemoteFindIterable<ResultT> find(final Class<ResultT> resultClass);

    /**
     * Finds all documents in the collection.
     *
     * @param filter the query filter
     * @return the find iterable interface
     */
    CoreRemoteFindIterable<DocumentT> find(final Bson filter);

    /**
     * Finds all documents in the collection.
     *
     * @param filter      the query filter
     * @param resultClass the class to decode each document into
     * @param <ResultT>   the target document type of the iterable.
     * @return the find iterable interface
     */
    <ResultT> CoreRemoteFindIterable<ResultT> find(
            final Bson filter,
            final Class<ResultT> resultClass
    );

    /**
     * Finds a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @return a task containing the document if found locally or remotely.
     */
    DocumentT findOneById(final BsonValue documentId);

    /**
     * Finds a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @param resultClass the class to decode each document into
     * @param <ResultT>   the target document type of the iterable.
     * @return a task containing the document if found locally or remotely.
     */
    <ResultT> ResultT findOneById(final BsonValue documentId, final Class<ResultT> resultClass);

    /**
     * Updates a document by the given id. It is first searched for in the local synchronized cache
     * and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @param update the update specifier.
     * @return a task containing the result of the local or remote update.
     */
    RemoteUpdateResult updateOneById(
            final BsonValue documentId, final Bson update);

    /**
     * Inserts a single document and begins to synchronize it.
     *
     * @param document the document to insert and synchronize.
     * @return the result of the insertion.
     */
    RemoteInsertOneResult insertOneAndSync(final DocumentT document);

    /**
     * Deletes a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @return a task containing the result of the local or remote update.
     */
    RemoteDeleteResult deleteOneById(final BsonValue documentId);
}
