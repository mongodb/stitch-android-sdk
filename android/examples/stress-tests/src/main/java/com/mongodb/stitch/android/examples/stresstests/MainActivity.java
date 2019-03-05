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

package com.mongodb.stitch.android.examples.stresstests;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.Sync;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

import org.bson.Document;
import org.bson.types.ObjectId;

public class MainActivity extends AppCompatActivity {

  private Button addSingleDocButton;
  private Button addManyDocsButton;
  private Button clearDocsButton;

  private EditText etManyDocs;
  private TextView label;

  private StitchAppClient stitchAppClient;
  private RemoteMongoCollection<Document> coll;
  private Sync<Document> syncedColl;

  private static final String TAG = MainActivity.class.getName();

  private static final Random RANDOM = new Random();

  private void initializeSync() {
    stitchAppClient.getAuth().loginWithCredential(new AnonymousCredential()).addOnSuccessListener(
        stitchUser -> {
          coll = stitchAppClient
              .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
              .getDatabase("stress")
              .getCollection("tests");

          syncedColl = coll.sync();
          syncedColl.configure(
              DefaultSyncConflictResolvers.remoteWins(),
              (documentId, event) -> {
                Log.i(TAG, String.format("Got event for doc %s: %s", documentId, event));
                updateLabels();
              },
              (documentId, error) -> Log.e(
                  TAG, String.format("Got sync error for doc %s: %s", documentId, error)
              )
          );
          updateLabels();
        });

  }

  private void updateLabels() {
    syncedColl.getSyncedIds().addOnSuccessListener(syncedIds -> label.setText(String.format(
        Locale.US,
        "# of synced docs: %d",
        syncedIds.size()
    )));
  }

  private void syncNewDocument() {
    final StitchUser user = stitchAppClient.getAuth().getUser();
    if (user == null) {
      return;
    }
    final Document docToInsert = new Document()
        .append("_id", new ObjectId())
        .append("owner_id", stitchAppClient.getAuth().getUser().getId())
        .append("message", String.format(Locale.US, "%d", RANDOM.nextInt()));

    syncedColl.insertOne(docToInsert).addOnCompleteListener(task -> updateLabels());
  }

  private void syncManyDocuments(final int numberOfDocs) {
    final StitchUser user = stitchAppClient.getAuth().getUser();
    if (user == null) {
      return;
    }

    final ArrayList<Document> docsToInsert = new ArrayList<>();

    for (int i = 0; i < numberOfDocs; ++i) {
      final Document docToInsert = new Document()
          .append("_id", new ObjectId())
          .append("owner_id", stitchAppClient.getAuth().getUser().getId())
          .append("message", String.format(Locale.US, "%d", RANDOM.nextInt()));

      docsToInsert.add(docToInsert);
    }

    syncedColl.insertMany(docsToInsert).addOnCompleteListener(task -> updateLabels());
  }

  private void clearAllDocuments() {
    final StitchUser user = stitchAppClient.getAuth().getUser();
    if (user == null) {
      return;
    }

    syncedColl.deleteMany(new Document()).addOnCompleteListener(task -> updateLabels());
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (!Stitch.hasAppClient("stitch-tests-js-sdk-jntlj")) {
      Stitch.initializeDefaultAppClient("stitch-tests-js-sdk-jntlj");
    }

    stitchAppClient = Stitch.getDefaultAppClient();
    initializeSync();

    addSingleDocButton = findViewById(R.id.button);
    addManyDocsButton = findViewById(R.id.button3);
    clearDocsButton = findViewById(R.id.button2);
    etManyDocs = findViewById(R.id.numInput);

    label = findViewById(R.id.textView);

    addSingleDocButton.setOnClickListener(v -> syncNewDocument());

    addManyDocsButton.setOnClickListener(v -> {
      final int numberOfDocsToInsert = Integer.decode(etManyDocs.getText().toString());
      syncManyDocuments(numberOfDocsToInsert);
    });

    clearDocsButton.setOnClickListener(v -> clearAllDocuments());
  }
}
