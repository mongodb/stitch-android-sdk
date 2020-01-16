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

import org.bson.Document;

import java.util.Date;
import java.util.List;

public interface CoreStitchUser {
  String getId();

  String getDeviceId();

  String getLoggedInProviderType();

  String getLoggedInProviderName();

  UserType getUserType();

  /**
   * @return The profile information of this user.
   */
  StitchUserProfile getProfile();

  List<? extends StitchUserIdentity> getIdentities();

  Date getLastAuthActivity();

  Document getCustomData();

  boolean isLoggedIn();
}
