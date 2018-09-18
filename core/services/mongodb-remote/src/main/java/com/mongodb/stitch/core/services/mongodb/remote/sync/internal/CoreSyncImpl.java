package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncFindIterable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.Codec;
import org.bson.conversions.Bson;

import java.util.Set;

import javax.annotation.Nullable;

public class CoreSyncImpl<DocumentT> implements CoreSync<DocumentT> {
    private final DataSynchronizer dataSynchronizer;
    private final SyncOperations<DocumentT> syncOperations;
    private final MongoNamespace namespace;
    private final Class<DocumentT> documentClass;
    private final CoreStitchServiceClient service;

    public CoreSyncImpl(final MongoNamespace namespace,
                        final Class<DocumentT> documentClass,
                        final DataSynchronizer dataSynchronizer,
                        final CoreStitchServiceClient service,
                        final SyncOperations<DocumentT> syncOperations) {
        this.namespace = namespace;
        this.documentClass = documentClass;
        this.dataSynchronizer = dataSynchronizer;
        this.syncOperations = syncOperations;
        this.service = service;
    }

    @Override
    public void configure(final MongoNamespace namespace,
                          final ConflictHandler<DocumentT> conflictResolver,
                          final ChangeEventListener<DocumentT> changeEventListener) {
        this.dataSynchronizer.configure(
          namespace,
          conflictResolver,
          changeEventListener,
          this.service.getCodecRegistry().get(documentClass)
        );
    }

    @Override
    public void syncOne(final BsonValue id) {
        this.dataSynchronizer.syncDocumentFromRemote(this.namespace, id);
    }

    @Override
    public void syncMany(final BsonValue... ids) {
        for (BsonValue id: ids) {
            this.syncOne(id);
        }
    }

    @Override
    public void desyncOne(final BsonValue id) {
        this.dataSynchronizer.desyncDocumentFromRemote(namespace, id);
    }

    @Override
    public void desyncMany(final BsonValue... ids) {
        for (BsonValue id: ids) {
            this.desyncOne(id);
        }
    }

    @Override
    public Set<BsonValue> getSyncedIds() {
        return this.dataSynchronizer.getSynchronizedDocumentIds(namespace);
    }

    /**
     * Finds a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @return the document if found locally or remotely.
     */
    @Nullable
    public DocumentT findOneById(final BsonValue documentId) {
        return findOneById(documentId, this.documentClass);
    }

    /**
     * Finds a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @param resultClass the class to decode each document into
     * @param <ResultT>   the target document type of the iterable.
     * @return the document if found locally or remotely.
     */
    @Nullable
    public <ResultT> ResultT findOneById(
            final BsonValue documentId,
            final Class<ResultT> resultClass
    ) {
        return syncOperations.findOneById(documentId, resultClass).execute(service);
    }

    /**
     * Updates a document by the given id. It is first searched for in the local synchronized cache
     * and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @param update the update specifier.
     * @return the result of the local or remote update.
     */
    public RemoteUpdateResult updateOneById(final BsonValue documentId, final Bson update) {
        return syncOperations.updateOneById(documentId, update).execute(service);
    }

    /**
     * Inserts a single document and begins to synchronize it.
     *
     * @param document the document to insert and synchronize.
     * @return the result of the insertion.
     */
    public RemoteInsertOneResult insertOneAndSync(
            final DocumentT document
    ) {
        return syncOperations.insertOneAndSync(document).execute(service);
    }

    /**
     * Deletes a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @return the result of the local or remote update.
     */
    public RemoteDeleteResult deleteOneById(final BsonValue documentId) {
        return syncOperations.deleteOneById(documentId).execute(service);
    }

    @Override
    public CoreSyncFindIterable<DocumentT> find() {
        return this.find(new BsonDocument(), documentClass);
    }

    @Override
    public CoreSyncFindIterable<DocumentT> find(Bson filter) {
        return this.find(filter, documentClass);
    }

    @Override
    public <ResultT> CoreSyncFindIterable<ResultT> find(Class<ResultT> resultClass) {
        return this.find(new BsonDocument(), resultClass);
    }

    /**
     * Finds all documents in the collection.
     *
     * @param filter      the query filter
     * @param resultClass the class to decode each document into
     * @param <ResultT>   the target document type of the iterable.
     * @return the find iterable interface
     */
    public <ResultT> CoreSyncFindIterable<ResultT> find(
            final Bson filter,
            final Class<ResultT> resultClass
    ) {
        return createFindIterable(filter, resultClass);
    }

    private <ResultT> CoreSyncFindIterable<ResultT> createFindIterable(
            final Bson filter,
            final Class<ResultT> resultClass
    ) {
        return new CoreSyncFindIterableImpl<>(
                filter,
                resultClass,
                service,
                syncOperations);
    }
}
