package com.mongodb.stitch.android.examples.chatsync.viewModel

import android.arch.lifecycle.LiveData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.mongodb.stitch.android.examples.chatsync.ChannelService
import com.mongodb.stitch.android.examples.chatsync.model.Channel

typealias SubscriptionId = String

class ChannelLiveData(private val context: Context,
                      private val id: String) : LiveData<Pair<Channel?, SubscriptionId?>>() {
    /** Messenger for communicating with the service. */
    private lateinit var mService: Messenger
    /** Flag indicating whether we have called bind on the service.  */
    private var mBound: Boolean = false

    @SuppressWarnings("HandlerLeak")
    private inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            Log.d("ChannelService",
                "CHANNEL_LIVE_DATA RECEIVED NEW MESSAGE: $msg WITH DATA: ${msg.data}")
            when (msg.what) {
                ChannelService.MSG_CHANNEL_SUBSCRIBE -> {
                    value = msg.data.getParcelable<Channel>(ChannelService.BUNDLE_CHANNEL) to
                            value?.second
                }
                ChannelService.MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE -> {
                    value = value?.first to
                        msg.data.getString(ChannelService.BUNDLE_CHANNEL_SUBSCRIPTION_ID)
                }
            }
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mService = Messenger(service)
            val msg = Message()
            msg.what = ChannelService.MSG_CHANNEL_SUBSCRIBE
            msg.data = Bundle()
            msg.data.putString(ChannelService.BUNDLE_CHANNEL_ID, id)
            msg.replyTo = Messenger(IncomingHandler())
            mService.send(msg)
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    fun sendMessage(channelSubscriptionId: SubscriptionId, content: String) {
        // We've bound to LocalService, cast the IBinder and get LocalService instance
        val msg = Message()
        msg.what = ChannelService.MSG_CHANNEL_MESSAGE_INSERT
        msg.data = Bundle()
        msg.data.putString(ChannelService.BUNDLE_CHANNEL_ID, id)
        msg.data.putString(ChannelService.BUNDLE_CHANNEL_SUBSCRIPTION_ID, channelSubscriptionId)
        msg.data.putString(ChannelService.BUNDLE_CHANNEL_MESSAGE_CONTENT, content)
        msg.replyTo = Messenger(IncomingHandler())
        mService.send(msg)
        mBound = true
    }

    override fun onActive() {
        if (!mBound) {
            Intent(context, ChannelService::class.java).also { intent ->
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    override fun onInactive() {
        if (mBound) {
            context.unbindService(connection)
        }
    }
}
