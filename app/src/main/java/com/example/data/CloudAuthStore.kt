package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.AutoBackupSchedule
import com.example.model.CloudAccountStatus
import com.example.model.CloudProviderType
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OAuthTokenBundle(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val email: String = "",
    val displayName: String = ""
) {
    fun isAccessTokenValid(bufferMillis: Long = 90_000L): Boolean {
        return accessToken.isNotBlank() && System.currentTimeMillis() + bufferMillis < expiresAtMillis
    }
}

data class PendingOAuthFlowState(
    val provider: CloudProviderType,
    val state: String,
    val codeVerifier: String,
    val clientId: String
)

class CloudAuthStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("examset_cloud_vault_prefs", Context.MODE_PRIVATE)

    fun getClientId(provider: CloudProviderType): String {
        val key = when (provider) {
            CloudProviderType.GOOGLE_DRIVE -> KEY_DRIVE_CLIENT_ID
            CloudProviderType.ONEDRIVE -> KEY_ONEDRIVE_CLIENT_ID
        }
        return prefs.getString(key, "")?.trim().orEmpty()
    }

    fun setClientId(provider: CloudProviderType, clientId: String) {
        val key = when (provider) {
            CloudProviderType.GOOGLE_DRIVE -> KEY_DRIVE_CLIENT_ID
            CloudProviderType.ONEDRIVE -> KEY_ONEDRIVE_CLIENT_ID
        }
        prefs.edit().putString(key, clientId.trim()).apply()
    }

    fun getTokenBundle(provider: CloudProviderType): OAuthTokenBundle? {
        val key = tokenKey(provider)
        val raw = prefs.getString(key, null) ?: return null
        val decrypted = decryptText(raw) ?: return null
        return try {
            val obj = JSONObject(decrypted)
            val access = obj.optString("accessToken", "")
            val refresh = obj.optString("refreshToken", "")
            if (access.isBlank() && refresh.isBlank()) return null
            OAuthTokenBundle(
                accessToken = access,
                refreshToken = refresh,
                expiresAtMillis = obj.optLong("expiresAtMillis", 0L),
                email = obj.optString("email", ""),
                displayName = obj.optString("displayName", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveTokenBundle(provider: CloudProviderType, bundle: OAuthTokenBundle) {
        val json = JSONObject().apply {
            put("accessToken", bundle.accessToken)
            put("refreshToken", bundle.refreshToken)
            put("expiresAtMillis", bundle.expiresAtMillis)
            put("email", bundle.email)
            put("displayName", bundle.displayName)
        }.toString()
        val encrypted = encryptText(json)
        prefs.edit().putString(tokenKey(provider), encrypted).apply()
    }

    fun clearTokenBundle(provider: CloudProviderType) {
        prefs.edit()
            .remove(tokenKey(provider))
            .remove(lastErrorKey(provider))
            .apply()
    }

    fun getLastSyncedAt(provider: CloudProviderType): Long {
        return prefs.getLong(lastSyncKey(provider), 0L)
    }

    fun setLastSyncedAt(provider: CloudProviderType, timestamp: Long) {
        prefs.edit()
            .putLong(lastSyncKey(provider), timestamp)
            .remove(lastErrorKey(provider))
            .apply()
    }

    fun getLastError(provider: CloudProviderType): String? {
        return prefs.getString(lastErrorKey(provider), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastError(provider: CloudProviderType, error: String?) {
        val editor = prefs.edit()
        if (error.isNullOrBlank()) editor.remove(lastErrorKey(provider))
        else editor.putString(lastErrorKey(provider), error)
        editor.apply()
    }

    fun getAutoBackupSchedule(): AutoBackupSchedule {
        return AutoBackupSchedule.fromCode(prefs.getString(KEY_AUTO_BACKUP_SCHEDULE, AutoBackupSchedule.OFF.code))
    }

    fun setAutoBackupSchedule(schedule: AutoBackupSchedule) {
        prefs.edit().putString(KEY_AUTO_BACKUP_SCHEDULE, schedule.code).apply()
    }

    fun getLastLocalSnapshotAt(): Long {
        return prefs.getLong(KEY_LAST_LOCAL_SNAPSHOT_AT, 0L)
    }

    fun setLastLocalSnapshotAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_LOCAL_SNAPSHOT_AT, timestamp).apply()
    }

    fun savePendingOAuthFlow(flow: PendingOAuthFlowState) {
        val json = JSONObject().apply {
            put("provider", flow.provider.code)
            put("state", flow.state)
            put("codeVerifier", flow.codeVerifier)
            put("clientId", flow.clientId)
        }.toString()
        prefs.edit().putString(KEY_PENDING_OAUTH, json).apply()
    }

    fun consumePendingOAuthFlow(): PendingOAuthFlowState? {
        val raw = prefs.getString(KEY_PENDING_OAUTH, null) ?: return null
        prefs.edit().remove(KEY_PENDING_OAUTH).apply()
        return try {
            val obj = JSONObject(raw)
            val provider = CloudProviderType.entries.firstOrNull { it.code == obj.optString("provider") } ?: return null
            PendingOAuthFlowState(
                provider = provider,
                state = obj.optString("state", ""),
                codeVerifier = obj.optString("codeVerifier", ""),
                clientId = obj.optString("clientId", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun buildAccountStatus(provider: CloudProviderType): CloudAccountStatus {
        val bundle = getTokenBundle(provider)
        val clientId = getClientId(provider)
        return CloudAccountStatus(
            provider = provider,
            isConnected = bundle != null && (bundle.accessToken.isNotBlank() || bundle.refreshToken.isNotBlank()),
            hasClientIdConfigured = clientId.isNotBlank(),
            accountEmail = bundle?.email.orEmpty(),
            accountName = bundle?.displayName.orEmpty(),
            lastSyncedAt = getLastSyncedAt(provider),
            lastError = getLastError(provider)
        )
    }

    private fun tokenKey(provider: CloudProviderType) = "enc_tokens_${provider.code}"
    private fun lastSyncKey(provider: CloudProviderType) = "last_sync_${provider.code}"
    private fun lastErrorKey(provider: CloudProviderType) = "last_err_${provider.code}"

    private fun getOrCreateAesKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptText(plain: String): String {
        return try {
            val key = getOrCreateAesKey() ?: return "PLAIN:${Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            "GCM:$ivB64:$ctB64"
        } catch (_: Exception) {
            "PLAIN:${Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
        }
    }

    private fun decryptText(encoded: String): String? {
        return try {
            if (encoded.startsWith("PLAIN:")) {
                val bytes = Base64.decode(encoded.removePrefix("PLAIN:"), Base64.NO_WRAP)
                return String(bytes, Charsets.UTF_8)
            }
            if (!encoded.startsWith("GCM:")) return null
            val parts = encoded.removePrefix("GCM:").split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val key = getOrCreateAesKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KEYSTORE_ALIAS = "examset_cloud_oauth_aes_v1"
        private const val KEY_DRIVE_CLIENT_ID = "oauth_client_id_google_drive"
        private const val KEY_ONEDRIVE_CLIENT_ID = "oauth_client_id_onedrive"
        private const val KEY_AUTO_BACKUP_SCHEDULE = "auto_backup_schedule"
        private const val KEY_LAST_LOCAL_SNAPSHOT_AT = "last_local_snapshot_at"
        private const val KEY_PENDING_OAUTH = "pending_oauth_flow"
    }
}
