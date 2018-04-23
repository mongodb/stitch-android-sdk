package com.mongodb.stitch.core.auth;

public final class ProviderCapabilities {
  public final boolean reusesExistingSession;

  public ProviderCapabilities() {
    reusesExistingSession = false;
  }

  public ProviderCapabilities(final boolean reusesExistingSession) {
    this.reusesExistingSession = reusesExistingSession;
  }
}
