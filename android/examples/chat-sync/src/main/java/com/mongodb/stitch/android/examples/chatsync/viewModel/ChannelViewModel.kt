package com.mongodb.stitch.android.examples.chatsync.viewModel

import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.ViewModel
import android.content.Context

class ChannelViewModel : ViewModel(), LifecycleObserver {
    lateinit var channel: ChannelLiveData
        private set

    fun selectChannel(context: Context, channelId: String): ChannelLiveData {
        channel = ChannelLiveData(context, channelId)
        return channel
    }
}
