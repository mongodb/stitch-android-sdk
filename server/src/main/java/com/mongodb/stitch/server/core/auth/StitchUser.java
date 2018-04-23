package com.mongodb.stitch.server.core.auth;

import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.CoreStitchUser;

public interface StitchUser extends CoreStitchUser {
  StitchUser linkWithCredential(final StitchCredential credential);
}
