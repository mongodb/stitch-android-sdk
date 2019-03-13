// Find 20 documents in a collection
// Note: log in first -- see StitchAuth
RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
RemoteMongoDatabase db = mongoClient.getDatabase("video");
RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");
movieDetails.find().limit(20).forEach(document -> {
    Log.i(TAG, "Got document: " + document.toString());
});
