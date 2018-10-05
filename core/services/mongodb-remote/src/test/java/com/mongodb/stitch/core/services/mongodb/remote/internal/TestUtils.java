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

import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl;
import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.CoreRemoteClientFactory;
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.mockito.Mockito;

public final class TestUtils {

  static CoreRemoteMongoClient getClient() {
    final StitchServiceRoutes routes = new StitchServiceRoutes("foo");
    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    final CoreStitchServiceClient service = Mockito.spy(new CoreStitchServiceClientImpl(
        requestClient,
        routes,
        BsonUtils.DEFAULT_CODEC_REGISTRY));
    return CoreRemoteClientFactory.getClient(
        service,
        getClientInfo(),
        ServerEmbeddedMongoClientFactory.getInstance());
  }

  static CoreRemoteMongoDatabase getDatabase(final String name) {
    return getClient().getDatabase(name);
  }

  static CoreRemoteMongoDatabase getDatabase() {
    return getDatabase("dbName1");
  }

  static CoreRemoteMongoCollection<Document> getCollection(final String name) {
    final CoreRemoteMongoDatabase db = getClient().getDatabase("dbName1");
    return db.getCollection(name);
  }

  static CoreRemoteMongoCollection<Document> getCollection() {
    return getCollection("collName1");
  }

  static CoreRemoteMongoCollection<Document> getCollection(final CoreRemoteMongoClient client) {
    return client.getDatabase("dbName1").getCollection("collName1");
  }

  private static final String CLIENT_KEY = new ObjectId().toHexString();

  static StitchAppClientInfo getClientInfo() {
    return new StitchAppClientInfo(
        CLIENT_KEY,
        String.format("%s/%s", System.getProperty("java.io.tmpdir"), CLIENT_KEY),
        new ObjectId().toHexString(),
        new ObjectId().toHexString(),
        BsonUtils.DEFAULT_CODEC_REGISTRY,
        null,
        null);
  }
}
