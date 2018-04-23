package com.mongodb.stitch.core.auth.internal.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.StitchUserIdentity;

public class APIStitchUserIdentity extends StitchUserIdentity {
  @SuppressWarnings("unused")
  @JsonCreator
  private APIStitchUserIdentity(
      @JsonProperty(Fields.ID) final String id,
      @JsonProperty(Fields.PROVIDER_TYPE) final String providerType) {
    super(id, providerType);
  }

  private static class Fields {
    private static final String ID = "id";
    private static final String PROVIDER_TYPE = "provider_type";
  }
}
