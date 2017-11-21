package com.mongodb.stitch.android;

import org.bson.BSONException;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonElement;
import org.bson.BsonInt64;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by jasonflax on 11/21/17.
 */

public class CustomBsonConverter {
    public static BsonArray fromArray(Object[] array) {
        BsonArray bsonArray = new BsonArray();

        for (Object o : array) {
            bsonArray.add(fromObject(o));
        }

        return bsonArray;
    }

    public static BsonArray fromList(List list) {
        BsonArray bsonArray = new BsonArray();

        for (Object o : list) {
            bsonArray.add(fromObject(o));
        }

        return bsonArray;
    }

    public static BsonDocument fromMap(Map map) {
        List<BsonElement> bsonElements = new ArrayList<>();
        for (Object entry : map.entrySet()) {
            if (entry instanceof Map.Entry) {
                Map.Entry mEntry = (Map.Entry)entry;
                if (!(mEntry.getKey() instanceof String)) {
                    throw new BsonInvalidOperationException(
                            mEntry.getKey().toString() + " was a key not of type String");
                }
                bsonElements.add(
                        new BsonElement((String)mEntry.getKey(), fromObject(mEntry.getValue())));
            } else {
                throw new BsonInvalidOperationException(
                        entry + " was not formatted properly");
            }
        }

        return new BsonDocument(bsonElements);
    }

    public static BsonValue fromObject(Object arg) {
        if (arg == null) {
            return new BsonNull();
        } if (arg instanceof Integer) {
            return new BsonInt64((int)arg);
        } else if (arg instanceof String) {
            return new BsonString((String)arg);
        } else if (arg instanceof Float) {
            return new BsonDouble((float)arg);
        } else if (arg instanceof Double) {
            return new BsonDouble((double)arg);
        } else if (arg instanceof Boolean) {
            return new BsonBoolean((boolean) arg);
        } else if (arg instanceof Map) {
            return fromMap((Map)arg);
        } else if (arg instanceof List) {
            return fromList((List)arg);
        } else if (arg instanceof Array) {
            return fromList(new ArrayList(Arrays.asList(arg)));
        } else if (arg instanceof BsonValue) {
            return (BsonValue)arg;
        } else {
            throw new BsonInvalidOperationException(arg.toString() + " was not a valid bson value");
        }
    }
}
