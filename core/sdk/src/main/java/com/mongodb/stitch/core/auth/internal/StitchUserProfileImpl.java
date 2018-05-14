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

package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.StitchUserProfile;
import com.mongodb.stitch.core.auth.UserType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StitchUserProfileImpl implements StitchUserProfile {
  private final UserType userType;
  private final Map<String, String> data;
  private final List<? extends StitchUserIdentity> identities;

  protected StitchUserProfileImpl(final StitchUserProfileImpl profile) {
    this.userType = profile.userType;
    this.data = profile.data;
    this.identities = profile.identities;
  }

  /**
   * Constructs a user profile.
   *
   * @param userType The type of the user.
   * @param data The profile data of the user.
   * @param identities The identities associated with a user.
   */
  public StitchUserProfileImpl(
      final UserType userType,
      final Map<String, String> data,
      final List<? extends StitchUserIdentity> identities) {
    this.userType = userType;
    this.data = data;
    this.identities = identities;
  }

  public static StitchUserProfileImpl empty() {
    return new StitchUserProfileImpl(
        null, new HashMap<String, String>(), new ArrayList<StitchUserIdentity>());
  }

  public UserType getUserType() {
    return userType;
  }

  public String getName() {
    return data.get(DataFields.NAME);
  }

  public String getEmail() {
    return data.get(DataFields.EMAIL);
  }

  public String getPictureUrl() {
    return data.get(DataFields.PICTURE_URL);
  }

  public String getFirstName() {
    return data.get(DataFields.FIRST_NAME);
  }

  public String getLastName() {
    return data.get(DataFields.LAST_NAME);
  }

  public String getGender() {
    return data.get(DataFields.GENDER);
  }

  public String getBirthday() {
    return data.get(DataFields.BIRTHDAY);
  }

  /**
   * Get the minimum age of this user; may be null.
   */
  public Integer getMinAge() {
    final String age = data.get(DataFields.MIN_AGE);
    if (age == null) {
      return null;
    }
    return Integer.parseInt(age);
  }

  /**
   * Get the maximum age of this user; may be null.
   */
  public Integer getMaxAge() {
    final String age = data.get(DataFields.MAX_AGE);
    if (age == null) {
      return null;
    }
    return Integer.parseInt(age);
  }

  public List<? extends StitchUserIdentity> getIdentities() {
    return identities;
  }

  public Map<String, String> getData() {
    return data;
  }

  private static class DataFields {
    private static final String NAME = "name";
    private static final String EMAIL = "email";
    private static final String PICTURE_URL = "picture";
    private static final String FIRST_NAME = "first_name";
    private static final String LAST_NAME = "last_name";
    private static final String GENDER = "gender";
    private static final String BIRTHDAY = "birthday";
    private static final String MIN_AGE = "min_age";
    private static final String MAX_AGE = "max_age";
  }
}
