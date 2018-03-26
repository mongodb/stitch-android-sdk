package com.mongodb.stitch.pushTest

import com.mongodb.stitch.android.push.PushMessage
import com.mongodb.stitch.android.push.gcm.GCMListenerService

typealias OnPushMessageReceivedListener = (PushMessage?) -> Unit

class AndroidTestGCMListenerService: GCMListenerService() {
    companion object {
        private var listeners = mutableListOf<OnPushMessageReceivedListener>()

        fun addListener(listener: OnPushMessageReceivedListener) {
            listeners.add(listener)
        }
    }

    override fun onPushMessageReceived(message: PushMessage?) {
        super.onPushMessageReceived(message)
        listeners.forEach { it(message) }
    }
}
