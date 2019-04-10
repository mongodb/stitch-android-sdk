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

import com.mongodb.client.model.WriteModel;
import com.mongodb.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract structure for collecting writes and applying them at a later time.
 *
 * @param <CollectionT> type of collection to which writes will be applied.
 * @param <DocumentT> type of document domain object.
 */
public abstract class WriteModelContainer<CollectionT, DocumentT> {
  private final CollectionT collection;
  private final List<WriteModel<DocumentT>> bulkWriteModels = new ArrayList<>();

  protected WriteModelContainer(final CollectionT collection) {
    this.collection = collection;
  }

  final void add(@Nullable final WriteModel<DocumentT> write) {
    if (write == null) {
      return;
    }
    this.bulkWriteModels.add(write);
  }

  final void merge(@Nullable final WriteModelContainer<CollectionT, DocumentT> container) {
    if (container == null) {
      return;
    }
    this.bulkWriteModels.addAll(container.bulkWriteModels);
  }

  final boolean commitAndClear() {
    boolean success = this.commit();
    this.bulkWriteModels.clear();
    return success;
  }

  protected CollectionT getCollection() {
    return collection;
  }

  protected List<WriteModel<DocumentT>> getBulkWriteModels() {
    return bulkWriteModels;
  }

  abstract boolean commit();
}
