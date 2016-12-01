package com.mongodb.baas.sdk.services.mongodb;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.mongodb.baas.sdk.BaaSClient;
import com.mongodb.baas.sdk.PipelineStage;

import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MongoClient {
    private final BaaSClient _baasClient;
    private final String _service;

    public MongoClient(final BaaSClient baasClient, final String service) {
        _baasClient = baasClient;
        _service = service;
    }

    public Database getDatabase(final String name) {
        return new Database(this, name);
    }

    public static class Database {
        private final MongoClient _client;
        private final String _dbName;

        public Database(final MongoClient client, final String dbName) {
            _client = client;
            _dbName = dbName;
        }

        public Collection getCollection(final String name) {
            return new Collection(this, name);
        }
    }

    public static class Collection {
        private final Database _database;
        private final String _collName;

        public Collection(final Database database, final String collName) {
            _database = database;
            _collName = collName;
        }

        private PipelineStage makeFindStage() {
            final Map<String, Object> args = new HashMap<>();
            args.put("database", _database._dbName);
            args.put("collection", _collName);
            return new PipelineStage(
                    "find",
                    _database._client._service,
                    args);
        }

        private Task<List<Document>> convertToDocuments(final Task<List<Object>> pipelineResult) {
            return pipelineResult.continueWithTask(new Continuation<List<Object>, Task<List<Document>>>() {
                @Override
                public Task<List<Document>> then(@NonNull Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        final List<Object> objs = task.getResult();
                        final List<Document> docs = new ArrayList<>(objs.size());
                        for (final Object obj : objs) {
                            docs.add((Document) obj);
                        }
                        return Tasks.forResult(docs);
                    } else {
                        return Tasks.forException(task.getException());
                    }
                }
            });
        }

        public Task<List<Document>> findMany() {
            return convertToDocuments(_database._client._baasClient.executePipeline(makeFindStage()));
        }
    }
}
