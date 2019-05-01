exports = async function(userId, deviceId, channelId) {
  var mongo = context.services.get("mongodb-atlas");
  var channelMembersCollection = mongo.db("chats").collection("channel_members");
  var channelSubscriptionsCollection = mongo.db("chats").collection("channel_subscriptions");
  
  
  await channelMembersCollection.updateOne(
    { "_id" : channelId },
    { "$push":
      {
        "members" : userId
      },
      "$set":
      {
        "__stitch_sync_version" : { "spv" : BSON.Int32(1), "id" : "71183e7a-4ead-432b-ab6a-c5faa3a528ef", "v" : BSON.Long(1) }
      }
    },
    { upsert: true }
  );
  
  var remoteTimestamp = await channelSubscriptionsCollection.findOne({ "channelId" : channelId })
  var insertOneResult = await channelSubscriptionsCollection.insertOne(
    { 
      "ownerId" : userId, 
      "deviceId": deviceId, 
      "channelId" : channelId, 
      "localTimestamp" : 0, 
      "remoteTimestamp": remoteTimestamp ? remoteTimestamp.remoteTimestamp : 0 
    }
  );
  
  return insertOneResult.insertedId;
}
