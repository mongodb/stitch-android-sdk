package com.mongodb.stitch.android.examples.stresstests;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.Sync;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;

import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

  private Button addSingleDocButton;
  private Button addManyDocsButton;
  private Button clearDocsButton;

  private EditText etManyDocs;
  private TextView label;

  private StitchAppClient stitchAppClient;
  private RemoteMongoCollection<Document> coll;
  private Sync<Document> syncedColl;

  private String TAG = MainActivity.class.getName();

  private void initializeSync() {
    stitchAppClient.getAuth().loginWithCredential(new AnonymousCredential()).addOnSuccessListener(new OnSuccessListener<StitchUser>() {
      @Override
      public void onSuccess(StitchUser stitchUser) {
        coll = stitchAppClient
            .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
            .getDatabase("stress")
            .getCollection("tests");

        syncedColl = coll.sync();
        syncedColl.configure(
            DefaultSyncConflictResolvers.remoteWins(),
            new ChangeEventListener<Document>() {
              @Override
              public void onEvent(BsonValue documentId, ChangeEvent<Document> event) {
                Log.i(TAG, String.format("Got event for doc %s: %s", documentId, event));
                updateLabels();
              }
            },
            new ErrorListener() {
              @Override
              public void onError(BsonValue documentId, Exception error) {
                Log.e(TAG, String.format("Got sync error for doc %s: %s", documentId, error));
              }
            }
        );
        updateLabels();
      }
    });

  }

  private void updateLabels() {
    label.setText(String.format(
        Locale.US,
        "# of synced docs: %d",
        syncedColl.getSyncedIds().size()
    ));
  }

  private void syncNewDocument() {
    final StitchUser user = stitchAppClient.getAuth().getUser();
    if (user == null) {
      return;
    }
    final Document docToInsert = new Document()
        .append("_id", new ObjectId())
        .append("owner_id", stitchAppClient.getAuth().getUser().getId())
        .append("message", String.format(Locale.US, "%d", (new Random()).nextInt()));

    syncedColl.insertOne(docToInsert).addOnCompleteListener(new OnCompleteListener<SyncInsertOneResult>() {
      @Override
      public void onComplete(@NonNull Task<SyncInsertOneResult> task) {
        updateLabels();
      }
    });
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
          .append("message", String.format(Locale.US, "%d", (new Random()).nextInt()));

      docsToInsert.add(docToInsert);
    }

    syncedColl.insertMany(docsToInsert).addOnCompleteListener(new OnCompleteListener<SyncInsertManyResult>() {
      @Override
      public void onComplete(@NonNull Task<SyncInsertManyResult> task) {
        updateLabels();
      }
    });
  }

  private void clearAllDocuments() {
    final StitchUser user = stitchAppClient.getAuth().getUser();
    if (user == null) {
      return;
    }

    syncedColl.deleteMany(new Document()).addOnCompleteListener(new OnCompleteListener<SyncDeleteResult>() {
      @Override
      public void onComplete(@NonNull Task<SyncDeleteResult> task) {
        updateLabels();
      }
    });
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
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

    addSingleDocButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        syncNewDocument();
      }
    });

    addManyDocsButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        final int numberOfDocsToInsert = Integer.decode(etManyDocs.getText().toString());
        syncManyDocuments(numberOfDocsToInsert);
      }
    });

    clearDocsButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearAllDocuments();
      }
    });
  }
}
