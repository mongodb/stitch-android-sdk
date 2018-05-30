/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl;
import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;
import org.bson.Document;
import org.mockito.Mockito;

final class TestUtils {

  static CoreRemoteMongoClient getClient() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    return new CoreRemoteMongoClientImpl(service);
  }

  static CoreRemoteMongoDatabase getDatabase(final String name) {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    return client.getDatabase(name);
  }

  static CoreRemoteMongoDatabase getDatabase() {
    return getDatabase("dbName1");
  }

  static CoreRemoteMongoCollection<Document> getCollection(final String name) {
    final StitchServiceRoutes routes = new StitchServiceRoutes("foo");
    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    final CoreStitchServiceClient service = Mockito.spy(new CoreStitchServiceClientImpl(
        requestClient,
        routes,
        BsonUtils.DEFAULT_CODEC_REGISTRY));
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoDatabase db = client.getDatabase("dbName1");
    return db.getCollection(name);
  }

  static CoreRemoteMongoCollection<Document> getCollection() {
    return getCollection("collName1");
  }

  static CoreRemoteMongoCollection<Document> getCollection(final CoreRemoteMongoClient client) {
    return client.getDatabase("dbName1").getCollection("collName1");
  }
}
