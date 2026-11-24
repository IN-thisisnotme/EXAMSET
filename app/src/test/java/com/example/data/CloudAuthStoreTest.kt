package com.example.data

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.model.CloudProviderType
import com.example.util.TrustedTimeManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudAuthStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("examset_cloud_vault_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        TrustedTimeManager.resetForTesting(context)
    }

    @After
    fun tearDown() {
        TrustedTimeManager.resetForTesting(context)
    }

    @Test
    fun oAuthTokenBundle_isAccessTokenValid_respectsTrustedTimeManagerClock() {
        val futureEpoch = 2_000_000_000_000L
        TrustedTimeManager.recordVerifiedNetworkEpoch(futureEpoch)

        val bundle = OAuthTokenBundle(
            accessToken = "ya29.test_access_token",
            refreshToken = "1//test_refresh_token",
            expiresAtMillis = futureEpoch + 300_000L
        )
        assertTrue(bundle.isAccessTokenValid())

        val expiredAgainstTrustedTime = OAuthTokenBundle(
            accessToken = "ya29.test_access_token",
            refreshToken = "1//test_refresh_token",
            expiresAtMillis = futureEpoch + 30_000L // within 90s buffer
        )
        assertFalse(expiredAgainstTrustedTime.isAccessTokenValid())
    }

    @Test
    fun cloudAuthStore_failsClosedWithoutPlaintextFallbackWhenKeystoreUnavailable() {
        val store = CloudAuthStore(context)
        val bundle = OAuthTokenBundle(
            accessToken = "secret_access_token",
            refreshToken = "secret_refresh_token",
            expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
            email = "candidate@example.com"
        )

        var failure: Throwable? = null
        try {
            store.saveTokenBundle(CloudProviderType.GOOGLE_DRIVE, bundle)
        } catch (t: Throwable) {
            failure = t
        }

        val prefs = context.getSharedPreferences("examset_cloud_vault_prefs", Context.MODE_PRIVATE)
        val rawStored = prefs.getString("enc_tokens_${CloudProviderType.GOOGLE_DRIVE.code}", null)

        if (failure != null) {
            assertTrue(failure is IllegalStateException)
            assertNull("Plaintext fallback must never be written on Keystore failure", rawStored)
        } else {
            assertNotNull(rawStored)
            assertTrue(
                "Stored token bundle must be encrypted with GCM prefix",
                rawStored!!.startsWith("GCM:")
            )
            assertFalse(rawStored.startsWith("PLAIN:"))
        }
    }

    @Test
    fun cloudAuthStore_readsLegacyPlaintextTokenBundleWithoutCrashing() {
        val legacyJson = JSONObject().apply {
            put("accessToken", "legacy_access")
            put("refreshToken", "legacy_refresh")
            put("expiresAtMillis", 1_900_000_000_000L)
            put("email", "legacy@example.com")
            put("displayName", "Legacy User")
        }.toString()
        val encoded = "PLAIN:${Base64.encodeToString(legacyJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"

        context.getSharedPreferences("examset_cloud_vault_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("enc_tokens_${CloudProviderType.ONEDRIVE.code}", encoded)
            .commit()

        val store = CloudAuthStore(context)
        val loaded = store.getTokenBundle(CloudProviderType.ONEDRIVE)
        assertNotNull(loaded)
        assertEquals("legacy_access", loaded?.accessToken)
        assertEquals("legacy_refresh", loaded?.refreshToken)
        assertEquals("legacy@example.com", loaded?.email)
    }

    @Test
    fun cloudAuthStore_consumePendingOAuthFlow_enforcesFifteenMinuteTtl() {
        val store = CloudAuthStore(context)
        val baseTime = 1_800_000_000_000L

        // Valid within 15-minute TTL (10 minutes elapsed)
        val validFlow = PendingOAuthFlowState(
            provider = CloudProviderType.ONEDRIVE,
            state = "state_valid",
            codeVerifier = "verifier_valid",
            clientId = "client_1",
            createdAtMillis = baseTime
        )
        store.savePendingOAuthFlow(validFlow)
        val consumedValid = store.consumePendingOAuthFlow(nowMillis = baseTime + 10L * 60L * 1000L)
        assertNotNull(consumedValid)
        assertEquals("state_valid", consumedValid?.state)
        assertNull("Pending OAuth state must be single-use", store.consumePendingOAuthFlow(nowMillis = baseTime))

        // Expired after 16 minutes
        store.savePendingOAuthFlow(validFlow.copy(state = "state_expired", createdAtMillis = baseTime))
        val consumedExpired = store.consumePendingOAuthFlow(nowMillis = baseTime + 16L * 60L * 1000L)
        assertNull("Expired pending OAuth state must be rejected", consumedExpired)

        // Future clock skew > 15 minutes rejected
        store.savePendingOAuthFlow(validFlow.copy(state = "state_future", createdAtMillis = baseTime + 16L * 60L * 1000L))
        val consumedFuture = store.consumePendingOAuthFlow(nowMillis = baseTime)
        assertNull("Excessive future-skewed pending OAuth state must be rejected", consumedFuture)

        // Zero timestamp rejected
        store.savePendingOAuthFlow(validFlow.copy(state = "state_zero", createdAtMillis = 0L))
        assertNull(store.consumePendingOAuthFlow(nowMillis = baseTime))
    }
}
