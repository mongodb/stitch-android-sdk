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

import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

/**
 * Utility functions for calculating hash of {@link BsonDocument}s.
 */
public final class HashUtils {
  private static final long FNV_64BIT_OFFSET_BASIS = -3750763034362895579L;
  private static final long FNV_64BIT_PRIME = 1099511628211L;

  private static final BsonDocumentCodec BSON_DOCUMENT_CODEC = new BsonDocumentCodec();

  private HashUtils() {
    // prevent instantiation
  }

  /**
   * Implementation of FNV-1a hash algorithm.
   * @see <a href="https://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function">
   *   ttps://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function</a>
   * @param doc the document to hash
   * @return
   */
  public static long hash(final BsonDocument doc) {
    if (doc == null) {
      return 0L;
    }

    final byte[] docBytes = toBytes(doc);
    long hashValue = FNV_64BIT_OFFSET_BASIS;

    for (int offset = 0; offset < docBytes.length; offset++) {
      hashValue ^= (0xFF & docBytes[offset]);
      hashValue *= FNV_64BIT_PRIME;
    }

    return hashValue;
  }

  public static byte[] toBytes(final BsonDocument doc) {
    final BasicOutputBuffer buffer = new BasicOutputBuffer();
    final BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
    BSON_DOCUMENT_CODEC.encode(writer, doc, EncoderContext.builder().build());

    return buffer.toByteArray();
  }
}
