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

package com.mongodb.stitch.core.auth.providers.userapikey.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;

import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;

import java.util.Map;

/**
 * A struct representing a user API key as returned by the Stitch client API.
 */
public final class UserApiKey {
  private final ObjectId id;
  private final String key;
  private final String name;
  private final Boolean disabled;

  /**
   * Constructs a User API key with the provided parameters.
   * @param id The id of the user API key as an ObjectId hex string.
   * @param key The key itself.
   * @param name The name of the key.
   * @param disabled Whether or not the key is disabled.
   */
  @JsonCreator
  public UserApiKey(
          @JsonProperty(Fields.ID) final String id,
          @JsonProperty(Fields.KEY) final String key,
          @JsonProperty(Fields.NAME) final String name,
          @JsonProperty(Fields.DISABLED) final Boolean disabled) {
    this.id = new ObjectId(id);
    this.key = key;
    this.name = name;
    this.disabled = disabled;
  }

  @JsonProperty(Fields.ID)
  public ObjectId getId() {
    return id;
  }

  @JsonProperty(Fields.KEY)
  public String getKey() {
    return key;
  }

  @JsonProperty(Fields.NAME)
  public String getName() {
    return name;
  }

  @JsonProperty(Fields.DISABLED)
  public Boolean getDisabled() {
    return disabled;
  }


  @Override
  public String toString() {
    try {
      return StitchObjectMapper.getInstance().writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private static class Fields {
    private static final String ID = "_id";
    private static final String KEY = "key";
    private static final String NAME = "name";
    private static final String DISABLED = "disabled";
  }

  public static final class UserApiKeyDecoder implements Decoder<UserApiKey> {
    // TODO: Delete when merging with remote mongodb service PR
    private static void keyPresent(final String key, final Map<String, ?> map) {
      if (!map.containsKey(key)) {
        throw new IllegalStateException(
                String.format("expected %s to be present", key));
      }
    }
    /**
     * Decodes a BSON value from the given reader into an instance of the type parameter {@code T}.
     *
     * @param reader         the BSON reader
     * @param decoderContext the decoder context
     * @return an instance of the type parameter {@code T}.
     */
     @Override
     public UserApiKey decode(BsonReader reader, DecoderContext decoderContext) {
       final Document document = (new DocumentCodec()).decode(reader, decoderContext);
       keyPresent(Fields.ID, document);
       keyPresent(Fields.NAME, document);
       keyPresent(Fields.DISABLED, document);
       return new UserApiKey(
               document.getString(Fields.ID),
               document.getString(Fields.KEY),
               document.getString(Fields.NAME),
               document.getBoolean(Fields.DISABLED)
       );
     }
  }
}
