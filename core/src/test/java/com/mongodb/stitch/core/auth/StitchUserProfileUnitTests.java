package com.mongodb.stitch.core.auth;

import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;

import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
                new StitchUserProfileImpl(
                        "user",
                        data,
                        Collections.singletonList(anonIdentity)
                );

        assertEquals(stitchUserProfileImpl.getFirstName(), firstName);
        assertEquals(stitchUserProfileImpl.getLastName(), lastName);
        assertEquals(stitchUserProfileImpl.getEmail(), email);
        assertEquals(stitchUserProfileImpl.getGender(), gender);
        assertEquals(stitchUserProfileImpl.getBirthday(), birthday);
        assertEquals(stitchUserProfileImpl.getPictureURL(), pictureUrl);
        assertEquals(stitchUserProfileImpl.getMinAge(), new Integer(minAge));
        assertEquals(stitchUserProfileImpl.getMaxAge(), new Integer(maxAge));
        assertEquals(stitchUserProfileImpl.getUserType(), "user");
        assertEquals(stitchUserProfileImpl.getIdentities().size(), 1);
        assertEquals(stitchUserProfileImpl.getIdentities().get(0), anonIdentity);
    }
}
