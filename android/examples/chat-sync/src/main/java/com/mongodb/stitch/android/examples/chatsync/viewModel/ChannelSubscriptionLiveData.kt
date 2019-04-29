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

class ChannelSubscriptionLiveData(private val context: Context,
                                  private val channelId: String,
                                  private val channelSubscriptionId: String) :
    LiveData<Array<String>>() {

    /** Messenger for communicating with the service. */
    private lateinit var mService: Messenger
    /** Flag indicating whether we have called bind on the service.  */
    private var mBound: Boolean = false

    @SuppressWarnings("HandlerLeak")
    private inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            Log.d("ChannelService", "CHANNEL_SUBSCRIPTION_LIVE_DATA: $msg")
            when (msg.what) {
                ChannelService.MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE_TO_MESSAGE_ID -> {
                    msg.data.getString(ChannelService.BUNDLE_CHANNEL_MESSAGE_ID)?.let { id ->
                        value = value?.plus(id) ?: arrayOf(id)
                    }
                }
                ChannelService.MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE_TO_MESSAGE_IDS -> {
                    value = msg.data.getStringArray(ChannelService.BUNDLE_CHANNEL_MESSAGE_IDS)
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
            msg.what = ChannelService.MSG_CHANNEL_SUBSCRIPTION_SUBSCRIBE
            msg.data = Bundle()
            msg.data.putString(ChannelService.BUNDLE_CHANNEL_ID, channelId)
            msg.data.putString(ChannelService.BUNDLE_CHANNEL_SUBSCRIPTION_ID, channelSubscriptionId)
            msg.replyTo = Messenger(IncomingHandler())
            mService.send(msg)
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override fun onActive() {
        Intent(context, ChannelService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onInactive() {
        context.unbindService(connection)
    }
}
