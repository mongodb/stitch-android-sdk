// Get the default app client. This is automatically initialized by Stitch
// if there is a `stitch_client_app_id` value in the values/strings.xml file.
StitchAppClient appClient = Stitch.getDefaultAppClient();

// Log in anonymously. Some form of login is required before we can read documents.
appClient.getAuth().loginWithCredential(new AnonymousCredential()).addOnCompleteListener(new OnCompleteListener<StitchUser>() {
    @Override
    public void onComplete(@NonNull Task<StitchUser> task) {
        if (!task.isSuccessful()) {
            Log.e(TAG, "Failed to log in!", task.getException());
            return;
        }

        // Get the Atlas client.
        RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
        RemoteMongoDatabase db = mongoClient.getDatabase("video");
        RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");

        // Find 20 documents
        movieDetails.find()
                .projection(new Document().append("title", 1).append("year", 1))
                .limit(20)
                .forEach(document -> {
            // Print documents to the log.
            Log.i(TAG, "Got document: " + document.toString());
        });
    }
});
