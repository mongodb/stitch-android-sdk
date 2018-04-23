package com.mongodb.stitch.examples.example1;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.TimingLogger;
import android.view.View;
import android.widget.TextView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchAuth;
import com.mongodb.stitch.android.core.auth.StitchAuthListener;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.core.auth.providers.internal.anonymous.AnonymousAuthProvider;
import com.mongodb.stitch.android.core.auth.providers.internal.anonymous.AnonymousAuthProviderClient;
import com.mongodb.stitch.android.services.mongodb.local.LocalMongoDBService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;

public class MainActivity extends Activity {

    private StitchAppClient mStitchAppClient;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mStitchAppClient = Stitch.getDefaultAppClient();

    final StitchAuth auth = mStitchAppClient.getAuth();
    auth.addAuthListener(new StitchAuthListener() {
      @Override
      public void onAuthEvent(final StitchAuth auth) {
        System.out.println("user is " + auth.getUser());
      }
    });

    final AnonymousAuthProviderClient anonAuth =
        auth.getProviderClient(AnonymousAuthProvider.ClientProvider);

    if (auth.isLoggedIn()) {
      auth.logout()
          .addOnCompleteListener(
              new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                  auth.loginWithCredential(anonAuth.getCredential())
                      .addOnCompleteListener(
                          new OnCompleteListener<StitchUser>() {
                            @Override
                            public void onComplete(@NonNull Task<StitchUser> task) {
                              final StitchUser user = task.getResult();
                              System.out.println("logged in as " + user.getId());
                            }
                          });
                }
              });
    } else {
      auth.loginWithCredential(anonAuth.getCredential())
          .addOnCompleteListener(
              new OnCompleteListener<StitchUser>() {
                @Override
                public void onComplete(@NonNull Task<StitchUser> task) {
                  final StitchUser user = task.getResult();
                  System.out.println("logged in as " + user.getId());
                }
              });
    }

//    final Task<Document> doc = client.callFunction("help", Arrays.asList(1, 2, 3), Document.class);

//    final MongoClient mclient = client.getServiceClient(LocalMongoDBService.ClientProvider);
//    mclient.getDatabase("foo").getCollection("bar").insertOne(new Document("hello", "world"));
//    final Long count = mclient.getDatabase("foo").getCollection("bar").count();
//    System.out.println(count);
//    System.out.println("WOOOOOOOOOOOOOAH");
  }

  public void doInsert(View view) {

    TimingLogger timings = new TimingLogger("MainActivity", "doInsert");

    final StitchAppClient client = Stitch.getDefaultAppClient();
    timings.addSplit("Client retrieved");

    final MongoClient mclient = client.getServiceClient(LocalMongoDBService.ClientProvider);
    timings.addSplit("Local mongodb client retrieved");

    final MongoDatabase fooDatabase = mclient.getDatabase("foo");
    timings.addSplit("Database object created");

    final MongoCollection<Document> barCollection = fooDatabase.getCollection("bar");
    timings.addSplit("Collection object created");

    barCollection.insertOne(new Document("hello", "world"));
    timings.addSplit("First document inserted");

    for(int i = 0; i < 100; ++i) {
        barCollection.insertOne(new Document("hello", "world"));
        timings.addSplit("Document " + i + " inserted");
    }

    final Long count = barCollection.count();
    timings.addSplit("Collection counted");

//    ((TextView) findViewById(R.id.blah)).setText(String.format("%d", count));

    timings.dumpToLog();

    // Do something in response to button click
  }

  public void callFunctionButton(View view) {
      mStitchAppClient.callFunction(
              "returnNumberArg",
              Collections.singletonList(1),
              Integer.class
      ).addOnCompleteListener(new OnCompleteListener<Integer>() {
          @Override
          public void onComplete(@NonNull Task<Integer> task) {
              if(task.isSuccessful()) {
                  Log.i("MainActivity", "Number: " + task.getResult().toString());
              } else {
                  Log.i("MainActivity", "Getting number failed: " + task.getException());
              }
          }
      });

      mStitchAppClient.callFunction(
              "returnArgsAsDocument",
              Arrays.asList(1, 2, 3),
              Document.class
      ).addOnCompleteListener(new OnCompleteListener<Document>() {
          @Override
          public void onComplete(@NonNull Task<Document> task) {
              Log.i("MainActivity", "Document: " + task.getResult().toJson());
          }
      });

      mStitchAppClient.callFunction(
              "returnTodoItem",
              Collections.EMPTY_LIST,
              TodoItem.class
      ).addOnCompleteListener(new OnCompleteListener<TodoItem>() {
          @Override
          public void onComplete(@NonNull Task<TodoItem> task) {
              Log.i(
                      "MainActivity",
                      "Custom Decoded ToDo Item: " + task.getResult().toString()
              );
          }
      });

      mStitchAppClient.callFunction(
              "returnList",
              Collections.EMPTY_LIST,
              List.class
      ).addOnCompleteListener(new OnCompleteListener<List>() {
          @Override
          public void onComplete(@NonNull Task<List> task) {
              Log.i(
                      "MainActivity",
                      "List: " + task.getResult().toString()
              );
          }
      });
  }
}
