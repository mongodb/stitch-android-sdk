package com.mongodb.stitch.core.internal.net;

public class StitchAuthRequest extends StitchRequest {
  public final boolean useRefreshToken;
  public final boolean shouldRefreshOnFailure;

  public StitchAuthRequest(final StitchAuthRequest request) {
    this(request, request.useRefreshToken, request.shouldRefreshOnFailure);
  }

  public StitchAuthRequest(final StitchRequest request, final boolean useRefreshToken) {
    this(request, useRefreshToken, true);
  }

  public StitchAuthRequest(final StitchRequest request, final boolean useRefreshToken, final boolean shouldRefreshOnFailure) {
    super(request.method, request.path, request.headers, request.body, request.startedAt);
    this.useRefreshToken = useRefreshToken;
    this.shouldRefreshOnFailure = shouldRefreshOnFailure;
  }

  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder extends StitchRequest.Builder {
    private boolean useRefreshToken;
    private boolean shouldRefreshOnFailure = true;

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

    public Builder withShouldRefreshOnFailure(final boolean shouldRefresh) {
      this.shouldRefreshOnFailure = shouldRefresh;
      return this;
    }

    public boolean getUseRefreshToken() {
      return this.useRefreshToken;
    }

    public StitchAuthRequest build() {
      return new StitchAuthRequest(super.build(), useRefreshToken, shouldRefreshOnFailure);
    }
  }
}
