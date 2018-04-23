package com.mongodb.stitch.core.mock;

import com.mongodb.stitch.core.auth.StitchUserIdentity;

public class MockStitchUserIdentity extends StitchUserIdentity {
    public MockStitchUserIdentity(final String id, final String providerType) {
        super(id, providerType);
    }
}
