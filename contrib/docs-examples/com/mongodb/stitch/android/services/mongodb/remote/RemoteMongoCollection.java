// Note: log in first -- see StitchAuth
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
