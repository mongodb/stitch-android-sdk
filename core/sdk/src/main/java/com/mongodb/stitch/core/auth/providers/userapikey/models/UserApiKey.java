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
import com.mongodb.stitch.core.auth.providers.userapikey.internal.CoreUserApiKeyAuthProviderClient.ApiKeyFields;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import javax.annotation.Nullable;
import org.bson.types.ObjectId;

/**
 * A struct representing a user API key as returned by the Stitch client API.
 */
public final class UserApiKey {
  private final ObjectId id;
  private final String key;
  private final String name;
  private final Boolean disabled;

  /**
   * Constructs a user API key with the provided parameters.
   * @param id The id of the user API key as an ObjectId hex string.
   * @param key The key itself.
   * @param name The name of the key.
   * @param disabled Whether or not the key is disabled.
   */
  @JsonCreator
  public UserApiKey(
          @JsonProperty(ApiKeyFields.ID) final String id,
          @JsonProperty(ApiKeyFields.KEY) final String key,
          @JsonProperty(ApiKeyFields.NAME) final String name,
          @JsonProperty(ApiKeyFields.DISABLED) final Boolean disabled) {
    this.id = new ObjectId(id);
    this.key = key;
    this.name = name;
    this.disabled = disabled;
  }

  /**
   * Returns the id of this API key.
   *
   * @return the id of this API key.
   */
  @JsonProperty(ApiKeyFields.ID)
  public ObjectId getId() {
    return id;
  }

  /**
   * Returns the key of this API key. This is only returned on the creation request for a key.
   *
   * @return the key of this API key.
   */
  @JsonProperty(ApiKeyFields.KEY)
  @Nullable
  public String getKey() {
    return key;
  }

  /**
   * Returns the name of the API key.
   *
   * @return the name of the API key.
   */
  @JsonProperty(ApiKeyFields.NAME)
  public String getName() {
    return name;
  }

  /**
   * Returns whether or not this API key is disabled for login usage.
   *
   * @return whether or not this API key is disabled for login usage.
   */
  @JsonProperty(ApiKeyFields.DISABLED)
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


}
