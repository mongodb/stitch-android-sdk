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

import static java.util.Arrays.asList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.IterableCodecProvider;
import org.bson.codecs.MapCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.json.JsonReader;

public final class BsonUtils extends RuntimeException {

  /**
   * A basic codec registry which provides codecs for all BSON types, BSON documents, iterable
   * types, and maps.
   */
  public static final CodecRegistry DEFAULT_CODEC_REGISTRY =
      fromProviders(
          asList(
              new ValueCodecProvider(),
              new BsonValueCodecProvider(),
              new DocumentCodecProvider(),
              new IterableCodecProvider(),
              new MapCodecProvider()));

  /**
   * Parses the provided extended JSON string and decodes it into a T value as specified by the
   * provided {@link Decoder}.
   *
   * @param json The JSON string to parse.
   * @param valueDecoder The {@link Decoder} to use to convert the BSON value into the type T.
   * @param <T> The type into which the JSON string is decoded.
   * @return The decoded value.
   */
  public static <T> T parseValue(final String json, final Decoder<T> valueDecoder) {
    final JsonReader bsonReader = new JsonReader(json);
    bsonReader.readBsonType();
    return valueDecoder.decode(bsonReader, DecoderContext.builder().build());
  }

  /**
   * Parses the provided extended JSON string and decodes it into a T value as specified by the
   * provided class type. The type will decoded using the codec found for the type in the default
   * codec registry. If the provided type is not supported by the default codec registry, the method
   * will throw a {@link org.bson.codecs.configuration.CodecConfigurationException}.
   *
   * @param json The JSON string to parse.
   * @param valueClass The class that the JSON string should be decoded into.
   * @param <T> The type into which the JSON string is decoded.
   * @return The decoded value.
   */
  public static <T> T parseValue(final String json, final Class<T> valueClass) {
    final JsonReader bsonReader = new JsonReader(json);
    bsonReader.readBsonType();
    return DEFAULT_CODEC_REGISTRY
        .get(valueClass)
        .decode(bsonReader, DecoderContext.builder().build());
  }

  /**
   * Parses the provided extended JSON string and decodes it into a T value as specified by the
   * provided class type. The type will decoded using the codec found for the type in the provided
   * codec registry. If the provided type is not supported by the provided codec registry, the
   * method will throw a {@link org.bson.codecs.configuration.CodecConfigurationException}.
   *
   * @param json The JSON string to parse.
   * @param valueClass The class that the JSON string should be decoded into.
   * @param codecRegistry The codec registry to use to find the codec for the provided class.
   * @param <T> The type into which the JSON string is decoded.
   * @return The decoded value.
   */
  public static <T> T parseValue(
      final String json, final Class<T> valueClass, final CodecRegistry codecRegistry) {
    final JsonReader bsonReader = new JsonReader(json);
    bsonReader.readBsonType();
    return codecRegistry.get(valueClass).decode(bsonReader, DecoderContext.builder().build());
  }
}
