package com.mongodb.stitch.android.examples.chatsync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import java.util.*

class LoginActivity : ScopeActivity() {
    private val usernameEditText by lazy { findViewById<EditText>(R.id.username_edit_text) }
    private val loginButton by lazy { findViewById<Button>(R.id.login_button) }

    private fun onLoginButtonClicked(v: View) {
        // disable the button so that it cannot be tapped again
        // until this handler is complete
        v.isEnabled = false

        launch(IO) {
            if (usernameEditText.text.length > 3 && usernameEditText.text.isNotEmpty()) {
                val stitchUser = Tasks.await(stitch.auth.loginWithCredential(AnonymousCredential()))
                user = User(stitchUser.id,
                    usernameEditText.text.toString(),
                    System.currentTimeMillis(),
                    Random().nextInt(7),
                    null,
                    listOf("default"))
                User.setCurrentUser(user)
                startActivity(Intent(this@LoginActivity, ChannelActivity::class.java))
            }
            launch(Main) {
                v.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        loginButton.setOnClickListener(::onLoginButtonClicked)
    }
}
