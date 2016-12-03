package com.mongodb.baas.sdk;

public abstract class AuthListener {
    public abstract void onLogin();
    public abstract void onLogout(final String lastProvider);
}
