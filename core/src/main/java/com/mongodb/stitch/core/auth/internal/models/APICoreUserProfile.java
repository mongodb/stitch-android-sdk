package com.mongodb.stitch.core.auth.internal.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import java.util.List;
import java.util.Map;

public class APICoreUserProfile extends StitchUserProfileImpl {
  @JsonCreator
  private APICoreUserProfile(
      @JsonProperty(Fields.USER_TYPE) final String userType,
      @JsonProperty(Fields.DATA) final Map<String, String> data,
      @JsonProperty(Fields.IDENTITIES) final List<APIStitchUserIdentity> identities) {
    super(userType, data, identities);
  }

  private static class Fields {
    private static final String DATA = "data";
    private static final String USER_TYPE = "type";
    private static final String IDENTITIES = "identities";
  }
}
