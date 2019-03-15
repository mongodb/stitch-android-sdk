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

import java.util.Iterator;

import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;

public class IteratorDecoder<ResultT> implements StreamedDecoder<Iterator<ResultT>> {

  private final Decoder<ResultT> decoder;

  public IteratorDecoder(final Decoder<ResultT> decoder) {
    this.decoder = decoder;
  }

  @Override
  public Iterator<ResultT> decode(final BsonReader reader, final DecoderContext decoderContext) {
    reader.readStartArray();
    return new DocumentIterator<>(reader, decoder, decoderContext);
  }

  private static final class DocumentIterator<ResultT> implements Iterator<ResultT> {

    private final BsonReader reader;
    private final Decoder<ResultT> decoder;
    private final DecoderContext decoderContext;
    private Boolean hasNext = null;

    private DocumentIterator(
        final BsonReader reader,
        final Decoder<ResultT> decoder,
        final DecoderContext decoderContext
    ) {
      this.reader = reader;
      this.decoder = decoder;
      this.decoderContext = decoderContext;
    }

    @Override
    public boolean hasNext() {
      if (hasNext != null) {
        return hasNext;
      }
      if (reader.readBsonType() == BsonType.END_OF_DOCUMENT) {
        reader.readEndArray();
        hasNext = false;
        reader.close();
        return false;
      }
      hasNext = true;
      return true;
    }

    @Override
    public ResultT next() {
      hasNext = null;
      return decoder.decode(reader, decoderContext);
    }
  }
}
