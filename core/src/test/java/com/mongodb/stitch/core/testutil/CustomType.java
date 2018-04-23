package com.mongodb.stitch.core.testutil;

import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;

public class CustomType {
    private ObjectId id;
    private int intValue;

    public CustomType(final ObjectId id,
                      final int intValue) {
        this.id = id;
        this.intValue = intValue;
    }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public CustomType withNewObjectId() {
        setId(new ObjectId());
        return this;
    }

    public int getIntValue() { return intValue; }

    public static class Codec implements CollectibleCodec<CustomType> {

        @Override
        public CustomType generateIdIfAbsentFromDocument(CustomType document) {
            return documentHasId(document) ? document.withNewObjectId() : document;
        }

        @Override
        public boolean documentHasId(CustomType document) { return document.getId() == null; }

        @Override
        public BsonValue getDocumentId(CustomType document) {
            return new BsonString(document.getId().toHexString());
        }

        @Override
        public CustomType decode(BsonReader reader, DecoderContext decoderContext) {
            Document document = (new DocumentCodec()).decode(reader, decoderContext);
            CustomType ct = new CustomType(
                    document.getObjectId("_id"),
                    document.getInteger("intValue")
            );
            return ct;
        }

        @Override
        public void encode(BsonWriter writer, CustomType value, EncoderContext encoderContext) {
            Document document = new Document();
            if(value.getId() != null) {
                document.put("_id", value.getId());
            }
            document.put("intValue", value.getIntValue());
            (new DocumentCodec()).encode(writer, document, encoderContext);
        }

        @Override
        public Class<CustomType> getEncoderClass() { return CustomType.class; }
    }
}
