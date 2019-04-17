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

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;

import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.CompactChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;

import java.util.HashMap;
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonReader;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;

public class ResultDecoders {

  public static final Decoder<RemoteUpdateResult> updateResultDecoder = new UpdateResultDecoder();

  private static final class UpdateResultDecoder implements Decoder<RemoteUpdateResult> {
    public RemoteUpdateResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.MATCHED_COUNT_FIELD, document);
      keyPresent(Fields.MODIFIED_COUNT_FIELD, document);
      final long matchedCount = document.getNumber(Fields.MATCHED_COUNT_FIELD).longValue();
      final long modifiedCount = document.getNumber(Fields.MODIFIED_COUNT_FIELD).longValue();
      if (!document.containsKey(Fields.UPSERTED_ID_FIELD)) {
        return new RemoteUpdateResult(matchedCount, modifiedCount, null);
      }

      return new RemoteUpdateResult(
          matchedCount,
          modifiedCount,
          document.get(Fields.UPSERTED_ID_FIELD));
    }

    private static final class Fields {
      static final String MATCHED_COUNT_FIELD = "matchedCount";
      static final String MODIFIED_COUNT_FIELD = "modifiedCount";
      static final String UPSERTED_ID_FIELD = "upsertedId";
    }
  }

  public static final Decoder<RemoteDeleteResult> deleteResultDecoder = new DeleteResultDecoder();

  private static final class DeleteResultDecoder implements Decoder<RemoteDeleteResult> {
    public RemoteDeleteResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.DELETED_COUNT_FIELD, document);
      return new RemoteDeleteResult(document.getNumber(Fields.DELETED_COUNT_FIELD).longValue());
    }

    private static final class Fields {
      static final String DELETED_COUNT_FIELD = "deletedCount";
    }
  }

  public static final Decoder<RemoteInsertOneResult> insertOneResultDecoder =
      new InsertOneResultDecoder();

  private static final class InsertOneResultDecoder implements Decoder<RemoteInsertOneResult> {
    public RemoteInsertOneResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.INSERTED_ID_FIELD, document);
      return new RemoteInsertOneResult(document.get(Fields.INSERTED_ID_FIELD));
    }

    private static final class Fields {
      static final String INSERTED_ID_FIELD = "insertedId";
    }
  }

  public static final Decoder<RemoteInsertManyResult> insertManyResultDecoder =
      new InsertManyResultDecoder();

  private static final class InsertManyResultDecoder implements Decoder<RemoteInsertManyResult> {
    public RemoteInsertManyResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.INSERTED_IDS_FIELD, document);
      final BsonArray arr = document.getArray(Fields.INSERTED_IDS_FIELD);
      final Map<Long, BsonValue> insertedIds = new HashMap<>();
      for (int i = 0; i < arr.size(); i++) {
        insertedIds.put((long) i, arr.get(i));
      }

      return new RemoteInsertManyResult(insertedIds);
    }

    private static final class Fields {
      static final String INSERTED_IDS_FIELD = "insertedIds";
    }
  }

  @SuppressWarnings("unused")
  public static <DocumentT> Decoder<ChangeEvent<DocumentT>>
      changeEventDecoder(final Codec<DocumentT> codec) {
    return new ChangeEventDecoder<>(codec);
  }

  @SuppressWarnings("unused")
  static <DocumentT> Decoder<CompactChangeEvent<DocumentT>>
      compactChangeEventDecoder(final Codec<DocumentT> codec) {
    return new CompactChangeEventDecoder<>(codec);
  }

  private abstract static class BaseChangeEventDecoder<DocumentT> {
    protected final Codec<DocumentT> codec;

    BaseChangeEventDecoder(final Codec<DocumentT> codec) {
      this.codec = codec;
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      final BaseChangeEventDecoder<?> other = (BaseChangeEventDecoder) obj;

      // caveat: if someone writes a stateful codec then this logic won't hold up, but we
      // can't use .equals without opening a can of worms
      return other.codec.getClass() == this.codec.getClass();
    }

    @Override
    public int hashCode() {
      return this.codec.hashCode();
    }
  }

  private static final class ChangeEventDecoder<DocumentT>
      extends BaseChangeEventDecoder<DocumentT>
      implements
      Decoder<ChangeEvent<DocumentT>> {

    ChangeEventDecoder(final Codec<DocumentT> codec) {
      super(codec);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChangeEvent<DocumentT> decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      final ChangeEvent<BsonDocument> rawChangeEvent = ChangeEvent.fromBsonDocument(document);

      if (codec == null || codec.getClass().equals(BsonDocumentCodec.class)) {
        return (ChangeEvent<DocumentT>)rawChangeEvent;
      }
      return new ChangeEvent<>(
          rawChangeEvent.getId(),
          rawChangeEvent.getOperationType(),
          rawChangeEvent.getFullDocument() == null ? null : codec.decode(
              rawChangeEvent.getFullDocument().asBsonReader(),
              DecoderContext.builder().build()),
          rawChangeEvent.getNamespace(),
          rawChangeEvent.getDocumentKey(),
          rawChangeEvent.getUpdateDescription(),
          rawChangeEvent.hasUncommittedWrites());
    }
  }

  private static final class CompactChangeEventDecoder<DocumentT> extends
      BaseChangeEventDecoder<DocumentT>
      implements
      Decoder<CompactChangeEvent<DocumentT>> {

    CompactChangeEventDecoder(final Codec<DocumentT> codec) {
      super(codec);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompactChangeEvent<DocumentT> decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      final CompactChangeEvent<BsonDocument> rawChangeEvent =
          CompactChangeEvent.fromBsonDocument(document);

      if (codec == null || codec.getClass().equals(BsonDocumentCodec.class)) {
        return (CompactChangeEvent<DocumentT>)rawChangeEvent;
      }
      return new CompactChangeEvent<>(
          rawChangeEvent.getOperationType(),
          rawChangeEvent.getFullDocument() == null ? null : codec.decode(
              rawChangeEvent.getFullDocument().asBsonReader(),
              DecoderContext.builder().build()),
          rawChangeEvent.getDocumentKey(),
          rawChangeEvent.getUpdateDescription(),
          rawChangeEvent.getStitchDocumentVersion(),
          rawChangeEvent.getStitchDocumentHash(),
          rawChangeEvent.hasUncommittedWrites());
    }
  }
}
