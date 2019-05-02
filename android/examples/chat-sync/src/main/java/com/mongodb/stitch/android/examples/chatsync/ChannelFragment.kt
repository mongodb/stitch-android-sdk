package com.mongodb.stitch.android.examples.chatsync

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.mongodb.stitch.android.examples.chatsync.repo.ChannelMessageRepo
import com.mongodb.stitch.android.examples.chatsync.repo.UserRepo
import com.mongodb.stitch.android.examples.chatsync.service.ChannelServiceAction
import com.mongodb.stitch.android.examples.chatsync.viewModel.ChannelViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class ChannelFragment : Fragment(), CoroutineScope {
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Main

    private lateinit var channelViewModel: ChannelViewModel

    private val channelMessagesRecyclerView by lazy {
        view!!.findViewById<RecyclerView>(R.id.channel_messages_recycler_view)
    }

    private val adapter by lazy { MessageAdapter(this.activity!!) }

    private var isInitialized = false

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.content_main, container, false)
    }

    private fun scrollToBottom() {
        launch(Main) {
            Handler().postDelayed({
                channelMessagesRecyclerView.smoothScrollToPosition(0)
            }, 100)
        }
    }

    private fun sendMessage(v: View) {
        val messageEditText = v.rootView.findViewById<EditText>(R.id.message_edit_text)
        channelViewModel.channel.observe(
            this, object : Observer<ChannelServiceAction> {

            var hasSentMessage: Boolean = false

            override fun onChanged(action: ChannelServiceAction?) {
                if (!hasSentMessage) {
                    hasSentMessage = true
                    channelViewModel.channel.sendMessage(messageEditText.text.toString())
                    messageEditText.text.clear()
                } else {
                    when (action) {
                        is ChannelServiceAction.SendMessageReply -> {
                            adapter.upsert(action.channelMessage)
                            scrollToBottom()

                            channelViewModel.channel.removeObserver(this)
                        }
                    }
                }
            }
        })
    }

    private val channelObserver = Observer<ChannelServiceAction> { data ->
        Log.d("ChannelObserver", "LiveData changed: $data")
        when (data) {
            is ChannelServiceAction.SubscribeToChannelReply -> {
                if (!isInitialized) {
                    launch(Main) {
                        val (channel) = data
                        launch(IO) {
                            adapter.setCursor(
                                    ChannelMessageRepo.getMessages(channel.id),
                                    ChannelMessageRepo.getMessagesCount(channel.id).toInt())
                            scrollToBottom()
                        }.join()
                        view?.findViewById<Button>(R.id.send_button)?.isEnabled = true
                        isInitialized = true
                    }
                }
            }
            is ChannelServiceAction.NewMessageReply -> {
                var shouldScrollToBottom = false
                if (!channelMessagesRecyclerView.canScrollVertically(1)) {
                    shouldScrollToBottom = true
                }

                adapter.upsert(data.channelMessage)
                if (shouldScrollToBottom) {
                    scrollToBottom()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = LinearLayoutManager(this.context)
        layoutManager.recycleChildrenOnDetach = true
        layoutManager.stackFromEnd = true
        layoutManager.reverseLayout = true
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        channelMessagesRecyclerView.layoutManager = layoutManager
        channelMessagesRecyclerView.adapter = adapter
        channelMessagesRecyclerView.addOnLayoutChangeListener {
            _, _, _, _, bottom, _, _, _, oldBottom ->

            if (bottom < oldBottom) {
                scrollToBottom()
            }
        }

        val sendButton = view.findViewById<Button>(R.id.send_button)
        sendButton.setOnClickListener(::sendMessage)
        sendButton.isEnabled = false

        launch(IO) {
            UserRepo.findCurrentUser()?.let {
                channelViewModel.selectChannel(view.context, it.channelsSubscribedTo.first())
                    .observe(this@ChannelFragment, channelObserver)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        channelViewModel.channel.removeObserver(channelObserver)
    }

    override fun onResume() {
        super.onResume()
        channelViewModel.channel.observe(this@ChannelFragment, channelObserver)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        channelViewModel = ViewModelProviders.of(this).get(ChannelViewModel::class.java)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
