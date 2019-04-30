# Stitch Mobile Sync Example - Todo List

## Installation

1. Create an Atlas cluster on [https://www.mongodb.com/cloud/atlas](https://www.mongodb.com/cloud/atlas)
2. Go to the `importexport` directory and edit the `services/mongodb-atlas/config.json` file and set the `clusterName` field to the name of the cluster you created.
3. Use the [Stitch CLI](https://docs.mongodb.com/stitch/import-export/stitch-cli-reference/) to import the app in the `importexport` directory.
4. In the project of your Atlas cluster, go to Stitch Apps and click the app you just imported.
5. On the Stitch App page, take note of the client app id on the Settings page.
6. Go to Users -> Providers -> API Keys and create an API key and copy down the key.
7. Inside `src/main/res/values/strings.xml` and replace the values for `stitch_client_app_id` and `stitch_user1_api_key` with the values you copied down.
8. Open up the `stitch-android-sdk` repo in Android Studio and run the `stitch-android-examples-todo-sync` app in the top right via the "play" button using an emulator or Android device.
