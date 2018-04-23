package com.mongodb.stitch.core;

import org.bson.codecs.configuration.CodecRegistry;

public final class StitchAppClientInfo {
  public final String clientAppId;
  public final String dataDirectory;
  public final String localAppName;
  public final String localAppVersion;
  public final CodecRegistry configuredCodecRegistry;

  public StitchAppClientInfo(
      final String clientAppId,
      final String dataDirectory,
      final String localAppName,
      final String localAppVersion,
      final CodecRegistry configuredCodecRegistry) {
    this.clientAppId = clientAppId;
    this.dataDirectory = dataDirectory;
    this.localAppName = localAppName;
    this.localAppVersion = localAppVersion;
    this.configuredCodecRegistry = configuredCodecRegistry;
  }
}
