package com.mongodb.stitch.core.auth.internal.model;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.StitchUserProfileUnitTests;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo;
import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.mock.MockStitchUserIdentity;
import com.mongodb.stitch.core.mock.MockStitchUserProfileImpl;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoreAuthInfoUnitTests {
    private static final StitchUserIdentity mockIdentity = new MockStitchUserIdentity(
            "garply",
            "waldo"
    );

    private static final StitchUserProfileImpl mockProfile = new MockStitchUserProfileImpl(
            "grault",
            StitchUserProfileUnitTests.ANON_USER_DATA,
            Collections.singletonList(mockIdentity)
    );

    @Test
    void testWrite() throws Exception {
        StoreAuthInfo storeAuthInfo = new StoreAuthInfo(
                "foo", "bar", "baz", "qux", "quux", "corge", mockProfile
        );


        final String rawInfo = StitchObjectMapper.getInstance().writeValueAsString(storeAuthInfo);

        Document doc = BSONUtils.parseValue(rawInfo, Document.class);

        assertEquals(doc.get("user_id"), "foo");
        assertEquals(doc.get("device_id"), "bar");
        assertEquals(doc.get("access_token"), "baz");
        assertEquals(doc.get("refresh_token"), "qux");

        assertEquals(doc.get("logged_in_provider_type"), "quux");
        assertEquals(doc.get("logged_in_provider_name"), "corge");

        Document userProfileDoc = (Document) doc.get("user_profile");
        assertEquals(mockProfile.getUserType(), userProfileDoc.get("user_type"));
        StitchUserIdentity identity0 = mockProfile.getIdentities().get(0);
        Document identity1 = (Document) userProfileDoc.get("identities", List.class).get(0);
        assertEquals(identity0.getId(), identity1.get("id"));
        assertEquals(identity0.getProviderType(), identity1.get("provider_type"));

        Document dataDoc = (Document) userProfileDoc.get("data");
        assertEquals(dataDoc.get("first_name"), mockProfile.getFirstName());
        assertEquals(dataDoc.get("last_name"), mockProfile.getLastName());
        assertEquals(new Integer((String) dataDoc.get("min_age")), mockProfile.getMinAge());
        assertEquals(new Integer((String) dataDoc.get("max_age")), mockProfile.getMaxAge());
        assertEquals(dataDoc.get("birthday"), mockProfile.getBirthday());
        assertEquals(dataDoc.get("gender"), mockProfile.getGender());
        assertEquals(dataDoc.get("picture"), mockProfile.getPictureURL());
    }
}
