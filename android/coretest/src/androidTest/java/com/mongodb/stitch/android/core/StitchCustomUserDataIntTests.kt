package com.mongodb.stitch.android.core

import android.support.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest
import com.mongodb.stitch.core.admin.Apps
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.core.admin.create
import com.mongodb.stitch.core.admin.customUserData.CustomUserConfigData
import com.mongodb.stitch.core.admin.functions.FunctionCreator
import com.mongodb.stitch.core.admin.services.ServiceConfigs
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential
import com.mongodb.stitch.core.internal.net.Method
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class StitchCustomUserDataIntTests : BaseStitchAndroidIntTest() {
    private val email = "stitch@10gen.com"
    private val pass = "stitchuser"

    private val mongodbUri: String = "mongodb://localhost:26000"

    private val dbName = ObjectId().toHexString()
    private val collName = ObjectId().toHexString()

    private var client: StitchAppClient? = null
    private var app: Apps.App? = null
    private var svcId: String? = null

    private fun prepareService() {
        val (appResponse, app) = this.createApp()
        this.app = app
        this.addProvider(app, ProviderConfigs.Anon)

        val svc = this.addService(
            app,
            "mongodb",
            "mongodb1",
            ServiceConfigs.Mongo(
//                "mongodb1",
                mongodbUri))

        this.svcId = svc.first.id
        this.addRule(
            svc.second,
            RuleCreator.MongoDb(
                dbName,
                collName,
                listOf(RuleCreator.MongoDb.Role(
                    read = true, write = true
                )),
                RuleCreator.MongoDb.Schema(properties = Document()))
        )

        this.addProvider(
            app,
            ProviderConfigs.Userpass(
                "http://emailConfirmUrl.com",
                "http://resetPasswordUrl.com",
                "email subject",
                "password subject"))

        this.client = this.getAppClient(appResponse)
    }

    fun testCustomUserData() {
        prepareService()

        this.app!!.customUserData.create(
            data = CustomUserConfigData(mongoServiceId = svcId!!,
                databaseName = dbName,
                collectionName = collName,
                userIdField = "recoome",
                enabled = true),
            method = Method.PATCH)
        this.app!!.functions.create(
            data = FunctionCreator(
                name = "addUserProfile",
                source = """
             exports = async function(color) {
             const coll = context.services.get("mongodb1")
             .db("\(dbName)").collection("\(collName)");
             await coll.insertOne({
             "recoome": context.user.id,
             "favoriteColor": "blue"
             });
             return true;
             }
             """,
                canEvaluate = null))

        registerAndLoginWithUserPass(app!!, client!!, email, pass)


        assertEquals(client!!.auth.user!!.customData, Document())

        Tasks.await(client!!.callFunction("addUserProfile", listOf("blue")))

        assertEquals(client!!.auth.user!!.customData, Document())

        Tasks.await(client!!.auth.refreshCustomData())

        assertEquals(client!!.auth.user!!.customData["favoriteColor"], "blue")

        Tasks.await(client!!.auth.logout())

        assertNull(client!!.auth.user)

        Tasks.await(client!!.auth.loginWithCredential(UserPasswordCredential(email, pass)))

        assertEquals(client!!.auth.user!!.customData["favoriteColor"], "blue")
    }
}
