package com.mongodb.stitch.android.examples.chatsync

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import kotlinx.android.synthetic.main.viewholder_message.view.*
import java.util.*

class MessageViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    private val content by lazy { itemView.findViewById<TextView>(R.id.content) }
    private val username by lazy { itemView.findViewById<TextView>(R.id.username) }
    private val avatar by lazy { itemView.findViewById<ImageView>(R.id.avatar) }
    private var currentMessage: ChannelMessage? = null

    fun setMessage(message: ChannelMessage) {
        username.text = message.ownerId
        content.text = message.content
        message.sentAt?.run { itemView.time.text = Date(message.sentAt).toString() }
    }
}

class MessageAdapter(private val context: Context,
                     private var messages: SortedSet<ChannelMessage>) :
    RecyclerView.Adapter<MessageViewHolder>() {

    private val mInflater by lazy { LayoutInflater.from(context) }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): MessageViewHolder {
        val view = mInflater.inflate(R.layout.viewholder_message, viewGroup, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: MessageViewHolder, position: Int) {
        viewHolder.setMessage(messages.elementAt(position))
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    fun addMessages(vararg messages: ChannelMessage) {
        this.messages = this.messages.union(messages.toList()).toSortedSet()
        this.notifyDataSetChanged()
    }
}
