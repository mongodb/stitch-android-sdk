package com.mongodb.stitch.core.auth.internal.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.StitchUserIdentity;

class StoreStitchUserIdentity extends StitchUserIdentity {

  StoreStitchUserIdentity(final StitchUserIdentity identity) {
    super(identity);
  }

  @JsonCreator
  private StoreStitchUserIdentity(
      @JsonProperty(Fields.ID) final String id,
      @JsonProperty(Fields.PROVIDER_TYPE) final String providerType) {
    super(id, providerType);
  }

  @JsonProperty(Fields.ID)
  private String getIdValue() {
    return id;
  }

  @JsonProperty(Fields.PROVIDER_TYPE)
  private String getProviderTypeValue() {
    return providerType;
  }

  private static class Fields {
    private static final String ID = "id";
    private static final String PROVIDER_TYPE = "provider_type";
  }
}
