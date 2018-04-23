package com.mongodb.stitch.core.internal.common;

public interface CallbackAsyncAdapter<T, U, V> extends Callback<T, U>, AsyncAdapter<V> {}
