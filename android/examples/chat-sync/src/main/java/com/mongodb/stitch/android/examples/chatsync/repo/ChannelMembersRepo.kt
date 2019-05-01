package com.mongodb.stitch.android.examples.chatsync.repo

import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMembers
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import org.bson.BsonString

object ChannelMembersRepo : SyncRepo<ChannelMembers, String>(5) {
    override val collection: RemoteMongoCollection<ChannelMembers> by lazy {
        remoteClient
            .getDatabase("chats")
            .getCollection("channel_members", ChannelMembers::class.java)
            .withCodecRegistry(defaultRegistry)
    }

    override fun idToBson(id: String) = BsonString(id)
}