package com.mongodb.stitch.android.examples.chatsync

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.examples.chatsync.model.Channel
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMembers
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.model.ChannelSubscription
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.coroutines.CoroutineContext



abstract class ScopeActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
