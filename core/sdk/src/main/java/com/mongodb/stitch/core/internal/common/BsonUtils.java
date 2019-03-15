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

import java.io.InputStream;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonValue;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.IterableCodecProvider;
import org.bson.codecs.MapCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
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
   * Parses the provided extended JSON stream and decodes it into a T value as specified by the
   * provided {@link Decoder}. The decoder should close the BsonReade{@link org.bson.BsonReader}
   * when it is finished with it.
   *
   * @param jsonStream the JSON stream to parse.
   * @param valueDecoder the {@link Decoder} to use to convert the BSON value into the type T.
   * @param <T> the type into which the JSON string is decoded.
   * @return the decoded value.
   */
  public static <T> T parseValue(final InputStream jsonStream, final Decoder<T> valueDecoder) {
    final JsonReader bsonReader = new JsonReader(jsonStream);
    bsonReader.readBsonType();
    return valueDecoder.decode(bsonReader, DecoderContext.builder().build());
  }

  /**
   * Parses the provided extended JSON stream and decodes it into a T value as specified by the
   * provided class type. The type will decoded using the codec found for the type in the provided
   * codec registry. If the provided type is not supported by the provided codec registry, the
   * method will throw a {@link org.bson.codecs.configuration.CodecConfigurationException}.
   *
   * @param jsonStream the JSON string to parse.
   * @param valueClass the class that the JSON string should be decoded into.
   * @param codecRegistry the codec registry to use to find the codec for the provided class.
   * @param <T> the type into which the JSON string is decoded.
   * @return the decoded value.
   */
  public static <T> T parseValue(
      final InputStream jsonStream, final Class<T> valueClass, final CodecRegistry codecRegistry) {
    final JsonReader bsonReader = new JsonReader(jsonStream);
    bsonReader.readBsonType();
    // We can't detect if their codecRegistry has any duplicate providers. There's also a chance
    // that putting ours first may prevent decoding of some of their classes if for example they
    // have their own way of decoding an Integer.
    final CodecRegistry newReg =
        CodecRegistries.fromRegistries(BsonUtils.DEFAULT_CODEC_REGISTRY, codecRegistry);
    return newReg.get(valueClass).decode(bsonReader, DecoderContext.builder().build());
  }

  /**
   * Parses the provided extended JSON string and decodes it into a T value as specified by the
   * provided {@link Decoder}.
   *
   * @param json the JSON string to parse.
   * @param valueDecoder the {@link Decoder} to use to convert the BSON value into the type T.
   * @param <T> the type into which the JSON string is decoded.
   * @return the decoded value.
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
   * @param json the JSON string to parse.
   * @param valueClass the class that the JSON string should be decoded into.
   * @param <T> the type into which the JSON string is decoded.
   * @return the decoded value.
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
   * @param json the JSON string to parse.
   * @param valueClass the class that the JSON string should be decoded into.
   * @param codecRegistry the codec registry to use to find the codec for the provided class.
   * @param <T> the type into which the JSON string is decoded.
   * @return the decoded value.
   */
  public static <T> T parseValue(
      final String json, final Class<T> valueClass, final CodecRegistry codecRegistry) {
    final JsonReader bsonReader = new JsonReader(json);
    bsonReader.readBsonType();
    // We can't detect if their codecRegistry has any duplicate providers. There's also a chance
    // that putting ours first may prevent decoding of some of their classes if for example they
    // have their own way of decoding an Integer.
    final CodecRegistry newReg =
        CodecRegistries.fromRegistries(BsonUtils.DEFAULT_CODEC_REGISTRY, codecRegistry);
    return newReg.get(valueClass).decode(bsonReader, DecoderContext.builder().build());
  }

  public static <T> Codec<T> getCodec(
      final CodecRegistry codecRegistry,
      final Class<T> documentClass
  ) {
    return codecRegistry.get(documentClass);
  }

  public static <T> BsonDocument documentToBsonDocument(
      final T document,
      final CodecRegistry codecRegistry
  ) {
    return BsonDocumentWrapper.asBsonDocument(document, codecRegistry);
  }

  public static <T> BsonDocument documentToBsonDocument(
      final T document,
      final Codec<T> codec
  ) {
    return documentToBsonDocument(document, CodecRegistries.fromCodecs(codec));
  }

  public static <T> BsonDocument toBsonDocument(
      final Bson bson,
      final Class<T> documentClass,
      final CodecRegistry codecRegistry
  ) {
    return bson == null ? null : bson.toBsonDocument(documentClass, codecRegistry);
  }

  public static BsonValue getDocumentId(final BsonDocument document) {
    return document.get("_id");
  }

  /**
   * Returns a copy of the given document.
   * @param document the document to copy.
   * @return a copy of the given document.
   */
  public static BsonDocument copyOfDocument(final BsonDocument document) {
    final BsonDocument newDocument = new BsonDocument();
    for (final Map.Entry<String, BsonValue> kv : document.entrySet()) {
      newDocument.put(kv.getKey(), kv.getValue());
    }
    return newDocument;
  }

  public static <T> BsonDocument toBsonDocumentOrNull(
      final Bson document,
      final Class<T> documentClass,
      final CodecRegistry codecRegistry
  ) {
    return document == null ? null : document.toBsonDocument(documentClass, codecRegistry);
  }
}
