package com.mongodb.stitch.android.examples.chatsync

import android.content.Intent
import android.os.Bundle
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.model.User
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

lateinit var user: User

class SplashActivity: ScopeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        launch(IO) {
            if (stitch.auth.isLoggedIn) {
                user = User.getCurrentUser()
                this@SplashActivity.startActivity(
                    Intent(this@SplashActivity, ChannelActivity::class.java))
            } else {
                this@SplashActivity.startActivity(
                    Intent(this@SplashActivity, LoginActivity::class.java))
            }
        }

    }
}
