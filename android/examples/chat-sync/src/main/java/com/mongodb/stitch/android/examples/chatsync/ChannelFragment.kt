package com.mongodb.stitch.android.examples.chatsync

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.mongodb.stitch.android.examples.chatsync.model.Channel
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.viewModel.ChannelMessageLiveData
import com.mongodb.stitch.android.examples.chatsync.viewModel.ChannelSubscriptionViewModel
import com.mongodb.stitch.android.examples.chatsync.viewModel.ChannelViewModel
import com.mongodb.stitch.android.examples.chatsync.viewModel.SubscriptionId

class ChannelFragment : Fragment() {
    private lateinit var channelViewModel: ChannelViewModel
    private lateinit var channelSubscriptionViewModel: ChannelSubscriptionViewModel

    private val adapter by lazy {
        MessageAdapter(this.context!!, sortedSetOf())
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.content_main, container, false)
    }

    private fun sendMessage(v: View) {
        Log.d("ChannelService", "sending message")
        val messageEditText = v.rootView.findViewById<EditText>(R.id.message_edit_text)
        channelViewModel.channel.observe(this, Observer { data ->
            Log.d("ChannelService", "sending message ii")
            data?.run {
                if (data.first != null && data.second != null) {
                    channelViewModel.channel.sendMessage(
                        data.second!!,
                        messageEditText.text.toString())
                }
            }
        })
    }

    private fun channelMessageObserver() = Observer<ChannelMessage> { message ->
        message?.run {
            adapter.addMessages(message)
        }
    }

    private fun channelSubscriptionObserver() = Observer<Array<String>> { vectorMessageIds ->
        Log.d("ChannelService", "CHANNEL_SUBSCRIPTION_OBSERVER: $vectorMessageIds")
        vectorMessageIds?.run {
            vectorMessageIds.forEach { messageId ->
                ChannelMessageLiveData(view!!.context, messageId)
                    .observe(this@ChannelFragment, channelMessageObserver())
            }
        }
    }

    private fun channelObserver() = Observer<Pair<Channel?, SubscriptionId?>> { data ->
        Log.d("ChannelService", "CHANNEL_OBSERVER DATA SHIFT: $data")
        data?.run {
            view?.findViewById<Button>(R.id.send_button)?.isEnabled = true
            val (channel, subscriptionId) = data
            if (channel != null && subscriptionId != null) {
                channelSubscriptionViewModel.addChannelSubscription(
                    view!!.context, channel.id, subscriptionId
                ).value?.get(channel.id)?.observe(
                    this@ChannelFragment, channelSubscriptionObserver())
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val channelMessagesRecyclerView =
            view.findViewById<RecyclerView>(R.id.channel_messages_recycler_view)
        channelMessagesRecyclerView.layoutManager = LinearLayoutManager(this.context)
        channelMessagesRecyclerView.adapter = adapter

        val sendButton = view.findViewById<Button>(R.id.send_button)
        sendButton.setOnClickListener(::sendMessage)
        sendButton.isEnabled = false

        channelViewModel = ViewModelProviders.of(activity!!)
            .get(ChannelViewModel::class.java)
        channelSubscriptionViewModel = ViewModelProviders.of(activity!!)
            .get(ChannelSubscriptionViewModel::class.java)

        channelViewModel.selectChannel(view.context, user.channelsSubscribedTo.first())
            .observe(this, channelObserver())
    }
}
