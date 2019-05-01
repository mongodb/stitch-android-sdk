package com.mongodb.stitch.android.examples.chatsync

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.BitmapFactory
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.RecyclerView
import android.text.format.DateFormat
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.android.examples.chatsync.service.ChannelServiceAction
import com.mongodb.stitch.android.examples.chatsync.viewModel.ChannelViewModel
import kotlinx.android.synthetic.main.viewholder_message.view.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.coroutineContext

enum class MessageType {
    FULL,
    CONTINUATION
}

sealed class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract suspend fun setMessage(message: ChannelMessage)
}

class ContinuationMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
    private val content by lazy { itemView.findViewById<TextView>(R.id.content) }

    override suspend fun setMessage(message: ChannelMessage) = withContext(coroutineContext) {
        content.text = message.content
        content.setTextColor(
            itemView.resources.getColor(android.R.color.black, null))
        itemView.visibility = VISIBLE
    }
}

class FullMessageViewHolder(private val activity: FragmentActivity, itemView: View) :
    MessageViewHolder(itemView) {

    private val content by lazy { itemView.findViewById<TextView>(R.id.content) }
    private val username by lazy { itemView.findViewById<TextView>(R.id.username) }
    private val avatar by lazy { itemView.findViewById<ImageView>(R.id.avatar) }
    private lateinit var currentMessage: ChannelMessage

    private val observer: Observer<ChannelServiceAction?> by lazy {
        Observer { action: ChannelServiceAction? ->
            when (action) {
                is ChannelServiceAction.UserUpdated -> {
                    if (action.user.id == currentMessage.ownerId) {
                        setUser(action.user)
                    }
                }
            }
        }
    }

    init {
        val channelViewModel = ViewModelProviders
            .of(activity)
            .get(ChannelViewModel::class.java)

        channelViewModel.channel.observe(activity, observer)
    }

    private fun setUser(user: User) = GlobalScope.launch(Main) {
        if (user.avatar != null) {
            avatar.setImageBitmap(
                BitmapFactory.decodeByteArray(user.avatar, 0, user.avatar.count()))
        } else {
            avatar.setImageResource(defaultAvatars[user.defaultAvatarOrdinal])
        }

        username.text = user.name
    }

    override suspend fun setMessage(message: ChannelMessage) = withContext(coroutineContext) {
        currentMessage = message
        content.text = message.content

        if (message.remoteTimestamp != null) {
            content.setTextColor(
                itemView.resources.getColor(android.R.color.black, null))
            itemView.time.text = DateFormat.getTimeFormat(activity).format(Date(message.sentAt))
            itemView.time.visibility = VISIBLE
        } else {
            itemView.time.visibility = GONE
            content.setTextColor(
                itemView.resources.getColor(android.R.color.darker_gray, null))
        }

        launch(IO) {
            User.getUser(message.ownerId)?.let {
                setUser(it)
            }
        }.join()

        itemView.visibility = VISIBLE
    }
}

class MessageAdapter(private val activity: FragmentActivity) :
    MongoCursorAdapter<MessageViewHolder, ChannelMessage>() {

    private val mInflater by lazy { LayoutInflater.from(activity) }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): MessageViewHolder =
        when (viewType) {
            MessageType.CONTINUATION.ordinal ->
                ContinuationMessageViewHolder(
                    mInflater.inflate(R.layout.viewholder_continuation_message, viewGroup, false))
            else ->
                FullMessageViewHolder(
                    activity, mInflater.inflate(R.layout.viewholder_message, viewGroup, false))
        }.also {
            it.itemView.visibility = INVISIBLE
        }

    override suspend fun onBindViewHolder(viewHolder: MessageViewHolder,
                                          position: Int,
                                          cursor: SparseRemoteMongoCursor<ChannelMessage>) {
        viewHolder.itemView.visibility = INVISIBLE
        cursor.moveToPosition(position)
        cursor[position]?.let {
            GlobalScope.launch(Main) { viewHolder.setMessage(it) }
        }
    }

    override fun getItemViewType(position: Int): Int =
        runBlocking {
            cursor?.let { cursor ->
                launch(IO) {
                    cursor.moveToPosition(position + 1)
                }.join()

                if (cursor.count > position + 1
                    && (cursor[position + 1]?.ownerId == cursor[position]?.ownerId)) {
                    MessageType.CONTINUATION.ordinal
                } else {
                    MessageType.FULL.ordinal
                }
            } ?: 0
        }
}
