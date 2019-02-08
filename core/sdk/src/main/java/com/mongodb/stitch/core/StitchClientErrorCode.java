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

package com.mongodb.stitch.core;

/**
 * StitchClientErrorCode represents the errors that can occur when using the Stitch client,
 * typically before a request is made to the Stitch server.
 */
public enum StitchClientErrorCode {
  LOGGED_OUT_DURING_REQUEST("logged out while making a request to Stitch"),
  MUST_AUTHENTICATE_FIRST("method called requires being authenticated"),
  USER_NO_LONGER_VALID(
      "user instance being accessed is no longer valid; please get a new user with auth.getUser()"),
  USER_NOT_FOUND("user not found in list of users"),
  USER_NOT_LOGGED_IN(
      "cannot make the active user a logged out user; please use loginWithCredential() to "
       + " switch to this user"),
  COULD_NOT_LOAD_PERSISTED_AUTH_INFO("failed to load stored auth information for Stitch"),
  COULD_NOT_PERSIST_AUTH_INFO("failed to save auth information for Stitch"),
  COULD_NOT_LOAD_DATA_SYNCHRONIZER("failed to load data synchronizer for Stitch");

  private final String description;

  StitchClientErrorCode(final String description) {
    this.description = description;
  }

  @Override
  public String toString() {
    return description;
  }
}
