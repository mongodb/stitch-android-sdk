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

package com.mongodb.stitch.core.services.mongodb.local.internal;

import com.mongodb.client.MongoClient;
import org.bson.codecs.configuration.CodecRegistry;

public abstract class EmbeddedMongoClientFactory {
  private static final MongoClientHolder clientHolder = new MongoClientHolder();

  protected abstract MongoClient createClient(
      final String dbPath,
      final CodecRegistry codecRegistry);

  protected EmbeddedMongoClientFactory() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (clientHolder.client != null) {
        clientHolder.client.close();
      }
    }));
  }

  public MongoClient getClient(
      final String key,
      final String dbPath,
      final CodecRegistry codecRegistry
  ) {
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("keymust be non-empty");
    }
    if (dbPath == null || dbPath.isEmpty()) {
      throw new IllegalArgumentException("dbPath must be non-empty");
    }

    if (!key.equals(clientHolder.currentKey) || !dbPath.equals(clientHolder.currentDbPath)) {
      synchronized (clientHolder) {
        if (!key.equals(clientHolder.currentKey) || !dbPath.equals(clientHolder.currentDbPath)) {
          if (clientHolder.client != null) {
            clientHolder.client.close();
          }
          clientHolder.set(createClient(dbPath, codecRegistry), key, dbPath);
        }
      }
    }
    return clientHolder.client;
  }

  public void removeClient(final String key) {
    synchronized (clientHolder) {
      if (clientHolder.currentKey.equals(key)) {
        clientHolder.clear();
      }
    }
  }

  public void close() {
    synchronized (clientHolder) {
      if (clientHolder.client != null) {
        clientHolder.client.close();
      }
      clientHolder.clear();
    }
  }

  private static final class MongoClientHolder {
    MongoClient client;
    String currentKey;
    String currentDbPath;

    void set(final MongoClient client, final String key, final String dbPath) {
      this.client = client;
      this.currentKey = key;
      this.currentDbPath = dbPath;
    }

    void clear() {
      this.set(null, null, null);
    }
  }
}
