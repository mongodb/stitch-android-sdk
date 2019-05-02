package com.mongodb.stitch.android.examples.chatsync.service

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
import com.mongodb.stitch.android.examples.chatsync.repo.ChannelSubscriptionRepo
import com.mongodb.stitch.android.examples.chatsync.repo.UserRepo
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
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
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.arch.lifecycle.LifecycleService
import android.arch.lifecycle.Observer
import android.content.Context
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.Person
import android.support.v4.graphics.drawable.IconCompat
import com.mongodb.stitch.android.examples.chatsync.ChannelActivity
import com.mongodb.stitch.android.examples.chatsync.R
import com.mongodb.stitch.android.examples.chatsync.defaultAvatars
import com.mongodb.stitch.android.examples.chatsync.repo.ChannelMembersRepo
import com.mongodb.stitch.android.examples.chatsync.repo.ChannelMessageRepo
import com.mongodb.stitch.android.examples.chatsync.repo.ChannelRepo
import com.mongodb.stitch.android.examples.chatsync.repo.ReadOnlyLruCache
import org.bson.types.ObjectId


/* Type alias for readability */
private typealias ChannelId = String

/**
 * Background process that handles the synchronization processes for our domain.
 */
class ChannelService : LifecycleService(), CoroutineScope, LifecycleObserver {
    companion object {
        /** Unique channel id for notifications */
        private val NOTIFICATION_CHANNEL_ID: String = ObjectId().toHexString()
    }

    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val mMessengers: MutableList<Messenger> = mutableListOf()

    private val mChannelClients = mutableMapOf<ChannelId, Messenger>()

    /**
     * [ChangeEventListener] for [Channel] domain.
     */
    private inner class ChannelListener : ChangeEventListener<Channel> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<Channel>) {
            // TODO: Handle channel updates
            Log.d("ChannelListener", "onEvent: ${event.toBsonDocument()}")
        }
    }

    /**
     * [ChangeEventListener] for [User] domain. This will pass through user
     * updates to messengers subscribed to this service.
     */
    private inner class UserListener : ChangeEventListener<User> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<User>) {
            Log.w("UserListener",
                "onEvent: ${event.toBsonDocument()} fullDocument: ${event.fullDocument}")

            when (event.operationType) {
                OperationType.REPLACE, OperationType.UPDATE -> {
                    val user = checkNotNull(event.fullDocument)
                    // put the updated user into the live cache so that
                    // the observers of the cache will receive the user
                    UserRepo.putIntoCache(user.id, user)
                    // pass along the user to any subscribers of this service
                    // TODO: there should be a different set of listeners for
                    // TODO: users since users are orthogonal to channels
                    user.channelsSubscribedTo.forEach {
                        mChannelClients[it]?.send(ChannelServiceAction.UserUpdated(user))
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * [ChangeEventListener] for the [ChannelMessage] domain.
     */
    private inner class ChannelMessageListener : ChangeEventListener<ChannelMessage> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<ChannelMessage>) {
            Log.w("ChannelMessageListener",
                "onEvent: ${event.toBsonDocument()} fullDocument: ${event.fullDocument}")
            // if the event was only committed locally, ignore
            if (event.hasUncommittedWrites()) {
                return
            }

            when (event.operationType) {
                OperationType.REPLACE -> {
                    val message = checkNotNull(event.fullDocument)
                    val messageId = message.id.toHexString()
                    mChannelClients[message.channelId]?.send(
                        ChannelServiceAction.NewMessageReply(messageId, message)) ?:
                        notifyMessageReceived(message)
                }
                else -> {}
            }
        }
    }

    private inner class ChannelSubscriptionListener :
        ConflictHandler<ChannelSubscription>, ChangeEventListener<ChannelSubscription> {

        override fun resolveConflict(
            documentId: BsonValue,
            localEvent: ChangeEvent<ChannelSubscription>,
            remoteEvent: ChangeEvent<ChannelSubscription>
        ): ChannelSubscription? {
            Log.w("ChannelSubscriptionListener", "Received conflict: " +
                "${localEvent.toBsonDocument()} and ${remoteEvent.toBsonDocument()}")

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
                val messageIds = ChannelMessageRepo.fetchMessageIdsFromVector(
                    channelId,
                    localTimestamp.value,
                    remoteTimestamp.value
                )
                ChannelMessageRepo.syncMessages(*messageIds)
            }.start()

            return ChannelSubscription(
                documentId.asObjectId().value,
                channelId,
                stitch.auth.user!!.id,
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
                runBlocking {
                    launch(IO) {
                        val subscriptionId = (documentId as BsonObjectId).value
                        val subscription = event.fullDocument

                        checkNotNull(subscription)

                        if (subscription.localTimestamp == subscription.remoteTimestamp) {
                            return@launch
                        }

                        Log.w("ChannelSubscriptionListener",
                            "Local timestamp differs from remote timestamp")

                        val messageIds = ChannelMessageRepo.fetchMessageIdsFromVector(
                            subscription.channelId,
                            subscription.localTimestamp,
                            subscription.remoteTimestamp
                        )
                        ChannelMessageRepo.syncMessages(*messageIds)

                        ChannelSubscriptionRepo.updateLocalVector(
                            subscriptionId, subscription.remoteTimestamp)
                    }.join()
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

            event.fullDocument?.let {
                launch(IO) { UserRepo.sync(*it.members.toTypedArray()) }
            }
        }
    }

    private suspend fun handleSendMessageMessage(action: ChannelServiceAction.SendMessage) {
        // de-structure the action
        val (channelId, content) = action

        // send the message to the repo
        val message = ChannelMessageRepo.sendMessage(channelId, content)

        // send a send message reply to the respondent so that they can
        // update their local copy of the message
        mChannelClients[channelId]?.send(ChannelServiceAction.SendMessageReply(message))
            ?: throw Error("Not subscribed to channel")
    }

    private fun handleUnsubscribeToChannelMessage(
        action: ChannelServiceAction.UnsubscribeToChannel
    ) {
        mChannelClients.remove(action.channelId)
    }

    private suspend fun handleSubscribeToChannelMessage(
        action: ChannelServiceAction.SubscribeToChannel
    ) {
        // de-structure the action
        val (channelId, replyTo) = action

        // add the respondent to our listener map
        mChannelClients[channelId] = replyTo

        // find the channel. if it does not exist locally, fetch it remotely
        val channel = checkNotNull(
            ChannelRepo.findLocalById(channelId) ?:
            ChannelRepo.findRemoteChannel(channelId))

        // find the current user
        val currentUser = checkNotNull(UserRepo.findCurrentUser())

        // synchronize the channel and channel members
        ChannelRepo.sync(channelId)
        ChannelMembersRepo.sync(channelId)

        // check if we already have a channelSubscription; if not,
        // subscribe to the channel via the stitch server
        ChannelSubscriptionRepo.getLocalChannelSubscriptionId(
            currentUser.id, stitch.auth.user!!.deviceId, channelId
        ) ?: ChannelRepo.subscribeToChannel(
            currentUser.id, stitch.auth.user!!.deviceId, channelId
        )

        // send a subscription reply to the respondent
        action.replyTo.send(ChannelServiceAction.SubscribeToChannelReply(channel))
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
                    is ChannelServiceAction.SubscribeToChannel ->
                        handleSubscribeToChannelMessage(action)
                    is ChannelServiceAction.UnsubscribeToChannel ->
                        handleUnsubscribeToChannelMessage(action)
                    is ChannelServiceAction.SendMessage ->
                        handleSendMessageMessage(action)
                    is ChannelServiceAction.SetAvatar ->
                        UserRepo.updateAvatar(action.avatar)
                    else -> super.handleMessage(msg)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        val messenger = Messenger(IncomingHandler())
        mMessengers.add(messenger)
        return messenger.binder
    }


    private fun notifyMessageReceived(message: ChannelMessage) {
        UserRepo.refreshCacheForId(message.ownerId)
        UserRepo.liveCache.observe(this, object : Observer<ReadOnlyLruCache<Int, User>> {
            override fun onChanged(cache: ReadOnlyLruCache<Int, User>?) {
                cache?.get(message.ownerId.hashCode())?.let { user ->
                    // Create an explicit intent for an Activity in your app
                    val intent = Intent(this@ChannelService, ChannelActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    val pendingIntent: PendingIntent = PendingIntent.getActivity(
                        this@ChannelService, 0, intent, 0)


                    val person = Person.Builder()
                        .setName(user.name)
                        .setIcon(
                            user.avatar?.let { avatar ->
                                IconCompat.createWithData(avatar, 0, avatar.size)
                            } ?: IconCompat.createWithResource(
                                this@ChannelService, defaultAvatars[user.defaultAvatarOrdinal])
                        ).build()

                    val style = NotificationCompat.MessagingStyle(person)

                    style.addMessage(NotificationCompat.MessagingStyle.Message(
                        message.content, message.sentAt, person
                    ))

                    val builder = NotificationCompat.Builder(
                        this@ChannelService, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.mind_map_icn)
                        .setColorized(true)
                        .setContentTitle("New message")
                        .setContentText(message.content)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setStyle(style)

                    with(NotificationManagerCompat.from(this@ChannelService)) {
                        notify(message.id.hashCode(), builder.build())
                    }

                    UserRepo.liveCache.removeObserver(this)
                }
            }
        })
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "chat sync", importance).apply {
                description = "notification channel for chat sync"
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        job = Job()

        createNotificationChannel()
        launch(IO) {
            ChannelRepo.configure(DefaultSyncConflictResolvers.remoteWins(), ChannelListener())
            ChannelSubscriptionListener().also { ChannelSubscriptionRepo.configure(it, it) }
            ChannelMembersListener().also { ChannelMembersRepo.configure(it, it) }
            ChannelMessageRepo
                .configure(DefaultSyncConflictResolvers.remoteWins(), ChannelMessageListener())
            UserRepo.configure(DefaultSyncConflictResolvers.remoteWins(), UserListener())
            Log.d("ChannelService", "all configures called")
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("ChannelService", "Destroying service")
        job.cancel()
    }
}
