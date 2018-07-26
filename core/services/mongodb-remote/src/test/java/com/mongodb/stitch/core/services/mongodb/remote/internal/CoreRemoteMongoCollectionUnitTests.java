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

import static com.mongodb.stitch.core.services.mongodb.remote.internal.TestUtils.getCollection;
import static com.mongodb.stitch.core.services.mongodb.remote.internal.TestUtils.getDatabase;
import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.CollectionDecoder;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreRemoteMongoCollectionUnitTests {

  @Test
  public void testGetNamespace() {
    final CoreRemoteMongoCollection<Document> coll1 = getCollection();
    assertEquals(new MongoNamespace("dbName1", "collName1"), coll1.getNamespace());

    final CoreRemoteMongoCollection<Document> coll2 = getCollection("collName2");
    assertEquals(new MongoNamespace("dbName1", "collName2"), coll2.getNamespace());
  }

  @Test
  public void testGetDocumentClass() {
    final CoreRemoteMongoCollection<Document> coll1 = getCollection();
    assertEquals(Document.class, coll1.getDocumentClass());
    final CoreRemoteMongoCollection<Integer> coll3 =
        getDatabase().getCollection("collName3", Integer.class);
    assertEquals(Integer.class, coll3.getDocumentClass());
  }

  @Test
  public void testGetCodecRegistry() {
    final CoreRemoteMongoCollection<Document> coll1 = getCollection();
    assertEquals(BsonUtils.DEFAULT_CODEC_REGISTRY, coll1.getCodecRegistry());
  }

  @Test
  public void testWithDocumentClass() {
    final CoreRemoteMongoCollection<Document> coll1 = getCollection();
    final CoreRemoteMongoCollection<Integer> coll2 = coll1.withDocumentClass(Integer.class);
    assertEquals(Integer.class, coll2.getDocumentClass());
  }

  @Test
  public void testWithCodecRegistry() {
    final CoreRemoteMongoCollection<Document> coll1 = getCollection();
    final CodecRegistry myReg = CodecRegistries.fromRegistries(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoCollection<Document> coll2 = coll1.withCodecRegistry(myReg);
    assertEquals(myReg, coll2.getCodecRegistry());
    assertNotEquals(myReg, coll1.getCodecRegistry());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testCount() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    doReturn(42L)
        .when(service).callFunction(any(), any(), any(Class.class));
    assertEquals(42, coll.count());

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Class<Long>> resultClassArg =
        ArgumentCaptor.forClass(Class.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("count", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("query", new BsonDocument());
    expectedArgs.put("limit", 0);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(Long.class, resultClassArg.getValue());

    final BsonDocument expectedFilter = new BsonDocument("one", new BsonInt32(23));
    assertEquals(42, coll.count(expectedFilter, new RemoteCountOptions().limit(5)));

    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("count", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("query", expectedFilter);
    expectedArgs.put("limit", 5);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(Long.class, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Class.class));
    assertThrows(coll::count,
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testFind() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    final Document doc1 = new Document("one", 2);
    final Document doc2 = new Document("three", 4);
    final Collection<Document> docs = Arrays.asList(doc1, doc2);
    doReturn(docs)
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final CoreRemoteFindIterable<Document> iter = coll.find();
    assertEquals(docs, iter.into(new ArrayList<>()));

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("find", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("query", new BsonDocument());
    expectedArgs.put("limit", 0);
    expectedArgs.put("project", null);
    expectedArgs.put("sort", null);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(CollectionDecoder.class, resultClassArg.getValue().getClass());

    final BsonDocument expectedFilter = new BsonDocument("one", new BsonInt32(23));
    final BsonDocument expectedProject = new BsonDocument("two", new BsonString("four"));
    final BsonDocument expectedSort = new BsonDocument("_id", new BsonInt64(-1));
    iter.filter(expectedFilter)
        .projection(expectedProject)
        .sort(expectedSort)
        .limit(5);

    assertEquals(docs, iter.into(new ArrayList<>()));
    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("find", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    expectedArgs.put("query", expectedFilter);
    expectedArgs.put("project", expectedProject);
    expectedArgs.put("sort", expectedSort);
    expectedArgs.put("limit", 5);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(CollectionDecoder.class, resultClassArg.getValue().getClass());

    doReturn(Arrays.asList(1, 2, 3))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertEquals(Arrays.asList(1, 2, 3),
        coll.find(expectedFilter, Integer.class).into(new ArrayList<>()));

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.find().first(),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testAggregate() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    final Document doc1 = new Document("one", 2);
    final Document doc2 = new Document("three", 4);
    final Collection<Document> docs = Arrays.asList(doc1, doc2);
    doReturn(docs)
        .when(service).callFunction(any(), any(), any(Decoder.class));

    CoreRemoteAggregateIterable<Document> iter = coll.aggregate(Collections.emptyList());
    assertEquals(docs, iter.into(new ArrayList<>()));

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("aggregate", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("pipeline", new ArrayList<BsonDocument>());
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(CollectionDecoder.class, resultClassArg.getValue().getClass());

    iter = coll.aggregate(Arrays.asList(new Document("$match", 1), new Document("sort", 2)));
    final List<BsonDocument> expectedPipeline = Arrays.asList(
        new BsonDocument("$match", new BsonInt32(1)), new BsonDocument("sort", new BsonInt32(2))
    );

    assertEquals(docs, iter.into(new ArrayList<>()));
    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("aggregate", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    expectedArgs.put("pipeline", expectedPipeline);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(CollectionDecoder.class, resultClassArg.getValue().getClass());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.aggregate(Collections.emptyList()).first(),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testInsertOne() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    final Document doc1 = new Document("one", 2);
    final BsonObjectId id = new BsonObjectId();
    doReturn(new RemoteInsertOneResult(id))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final RemoteInsertOneResult result = coll.insertOne(doc1);
    assertEquals(id, result.getInsertedId());
    assertNotEquals(id.getValue(), doc1.getObjectId("_id"));

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("insertOne", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("document", doc1.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY));
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.insertOneResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.insertOne(new Document()),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testInsertMany() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    final Document doc1 = new Document("one", 2);
    final Document doc2 = new Document("three", 4);
    final BsonObjectId id1 = new BsonObjectId();
    final BsonObjectId id2 = new BsonObjectId();

    final Map<Long, BsonValue> ids = new HashMap<>();
    ids.put(0L, id1);
    ids.put(1L, id2);
    doReturn(new RemoteInsertManyResult(ids))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final RemoteInsertManyResult result = coll.insertMany(Arrays.asList(doc1, doc2));
    assertEquals(ids, result.getInsertedIds());
    assertNotEquals(ids.get(0L).asObjectId().getValue(), doc1.getObjectId("_id"));
    assertNotEquals(ids.get(1L).asObjectId().getValue(), doc2.getObjectId("_id"));

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("insertMany", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("documents",
        Arrays.asList(
            doc1.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY),
            doc2.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY)));
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.insertManyResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.insertMany(Collections.singletonList(new Document())),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeleteOne() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    doReturn(new RemoteDeleteResult(1))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final Document expectedFilter = new Document("one", 2);
    final RemoteDeleteResult result = coll.deleteOne(expectedFilter);
    assertEquals(1, result.getDeletedCount());

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("deleteOne", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("query",
        expectedFilter.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY));
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.deleteResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.deleteOne(new Document()),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeleteMany() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    doReturn(new RemoteDeleteResult(1))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final Document expectedFilter = new Document("one", 2);
    final RemoteDeleteResult result = coll.deleteMany(expectedFilter);
    assertEquals(1, result.getDeletedCount());

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("deleteMany", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("query",
        expectedFilter.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY));
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.deleteResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.deleteMany(new Document()),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testUpdateOne() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    final BsonObjectId id = new BsonObjectId();
    doReturn(new RemoteUpdateResult(1, 1, id))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final Document expectedFilter = new Document("one", 2);
    final Document expectedUpdate = new Document("three", 4);
    RemoteUpdateResult result = coll.updateOne(expectedFilter, expectedUpdate);
    assertEquals(1, result.getMatchedCount());
    assertEquals(1, result.getModifiedCount());
    assertEquals(id, result.getUpsertedId());

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("updateOne", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("query",
        expectedFilter.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY));
    expectedArgs.put("update",
        expectedUpdate.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY));
    expectedArgs.put("upsert", false);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.updateResultDecoder, resultClassArg.getValue());

    result = coll.updateOne(expectedFilter, expectedUpdate, new RemoteUpdateOptions().upsert(true));
    assertEquals(1, result.getMatchedCount());
    assertEquals(1, result.getModifiedCount());
    assertEquals(id, result.getUpsertedId());
    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("updateOne", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    expectedArgs.put("upsert", true);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.updateResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.updateOne(new Document(), new Document()),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testUpdateMany() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    when(service.getCodecRegistry()).thenReturn(BsonUtils.DEFAULT_CODEC_REGISTRY);
    final CoreRemoteMongoClient client = new CoreRemoteMongoClientImpl(service);
    final CoreRemoteMongoCollection<Document> coll = getCollection(client);

    final BsonObjectId id = new BsonObjectId();
    doReturn(new RemoteUpdateResult(1, 1, id))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final Document expectedFilter = new Document("one", 2);
    final Document expectedUpdate = new Document("three", 4);
    RemoteUpdateResult result = coll.updateMany(expectedFilter, expectedUpdate);
    assertEquals(1, result.getMatchedCount());
    assertEquals(1, result.getModifiedCount());
    assertEquals(id, result.getUpsertedId());

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<Collection<Document>>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("updateMany", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("database", "dbName1");
    expectedArgs.put("collection", "collName1");
    expectedArgs.put("query",
        expectedFilter.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY));
    expectedArgs.put("update",
        expectedUpdate.toBsonDocument(null, BsonUtils.DEFAULT_CODEC_REGISTRY));
    expectedArgs.put("upsert", false);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.updateResultDecoder, resultClassArg.getValue());

    result = coll.updateMany(
        expectedFilter, expectedUpdate, new RemoteUpdateOptions().upsert(true));
    assertEquals(1, result.getMatchedCount());
    assertEquals(1, result.getModifiedCount());
    assertEquals(id, result.getUpsertedId());
    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("updateMany", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    expectedArgs.put("upsert", true);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.updateResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> coll.updateMany(new Document(), new Document()),
        IllegalArgumentException.class);
  }
}
