/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;

import java.util.List;

import org.bson.conversions.Bson;

/**
 * Write model container that permits committing queued write operations to an instance
 * of {@link CoreRemoteMongoCollection}.
 *
 * @param <DocumentT> type of document domain object
 */
public class CoreRemoteMongoCollectionWriteModelContainer<DocumentT>
    extends WriteModelContainer<CoreRemoteMongoCollection<DocumentT>, DocumentT> {
  public CoreRemoteMongoCollectionWriteModelContainer(
      final CoreRemoteMongoCollection<DocumentT> collection
  ) {
    super(collection);
  }

  /**
   * Commits the writes to the remote collection.
   */
  @Override
  public boolean commit() {
    final CoreRemoteMongoCollection<DocumentT> collection = getCollection();
    final List<WriteModel<DocumentT>> writeModels = getBulkWriteModels();

    // define success as any one operation succeeding for now
    boolean success = true;
    for (final WriteModel<DocumentT> write : writeModels) {
      if (write instanceof ReplaceOneModel) {
        final ReplaceOneModel<DocumentT> replaceModel = ((ReplaceOneModel) write);
        RemoteUpdateResult result =
            collection.updateOne(replaceModel.getFilter(), (Bson) replaceModel.getReplacement());
        success = success &&
            (result != null && result.getModifiedCount() == result.getMatchedCount());
      } else if (write instanceof UpdateOneModel) {
        final UpdateOneModel<DocumentT> updateModel = ((UpdateOneModel) write);
        RemoteUpdateResult result =
            collection.updateOne(updateModel.getFilter(), updateModel.getUpdate());
        success = success &&
            (result != null && result.getModifiedCount() == result.getMatchedCount());
      } else if (write instanceof UpdateManyModel) {
        final UpdateManyModel<DocumentT> updateModel = ((UpdateManyModel) write);
        RemoteUpdateResult result =
            collection.updateMany(updateModel.getFilter(), updateModel.getUpdate());
        success = success &&
            (result != null && result.getModifiedCount() == result.getMatchedCount());
      }
    }
    return success;
  }
}
