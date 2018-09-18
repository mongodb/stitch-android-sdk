package com.mongodb.stitch.android.services.mongodb.remote;

import android.support.annotation.Nullable;

import org.bson.conversions.Bson;

public interface SyncFindIterable<ResultT> extends RemoteMongoIterable<ResultT> {
    /**
   * Sets the query filter to apply to the query.
   *
   * @param filter the filter, which may be null.
   * @return this
   */
  SyncFindIterable<ResultT> filter(@Nullable final Bson filter);

  /**
   * Sets the limit to apply.
   *
   * @param limit the limit, which may be 0
   * @return this
   */
  SyncFindIterable<ResultT> limit(final int limit);

  /**
   * Sets a document describing the fields to return for all matching documents.
   *
   * @param projection the project document, which may be null.
   * @return this
   */
  SyncFindIterable<ResultT> projection(@Nullable final Bson projection);

  /**
   * Sets the sort criteria to apply to the query.
   *
   * @param sort the sort criteria, which may be null.
   * @return this
   */
  SyncFindIterable<ResultT> sort(@Nullable final Bson sort);
}
