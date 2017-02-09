package com.mongodb.baas.sdk.auth.anonymous;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnonymousAuthProviderInfo {
    public static final String FQ_NAME = "anon/user";
}
