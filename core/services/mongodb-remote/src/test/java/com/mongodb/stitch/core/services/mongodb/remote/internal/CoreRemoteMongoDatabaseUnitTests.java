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

import static com.mongodb.stitch.core.services.mongodb.remote.internal.TestUtils.getDatabase;
import static org.junit.Assert.assertEquals;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.CoreRemoteClientFactory;
import com.mongodb.stitch.server.services.mongodb.local.internal.ServerEmbeddedMongoClientFactory;

import org.bson.Document;
import org.junit.After;
import org.junit.Test;

public class CoreRemoteMongoDatabaseUnitTests {

  @After
  public void teardown() {
    CoreRemoteClientFactory.close();
    ServerEmbeddedMongoClientFactory.getInstance().close();
  }

  @Test
  public void testGetName() {
    final CoreRemoteMongoDatabase db1 = getDatabase();
    assertEquals("dbName1", db1.getName());

    final CoreRemoteMongoDatabase db2 = getDatabase("dbName2");
    assertEquals("dbName2", db2.getName());
  }

  @Test
  public void testGetCollection() {
    final CoreRemoteMongoDatabase db1 = getDatabase();
    final CoreRemoteMongoCollection<Document> coll1 = db1.getCollection("collName1");
    assertEquals(new MongoNamespace("dbName1", "collName1"), coll1.getNamespace());

    final CoreRemoteMongoCollection<Document> coll2 = db1.getCollection("collName2");
    assertEquals(new MongoNamespace("dbName1", "collName2"), coll2.getNamespace());

    assertEquals(Document.class, coll1.getDocumentClass());
    final CoreRemoteMongoCollection<Integer> coll3 =
        db1.getCollection("collName3", Integer.class);
    assertEquals(Integer.class, coll3.getDocumentClass());
  }
}
