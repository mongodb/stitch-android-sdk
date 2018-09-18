package com.mongodb.stitch.server.services.mongodb.remote.internal;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;
import com.mongodb.stitch.server.services.mongodb.remote.Sync;
import com.mongodb.stitch.server.services.mongodb.remote.SyncFindIterable;

import org.bson.BsonValue;
import org.bson.conversions.Bson;

import java.util.Set;

public class SyncImpl<DocumentT> implements Sync<DocumentT> {
  private final CoreSync<DocumentT> proxy;

  SyncImpl(final CoreSync<DocumentT> proxy) {
    this.proxy = proxy;
  }

  @Override
  public void configure(final ConflictHandler<DocumentT> conflictResolver,
                        final ChangeEventListener<DocumentT> changeEventListener,
                        ErrorListener errorListener) {
    this.proxy.configure(conflictResolver, changeEventListener, errorListener);
  }

  @Override
  public void syncOne(final BsonValue id) {
    proxy.syncOne(id);
  }

  @Override
  public void syncMany(final BsonValue... ids) {
    proxy.syncMany(ids);
  }

  @Override
  public void desyncOne(final BsonValue id) {
    proxy.desyncOne(id);
  }

  @Override
  public void desyncMany(final BsonValue... ids) {
    proxy.desyncMany(ids);
  }

  @Override
  public Set<BsonValue> getSyncedIds() {
    return this.proxy.getSyncedIds();
  }

  @Override
  public SyncFindIterable<DocumentT> find() {
      return new SyncFindIterableImpl<>(proxy.find());
  }

  @Override
  public SyncFindIterable<DocumentT> find(final Bson filter) {
    return new SyncFindIterableImpl<>(proxy.find(filter));
  }

  @Override
  public <ResultT> SyncFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return new SyncFindIterableImpl<>(proxy.find(resultClass));
  }

  @Override
  public <ResultT> SyncFindIterable<ResultT> find(final Bson filter,
                                                    final Class<ResultT> resultClass) {
    return new SyncFindIterableImpl<>(proxy.find(filter, resultClass));
  }

  @Override
  public DocumentT findOneById(final BsonValue documentId) {
    return proxy.findOneById(documentId);
  }

  @Override
  public <ResultT> ResultT findOneById(final BsonValue documentId, final Class<ResultT> resultClass) {
    return proxy.findOneById(documentId, resultClass);
  }

  @Override
  public RemoteDeleteResult deleteOneById(final BsonValue documentId) {
    return proxy.deleteOneById(documentId);
  }

  @Override
  public RemoteInsertOneResult insertOneAndSync(DocumentT document) {
    return this.proxy.insertOneAndSync(document);
  }

  @Override
  public RemoteUpdateResult updateOneById(final BsonValue documentId, final Bson update) {
    return proxy.updateOneById(documentId, update);
  }
}
