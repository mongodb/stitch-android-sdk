package com.mongodb.stitch.android.examples.chatsync

import android.arch.lifecycle.Observer
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.RecyclerView
import android.text.format.DateFormat
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.android.examples.chatsync.repo.UserRepo
import kotlinx.android.synthetic.main.viewholder_message.view.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.coroutineContext

private enum class MessageType {
    FULL,
    CONTINUATION
}

sealed class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    protected val content: TextView by lazy { itemView.findViewById<TextView>(R.id.content) }

    open suspend fun setMessage(message: ChannelMessage) {
        content.text = message.content
        if (message.remoteTimestamp != null) {
            content.setTextColor(itemView.resources.getColor(android.R.color.black, null))
        } else {
            content.setTextColor(
                itemView.resources.getColor(android.R.color.darker_gray, null))
        }
        itemView.visibility = VISIBLE
    }
}

class ContinuationMessageViewHolder(itemView: View) : MessageViewHolder(itemView)

class FullMessageViewHolder(private val activity: FragmentActivity, itemView: View) :
    MessageViewHolder(itemView) {

    private val username by lazy { itemView.findViewById<TextView>(R.id.username) }
    private val avatar by lazy { itemView.findViewById<ImageView>(R.id.avatar) }
    private lateinit var currentMessage: ChannelMessage

    private fun setUser(user: User) {
        if (user.avatar != null) {
            avatar.setImageBitmap(user.bitmapAvatar())
        } else {
            avatar.setImageResource(defaultAvatars[user.defaultAvatarOrdinal])
        }

        username.text = user.name
    }

    override suspend fun setMessage(message: ChannelMessage) = withContext(coroutineContext) {
        currentMessage = message
        content.text = message.content

        itemView.time.text = DateFormat.getTimeFormat(activity).format(Date(message.sentAt))
        itemView.time.visibility = VISIBLE

        UserRepo.refreshCacheForId(message.ownerId)
        UserRepo.liveCache.observe(activity, Observer { cache ->
            cache?.let { users ->
                users[message.ownerId.hashCode()]?.let(::setUser)
            }
        })

        super.setMessage(message)
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

    override suspend fun onBindSynchronizedViewHolder(viewHolder: MessageViewHolder,
                                                      position: Int) {
        viewHolder.itemView.visibility = INVISIBLE
        getItem(position)?.let {
            launch(Main) { viewHolder.setMessage(it) }.join()
        }
    }

    override fun getItemViewType(position: Int): Int =
        runBlocking {
            withContext(IO) {
                if (itemCount > position + 1
                    && (getItem(position + 1)?.ownerId == getItem(position)?.ownerId)) {
                    MessageType.CONTINUATION.ordinal
                } else {
                    MessageType.FULL.ordinal
                }
            }
        }
}
