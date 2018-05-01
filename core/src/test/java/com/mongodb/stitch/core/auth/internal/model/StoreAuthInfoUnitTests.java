package com.mongodb.stitch.core.auth.internal.model;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo;
import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;

import org.bson.Document;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StoreAuthInfoUnitTests {
    @Test
    public void testWrite() throws Exception {
        final StitchUserIdentity mockIdentity = new StitchUserIdentity(
                "garply",
                "waldo"
        );

        // TODO: refactor to common function
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

        final StitchUserProfileImpl mockProfile = new StitchUserProfileImpl(
                "grault",
                data,
                Collections.singletonList(mockIdentity)
        );

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
