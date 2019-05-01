package com.mongodb.stitch.android.examples.chatsync.service

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import com.mongodb.stitch.android.examples.chatsync.model.Channel
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.model.User

private enum class Action {
    SUBSCRIBE_TO_CHANNEL,
    SUBSCRIBE_TO_CHANNEL_REPLY,
    UNSUBSCRIBE_TO_CHANNEL,

    BIND_TO_CHANNEL_SUBSCRIPTION,
    BIND_TO_CHANNEL_SUBSCRIPTION_REPLY,

    SEND_MESSAGE,
    SEND_MESSAGE_REPLY,
    NEW_MESSAGE_REPLY,

    SET_AVATAR,
    USER_UPDATED
}

private const val BUNDLE_CHANNEL = "__channel__"
private const val BUNDLE_CHANNEL_ID = "__channel_id__"

private const val BUNDLE_CHANNEL_MESSAGE = "__channel_message__"
private const val BUNDLE_CHANNEL_MESSAGE_ID = "__channel_message_id__"
private const val BUNDLE_CHANNEL_MESSAGE_CONTENT = "__channel_message_content__"

private const val BUNDLE_AVATAR = "__avatar__"
private const val BUNDLE_USER = "__user__"

fun Messenger.send(channelServiceAction: ChannelServiceAction) {
    this.send(channelServiceAction.asMessage)
}

fun Message.asChannelServiceAction(): ChannelServiceAction {
    return when (this.what) {
        Action.SUBSCRIBE_TO_CHANNEL.ordinal -> {
            ChannelServiceAction.SubscribeToChannel(
                this.data.getString(BUNDLE_CHANNEL_ID)!!, this.replyTo)
        }
        Action.SUBSCRIBE_TO_CHANNEL_REPLY.ordinal ->
            ChannelServiceAction.SubscribeToChannelReply(this.data.getParcelable(BUNDLE_CHANNEL)!!)
        Action.UNSUBSCRIBE_TO_CHANNEL.ordinal -> {
            ChannelServiceAction.UnsubscribeToChannel(
                this.data.getString(BUNDLE_CHANNEL_ID)!!)
        }
        Action.SEND_MESSAGE.ordinal -> {
            ChannelServiceAction.SendMessage(
                this.data.getString(BUNDLE_CHANNEL_ID)!!,
                this.data.getString(BUNDLE_CHANNEL_MESSAGE_CONTENT)!!
            )
        }
        Action.SEND_MESSAGE_REPLY.ordinal -> {
            ChannelServiceAction.SendMessageReply(this.data.getParcelable(BUNDLE_CHANNEL_MESSAGE)!!)
        }
        Action.NEW_MESSAGE_REPLY.ordinal -> {
            ChannelServiceAction.NewMessageReply(
                this.data.getString(BUNDLE_CHANNEL_MESSAGE_ID)!!,
                this.data.getParcelable(BUNDLE_CHANNEL_MESSAGE)!!
            )
        }
        Action.SET_AVATAR.ordinal -> {
            ChannelServiceAction.SetAvatar(this.data.getByteArray(BUNDLE_AVATAR)!!)
        }
        Action.USER_UPDATED.ordinal -> {
            ChannelServiceAction.UserUpdated(this.data.getParcelable(BUNDLE_USER)!!)
        }
        else -> {
            throw Error("${this.what} is not a valid Action")
        }
    }
}

sealed class ChannelServiceAction constructor(private val action: Action) {
    data class SubscribeToChannel(val channelId: String, val replyTo: Messenger) :
        ChannelServiceAction(Action.SUBSCRIBE_TO_CHANNEL) {
        override val asMessage: Message = getMessage {
            it.data.putString(BUNDLE_CHANNEL_ID, channelId)
            it.replyTo = replyTo
        }
    }

    data class SubscribeToChannelReply(val channel: Channel) :
        ChannelServiceAction(Action.SUBSCRIBE_TO_CHANNEL_REPLY) {
        override val asMessage = getMessage {
            it.data.putParcelable(BUNDLE_CHANNEL, channel)
        }
    }

    data class UnsubscribeToChannel(val channelId: String) :
        ChannelServiceAction(Action.UNSUBSCRIBE_TO_CHANNEL) {
        override val asMessage = getMessage {
            it.data.putString(BUNDLE_CHANNEL_ID, channelId)
        }
    }

    data class SendMessage(val channelId: String, val content: String) :
        ChannelServiceAction(Action.SEND_MESSAGE) {
        override val asMessage: Message = getMessage {
            it.data.putString(BUNDLE_CHANNEL_ID, channelId)
            it.data.putString(BUNDLE_CHANNEL_MESSAGE_CONTENT, content)
        }
    }

    data class SendMessageReply(val channelMessage: ChannelMessage) :
        ChannelServiceAction(Action.SEND_MESSAGE_REPLY) {
        override val asMessage: Message = getMessage {
            it.data.putParcelable(BUNDLE_CHANNEL_MESSAGE, channelMessage)
        }
    }

    data class NewMessageReply(val messageId: String, val channelMessage: ChannelMessage) :
        ChannelServiceAction(Action.NEW_MESSAGE_REPLY) {
        override val asMessage = getMessage {
            it.data.putString(BUNDLE_CHANNEL_MESSAGE_ID, messageId)
            it.data.putParcelable(BUNDLE_CHANNEL_MESSAGE, channelMessage)
        }
    }

    data class SetAvatar(val avatar: ByteArray) : ChannelServiceAction(Action.SET_AVATAR) {
        override val asMessage = getMessage {
            it.data.putByteArray(BUNDLE_AVATAR, avatar)
        }
    }

    data class UserUpdated(val user: User) : ChannelServiceAction(Action.USER_UPDATED) {
        override val asMessage = getMessage {
            it.data.putParcelable(BUNDLE_USER, user)
        }
    }

    abstract val asMessage: Message

    protected fun getMessage(messageMutator: (Message) -> Unit): Message {
        val message = Message()
        message.what = action.ordinal
        message.data = Bundle()
        messageMutator(message)
        return message
    }
}