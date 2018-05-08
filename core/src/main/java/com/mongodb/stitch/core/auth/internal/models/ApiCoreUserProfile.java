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
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;

import java.util.List;
import java.util.Map;

public final class ApiCoreUserProfile extends StitchUserProfileImpl {
  @JsonCreator
  private ApiCoreUserProfile(
      @JsonProperty(Fields.USER_TYPE) final String userType,
      @JsonProperty(Fields.DATA) final Map<String, String> data,
      @JsonProperty(Fields.IDENTITIES) final List<ApiStitchUserIdentity> identities) {
    super(userType, data, identities);
  }

  private static class Fields {
    private static final String DATA = "data";
    private static final String USER_TYPE = "type";
    private static final String IDENTITIES = "identities";
  }
}
