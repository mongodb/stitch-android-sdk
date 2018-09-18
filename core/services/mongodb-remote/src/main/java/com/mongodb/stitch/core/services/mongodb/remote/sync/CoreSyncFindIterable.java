package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoIterable;

import org.bson.conversions.Bson;

import javax.annotation.Nullable;

public interface CoreSyncFindIterable<ResultT> extends CoreRemoteMongoIterable<ResultT> {

  /**
   * Sets the query filter to apply to the query.
   *
   * @param filter the filter, which may be null.
   * @return this
   */
  CoreSyncFindIterable<ResultT> filter(@Nullable final Bson filter);

  /**
   * Sets the limit to apply.
   *
   * @param limit the limit, which may be 0
   * @return this
   */
  CoreSyncFindIterable<ResultT> limit(final int limit);

  /**
   * Sets a document describing the fields to return for all matching documents.
   *
   * @param projection the project document, which may be null.
   * @return this
   */
  CoreSyncFindIterable<ResultT> projection(@Nullable final Bson projection);

  /**
   * Sets the sort criteria to apply to the query.
   *
   * @param sort the sort criteria, which may be null.
   * @return this
   */
  CoreSyncFindIterable<ResultT> sort(@Nullable final Bson sort);
}
