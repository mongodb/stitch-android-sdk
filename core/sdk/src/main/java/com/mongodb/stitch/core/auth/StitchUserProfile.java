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

package com.mongodb.stitch.core.auth;

import javax.annotation.Nullable;

/**
 * Profile information on a Stitch user. The values here are sourced from the providers that
 * a user is linked to and as such, the information here is sparse.
 */
public interface StitchUserProfile {

  /**
   * Returns the name of the user.
   *
   * @return the name of the user.
   */
  @Nullable
  String getName();

  /**
   * Returns the email of the user.
   *
   * @return the email of the user.
   */
  @Nullable
  String getEmail();

  /**
   * Returns the url to a picture of the user.
   *
   * @return the url to a picture of the user.
   */
  @Nullable
  String getPictureUrl();

  /**
   * Returns the first name of the user.
   *
   * @return the first name of the user.
   */
  @Nullable
  String getFirstName();

  /**
   * Returns the last name of the user.
   *
   * @return the last name of the user.
   */
  @Nullable
  String getLastName();

  /**
   * Returns the gender of the user.
   *
   * @return the gender of the user.
   */
  @Nullable
  String getGender();

  /**
   * Returns the birthday of the user.
   *
   * @return the birthday of the user.
   */
  @Nullable
  String getBirthday();

  /**
   * Returns the minmum age of the user.
   *
   * @return the minmum age of the user.
   */
  @Nullable
  String getMinAge();

  /**
   * Returns the maximum age of the user.
   *
   * @return the maximum age of the user.
   */
  @Nullable
  String getMaxAge();
}
