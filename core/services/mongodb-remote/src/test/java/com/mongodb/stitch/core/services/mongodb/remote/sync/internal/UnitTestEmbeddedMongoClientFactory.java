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

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.client.MongoClient;
import com.mongodb.embedded.client.MongoClientSettings;
import com.mongodb.embedded.client.MongoClients;
import com.mongodb.embedded.client.MongoEmbeddedSettings;
import com.mongodb.stitch.core.services.mongodb.local.internal.EmbeddedMongoClientFactory;

import org.bson.codecs.configuration.CodecRegistry;

public final class UnitTestEmbeddedMongoClientFactory extends EmbeddedMongoClientFactory {
  private static UnitTestEmbeddedMongoClientFactory factory;

  private UnitTestEmbeddedMongoClientFactory() {
    super();
    MongoClients.init(MongoEmbeddedSettings.builder().build());
  }

  public static UnitTestEmbeddedMongoClientFactory getInstance() {
    synchronized (UnitTestEmbeddedMongoClientFactory.class) {
      if (factory != null) {
        return factory;
      }

      factory = new UnitTestEmbeddedMongoClientFactory();
    }
    return factory;
  }

  @Override
  protected MongoClient createClient(final String dbPath, final CodecRegistry codecRegistry) {
    return MongoClients.create(MongoClientSettings.builder()
        .dbPath(dbPath)
        .codecRegistry(codecRegistry)
        .build());
  }
}
