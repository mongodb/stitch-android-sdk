package com.mongodb.stitch.auth

import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.android.StitchException
import com.mongodb.stitch.android.auth.DecodedJWT
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Test decoding JWT and the possible errors it could throw.
 */
@RunWith(AndroidJUnit4::class)
class DecodedJWTTest {
    companion object {
        const val VALID_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjMyNTAzNjk4MDAwLCJuYW1lIjoidGVzdC1qd3QtdG9rZW4ifQ.BS2VIFFc9AI9_6xlSXZAxwfI_oueaPaJameTynQs8ZA";
        const val SHORT_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.ZdfK0WTpBrR-vbGVDMbkycnyP3QqQpRTXLKkkqr6tXU";
        const val INVALID_EXP = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOiJoZWxsbyIsIm5hbWUiOiJ0ZXN0LWp3dC10b2tlbiJ9.E5eTRYDTM1_xnOpSoCmirUGsEUPRP_TxVBvQHMPcunM";
        const val INVALID_NAME = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjMyNTAzNjk4MDAwLCJuYW1lIjoxMjN9.bwjifRY5DBCby4yrwxlSk6cFI5bXOAFiYimOHa-9QN4";
        const val INVALID_JSON = "this.is.invalid";
    }

    @Test
    fun testValidAccessToken() {
        val decodedToken = DecodedJWT(VALID_ACCESS_TOKEN)
        assertEquals(32503698000, decodedToken.expiration)
        assertEquals("test-jwt-token", decodedToken.name)
    }

    @Test
    fun testShortAccessToken() {
        val malformedJWTException = "Malformed JWT token. The string $SHORT_ACCESS_TOKEN should have 3 parts."
        assertFailsWith(StitchException.StitchRequestException::class, malformedJWTException) {
            DecodedJWT(SHORT_ACCESS_TOKEN)
        }
    }

    @Test
    fun testInvalidName() {
        val decodedToken = DecodedJWT(INVALID_NAME)
        val malformedJWTException = "Malformed JWT token. The name field must be a string."
        assertFailsWith(StitchException.StitchRequestException::class, malformedJWTException) {
            decodedToken.name
        }
    }

    @Test
    fun testInvalidExpiration() {
        val decodedToken = DecodedJWT(INVALID_EXP)
        val malformedJWTException = "Malformed JWT token. The expiration field must be a number."
        assertFailsWith(StitchException.StitchRequestException::class, malformedJWTException) {
            decodedToken.expiration
        }
    }

    @Test
    fun testInvalidJson() {
        val malformedJWTException = "The string $INVALID_JSON doesn't have a valid JSON format."
        assertFailsWith(StitchException.StitchRequestException::class, malformedJWTException) {
            DecodedJWT(INVALID_JSON)
        }
    }
}
