package com.mongodb.stitch.auth.oauth2.google

import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.StitchTestCase
import com.mongodb.stitch.admin.authProviders.GoogleMetadataFields
import com.mongodb.stitch.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.assertThat
import com.mongodb.stitch.await
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AndroidGoogleAuthProviderTest: StitchTestCase() {
    private val clientId = "405021717222-ijhi51r06t2t72u1rbdaqstc73hncjes.apps.googleusercontent.com"

    override fun setup() {
        super.setup()

        harness.addProvider(ProviderConfigs.Google(
                clientId = clientId,
                clientSecret = "pQH2vQo7Zyvn4dA0nzKwVKrpldXi9g1fkqXeTxh8"
        ))
    }

    @Test
    fun testGoogleAuthProviderInfo() {
        val google = await(stitchClient.authProviders).google
        assertNotNull(google)
        assertThat(google!!.config.clientId == clientId)
        assertThat(google.name == "oauth2-google")
        assertThat(google.type == "oauth2-google")
        assertNull(google.metadataFields)
    }
}
