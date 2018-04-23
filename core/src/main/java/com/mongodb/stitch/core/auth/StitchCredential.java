package com.mongodb.stitch.core.auth;

import org.bson.Document;

public interface StitchCredential {
  String getProviderName();

  String getProviderType();

  Document getMaterial();

  ProviderCapabilities getProviderCapabilities();
}
