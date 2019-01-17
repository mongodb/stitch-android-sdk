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

package com.mongodb.stitch.android.services.mongodb.local.internal;

import android.app.Application;

import com.mongodb.client.MongoClient;
import com.mongodb.embedded.client.MongoClientSettings;
import com.mongodb.embedded.client.MongoClients;
import com.mongodb.embedded.client.MongoEmbeddedSettings;
import com.mongodb.stitch.core.services.mongodb.local.internal.EmbeddedMongoClientFactory;

import org.bson.codecs.configuration.CodecRegistry;

public final class AndroidEmbeddedMongoClientFactory extends EmbeddedMongoClientFactory {

  private static AndroidEmbeddedMongoClientFactory factory;

  private AndroidEmbeddedMongoClientFactory() {
    super();
    try {
      Application application = (Application)Class.forName("android.app.ActivityThread")
          .getMethod("currentApplication").invoke(null, (Object[]) null);
      MongoClients.init(MongoEmbeddedSettings.builder().libraryPath(application.getApplicationInfo().nativeLibraryDir).build());
    } catch (Exception e) {
//      MongoClients.init(MongoEmbeddedSettings.builder().build());
    }

  }

  public static synchronized AndroidEmbeddedMongoClientFactory getInstance() {
    if (factory != null) {
      return factory;
    }

    factory = new AndroidEmbeddedMongoClientFactory();
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
