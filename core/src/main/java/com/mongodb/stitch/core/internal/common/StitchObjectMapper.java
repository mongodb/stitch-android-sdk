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
 * objects with special serialization support for {@link Document}s and {@link ObjectId}s
 */
public final class StitchObjectMapper {

  private static ObjectMapper _singleton;

  public static synchronized ObjectMapper getInstance() {
    if (_singleton != null) {
      return _singleton;
    }
    _singleton =
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
                                JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
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
    _singleton.setVisibility(
        new VisibilityChecker.Std(
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE,
            JsonAutoDetect.Visibility.NONE));
    _singleton.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return _singleton;
  }
}
