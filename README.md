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
3. In your project tab, expand Gradle Scripts and open settings.gradle
4. Include the baas-sdk module by adding the following lines:
	* The path to the SDK from the BaaS repo is ./clients/android/sdk/sdk
	* Note: This is a temporary requirement until BaaS is in a remote repository

	```
	include ':baas-sdk'
	project(':baas-sdk').projectDir=new File('/Users/user1/go/src/github.com/10gen/baas/clients/android/sdk/sdk')
	```

5. In your build.gradle for your app module, add the following line to your dependencies block:

	```
	compile project(path: ':baas-sdk')
	```

6. Android Studio will prompt you to sync your changes in your project; hit Sync Now

7. Using BaaS requires internet access so add the following to your app's manifests/AndroidManifest.xml within the manifest element:

	```
	<uses-permission android:name="android.permission.INTERNET" />
	```

### Set up an Android Virtual Device

1. In Android Studio, go to Tools, Android, AVD manager
2. Click Create Virtual Device
3. Select a device that should run your app (the default is fine)
4. Select and download a recommended system image of your choie (the latest is fine)
	* x86_64 images are available in the x86 tab
5. Name your device and hit finish

### Using the SDK

#### Logging In
1. To initialize our connection to BaaS, go to your MainAcitvity.java and within your onCreate method, add the following line and replace your-app-id with the app ID you took note of when setting up the application in BaaS:

	```
	final BaasClient _client = new BaasClient(this, "your-app-id");
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
3. Call _init()_ after logging in and run your app. You should see a messages like:

	```
	03-12 20:14:15.601 2592-2592/com.mongodb.baas.myapplication D/baas: number of results: 1
03-12 20:14:15.601 2592-2592/com.mongodb.baas.myapplication D/baas: Hello world!
	```


For more examples of SDK usage, see the todo app example in the examples directory
