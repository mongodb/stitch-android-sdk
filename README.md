# Android SDK

[![Join the chat at https://gitter.im/mongodb/stitch](https://badges.gitter.im/mongodb/stitch.svg)](https://gitter.im/mongodb/stitch?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Creating a new app with the SDK

### Set up an application on Stitch
1. Go to https://stitch.mongodb.com/ and log in
2. Create a new app with your desired name
3. Take note of the app's client App ID by going to Clients under Platform in the side pane
4. Go to Authentication under Control in the side pane and enable "Allow users to log in anonymously"

### Set up a project in Android Studio using Stitch
1. Download and install [Android Studio](https://developer.android.com/studio/index.html)
2. Start a new Android Studio project
	* Note: The minimum supported API level is 19 (Android 4.4 KitKat)
	* Starting with an empty activity is ideal
3. In your build.gradle for your app module, add the following to your dependencies block:

	```
    compile 'org.mongodb:stitch:2.0.0'
    ```
4. Android Studio will prompt you to sync your changes in your project; hit Sync Now

### Set up an Android Virtual Device

1. In Android Studio, go to Tools, Android, AVD manager
2. Click Create Virtual Device
3. Select a device that should run your app (the default is fine)
4. Select and download a recommended system image of your choice (the latest is fine)
	* x86_64 images are available in the x86 tab
5. Name your device and hit finish

### Using the SDK

#### Logging In
1. To initialize our connection to Stitch, go to your **MainActivity.java** and within your *onCreate* method, add the following line and replace your-app-id with the app ID you took note of when setting up the application in Stitch:

	```
	final StitchClient _client = new StitchClient(this, "your-app-id");
	```
	
	* Note: To create a StitchClient using properties, make sure to set the **appId** property in your **stitch.properties** and use the following factory method:

		```
		final StitchClient _client = StitchClient.fromProperties(this);
		```

2. This will only instantiate a client but will not make any outgoing connection to Stitch
3. Since we enabled anonymous log in, let's log in with it; add the following after your new _client:

	```
	 _client.getAuthProviders().addOnSuccessListener(new OnSuccessListener<AvailableAuthProviders>() {
            @Override
            public void onSuccess(final AvailableAuthProviders auth) {
                if (auth.hasAnonymous()) {
                    Log.d("stitch", "logging in anonymously");
                    _client.logInWithProvider(new AnonymousAuthProvider()).addOnCompleteListener(new OnCompleteListener<String>() {
                        @Override
                        public void onComplete(@NonNull final Task<String> task) {
                            if (task.isSuccessful()) {
                                Log.d("stitch", "logged in anonymously as user " + task.getResult());
                            } else {
                                Log.e("stitch", "failed to log in anonymously", task.getException());
                            }
                        }
                    });
                } else {
                    Log.e("stitch", "no anonymous provider");
                }
            }
        });
	```

4. Now run your app in Android Studio by going to run, Run 'app'. Use the Android Virtual Device you created previously
5. Once the app is running, open up the Android Monitor by going to View, Tool Windows, Android Monitor
6. You should see log messages with stitch as a tag showing messages like:

	```
	03-12 19:16:59.003 6175-6175/? D/stitch: logging in anonymously                                                    	
	03-12 19:16:59.103 6175-6175/? D/stitch: logged in anonymously as user 58c5d6ebb9ede022a3d75050
	```

#### Set up Push Notifications (GCM)

##### Set up a GCM provider

1. Create a Firebase Project
2. Click Add Firebase to your Android app
3. Skip downloading the config file
4. Skip adding the Firebase SDK
5. Click the gear next to overview in your Firebase project and go to Project Settings
6. Go to Cloud Messaging and take note of your Legacy server key and Sender ID
7. In Stitch go to the Notifications section and enter in your API Key (legacy server key) and Sender ID

##### Receive Push Notifications in Android

1. In order to listen in on notifications arriving from GCM, we must implement the GCMListenerService
	1. Create a new class called *MyGCMService* in your app's package and use the following code to start with:
	
		```
		package your.app.package.name;
	
		import com.mongodb.stitch.android.push.gcm.GCMListenerService;
		
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
	
2. Once logged in, you can either create a GCM Push Provider by asking Stitch for the provider information or providing it in your **stitch.properties**
3. To create a GCM Push Provider from properties, simply use the provided factory method:

	```
	final PushClient pushClient = _client.getPush().forProvider(GCMPushProviderInfo.fromProperties());
	```
	* Note: This assumed you've set the **push.gcm.senderId** and **push.gcm.service** property in your **stitch.properties**
	
4. To create a GCM Push Provider by asking Stitch, you must use the *getPushProviders* method and ensure a GCM provider exists:

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
