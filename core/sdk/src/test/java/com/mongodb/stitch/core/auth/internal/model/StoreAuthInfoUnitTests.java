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

package com.mongodb.stitch.core.auth.internal.model;

import static org.junit.Assert.assertEquals;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.UserType;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;

public class StoreAuthInfoUnitTests {

  @Test
  public void testWrite() throws Exception {
    final StitchUserIdentity identity = new StitchUserIdentity("garply", "waldo");

    final String firstName = "FIRST_NAME";
    final String lastName = "LAST_NAME";
    final String email = "EMAIL";
    final String gender = "GENDER";
    final String birthday = "BIRTHDAY";
    final String pictureUrl = "PICTURE_URL";
    final String minAge = "42";
    final String maxAge = "84";

    final Map<String, String> data = new HashMap<>();
    data.put("first_name", firstName);
    data.put("last_name", lastName);
    data.put("email", email);
    data.put("gender", gender);
    data.put("birthday", birthday);
    data.put("picture", pictureUrl);
    data.put("min_age", minAge);
    data.put("max_age", maxAge);

    final Date currentDate = new Date();

    final StitchUserProfileImpl mockProfile =
        new StitchUserProfileImpl(UserType.SERVER, data, Collections.singletonList(identity));

    final StoreAuthInfo storeAuthInfo =
        new StoreAuthInfo("foo", "bar", "baz", "qux", "quux", "corge", mockProfile, currentDate);

    final String rawInfo = StitchObjectMapper.getInstance().writeValueAsString(storeAuthInfo);

    final Document doc = BsonUtils.parseValue(rawInfo, Document.class);

    assertEquals(doc.get("user_id"), "foo");
    assertEquals(doc.get("device_id"), "bar");
    assertEquals(doc.get("access_token"), "baz");
    assertEquals(doc.get("refresh_token"), "qux");
    assertEquals(doc.get("last_auth_activity"), currentDate.getTime());

    assertEquals(doc.get("logged_in_provider_type"), "quux");
    assertEquals(doc.get("logged_in_provider_name"), "corge");

    final Document userProfileDoc = (Document) doc.get("user_profile");
    assertEquals(
        mockProfile.getUserType(), UserType.fromName(userProfileDoc.getString("user_type")));
    final StitchUserIdentity identity0 = mockProfile.getIdentities().get(0);
    final Document identity1 = (Document) userProfileDoc.get("identities", List.class).get(0);
    assertEquals(identity0.getId(), identity1.get("id"));
    assertEquals(identity0.getProviderType(), identity1.get("provider_type"));

    final Document dataDoc = (Document) userProfileDoc.get("data");
    assertEquals(dataDoc.get("first_name"), mockProfile.getFirstName());
    assertEquals(dataDoc.get("last_name"), mockProfile.getLastName());
    assertEquals(dataDoc.get("min_age"), mockProfile.getMinAge());
    assertEquals(dataDoc.get("max_age"), mockProfile.getMaxAge());
    assertEquals(dataDoc.get("birthday"), mockProfile.getBirthday());
    assertEquals(dataDoc.get("gender"), mockProfile.getGender());
    assertEquals(dataDoc.get("picture"), mockProfile.getPictureUrl());
  }
}
