package com.mongodb.stitch.android.examples.chatsync

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.BitmapFactory
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.RecyclerView
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
import java.util.*

class MessageViewHolder(
    private val activity: FragmentActivity,
    itemView: View
): RecyclerView.ViewHolder(itemView) {
    private val content by lazy { itemView.findViewById<TextView>(R.id.content) }
    private val username by lazy { itemView.findViewById<TextView>(R.id.username) }
    private val avatar by lazy { itemView.findViewById<ImageView>(R.id.avatar) }
    private var currentMessage: ChannelMessage? = null

    private fun setUser(user: User) = GlobalScope.launch(Main) {
        if (user.avatar != null) {
            avatar.setImageBitmap(
                BitmapFactory.decodeByteArray(user.avatar.toByteArray(), 0, user.avatar.count()))
        } else {
            avatar.setImageResource(defaultAvatars[user.defaultAvatarOrdinal])
        }

        username.text = user.name
    }

    fun setMessage(message: ChannelMessage) = GlobalScope.launch(Main) {
        content.text = message.content

        if (message.sentAt != null) {
            content.setTextColor(
                itemView.resources.getColor(android.R.color.black, null))
            itemView.time.text = Date(message.sentAt).toString()
            itemView.time.visibility = VISIBLE
        } else {
            itemView.time.visibility = GONE
            content.setTextColor(
                itemView.resources.getColor(android.R.color.darker_gray, null))
        }

        launch(IO) {
            User.getUser(message.ownerId)?.let {
                setUser(it)
            } ?: {
                val channelViewModel = ViewModelProviders
                    .of(activity)
                    .get(ChannelViewModel::class.java)

                channelViewModel.channel.observe(activity, object : Observer<ChannelServiceAction> {
                    override fun onChanged(action: ChannelServiceAction?) {
                        when (action) {
                            is ChannelServiceAction.UserUpdated -> {
                                setUser(action.user)
                                channelViewModel.channel.removeObserver(this)
                            }
                        }
                    }
                })
            }()
        }.join()

        itemView.visibility = VISIBLE
    }
}

class MessageAdapter(private val activity: FragmentActivity) :
    MongoCursorAdapter<MessageViewHolder, ChannelMessage>() {

    private val mInflater by lazy { LayoutInflater.from(activity) }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): MessageViewHolder {
        val view = mInflater.inflate(R.layout.viewholder_message, viewGroup, false)
        view.visibility = INVISIBLE
        return MessageViewHolder(activity, view)

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
}
