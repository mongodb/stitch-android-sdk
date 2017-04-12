package com.mongodb.baas.android.services.mongodb;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.mongodb.baas.android.BaasClient;
import com.mongodb.baas.android.PipelineStage;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoClient provides a simple wrapper around pipelines to enable CRUD usage of
 * a MongoDB service.
 */
public class MongoClient {
    private static final String TAG = "BaaS-MongoDB";

    private final BaasClient _baasClient;
    private final String _service;

    /**
     * @param baasClient The client to execute with.
     * @param service    The name of the MongoDB service.
     */
    public MongoClient(final BaasClient baasClient, final String service) {
        _baasClient = baasClient;
        _service = service;
    }

    /**
     * Gets a database.
     *
     * @param name The name of the database.
     * @return A reference to the database.
     */
    public Database getDatabase(final String name) {
        return new Database(this, name);
    }

    /**
     * Database represents a reference to a MongoDB database accessed through BaaS.
     */
    public static class Database {
        private final MongoClient _client;
        private final String _dbName;

        /**
         * @param client The client to which this database is referenced by.
         * @param dbName The name of the database.
         */
        public Database(final MongoClient client, final String dbName) {
            _client = client;
            _dbName = dbName;
        }

        /**
         * Gets a collection in this database.
         *
         * @param name The name of the collection.
         * @return A reference to the collection.
         */
        public Collection getCollection(final String name) {
            return new Collection(this, name);
        }
    }

    /**
     * Collection represents a reference to a MongoDB collection accessed through BaaS.
     */
    public static class Collection {
        private final Database _database;
        private final String _collName;

        /**
         * @param database The database to which this collection is contained in.
         * @param collName The name of the collection.
         */
        public Collection(final Database database, final String collName) {
            _database = database;
            _collName = collName;
        }

        /**
         * Makes a stage that executes a find on the collection.
         *
         * @param query      The query specifier.
         * @param projection The projection document.
         * @return A stage representing this CRUD action.
         */
        public PipelineStage makeFindStage(
                final Document query,
                final Document projection
        ) {
            final Map<String, Object> args = new HashMap<>();
            args.put("database", _database._dbName);
            args.put("collection", _collName);
            args.put("query", query);
            args.put("project", projection);
            return new PipelineStage(
                    "find",
                    _database._client._service,
                    args);
        }

        /**
         * Makes a stage that executes an update on the collection.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no document.
         * @param multi  Whether or not to update multiple documents.
         * @return A stage representing this CRUD action.
         */
        public PipelineStage makeUpdateStage(
                final Document query,
                final Document update,
                final boolean upsert,
                final boolean multi
        ) {
            final Map<String, Object> args = new HashMap<>();
            args.put("database", _database._dbName);
            args.put("collection", _collName);
            args.put("query", query);
            args.put("update", update);
            args.put("upsert", upsert);
            args.put("multi", multi);
            return new PipelineStage(
                    "update",
                    _database._client._service,
                    args);
        }

        /**
         * Makes a series of stages that execute an insert on the collection.
         *
         * @param documents The set of documents to insert.
         * @return The stages representing this CRUD action.
         */
        public List<PipelineStage> makeInsertStage(
                final List<Document> documents
        ) {
            final Map<String, Object> literalArgs = new HashMap<>();
            literalArgs.put("items", documents);

            final Map<String, Object> insertArgs = new HashMap<>();
            insertArgs.put("database", _database._dbName);
            insertArgs.put("collection", _collName);

            final List<PipelineStage> pipelineStages = new ArrayList<>();
            pipelineStages.add(new PipelineStage(
                    "literal",
                    literalArgs));
            pipelineStages.add(new PipelineStage(
                    "insert",
                    _database._client._service,
                    insertArgs));

            return pipelineStages;
        }

        /**
         * Makes a stage that executes a delete on the collection.
         *
         * @param query     The query specifier.
         * @param singleDoc Whether or not to delete only a single matched document.
         * @return A stage representing this CRUD action.
         */
        public PipelineStage makeDeleteStage(
                final Document query,
                final boolean singleDoc
        ) {
            final Map<String, Object> args = new HashMap<>();
            args.put("database", _database._dbName);
            args.put("collection", _collName);
            args.put("query", query);
            args.put("singleDoc", singleDoc);
            return new PipelineStage(
                    "delete",
                    _database._client._service,
                    args);
        }

        /**
         * Finds documents matching a query.
         *
         * @param query The query specifier.
         * @return A task containing the matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query) {
            return convertToDocuments(_database._client._baasClient.executePipeline(makeFindStage(query, null)));
        }

        /**
         * Finds and projects documents matching a query.
         *
         * @param query      The query specifier.
         * @param projection The projection document.
         * @return A task containing the matched and projected documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query, final Document projection) {
            return convertToDocuments(_database._client._baasClient.executePipeline(makeFindStage(query, projection)));
        }

        /**
         * Updates a single document matching a query
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateOne(final Document query, final Document update) {
            return updateOne(query, update, false);
        }

        /**
         * Updates a single document matching the query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no documents.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateOne(final Document query, final Document update, final boolean upsert) {
            return _database._client._baasClient.executePipeline(makeUpdateStage(query, update, upsert, false)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(TAG, "Error upserting single document", task.getException());
                    throw task.getException();
                }
            });
        }

        /**
         * Updates many documents matching a query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateMany(final Document query, final Document update) {
            return updateMany(query, update, false);
        }

        /**
         * Updates many documents matching a query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no documents.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateMany(final Document query, final Document update, final boolean upsert) {
            return _database._client._baasClient.executePipeline(makeUpdateStage(query, update, upsert, true)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(TAG, "Error updating many documents", task.getException());
                    throw task.getException();
                }
            });
        }

        /**
         * Inserts a single document.
         *
         * @param document The document to insert.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> insertOne(final Document document) {
            return _database._client._baasClient.executePipeline(makeInsertStage(Collections.singletonList(document))).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(TAG, "Error inserting single document", task.getException());
                    throw task.getException();
                }
            });
        }

        /**
         * Inserts many documents.
         *
         * @param documents The list of documents to insert.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> insertMany(final List<Document> documents) {
            return _database._client._baasClient.executePipeline(makeInsertStage(documents)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(TAG, "Error inserting multiple documents", task.getException());
                    throw task.getException();
                }
            });
        }

        /**
         * Deletes a single document matching a query specifier.
         *
         * @param query The query specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> deleteOne(final Document query) {
            return _database._client._baasClient.executePipeline(makeDeleteStage(query, true)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(TAG, "Error deleting single document", task.getException());
                    throw task.getException();
                }
            });
        }

        /**
         * Deletes many document matching a query specifier.
         *
         * @param query The query specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> deleteMany(final Document query) {
            return _database._client._baasClient.executePipeline(makeDeleteStage(query, false)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(TAG, "Error deleting many documents", task.getException());
                    throw task.getException();
                }
            });
        }

        /**
         * Converts a series of documents into their concrete {@link Document} format.
         *
         * @param pipelineResult The task representing the pipeline result
         * @return A task containing the converted documents that can be resolved
         * upon completion of the pipeline result.
         */
        private Task<List<Document>> convertToDocuments(final Task<List<Object>> pipelineResult) {
            return pipelineResult.continueWith(new Continuation<List<Object>, List<Document>>() {
                @Override
                public List<Document> then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        final List<Object> objects = task.getResult();
                        final List<Document> docs = new ArrayList<>(objects.size());
                        for (final Object obj : objects) {
                            docs.add((Document) obj);
                        }
                        return docs;
                    } else {
                        Log.d(TAG, "Error getting pipeline results", task.getException());
                        throw task.getException();
                    }
                }
            });
        }
    }
}
