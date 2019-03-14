// Get the Atlas client.
RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
RemoteMongoDatabase db = mongoClient.getDatabase("video");
RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");

// Find up to 20 documents that match the title regex
movieDetails.find(new Document().append("title", new BsonRegularExpression("Star Wars")))
        .limit(20)
        .forEach(document -> {
    Log.i(TAG, "Found document: " + document.toString());
});
