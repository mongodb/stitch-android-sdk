package com.mongodb.stitch.android.examples.chatsync.service

import android.app.Service
import android.arch.lifecycle.LifecycleObserver
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.mongodb.stitch.android.examples.chatsync.model.Channel
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMembers
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.model.ChannelSubscription
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.android.examples.chatsync.user
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bson.BsonInt64
import org.bson.BsonObjectId
import org.bson.BsonValue
import kotlin.coroutines.CoroutineContext

private typealias ChannelId = String

class ChannelService : Service(), CoroutineScope, LifecycleObserver {
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val mMessengers: MutableList<Messenger> = mutableListOf()

    private val mChannelClients = mutableMapOf<ChannelId, Messenger>()

    private inner class ChannelListener : ChangeEventListener<Channel> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<Channel>) {
            Log.d("ChannelListener", "onEvent: ${event.toBsonDocument()}")
        }
    }

    private inner class UserListener : ChangeEventListener<User> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<User>) {
            Log.w("UserListener",
                "onEvent: ${event.toBsonDocument()} fullDocument: ${event.fullDocument}")

            when (event.operationType) {
                OperationType.REPLACE, OperationType.UPDATE -> {
                    val user = checkNotNull(event.fullDocument)
                    user.channelsSubscribedTo.forEach {
                        mChannelClients[it]?.send(ChannelServiceAction.UserUpdated(user))
                    }
                }
                else -> {}
            }
        }
    }

    private inner class ChannelMessageListener : ChangeEventListener<ChannelMessage> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<ChannelMessage>) {
            Log.w("ChannelMessageListener",
                "onEvent: ${event.toBsonDocument()} fullDocument: ${event.fullDocument}")
            if (event.hasUncommittedWrites()) {
                return
            }

            when (event.operationType) {
                OperationType.REPLACE -> {
                    val message = checkNotNull(event.fullDocument)
                    val messageId = message.id.toHexString()
                    mChannelClients[message.channelId]?.send(
                        ChannelServiceAction.NewMessageReply(messageId, message))
                }
                else -> {}
            }
        }
    }

    private inner class ChannelSubscriptionListener :
        ConflictHandler<ChannelSubscription>, ChangeEventListener<ChannelSubscription> {

        override fun resolveConflict(documentId: BsonValue,
                                     localEvent: ChangeEvent<ChannelSubscription>,
                                     remoteEvent: ChangeEvent<ChannelSubscription>): ChannelSubscription? {
            Log.w(
                "ChannelSubscriptionListener",
                "Received conflict: ${localEvent.toBsonDocument()} " +
                    "and ${remoteEvent.toBsonDocument()}")

            if (remoteEvent.operationType == OperationType.DELETE) {
                return null
            }

            if (remoteEvent.operationType != OperationType.UPDATE ||
                localEvent.operationType != OperationType.UPDATE) {
                return remoteEvent.fullDocument
            }

            val remoteTimestamp =
                remoteEvent.updateDescription!!
                    .updatedFields[ChannelSubscription.KEY_REMOTE_TIMESTAMP]!! as BsonInt64

            val localTimestamp =
                localEvent.updateDescription!!
                    .updatedFields[ChannelSubscription.KEY_LOCAL_TIMESTAMP]!! as BsonInt64

            val channelId = localEvent.fullDocument!!.channelId

            launch(IO) {
                val messageIds = ChannelMessage.fetchMessageIdsFromVector(
                    channelId,
                    localTimestamp.value,
                    remoteTimestamp.value
                )
                ChannelMessage.syncMessages(*messageIds)
            }.start()

            return ChannelSubscription(
                channelId,
                user.id,
                stitch.auth.user!!.deviceId,
                remoteTimestamp.value,
                remoteTimestamp.value)
        }

        override fun onEvent(documentId: BsonValue, event: ChangeEvent<ChannelSubscription>) {
            Log.w("ChannelSubscriptionListener",
                "onEvent: ${event.toBsonDocument()} fullDocument: ${event.fullDocument}")

            if (event.hasUncommittedWrites() || event.operationType != OperationType.REPLACE) {
                return
            }

            synchronized(this) {
                launch(IO) {
                    val subscriptionId =
                        (documentId as BsonObjectId).asObjectId().value.toHexString()
                    val remoteSubscription = event.fullDocument
                    val localSubscription = ChannelSubscription
                        .getLocalChannelSubscription(subscriptionId)

                    checkNotNull(remoteSubscription)
                    localSubscription ?: return@launch

                    if (localSubscription.localTimestamp == remoteSubscription.remoteTimestamp) {
                        return@launch
                    }

                    Log.d("ChannelSubscriptionListener",
                        "Local subscription differs from remote subscription: " +
                            "local: $localSubscription remote: $remoteSubscription")

                    val messageIds = ChannelMessage.fetchMessageIdsFromVector(
                        localSubscription.channelId,
                        localSubscription.localTimestamp,
                        remoteSubscription.remoteTimestamp
                    )
                    ChannelMessage.syncMessages(*messageIds)

                    ChannelSubscription.setChannelSubscriptionLocalVector(
                        subscriptionId, remoteSubscription.remoteTimestamp)
                }
            }
        }
    }

    private inner class ChannelMembersListener :
        ConflictHandler<ChannelMembers>, ChangeEventListener<ChannelMembers> {

        override fun resolveConflict(documentId: BsonValue,
                                     localEvent: ChangeEvent<ChannelMembers>,
                                     remoteEvent: ChangeEvent<ChannelMembers>): ChannelMembers? {
            Log.w("ChannelMembersListener", "Conflict!")
            if (remoteEvent.operationType == OperationType.DELETE) {
                return null
            }

            val localChannelMembers = localEvent.fullDocument!!
            val remoteChannelMembers = remoteEvent.fullDocument!!

            return ChannelMembers(
                remoteChannelMembers.id,
                remoteChannelMembers.members.union(localChannelMembers.members).toList())
        }

        override fun onEvent(documentId: BsonValue, event: ChangeEvent<ChannelMembers>) {
            Log.w("ChannelMembersListener",
                "onEvent: ${event.toBsonDocument()} fullDocument: ${event.fullDocument}")
            if (event.hasUncommittedWrites()) {
                return
            }

            when (event.operationType) {
                OperationType.REPLACE -> {
                    val members = checkNotNull(event.fullDocument).members
                    launch(IO) { User.sync(*members.toTypedArray()) }
                }
                else -> {}
            }
        }
    }

    /**
     * Handler of incoming channelMessages from clients.
     */
    @SuppressWarnings("HandlerLeak")
    inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            runBlocking(IO) {
                Log.d("ChannelService", "Received new message: ${msg.asChannelServiceAction()}")
                when (val action = msg.asChannelServiceAction()) {
                    is ChannelServiceAction.SubscribeToChannel -> {
                        val (channelId, replyTo) = action

                        mChannelClients[channelId] = replyTo

                        val channel = checkNotNull(Channel.findLocalChannel(channelId) ?:
                            Channel.findRemoteChannel(channelId))

                        Channel.sync(channelId)
                        ChannelMembers.sync(channelId)

                        val subscriptionId = ChannelSubscription.getLocalChannelSubscriptionId(
                            User.getCurrentUser().id, stitch.auth.user!!.deviceId, channelId
                        )?.toHexString() ?: Channel.subscribeToChannel(
                            User.getCurrentUser().id, stitch.auth.user!!.deviceId, channelId
                        ).toHexString()

                        msg.replyTo.send(
                            ChannelServiceAction.SubscribeToChannelReply(channel, subscriptionId))
                    }
                    is ChannelServiceAction.UnsubscribeToChannel -> {
                        mChannelClients.remove(action.channelId)
                    }
                    is ChannelServiceAction.SendMessage -> {
                        val (channelId, content) = action

                        val message = ChannelMessage.sendMessage(channelId, content)

                        mChannelClients[channelId]?.send(
                            ChannelServiceAction.SendMessageReply(message)
                        ) ?: throw Error("Not subscribed to channel")
                    }
                    is ChannelServiceAction.SetAvatar -> {
                        User.setAvatar(action.avatar)
                    }
                    else -> super.handleMessage(msg)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        val messenger = Messenger(IncomingHandler())
        mMessengers.add(messenger)
        return messenger.binder
    }

    override fun onCreate() {
        super.onCreate()
        job = Job()

        launch(IO) {
            Channel.configure(ChannelListener())
            ChannelSubscription.configure(ChannelSubscriptionListener())
            ChannelMembers.configure(ChannelMembersListener())
            ChannelMessage.configure(ChannelMessageListener())
            User.configure(UserListener())
            Log.d("ChannelService", "all configures called")
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("ChannelService", "Destroying service")
        job.cancel()
    }
}
