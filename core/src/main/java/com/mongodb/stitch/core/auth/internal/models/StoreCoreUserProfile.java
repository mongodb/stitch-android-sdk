package com.mongodb.stitch.core.auth.internal.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class StoreCoreUserProfile extends StitchUserProfileImpl {

  StoreCoreUserProfile(final StitchUserProfileImpl profile) {
    super(profile);
  }

  @JsonCreator
  private StoreCoreUserProfile(
      @JsonProperty(Fields.USER_TYPE) final String userType,
      @JsonProperty(Fields.DATA) final Map<String, String> data,
      @JsonProperty(Fields.IDENTITIES) final List<StoreStitchUserIdentity> identities) {
    super(userType, data, identities);
  }

  @JsonProperty(Fields.USER_TYPE)
  private String getUserTypeValue() {
    return userType;
  }

  @JsonProperty(Fields.DATA)
  private Map<String, String> getData() {
    return data;
  }

  @JsonProperty(Fields.IDENTITIES)
  private List<StoreStitchUserIdentity> getIdentitiesValue() {
    final List<StoreStitchUserIdentity> copy = new ArrayList<>(identities.size());
    for (final StitchUserIdentity identity : identities) {
      copy.add(new StoreStitchUserIdentity(identity));
    }
    return copy;
  }

  private static class Fields {
    private static final String DATA = "data";
    private static final String USER_TYPE = "user_type";
    private static final String IDENTITIES = "identities";
  }
}
