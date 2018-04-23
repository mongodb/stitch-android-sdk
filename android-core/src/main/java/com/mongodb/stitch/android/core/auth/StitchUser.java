package com.mongodb.stitch.android.core.auth;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.CoreStitchUser;

public interface StitchUser extends CoreStitchUser {
  Task<StitchUser> linkWithCredential(final StitchCredential credential);
}
