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

package com.mongodb.stitch.core.auth.internal.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.StitchUserIdentity;

class StoreStitchUserIdentity extends StitchUserIdentity {

  StoreStitchUserIdentity(final StitchUserIdentity identity) {
    super(identity);
  }

  @JsonCreator
  private StoreStitchUserIdentity(
      @JsonProperty(Fields.ID) final String id,
      @JsonProperty(Fields.PROVIDER_TYPE) final String providerType) {
    super(id, providerType);
  }

  @JsonProperty(Fields.ID)
  private String getIdValue() {
    return getId();
  }

  @JsonProperty(Fields.PROVIDER_TYPE)
  private String getProviderTypeValue() {
    return getProviderType();
  }

  private static class Fields {
    private static final String ID = "id";
    private static final String PROVIDER_TYPE = "provider_type";
  }
}
