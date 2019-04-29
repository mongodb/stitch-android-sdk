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
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.ChannelService

class ChannelMessageLiveData(private val context: Context,
                             private val messageId: String) : LiveData<ChannelMessage>() {
    /** Messenger for communicating with the service.  */
    private lateinit var mService: Messenger
    /** Flag indicating whether we have called bind on the service.  */
    private var mBound: Boolean = false

    @SuppressWarnings("HandlerLeak")
    internal inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            Log.d("ChannelService", "CHANNEL_MESSAGE_LIVE_DATA: $msg")
            when (msg.what) {
                ChannelService.MSG_CHANNEL_MESSAGE_INSERT -> {
                    value = msg.data.getParcelable(
                        ChannelService.BUNDLE_CHANNEL_MESSAGE_NEW_MESSAGE)
                }
            }
        }
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mService = Messenger(service)
            val msg = Message()
            msg.what = ChannelService.MSG_CHANNEL_MESSAGE_SUBSCRIBE
            msg.data = Bundle()
            msg.data.putString(ChannelService.BUNDLE_CHANNEL_MESSAGE_ID, messageId)
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