exports = async function(changeEvent) {
  var messageId = changeEvent.documentKey._id;
  var channelId = changeEvent.fullDocument.channelId;
  
  var messagesCollection = context.services.get("mongodb-atlas").db("chats").collection("messages");
  var subscriptionsCollection = context.services.get("mongodb-atlas").db("chats").collection("channel_subscriptions");
  
  await subscriptionsCollection.updateMany({ "channelId" : channelId }, { "$inc": { "remoteTimestamp" : 1 }});
  await messagesCollection.updateOne(
    { "_id" : messageId },
    { "$set": 
      {
        "sentAt" : Date.now(), 
        "remoteTimestamp": (await subscriptionsCollection.findOne({ "channelId" : channelId })).remoteTimestamp
      }
    });
};
