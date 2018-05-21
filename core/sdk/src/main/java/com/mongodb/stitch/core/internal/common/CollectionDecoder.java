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

package com.mongodb.stitch.core.internal.common;

import java.util.ArrayList;
import java.util.Collection;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;

public class CollectionDecoder<ResultT> implements Decoder<Collection<ResultT>> {

  private final Decoder<ResultT> decoder;

  public CollectionDecoder(final Decoder<ResultT> decoder) {
    this.decoder = decoder;
  }

  @Override
  public Collection<ResultT> decode(final BsonReader reader, final DecoderContext decoderContext) {
    final Collection<ResultT> docs = new ArrayList<>();
    reader.readStartArray();
    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
      final ResultT doc = decoder.decode(reader, decoderContext);
      docs.add(doc);
    }
    reader.readEndArray();
    return docs;
  }
}
