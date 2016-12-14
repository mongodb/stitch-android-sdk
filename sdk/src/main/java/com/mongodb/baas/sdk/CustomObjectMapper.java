package com.mongodb.baas.sdk;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;

public class CustomObjectMapper {

    public static ObjectMapper createObjectMapper() {
        return new ObjectMapper().registerModule(new SimpleModule("baasModule")
            .addSerializer(Document.class, new JsonSerializer<Document>() {
                @Override
                public void serialize(
                        final Document value,
                        final JsonGenerator jgen,
                        final SerializerProvider provider
                ) throws IOException {
                    jgen.writeRawValue(value.toJson());
                }
            })
            .addSerializer(ObjectId.class, new JsonSerializer<ObjectId>() {
                @Override
                public void serialize(
                        final ObjectId value,
                        final JsonGenerator jgen,
                        final SerializerProvider provider
                ) throws IOException {
                    jgen.writeString(value.toString());
                }
            }));
    }
}
