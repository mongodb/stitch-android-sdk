# Android SDK

## Creating a new app with the SDK

### Set up an application on BaaS
1. Go to https://baas-dev.10gen.cc/ and log in
2. Create a new app with your desired name
3. Take note of the app's client App ID by going to Clients under Platform in the side pane
4. Go to Authentication under Control in the side pane and enable "Allow users to log in anonymously"

### Set up a project in Android Studio using BaaS
1. Download and install [Android Studio](https://developer.android.com/studio/index.html)
2. Start a new Android Studio project
	* Note: The minimum supported API level is 19 (Android 4.4 KitKat)
	* Starting with an empty activity is ideal
3. In your build.gradle for your app module, add the following block:
	
	```
	repositories {
		maven {
			url "https://s3.amazonaws.com/baas-sdks/android/maven/snapshots"
		}
	}
	```

4. Also add the following to your dependencies block:

	```
	compile('com.mongodb.baas:android-sdk:0.1.1-SNAPSHOT'){
		changing = true
	}
	```

5. Android Studio will prompt you to sync your changes in your project; hit Sync Now

### Set up an Android Virtual Device

1. In Android Studio, go to Tools, Android, AVD manager
2. Click Create Virtual Device
3. Select a device that should run your app (the default is fine)
4. Select and download a recommended system image of your choice (the latest is fine)
	* x86_64 images are available in the x86 tab
5. Name your device and hit finish

### Using the SDK

#### Logging In
1. To initialize our connection to BaaS, go to your **MainActivity.java** and within your *onCreate* method, add the following line and replace your-app-id with the app ID you took note of when setting up the application in BaaS:

	```
	final BaasClient _client = new BaasClient(this, "your-app-id");
	```
	
	* Note: To create a BaasClient using properties, make sure to set the **appId** property in your **baas.properties** and use the following factory method:

		```
		final BaasClient _client = BaasClient.fromProperties(this);
		```

2. This will only instantiate a client but will not make any outgoing connection to BaaS
3. Since we enabled anonymous log in, let's log in with it; add the following after your new _client:

	```
	_client.getAuthProviders().addOnSuccessListener(new OnSuccessListener<AuthProviderInfo>() {
	            @Override
	            public void onSuccess(final AuthProviderInfo authProviderInfo) {
	                if (authProviderInfo.hasAnonymous()) {
	                    Log.d("baas", "logging in anonymously");
	                    _client.logInWithProvider(new AnonymousAuthProvider()).addOnCompleteListener(new OnCompleteListener<Auth>() {
	                        @Override
	                        public void onComplete(@NonNull final Task<Auth> task) {
	                            if (task.isSuccessful()) {
	                                Log.d("baas", "logged in anonymously as user " + _client.getAuth().getUser().getId());
	                            } else {
	                                Log.e("baas", "failed to log in anonymously", task.getException());
	                            }
	                        }
	                    });
	                } else {
	                    Log.e("baas", "no anonymous provider");
	                }
	            }
	        });
	```

4. Now run your app in Android Studio by going to run, Run 'app'. Use the Android Virtual Device you created previously
5. Once the app is running, open up the Android Monitor by going to View, Tool Windows, Android Monitor
6. You should see log messages with baas as a tag showing messages like:

	```
	03-12 19:16:59.003 6175-6175/? D/baas: logging in anonymously                                                    	
	03-12 19:16:59.103 6175-6175/? D/baas: logged in anonymously as user 58c5d6ebb9ede022a3d75050
	```

#### Running a Pipeline

1. Once logged in, running a pipeline happens via the client's executePipeline method
2. To avoid nesting our tasks any further, after logging in we should call some init method that will use the client. We will also place the client as a member of our activity:

	```
	private BaasClient _client;
	
	private void init() {
        final Map<String, Object> literalArgs = new HashMap<>();
        literalArgs.put("items", Collections.singletonList("Hello world!"));
        _client.executePipeline(new PipelineStage("literal", literalArgs)).addOnSuccessListener(new OnSuccessListener<List<Object>>() {
            @Override
            public void onSuccess(final List<Object> objects) {
                Log.d("baas", "number of results: " + objects.size());
                for (final Object resultItem : objects) {
                    Log.d("baas", resultItem.toString());
                }
            }
        });
    }
	```
3. Call *init()* after logging in and run your app. You should see a messages like:

	```
	03-12 20:14:15.601 2592-2592/com.mongodb.baas.myapplication D/baas: number of results: 1
03-12 20:14:15.601 2592-2592/com.mongodb.baas.myapplication D/baas: Hello world!
	```

#### Set up Push Notifications (GCM)

##### Set up a GCM provider

1. Create a Firebase Project
2. Click Add Firebase to your Android app
3. Skip downloading the config file
4. Skip adding the Firebase SDK
5. Click the gear next to overview in your Firebase project and go to Project Settings
6. Go to Cloud Messaging and take note of your Legacy server key and Sender ID
7. In BaaS go to the Notifications section and enter in your API Key (legacy server key) and Sender ID

##### Receive Push Notifications in Android

1. In order to listen in on notifications arriving from GCM, we must implement the GCMListenerService
	1. Create a new class called *MyGCMService* in your app's package and use the following code to start with:
	
		```
		package your.app.package.name;
	
		import com.mongodb.baas.android.push.gcm.GCMListenerService;
		
		public class GCMService extends GCMListenerService {}
		``` 
	2. The included GCMListenerService contains a method called *onPushMessageReceived* that can be overridden to your liking
	3. Now register the service and a receiver in your **AndroidManifest.xml** to pick up on new messages:

		```
		<receiver
		    android:name="com.google.android.gms.gcm.GcmReceiver"
		    android:exported="true"
		    <intent-filter>
		        <action android:name="com.google.android.c2dm.intent.RECEIVE" />
		        <category android:name="your.app.package.name" />
		    </intent-filter>
		</receiver>
		
		<service
		    android:name=".MyGCMService"
		    android:exported="false" >
		    <intent-filter>
		        <action android:name="com.google.android.c2dm.intent.RECEIVE" />
		    </intent-filter>
		</service>
		```
	4. If you'd like to give the service a chance to process the message before sleeping, add the WAKE_LOCK permission to your manifest:

		```
		<uses-permission android:name="android.permission.WAKE_LOCK" />
		```
	
2. Once logged in, you can either create a GCM Push Provider by asking BaaS for the provider information or providing it in your **baas.properties**
3. To create a GCM Push Provider from properties, simply use the provided factory method:

	```
	final PushClient pushClient = _client.getPush().forProvider(GCMPushProviderInfo.fromProperties());
	```
	* Note: This assumed you've set the **push.gcm.senderId** property in your **baas.properties**
	
4. To create a GCM Push Provider by asking BaaS, you must use the *getPushProviders* method and ensure a GCM provider exists:

	```
	_client.getPushProviders().addOnSuccessListener(new OnSuccessListener<AvailablePushProviders>() {
		@Override
		public void onSuccess(final AvailablePushProviders availablePushProviders) {
			if (!availablePushProviders.hasGCM()) {
				return;
			}
			final PushClient pushClient = _client.getPush().forProvider(availablePushProviders.getGCM());
		}
	});
	```
5. To register for push notifications, use the *register* method:

	```
	_pushClient.register().addOnCompleteListener(new OnCompleteListener<Void>() {
		@Override
		public void onComplete(@NonNull final Task<Void> task) {
			if (!task.isSuccessful()) {
				Log.d(TAG, "Registration failed: " + task.getException());
				return;
			}
			Log.d(TAG, "Registration completed");
		}
	});
	```
6. To deregister from push notifications, use the *deregister* method:

	```
	_pushClient.deregister().addOnCompleteListener(new OnCompleteListener<Void>() {
		@Override
		public void onComplete(@NonNull final Task<Void> task) {
			if (!task.isSuccessful()) {
				Log.d(TAG, "Deregistration failed: " + task.getException());
				return;
			}
			Log.d(TAG, "Deregistration completed");
		}
	});
	```

For more examples of SDK usage, see the todo app example in the examples repository
