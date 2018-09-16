package com.mongodb.stitch.android.services.mongodb.sync.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.sync.Sync;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.sync.CoreSync;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteFindIterable;
import com.mongodb.stitch.server.services.mongodb.remote.internal.RemoteFindIterableImpl;

import org.bson.BsonValue;
import org.bson.conversions.Bson;

import java.util.Set;
import java.util.concurrent.Callable;

public class SyncImpl<DocumentT> implements Sync<DocumentT> {
  private final CoreSync<DocumentT> proxy;
  private final TaskDispatcher dispatcher;

  public SyncImpl(final CoreSync<DocumentT> proxy,
                  final TaskDispatcher dispatcher) {
    this.proxy = proxy;
    this.dispatcher = dispatcher;
  }

  @Override
  public void configure(ConflictHandler<DocumentT> conflictResolver,
                        ChangeEventListener<DocumentT> changeEventListener) {
    this.proxy.configure(conflictResolver, changeEventListener);
  }

  @Override
  public void syncOne(final BsonValue id) {
    this.dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        proxy.syncOne(id);
        return null;
      }
    });
  }

  @Override
  public void syncMany(final BsonValue... ids) {
    this.dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        proxy.syncMany(ids);
        return null;
      }
    });
  }

  @Override
  public void desyncOne(final BsonValue id) {
    this.dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        proxy.desyncOne(id);
        return null;
      }
    });
  }

  @Override
  public void desyncMany(final BsonValue... ids) {
    this.dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        proxy.desyncMany(ids);
        return null;
      }
    });
  }

  @Override
  public Set<BsonValue> getSyncedIds() {
    return this.proxy.getSyncedIds();
  }

  @Override
  public RemoteFindIterable<DocumentT> find() {
        return new RemoteFindIterableImpl<>(proxy.find());
  }

  @Override
  public RemoteFindIterable<DocumentT> find(final Bson filter) {
    return new RemoteFindIterableImpl<>(proxy.find(filter));
  }

  @Override
  public <ResultT> RemoteFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return new RemoteFindIterableImpl<>(proxy.find(resultClass));
  }

  @Override
  public <ResultT> RemoteFindIterable<ResultT> find(final Bson filter,
                                                          final Class<ResultT> resultClass) {
    return new RemoteFindIterableImpl<>(proxy.find(filter, resultClass));
  }

  @Override
  public Task<DocumentT> findOneById(final BsonValue documentId) {
    return this.dispatcher.dispatchTask(new Callable<DocumentT>() {
      @Override
      public DocumentT call() throws Exception {
        return proxy.findOneById(documentId);
      }
    });
  }

  @Override
  public <ResultT> Task<ResultT> findOneById(final BsonValue documentId, final Class<ResultT> resultClass) {
    return this.dispatcher.dispatchTask(new Callable<ResultT>() {
      @Override
      public ResultT call() throws Exception {
        return proxy.findOneById(documentId, resultClass);
      }
    });
  }

  @Override
  public Task<RemoteDeleteResult> deleteOneById(final BsonValue documentId) {
    return this.dispatcher.dispatchTask(new Callable<RemoteDeleteResult>() {
      @Override
      public RemoteDeleteResult call() throws Exception {
        return proxy.deleteOneById(documentId);
      }
    });
  }

  @Override
  public RemoteInsertOneResult insertOneAndSync(DocumentT document) {
    return this.proxy.insertOneAndSync(document);
  }

  @Override
  public Task<RemoteUpdateResult> updateOneById(final BsonValue documentId, final Bson update) {
    return this.dispatcher.dispatchTask(new Callable<RemoteUpdateResult>() {
      @Override
      public RemoteUpdateResult call() throws Exception {
        return proxy.updateOneById(documentId, update);
      }
    });
  }
}
