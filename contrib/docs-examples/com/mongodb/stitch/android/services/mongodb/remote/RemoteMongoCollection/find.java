// Get the Atlas client.
RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
RemoteMongoDatabase db = mongoClient.getDatabase("video");
RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");

// Find 20 documents
movieDetails.find().limit(20).forEach(document -> {
    Log.i(TAG, "Found document: " + document.toString());
});
