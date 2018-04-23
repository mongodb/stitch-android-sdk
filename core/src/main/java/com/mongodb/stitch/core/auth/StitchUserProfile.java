package com.mongodb.stitch.core.auth;

public interface StitchUserProfile {

  String getName();

  String getEmail();

  String getPictureURL();

  String getFirstName();

  String getLastName();

  String getGender();

  String getBirthday();

  Integer getMinAge();

  Integer getMaxAge();
}
