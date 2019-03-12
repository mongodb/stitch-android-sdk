/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.android.examples.stresstests

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.tasks.Tasks

import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.core.StitchAppClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.Sync
import com.mongodb.stitch.android.services.mongodb.remote.internal.RemoteMongoClientImpl
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler

import org.bson.Document
import org.bson.types.Binary
import org.bson.types.ObjectId
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.bson.BsonObjectId
import org.bson.BsonValue
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val insertManyDocsButton by lazy { findViewById<Button>(R.id.button3) }
    private val clearDocsButton by lazy { findViewById<Button>(R.id.button2) }
    private val doSyncPassButton by lazy { findViewById<Button>(R.id.doSyncPass) }

    private val etManyDocs by lazy { findViewById<EditText>(R.id.numInput) }
    private val sizeOfDocs by lazy { findViewById<EditText>(R.id.size_input) }

    private val label by lazy { findViewById<TextView>(R.id.textView) }
    private val timerTextView by lazy { findViewById<TextView>(R.id.timer_textview) }

    private var stitchAppClient: StitchAppClient? = null
    private var coll: RemoteMongoCollection<Document>? = null
    private var syncedColl: Sync<Document>? = null

    private suspend fun initializeSync() = coroutineScope {
        withContext(coroutineContext) {
            Tasks.await(stitchAppClient!!.auth.loginWithCredential(AnonymousCredential()))
        }

        coll = stitchAppClient!!
            .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
            .getDatabase("stress")
            .getCollection("tests")

        syncedColl = coll!!.sync()
        syncedColl!!.configure(
            ConflictHandler { _, _, _ -> null },
            ChangeEventListener { _, _ ->
            },
            ExceptionListener { documentId, error ->
                Log.e(
                    TAG, String.format("Got sync error for doc %s: %s", documentId, error)
                )
            }
        )
        (stitchAppClient!!
            .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas") as RemoteMongoClientImpl)
            .dataSynchronizer.disableSyncThread()
        updateLabels()
    }

    private suspend fun doSyncPass() = coroutineScope {
        measureTimeMillis {
            val startTime = System.currentTimeMillis()
            val cr = launch(IO) {
                while (isActive) {
                    withContext(Main) {
                        timerTextView.text =
                            "${(System.currentTimeMillis() - startTime).toFloat() / 1000.0} seconds"
                    }
                    Thread.sleep(30)
                }
            }
            withContext(coroutineContext) {
                (stitchAppClient!!
                    .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
                    as RemoteMongoClientImpl)
                    .dataSynchronizer.doSyncPass()
            }

            cr.cancel()
        }
    }

    private suspend fun updateLabels() = coroutineScope {
        val syncedIds = withContext(IO) { Tasks.await(syncedColl!!.syncedIds) }
        withContext(Main) { label!!.text ="# of synced docs: ${syncedIds.size}" }
    }

    private fun showDialog(): AlertDialog {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.progress, null)
        builder.setView(view)
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
        return dialog
    }

    private suspend fun insertManyDocuments(numberOfDocs: Int,
                                            sizeOfDocsInBytes: Int) = coroutineScope {
        val dialog = withContext(Main) { showDialog() }

        val loadingMsg = dialog.findViewById<TextView>(R.id.loading_msg)!!

        val user = stitchAppClient!!.auth.user ?: return@coroutineScope

        val array: List<Byte> = (0 until sizeOfDocsInBytes).map { 0.toByte() }
        val docs: List<Document> = (0 until numberOfDocs).map {
            Document(mapOf(
                "_id" to ObjectId(),
                "owner_id" to user.id,
                "bin" to Binary(array.toByteArray())
            ))
        }

        var batch = 1
        for (it in docs.chunked(500)) {
            withContext(Main) { loadingMsg.text = "inserting batch $batch of ${numberOfDocs/500}" }
            batch++
            Tasks.await(coll!!.insertMany(it))
        }

        val ids: Array<BsonValue> = docs.map {
            BsonObjectId(it["_id"] as ObjectId) as BsonValue
        }.toTypedArray()

        withContext(Main) { loadingMsg.text = "running syncMany" }
        Tasks.await(syncedColl!!.syncMany(*ids))
        withContext(Main) { dialog.dismiss() }
        return@coroutineScope
    }

    private suspend fun clearAllDocuments() = coroutineScope {
        if (!stitchAppClient!!.auth.isLoggedIn) {
            return@coroutineScope
        }

        withContext(coroutineContext) {
            val syncedIds = Tasks.await(syncedColl!!.syncedIds)
            if (syncedIds.size > 0) {
                Tasks.await(syncedColl!!.desyncMany(*syncedIds.toTypedArray()))
                (stitchAppClient!!
                    .getServiceClient(RemoteMongoClient.factory, "mongodb-atlas")
                    as RemoteMongoClientImpl).dataSynchronizer.doSyncPass()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Stitch.hasAppClient("stitch-tests-js-sdk-jntlj")) {
            Stitch.initializeDefaultAppClient("stitch-tests-js-sdk-jntlj")
        }

        stitchAppClient = Stitch.getDefaultAppClient()
        launch(IO) {
            initializeSync()
        }

        insertManyDocsButton.setOnClickListener {
            launch {
                val numberOfDocsToInsert = Integer.decode(etManyDocs.text.toString())
                val sizeOfDocsToInsert = Integer.decode(sizeOfDocs.text.toString())

                withContext(IO) { insertManyDocuments(numberOfDocsToInsert, sizeOfDocsToInsert) }

                updateLabels()
            }
        }

        clearDocsButton!!.setOnClickListener {
            launch {
                val dialog = showDialog()
                dialog.findViewById<TextView>(R.id.loading_msg)!!.text = "desyncing documents"
                withContext(IO) { clearAllDocuments() }
                updateLabels()
                dialog.dismiss()
            }
        }

        doSyncPassButton.setOnClickListener {
            launch {
                val dialog = showDialog()
                dialog.findViewById<TextView>(R.id.loading_msg)!!.text = "doing sync pass"
                withContext(IO) { doSyncPass() }
                dialog.dismiss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel() // CoroutineScope.cancel
    }

    companion object {
        private val TAG = MainActivity::class.java.name
    }
}
