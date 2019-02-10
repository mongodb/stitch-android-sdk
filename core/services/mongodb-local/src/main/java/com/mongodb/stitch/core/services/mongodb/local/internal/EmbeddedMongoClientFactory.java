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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.codecs.configuration.CodecRegistry;

public abstract class EmbeddedMongoClientFactory {

  private static final Map<String, MongoClient> instances = new ConcurrentHashMap<>();

  protected abstract MongoClient createClient(
      final String dbPath,
      final CodecRegistry codecRegistry);

  protected EmbeddedMongoClientFactory() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      for (final MongoClient client : instances.values()) {
        client.close();
      }
    }));
  }

  public synchronized MongoClient getClient(
      final String key,
      final String dbPath,
      final CodecRegistry codecRegistry
  ) {
    if (dbPath == null || dbPath.isEmpty()) {
      throw new IllegalArgumentException("dbPath must be non-empty");
    }
    if (instances.containsKey(key)) {
      return instances.get(key);
    }

    for (final MongoClient value : instances.values()) {
      value.close();
    }
    instances.clear();

    final MongoClient client = createClient(dbPath, codecRegistry);

    instances.put(key, client);
    return client;
  }

  public synchronized void close() {
    for (final MongoClient instance : instances.values()) {
      instance.close();
    }
    instances.clear();
  }
}
