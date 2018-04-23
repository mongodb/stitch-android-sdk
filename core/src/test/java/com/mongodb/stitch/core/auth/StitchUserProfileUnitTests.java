package com.mongodb.stitch.core.auth;

import com.mongodb.stitch.core.mock.MockStitchUserIdentity;
import com.mongodb.stitch.core.mock.MockStitchUserProfileImpl;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StitchUserProfileUnitTests {
    private static final String FIRST_NAME = "FIRST_NAME";
    private static final String LAST_NAME = "LAST_NAME";
    private static final String EMAIL = "EMAIL";
    private static final String GENDER = "GENDER";
    private static final String BIRTHDAY = "BIRTHDAY";
    private static final String PICTURE_URL = "PICTURE_URL";
    private static final String MIN_AGE = "42";
    private static final String MAX_AGE = "84";

    public static final Map<String, String> ANON_USER_DATA;
    static {
       final Map<String, String> document = new HashMap<>();

        document.put("first_name", FIRST_NAME);
        document.put("last_name", LAST_NAME);
        document.put("email", EMAIL);
        document.put("gender", GENDER);
        document.put("birthday", BIRTHDAY);
        document.put("picture", PICTURE_URL);
        document.put("min_age", MIN_AGE);
        document.put("max_age", MAX_AGE);

        ANON_USER_DATA = document;
    }

    @Test
    void testStitchUserProfileImplInit() throws Exception {
        final StitchUserIdentity anonIdentity =
                new MockStitchUserIdentity(new ObjectId().toHexString(), "anon-user");

        final StitchUserProfileImpl stitchUserProfileImpl =
                new MockStitchUserProfileImpl(
                        "local-userpass",
                        ANON_USER_DATA,
                        Collections.singletonList(anonIdentity)
                );

        assertEquals(stitchUserProfileImpl.getFirstName(), FIRST_NAME);
        assertEquals(stitchUserProfileImpl.getLastName(), LAST_NAME);
        assertEquals(stitchUserProfileImpl.getEmail(), EMAIL);
        assertEquals(stitchUserProfileImpl.getGender(), GENDER);
        assertEquals(stitchUserProfileImpl.getBirthday(), BIRTHDAY);
        assertEquals(stitchUserProfileImpl.getPictureURL(), PICTURE_URL);
        assertEquals(stitchUserProfileImpl.getMinAge(), new Integer(MIN_AGE));
        assertEquals(stitchUserProfileImpl.getMaxAge(), new Integer(MAX_AGE));
        assertEquals(stitchUserProfileImpl.getUserType(), "local-userpass");
        assertEquals(stitchUserProfileImpl.getIdentities().size(), 1);
        assertEquals(stitchUserProfileImpl.getIdentities().get(0), anonIdentity);
    }
}
