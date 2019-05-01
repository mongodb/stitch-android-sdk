package com.mongodb.stitch.android.examples.chatsync

import android.content.Intent
import android.os.Bundle
import com.mongodb.stitch.android.examples.chatsync.repo.UserRepo
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

class SplashActivity: ScopeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        launch(IO) {
            if (stitch.auth.isLoggedIn) {
                UserRepo.findCurrentUser()?.run {
                    this@SplashActivity
                        .startActivity(Intent(this@SplashActivity, ChannelActivity::class.java))
                } ?: this@SplashActivity
                    .startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            } else {
                this@SplashActivity
                    .startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
        }
    }
}
