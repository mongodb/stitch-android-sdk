package com.mongodb.stitch.android.examples.chatsync.viewModel

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context

class ChannelSubscriptionViewModel : ViewModel() {
    var channelSubscriptions: MutableLiveData<MutableMap<String, ChannelSubscriptionLiveData>> =
        MutableLiveData()
        private set

    fun addChannelSubscription(
        context: Context,
        channelId: String,
        channelSubscriptionId: String
    ): MutableLiveData<MutableMap<String, ChannelSubscriptionLiveData>>  {
        if (channelSubscriptions.value == null) {
            channelSubscriptions.value = mutableMapOf(
                channelId to ChannelSubscriptionLiveData(context, channelId, channelSubscriptionId)
            )
        } else {
            channelSubscriptions.value!! +=
                channelId to ChannelSubscriptionLiveData(context, channelId, channelSubscriptionId)
            channelSubscriptions.value = channelSubscriptions.value
        }


        return channelSubscriptions
    }
}
