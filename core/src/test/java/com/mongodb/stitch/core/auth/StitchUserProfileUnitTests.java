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

import static org.junit.Assert.assertEquals;

import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.Test;

public class StitchUserProfileUnitTests {

  @Test
  public void testStitchUserProfileImplInit() {
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

    final String providerType = "anon-user"; // TODO: Use constant
    final StitchUserIdentity anonIdentity =
        new StitchUserIdentity(new ObjectId().toHexString(), providerType);

    final StitchUserProfileImpl stitchUserProfileImpl =
        new StitchUserProfileImpl("user", data, Collections.singletonList(anonIdentity));

    assertEquals(stitchUserProfileImpl.getFirstName(), firstName);
    assertEquals(stitchUserProfileImpl.getLastName(), lastName);
    assertEquals(stitchUserProfileImpl.getEmail(), email);
    assertEquals(stitchUserProfileImpl.getGender(), gender);
    assertEquals(stitchUserProfileImpl.getBirthday(), birthday);
    assertEquals(stitchUserProfileImpl.getPictureUrl(), pictureUrl);
    assertEquals(stitchUserProfileImpl.getMinAge(), new Integer(minAge));
    assertEquals(stitchUserProfileImpl.getMaxAge(), new Integer(maxAge));
    assertEquals(stitchUserProfileImpl.getUserType(), "user");
    assertEquals(stitchUserProfileImpl.getIdentities().size(), 1);
    assertEquals(stitchUserProfileImpl.getIdentities().get(0), anonIdentity);
  }
}
