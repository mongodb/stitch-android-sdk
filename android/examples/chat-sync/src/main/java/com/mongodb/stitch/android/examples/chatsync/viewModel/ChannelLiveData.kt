package com.mongodb.stitch.android.examples.chatsync.viewModel

import android.arch.lifecycle.LiveData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.mongodb.stitch.android.examples.chatsync.repo.UserRepo
import com.mongodb.stitch.android.examples.chatsync.service.ChannelService
import com.mongodb.stitch.android.examples.chatsync.service.ChannelServiceAction
import com.mongodb.stitch.android.examples.chatsync.service.asChannelServiceAction
import com.mongodb.stitch.android.examples.chatsync.service.send

class ChannelLiveData(private val context: Context,
                      private val channelId: String) :
    LiveData<ChannelServiceAction>() {

    /** Messenger for communicating with the service. */
    private lateinit var mService: Messenger
    /** Flag indicating whether we have called bind on the service.  */
    private var mBound: Boolean = false

    @SuppressWarnings("HandlerLeak")
    private inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            Log.d("ChannelLiveData", "Received new message: ${msg.asChannelServiceAction()}")
            val action = msg.asChannelServiceAction()
            when (action) {
                is ChannelServiceAction.UserUpdated ->
                    UserRepo.putIntoCache(action.user.id, action.user)
            }
            value = action
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mService = Messenger(service)
            mService.send(
                ChannelServiceAction.SubscribeToChannel(channelId, Messenger(IncomingHandler())))
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    fun setAvatar(avatar: ByteArray) {
        mService.send(ChannelServiceAction.SetAvatar(avatar))
    }

    fun sendMessage(content: String) =
        mService.send(ChannelServiceAction.SendMessage(channelId, content))

    override fun onActive() {
        if (!mBound) {
            Intent(context, ChannelService::class.java).also { intent ->
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    override fun onInactive() {
        if (mBound) {
            mBound = false
            mService.send(ChannelServiceAction.UnsubscribeToChannel(channelId))
            context.unbindService(connection)
        }
    }
}
