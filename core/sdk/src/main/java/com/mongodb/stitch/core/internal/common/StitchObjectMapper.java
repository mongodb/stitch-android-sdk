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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;

/**
 * StitchObjectMapper is responsible for handling the serialization and deserialization of JSON
 * objects with special serialization support for {@link Document}s and {@link ObjectId}s.
 */
public final class StitchObjectMapper {

  private static ObjectMapper singleton;

  private StitchObjectMapper() {}

  /**
   * Gets an instance of the object mapper.
   */
  public static synchronized ObjectMapper getInstance() {
    if (singleton != null) {
      return singleton;
    }
    singleton =
        new ObjectMapper()
            .registerModule(
                new SimpleModule("stitchModule")
                    .addSerializer(
                        Document.class,
                        new JsonSerializer<Document>() {
                          @Override
                          public void serialize(
                              final Document value,
                              final JsonGenerator jsonGenerator,
                              final SerializerProvider provider)
                              throws IOException {
                            final JsonWriterSettings writerSettings =
                                JsonWriterSettings.builder()
                                    .outputMode(JsonMode.EXTENDED)
                                    .indent(true)
                                    .newLineCharacters("")
                                    .indentCharacters("")
                                    .build();
                            jsonGenerator.writeRawValue(value.toJson(writerSettings));
                          }
                        })
                    .addSerializer(
                        ObjectId.class,
                        new JsonSerializer<ObjectId>() {
                          @Override
                          public void serialize(
                              final ObjectId value,
                              final JsonGenerator jsonGenerator,
                              final SerializerProvider provider)
                              throws IOException {
                            jsonGenerator.writeString(value.toString());
                          }
                        }));
    singleton.setVisibility(
        new VisibilityChecker.Std(
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE));
    singleton.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return singleton;
  }
}
