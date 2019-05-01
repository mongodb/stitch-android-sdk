package com.mongodb.stitch.android.examples.chatsync

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.view.MenuItem
import android.support.v4.widget.DrawerLayout
import android.support.design.widget.NavigationView
import android.support.v7.widget.Toolbar
import android.view.Menu
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.repo.UserRepo
import com.mongodb.stitch.android.examples.chatsync.service.ChannelService
import com.mongodb.stitch.android.examples.chatsync.service.ChannelServiceAction
import com.mongodb.stitch.android.examples.chatsync.viewModel.ChannelViewModel
import com.mvc.imagepicker.ImagePicker
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import java.io.File


class ChannelActivity : ScopeActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var channelViewModel: ChannelViewModel

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "default"
        toolbar.setNavigationIcon(R.drawable.mind_map_icn_24)

        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open,
            R.string.navigation_drawer_close)

        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)
        navView.menu.clear()

        launch(IO) {
            UserRepo.findCurrentUser()?.channelsSubscribedTo?.forEach { channelId ->
                launch(Main) {
                    navView.menu.add(0, channelId.hashCode(), Menu.NONE, channelId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        channelViewModel = ViewModelProviders.of(this).get(ChannelViewModel::class.java)

        setupToolbar()
    }

    override fun onResume() {
        super.onResume()
        channelViewModel = ViewModelProviders.of(this).get(ChannelViewModel::class.java)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (!::channelViewModel.isInitialized) {
            channelViewModel = ViewModelProviders.of(this).get(ChannelViewModel::class.java)
        }


        channelViewModel.selectChannel(this, "default").observe(
            this, object : Observer<ChannelServiceAction> {

            override fun onChanged(action: ChannelServiceAction?) {
                if (action != null) {
                    channelViewModel.channel.setAvatar(File(ImagePicker.getImagePathFromResult(
                        this@ChannelActivity, requestCode, resultCode, data)).readBytes())
                    channelViewModel.channel.removeObserver(this)
                }
            }

        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                launch(IO) {
                    Tasks.await(stitch.auth.logout())
                    launch(Main) {
                        startActivity(Intent(this@ChannelActivity, LoginActivity::class.java))
                    }
                }
                true
            }
            R.id.action_select_avatar -> {
                ImagePicker.pickImage(this, "Select your image:")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // TODO: Instantiate ChannelFragment when new channels are added
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onStart() {
        super.onStart()
        startService(Intent(this, ChannelService::class.java))
    }
}
