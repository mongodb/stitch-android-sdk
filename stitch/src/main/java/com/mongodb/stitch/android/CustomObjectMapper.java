package com.mongodb.stitch.android;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;

import java.io.IOException;

/**
 * CustomObjectMapper is responsible for handling the serialization and deserialization of JSON
 * objects with special serialization support for {@link Document}s and {@link ObjectId}s
 */
class CustomObjectMapper {

    private static ObjectMapper _singleton;

    public static ObjectMapper createObjectMapper() {
        if (_singleton != null) {
            return _singleton;
        }
        _singleton = new ObjectMapper().registerModule(new SimpleModule("stitchModule")
                .addSerializer(Document.class, new JsonSerializer<Document>() {
                    @Override
                    public void serialize(
                            final Document value,
                            final JsonGenerator jsonGenerator,
                            final SerializerProvider provider
                    ) throws IOException {
                        final JsonWriterSettings writerSettings =
                                JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
                        jsonGenerator.writeRawValue(value.toJson(writerSettings));
                    }
                })
                .addSerializer(ObjectId.class, new JsonSerializer<ObjectId>() {
                    @Override
                    public void serialize(
                            final ObjectId value,
                            final JsonGenerator jsonGenerator,
                            final SerializerProvider provider
                    ) throws IOException {
                        jsonGenerator.writeString(value.toString());
                    }
                }));
        return _singleton;
    }
}
