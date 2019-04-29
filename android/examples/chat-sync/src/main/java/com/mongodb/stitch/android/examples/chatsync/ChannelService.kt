package com.mongodb.stitch.android.examples.chatsync

import android.app.Service
import android.arch.lifecycle.LifecycleObserver
import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.withContext
import org.bson.BsonInt64
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.BsonValue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private typealias ChannelId = String
private typealias MessageId = String

class ChannelService : Service(), CoroutineScope, LifecycleObserver {
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    companion object {
        /* Channel Actions */
        const val MSG_CHANNEL_SUBSCRIBE = 1
        private fun getMessageForActionSubscribeToChannel(
            channel: Channel
        ): Message {
            val msg = Message()
            msg.what = MSG_CHANNEL_SUBSCRIBE
            msg.data = Bundle()
            msg.data.putParcelable(BUNDLE_CHANNEL, channel)
            return msg
        }

        const val MSG_CHANNEL_UNSUBSCRIBE = 2

        /* ChannelSubscription Actions */
        const val MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE = 4
        private fun getMessageForActionSubscribeToSubscription(
            channelSubscriptionId: String
        ): Message {
            val msg = Message()
            msg.what = MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE
            msg.data = Bundle()
            msg.data.putString(
                BUNDLE_CHANNEL_SUBSCRIPTION_ID,
                channelSubscriptionId)
            return msg
        }

        const val MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE_TO_MESSAGE_ID = 12
        private fun getMessageForActionSubscribeToMessageId(
            messageId: String
        ): Message {
            val msg = Message()
            msg.what = MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE_TO_MESSAGE_ID
            msg.data = Bundle()
            msg.data.putString(BUNDLE_CHANNEL_MESSAGE_ID, messageId)
            return msg
        }

        const val MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE_TO_MESSAGE_IDS = 5
        private suspend fun getMessageForActionSubscribeToMessageIds(
            channelId: String,
            localTimestamp: Long,
            remoteTimestamp: Long
        ): Message = withContext(coroutineContext) {
            val msg = Message()
            msg.what = MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE_TO_MESSAGE_IDS
            msg.data = Bundle()
            msg.data.putStringArray(
                BUNDLE_CHANNEL_MESSAGE_IDS,
                ChannelMessage.fetchMessageIdsFromVector(
                    channelId,
                    localTimestamp,
                    remoteTimestamp))
            msg
        }

        const val MSG_CHANNEL_MESSAGE_SYNC_MESSAGE_IDS = 6

        /* ChannelMembers Actions */
        const val MSG_CHANNEL_MEMBER_REFRESH_MEMBERS = 7

        /* ChannelMessage Actions */
        const val MSG_CHANNEL_MESSAGE_INSERT = 8
        const val MSG_CHANNEL_MESSAGE_UPDATE = 9
        const val MSG_CHANNEL_MESSAGE_SUBSCRIBE = 10
        const val MSG_CHANNEL_MESSAGE_UNSUBSCRIBE = 11

        const val BUNDLE_CHANNEL = "__channel__"
        const val BUNDLE_CHANNEL_ID = "__channel_id__"

        const val BUNDLE_CHANNEL_MESSAGE_ID = "__channel_message_id__"
        const val BUNDLE_CHANNEL_MESSAGE_IDS = "__channel_message_ids__"
        const val BUNDLE_CHANNEL_MESSAGE_CONTENT = "__channel_message_content__"
        const val BUNDLE_CHANNEL_MESSAGE_NEW_MESSAGE = "__channel_message_new_message__"

        const val BUNDLE_CHANNEL_SUBSCRIPTION_ID = "__channel_subscription_id__"
        const val BUNDLE_CHANNEL_SUBSCRIPTION_LOCAL_TIMESTAMP = "__channel_monitor_local_timestamp__"
        const val BUNDLE_CHANNEL_SUBSCRIPTION_REMOTE_TIMESTAMP = "__channel_monitor_remote_timestamp__"
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private val mMessengers: MutableList<Messenger> = mutableListOf()

    private val mChannelClients = mutableMapOf<ChannelId, Messenger>()
    private val mChannelMembersClients = mutableMapOf<ChannelId, Messenger>()
    private val mChannelSubscriptionClients = mutableMapOf<ChannelId, Messenger>()
    private val mChannelMessageClients = mutableMapOf<MessageId, Messenger>()

    private inner class ChannelListener : ChangeEventListener<Channel> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<Channel>) {
            Log.d("ChannelService", "CHANNEL_LISTENER RECEIVED EVENT: ${event.operationType}")
            if (event.hasUncommittedWrites()) {
                return
            }

            when (event.operationType) {
                OperationType.INSERT -> {
                    mChannelClients[(documentId as BsonString).value]?.send(
                        getMessageForActionSubscribeToChannel(event.fullDocument!!))
                }
                else -> return
            }
        }
    }

    private inner class ChannelMessageListener : ChangeEventListener<ChannelMessage> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<ChannelMessage>) {
            Log.d("ChannelService", "CHANNEL_MESSAGE_LISTENER: ${event.operationType}")

            when (event.operationType) {
                OperationType.INSERT, OperationType.REPLACE -> {
                    val insertedMessage = checkNotNull(event.fullDocument)
                    val msg = Message()
                    msg.what = MSG_CHANNEL_MESSAGE_INSERT
                    msg.data = Bundle()
                    msg.data.putParcelable(BUNDLE_CHANNEL_MESSAGE_NEW_MESSAGE, insertedMessage)
                    mChannelMessageClients[insertedMessage.id.toHexString()]?.send(msg)
                }
                OperationType.UPDATE -> {
//                    val insertedMessage = checkNotNull(event.fullDocument)
//                    val msg = Message()
//                    msg.what = MSG_CHANNEL_MESSAGE_UPDATE
//                    msg.data = Bundle()
//                    msg.data.putParcelable(BUNDLE_CHANNEL_MESSAGE_NEW_MESSAGE, insertedMessage)
//                    mChannelMessageClients[insertedMessage.id.toHexString()]?.send(msg)
                }
                else -> {

                }
            }
        }
    }

    private inner class ChannelSubscriptionListener :
        ConflictHandler<ChannelSubscription>, ChangeEventListener<ChannelSubscription> {

        override fun resolveConflict(documentId: BsonValue,
                                     localEvent: ChangeEvent<ChannelSubscription>,
                                     remoteEvent: ChangeEvent<ChannelSubscription>): ChannelSubscription? {
            Log.d("ChannelService",
                "CHANNEL_SUBSCRIPTION_LISTENER RECEIVED CONFLICT")

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

            launch(IO) {
                mChannelSubscriptionClients[(documentId as BsonString).value]?.send(
                    getMessageForActionSubscribeToMessageIds(
                        documentId.value, localTimestamp.value, remoteTimestamp.value)
                )
            }.start()

            return ChannelSubscription(
                (documentId as BsonString).value,
                user.id,
                remoteTimestamp.value,
                remoteTimestamp.value)
        }

        override fun onEvent(documentId: BsonValue, event: ChangeEvent<ChannelSubscription>) {
            if (event.hasUncommittedWrites() || event.operationType != OperationType.REPLACE) {
                return
            }

            launch(IO) {
                val subscriptionId = (documentId as BsonObjectId).asObjectId().value.toHexString()
                ChannelSubscription.getRemoteChannelSubscription(subscriptionId).let { subscription ->
                    checkNotNull(subscription)
                    mChannelSubscriptionClients[subscription.channelId]?.send(
                        getMessageForActionSubscribeToMessageIds(
                            subscription.channelId,
                            subscription.localTimestamp,
                            subscription.remoteTimestamp)
                    )

                    ChannelSubscription.setChannelSubscriptionLocalVector(
                        subscriptionId, subscription.remoteTimestamp)

                    Log.d("ChannelService", "CHANNEL_SUBSCRIPTION_LISTENER RESET VECTOR")
                }
            }
        }
    }

    private inner class ChannelMembersListener :
        ConflictHandler<ChannelMembers>, ChangeEventListener<ChannelMembers> {

        override fun resolveConflict(documentId: BsonValue,
                                     localEvent: ChangeEvent<ChannelMembers>,
                                     remoteEvent: ChangeEvent<ChannelMembers>): ChannelMembers? {
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
            if (event.hasUncommittedWrites()) {
                return
            }

            val channelId = (documentId as BsonString).value

            mChannelMembersClients[channelId]
                ?.send(Message.obtain(null, MSG_CHANNEL_MEMBER_REFRESH_MEMBERS))
        }
    }

    /**
     * Handler of incoming channelMessages from clients.
     */
    @SuppressWarnings("HandlerLeak")
    inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            runBlocking {
                launch(IO) {
                    Log.d("ChannelService", "RECEIVED NEW MESSAGE: $msg WITH DATA: ${msg.data}")
                    when (msg.what) {
                        MSG_CHANNEL_SUBSCRIBE -> {
                            Log.d("ChannelService", "HANDLING CHANNEL SUBSCRIPTION")
                            val channelId = checkNotNull(
                                msg.data.getString(BUNDLE_CHANNEL_ID),
                                { "ChannelService#BUNDLE_CHANNEL_ID must be sent" })

                            mChannelClients[channelId] = msg.replyTo

                            Channel.getChannel(channelId)?.let { channel ->
                                Log.d("ChannelService", "SENT CHANNEL: $channel")
                                msg.replyTo.send(
                                    getMessageForActionSubscribeToChannel(channel))
                            } ?: Channel.sync(channelId)

                            val subscriptionId = Channel.subscribeToChannel(
                                User.getCurrentUser().id, channelId
                            ).toHexString()

                            msg.replyTo.send(
                                getMessageForActionSubscribeToSubscription(subscriptionId))
                            Log.d("ChannelService", "SENT SUBSCRIPTION ID: $subscriptionId")
                        }
                        MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE -> {
                            Log.d("ChannelService", "HANDLING CHANNEL SUBSCRIPTION SUBSCRIPTION")
                            val channelId = checkNotNull(
                                msg.data.getString(BUNDLE_CHANNEL_ID),
                                { "ChannelService#BUNDLE_CHANNEL_ID must be sent" })
                            val channelSubscriptionId = checkNotNull(
                                msg.data.getString(BUNDLE_CHANNEL_SUBSCRIPTION_ID),
                                { "ChannelService#BUNDLE_CHANNEL_SUBSCRIPTION_ID must be sent" })

                            mChannelSubscriptionClients[channelId] = msg.replyTo

                            ChannelSubscription
                                .getChannelSubscription(channelSubscriptionId)?.let {
                                    mChannelSubscriptionClients[channelId]?.send(
                                        getMessageForActionSubscribeToMessageIds(
                                            channelId, it.localTimestamp, it.remoteTimestamp)
                                    )
                            } ?: ChannelSubscription.sync(channelSubscriptionId)
                        }
                        MSG_CHANNEL_MESSAGE_SYNC_MESSAGE_IDS -> {
                            Log.d("ChannelService", "HANDLING CHANNEL MESSAGE SYNC MESSAGE IDS")
                            val messageIds = checkNotNull(
                                msg.data.getStringArray(BUNDLE_CHANNEL_MESSAGE_IDS),
                                { "ChannelService#BUNDLE_CHANNEL_MESSAGE_IDS must be sent" })

                            ChannelMessage.syncMessages(*messageIds)
                        }
                        MSG_CHANNEL_MESSAGE_SUBSCRIBE -> {
                            Log.d("ChannelService", "HANDLING CHANNEL MESSAGE SUBSCRIBE")
                            val messageId = checkNotNull(
                                msg.data.getString(BUNDLE_CHANNEL_MESSAGE_ID),
                                { "ChannelService#BUNDLE_CHANNEL_MESSAGE_ID must be sent" })

                            mChannelMessageClients[messageId] = msg.replyTo
                            ChannelMessage.syncMessages(messageId)
                        }
                        MSG_CHANNEL_MESSAGE_INSERT -> {
                            Log.d("ChannelService", "HANDLING CHANNEL_MESSAGE_INSERT")
                            val channelId = checkNotNull(
                                msg.data.getString(BUNDLE_CHANNEL_ID),
                                { "ChannelService#BUNDLE_CHANNEL_ID must be sent" })
                            val channelSubscriptionId = checkNotNull(
                                msg.data.getString(BUNDLE_CHANNEL_SUBSCRIPTION_ID),
                                { "ChannelService#BUNDLE_CHANNEL_SUBSCRIPTION_ID must be sent" })
                            val content = checkNotNull(
                                msg.data.getString(BUNDLE_CHANNEL_MESSAGE_CONTENT),
                                { "ChannelService#BUNDLE_CHANNEL_SUBSCRIPTION_ID must be sent" })

                            val message = ChannelMessage.sendMessage(
                                channelId, channelSubscriptionId, content)

                            mChannelSubscriptionClients[channelId]?.send(
                                getMessageForActionSubscribeToMessageId(message.id.toHexString())
                            )
                        }
                        else -> super.handleMessage(msg)
                    }
                }.join()
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
            Log.d("ChannelService", "all configures called")
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("ChannelService", "Destroying service")
        job.cancel()
    }
}
