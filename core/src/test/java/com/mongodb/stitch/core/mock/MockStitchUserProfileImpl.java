package com.mongodb.stitch.core.mock;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;

import java.util.List;
import java.util.Map;

public class MockStitchUserProfileImpl extends StitchUserProfileImpl{
    public MockStitchUserProfileImpl(String userType,
                                     final Map<String, String> data,
                                     final List<? extends StitchUserIdentity> identities) {
        super(userType, data, identities);
    }
}
