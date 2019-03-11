// Update one document and handle the result.
// 
Document filterDoc = new Document("name", "board game");
Document updateDoc = new Document("$inc", new Document("quantity", 5));
RemoteUpdateOptions options = new RemoteUpdateOptions().upsert(true);

final Task <RemoteUpdateResult> updateTask =
  itemsCollection.updateOne(filterDoc, updateDoc, options);

updateTask.addOnCompleteListener(new OnCompleteListener <RemoteUpdateResult> () {
    @Override
    public void onComplete(@NonNull Task <RemoteUpdateResult> task) {
        if (task.isSuccessful()) {
            if (task.getResult().getUpsertedId() != null) {
                String upsertedId = task.getResult().getUpsertedId().toString();
                Log.d("app", String.format("successfully upserted document with id: %s",
                  upsertedId));
            } else {
                long numMatched = task.getResult().getMatchedCount();
                long numModified = task.getResult().getModifiedCount();
                Log.d("app", String.format("successfully matched %d and modified %d documents",
                      numMatched, numModified));
            }
        } else {
            Log.e("app", "failed to update document with: ", task.getException());
        }
    }
});
