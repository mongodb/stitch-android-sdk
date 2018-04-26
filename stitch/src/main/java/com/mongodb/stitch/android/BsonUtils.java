package com.mongodb.stitch.android;

import org.bson.BsonType;
import org.bson.BsonWriterSettings;
import org.bson.codecs.BsonTypeClassMap;
import org.bson.codecs.BsonTypeCodecMap;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.IterableCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.json.JsonMode;
import org.bson.json.JsonReader;
import org.bson.json.JsonWriterSettings;

import java.util.List;

import static java.util.Arrays.asList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class BsonUtils extends RuntimeException {
    private static final CodecRegistry DEFAULT_CODEC_REGISTRY = fromProviders(asList(
            new IterableCodecProvider(),
            new ValueCodecProvider(),
            new BsonValueCodecProvider(),
            new DocumentCodecProvider()));
    private static final BsonTypeClassMap DEFAULT_BSON_TYPE_CLASS_MAP = new BsonTypeClassMap();
    private static final BsonTypeCodecMap DEFAULT_BSON_TYPE_CODEC_MAP =
            new BsonTypeCodecMap(DEFAULT_BSON_TYPE_CLASS_MAP, DEFAULT_CODEC_REGISTRY);

    private BsonUtils() {}

    public static final JsonWriterSettings EXTENDED_JSON_WRITER_SETTINGS =
            JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    public static Iterable parseIterable(final String json) {
        final JsonReader bsonReader = new JsonReader(json);
        final Object decoded = DEFAULT_BSON_TYPE_CODEC_MAP.get(BsonType.ARRAY).decode(bsonReader, DecoderContext.builder().build());
        return (Iterable) decoded;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseList(final String json) {
        return (List<Object>) parseIterable(json);
    }

    public static Object parseValue(final String json) {
        final JsonReader bsonReader = new JsonReader(json);
        bsonReader.readBsonType();
        final Object decoded = DEFAULT_BSON_TYPE_CODEC_MAP.get(bsonReader.getCurrentBsonType()).decode(
                bsonReader, DecoderContext.builder().build());
        return decoded;
    }
}
