package com.mongodb.stitch.core.internal.net;

public class StitchAuthRequest extends StitchRequest {
  public final boolean useRefreshToken;

  public StitchAuthRequest(final StitchRequest request, final boolean useRefreshToken) {
    super(request.method, request.path, request.headers, request.body, request.startedAt);
    this.useRefreshToken = useRefreshToken;
  }

  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder extends StitchRequest.Builder {
    private boolean useRefreshToken;

    public Builder() {
      super();
    }

    Builder(final StitchAuthRequest request) {
      super(request);
      useRefreshToken = request.useRefreshToken;
    }

    public Builder withAccessToken() {
      this.useRefreshToken = false;
      return this;
    }

    public Builder withRefreshToken() {
      this.useRefreshToken = true;
      return this;
    }

    public boolean getUseRefreshToken() {
      return this.useRefreshToken;
    }

    public StitchAuthRequest build() {
      return new StitchAuthRequest(super.build(), useRefreshToken);
    }
  }
}
