package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.StitchUserProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StitchUserProfileImpl implements StitchUserProfile {
  protected final String userType;
  protected final Map<String, String> data;
  protected final List<? extends StitchUserIdentity> identities;

  protected StitchUserProfileImpl(final StitchUserProfileImpl profile) {
    this.userType = profile.userType;
    this.data = profile.data;
    this.identities = profile.identities;
  }

  public StitchUserProfileImpl(
      final String userType,
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

  public String getUserType() {
    return userType;
  }

  public String getName() {
    return data.get(DataFields.NAME);
  }

  public String getEmail() {
    return data.get(DataFields.EMAIL);
  }

  public String getPictureURL() {
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

  public Integer getMinAge() {
    final String age = data.get(DataFields.MIN_AGE);
    if (age == null) {
      return null;
    }
    return Integer.parseInt(age);
  }

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
