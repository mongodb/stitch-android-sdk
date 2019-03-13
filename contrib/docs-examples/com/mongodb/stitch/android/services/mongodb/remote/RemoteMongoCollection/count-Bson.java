// Get the Atlas client.
RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
RemoteMongoDatabase db = mongoClient.getDatabase("video");
RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");

// Count all documents where title matches a regex
movieDetails.count(new Document().append("title", new BsonRegularExpression("Star Wars"))).addOnCompleteListener(new OnCompleteListener<Long>() {
    @Override
    public void onComplete(@android.support.annotation.NonNull Task<Long> task) {
        if (!task.isSuccessful()) {
            Log.e(TAG, "Count failed", task.getException());
            return;
        }
        Log.i(TAG, "Count is " + task.getResult());
    }
});
