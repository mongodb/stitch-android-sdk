package com.mongodb.stitch.android.examples.chatsync

import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.examples.chatsync.model.Channel
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMembers
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.model.ChannelSubscription
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import org.bson.codecs.BooleanCodec
import org.bson.codecs.ByteArrayCodec
import org.bson.codecs.DocumentCodecProvider
import org.bson.codecs.IntegerCodec
import org.bson.codecs.LongCodec
import org.bson.codecs.ObjectIdCodec
import org.bson.codecs.StringCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider

// Not as evil as you think.


val stitch: StitchAppClient = Stitch.initializeAppClient("chat-eqvtc")!!

val remoteClient: RemoteMongoClient by lazy {
    stitch.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
}

val defaultRegistry: CodecRegistry by lazy {
    CodecRegistries.fromRegistries(
        CodecRegistries.fromCodecs(
            StringCodec(),
            LongCodec(),
            ObjectIdCodec(),
            IntegerCodec(),
            BooleanCodec(),
            ByteArrayCodec()
        ),
        CodecRegistries.fromProviders(
            DocumentCodecProvider(),
            PojoCodecProvider.builder().register(
                Channel::class.java,
                ChannelMessage::class.java,
                ChannelMembers::class.java,
                ChannelSubscription::class.java,
                User::class.java
            ).build()))
}

val defaultAvatars = arrayOf(
    R.drawable.mind_map_icn,
    R.drawable.mind_map_icn_2,
    R.drawable.mind_map_icn_3,
    R.drawable.mind_map_icn_4,
    R.drawable.mind_map_icn_5,
    R.drawable.mind_map_icn_6,
    R.drawable.mind_map_icn_7
)