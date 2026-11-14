package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.data.CloudAuthStore
import com.example.data.OAuthTokenBundle
import com.example.data.PendingOAuthFlowState
import com.example.model.CloudProviderType
import com.example.model.RemoteCloudBackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object CloudBackupClients {

    const val REDIRECT_URI = "com.aistudio.examset.tbqpeh:/oauth2redirect"
    private const val DRIVE_FOLDER_NAME = "ExamSet Backups"
    private const val ONEDRIVE_FOLDER_NAME = "ExamSetBackups"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun buildAuthorizeIntent(
        provider: CloudProviderType,
        authStore: CloudAuthStore
    ): Result<Intent> {
        val clientId = authStore.getClientId(provider)
        if (clientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Set your ${provider.displayName} OAuth Client ID in Cloud Sync Settings first, or use the zero-config Save/Open via Drive & OneDrive System Picker."
                )
            )
        }

        val state = generateRandomUrlSafe(24)
        val verifier = generateRandomUrlSafe(48)
        val challenge = sha256UrlSafe(verifier)

        authStore.savePendingOAuthFlow(
            PendingOAuthFlowState(
                provider = provider,
                state = state,
                codeVerifier = verifier,
                clientId = clientId
            )
        )

        val authUri = when (provider) {
            CloudProviderType.GOOGLE_DRIVE -> {
                Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
                    .appendQueryParameter("client_id", clientId)
                    .appendQueryParameter("redirect_uri", REDIRECT_URI)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("scope", "https://www.googleapis.com/auth/drive.file email profile")
                    .appendQueryParameter("code_challenge", challenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("access_type", "offline")
                    .appendQueryParameter("prompt", "consent")
                    .appendQueryParameter("state", state)
                    .build()
            }
            CloudProviderType.ONEDRIVE -> {
                Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize").buildUpon()
                    .appendQueryParameter("client_id", clientId)
                    .appendQueryParameter("redirect_uri", REDIRECT_URI)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("scope", "Files.ReadWrite.AppFolder offline_access User.Read")
                    .appendQueryParameter("code_challenge", challenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("state", state)
                    .build()
            }
        }

        val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Result.success(intent)
    }

    suspend fun handleOAuthRedirect(
        uri: Uri,
        authStore: CloudAuthStore
    ): Result<CloudProviderType> = withContext(Dispatchers.IO) {
        val pending = authStore.consumePendingOAuthFlow()
            ?: return@withContext Result.failure(IllegalStateException("No active OAuth sign-in flow found."))

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val desc = uri.getQueryParameter("error_description") ?: error
            authStore.setLastError(pending.provider, desc)
            return@withContext Result.failure(IllegalStateException("${pending.provider.displayName} sign-in cancelled: $desc"))
        }

        val returnedState = uri.getQueryParameter("state")
        if (returnedState != pending.state) {
            return@withContext Result.failure(IllegalStateException("OAuth state mismatch. Please retry sign-in."))
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authorization code missing in redirect."))
        }

        val tokenEndpoint = when (pending.provider) {
            CloudProviderType.GOOGLE_DRIVE -> "https://oauth2.googleapis.com/token"
            CloudProviderType.ONEDRIVE -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        }

        val form = FormBody.Builder()
            .add("client_id", pending.clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", pending.codeVerifier)
            .build()

        val req = Request.Builder().url(tokenEndpoint).post(form).build()
        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = extractOAuthError(bodyStr, "HTTP ${resp.code}")
                authStore.setLastError(pending.provider, msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }
            val obj = JSONObject(bodyStr)
            val accessToken = obj.optString("access_token", "")
            val refreshToken = obj.optString("refresh_token", "")
            val expiresIn = obj.optLong("expires_in", 3600L)
            if (accessToken.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Empty access token received."))
            }

            val (email, name) = fetchProfileInfo(pending.provider, accessToken)
            val bundle = OAuthTokenBundle(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000L),
                email = email,
                displayName = name
            )
            authStore.saveTokenBundle(pending.provider, bundle)
            authStore.setLastError(pending.provider, null)
            Result.success(pending.provider)
        }
    }

    suspend fun ensureValidAccessToken(
        provider: CloudProviderType,
        authStore: CloudAuthStore
    ): Result<String> = withContext(Dispatchers.IO) {
        val current = authStore.getTokenBundle(provider)
            ?: return@withContext Result.failure(IllegalStateException("${provider.displayName} is not connected."))
        if (current.isAccessTokenValid()) {
            return@withContext Result.success(current.accessToken)
        }
        val clientId = authStore.getClientId(provider)
        if (current.refreshToken.isBlank() || clientId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("${provider.displayName} session expired. Please reconnect."))
        }

        val tokenEndpoint = when (provider) {
            CloudProviderType.GOOGLE_DRIVE -> "https://oauth2.googleapis.com/token"
            CloudProviderType.ONEDRIVE -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        }

        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", current.refreshToken)
            .build()

        val req = Request.Builder().url(tokenEndpoint).post(form).build()
        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = extractOAuthError(bodyStr, "Token refresh failed (HTTP ${resp.code})")
                authStore.setLastError(provider, msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }
            val obj = JSONObject(bodyStr)
            val newAccess = obj.optString("access_token", "")
            val newRefresh = obj.optString("refresh_token", "").ifBlank { current.refreshToken }
            val expiresIn = obj.optLong("expires_in", 3600L)
            if (newAccess.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Empty refreshed access token."))
            }
            val updated = current.copy(
                accessToken = newAccess,
                refreshToken = newRefresh,
                expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000L)
            )
            authStore.saveTokenBundle(provider, updated)
            Result.success(newAccess)
        }
    }

    suspend fun uploadBackupFile(
        provider: CloudProviderType,
        authStore: CloudAuthStore,
        vaultFile: File
    ): Result<RemoteCloudBackupFile> = withContext(Dispatchers.IO) {
        val token = ensureValidAccessToken(provider, authStore).getOrElse {
            return@withContext Result.failure(it)
        }
        return@withContext runCatching {
            when (provider) {
                CloudProviderType.GOOGLE_DRIVE -> uploadToGoogleDrive(token, vaultFile)
                CloudProviderType.ONEDRIVE -> uploadToOneDrive(token, vaultFile)
            }
        }.onSuccess {
            authStore.setLastSyncedAt(provider, System.currentTimeMillis())
        }.onFailure { err ->
            authStore.setLastError(provider, err.localizedMessage ?: "Upload failed")
        }
    }

    suspend fun listRemoteBackups(
        provider: CloudProviderType,
        authStore: CloudAuthStore
    ): Result<List<RemoteCloudBackupFile>> = withContext(Dispatchers.IO) {
        val token = ensureValidAccessToken(provider, authStore).getOrElse {
            return@withContext Result.failure(it)
        }
        return@withContext runCatching {
            when (provider) {
                CloudProviderType.GOOGLE_DRIVE -> listGoogleDriveBackups(token)
                CloudProviderType.ONEDRIVE -> listOneDriveBackups(token)
            }
        }.onFailure { err ->
            authStore.setLastError(provider, err.localizedMessage ?: "Failed to list cloud backups")
        }
    }

    suspend fun downloadRemoteBackup(
        context: Context,
        remoteFile: RemoteCloudBackupFile,
        authStore: CloudAuthStore
    ): Result<File> = withContext(Dispatchers.IO) {
        val token = ensureValidAccessToken(remoteFile.provider, authStore).getOrElse {
            return@withContext Result.failure(it)
        }
        return@withContext runCatching {
            val dest = File(VaultArchiveEngine.getExportCacheDir(context), remoteFile.name)
            val url = when (remoteFile.provider) {
                CloudProviderType.GOOGLE_DRIVE ->
                    "https://www.googleapis.com/drive/v3/files/${Uri.encode(remoteFile.remoteId)}?alt=media"
                CloudProviderType.ONEDRIVE ->
                    "https://graph.microsoft.com/v1.0/me/drive/items/${Uri.encode(remoteFile.remoteId)}/content"
            }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Download failed (HTTP ${resp.code})")
                }
                val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty download body")
                BufferedOutputStream(FileOutputStream(dest)).use { out ->
                    stream.copyTo(out, bufferSize = 16 * 1024)
                }
            }
            dest
        }
    }

    // --- Google Drive v3 Implementation ---

    private fun ensureGoogleDriveBackupFolder(token: String): String {
        val query = Uri.encode("name='$DRIVE_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
        val searchReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)&spaces=drive")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(searchReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Google Drive lookup failed (HTTP ${resp.code})")
            val files = JSONObject(body).optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).getString("id")
            }
        }

        val metaJson = JSONObject().apply {
            put("name", DRIVE_FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString()
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(metaJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(createReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Unable to create Google Drive folder (HTTP ${resp.code})")
            return JSONObject(body).getString("id")
        }
    }

    private fun uploadToGoogleDrive(token: String, vaultFile: File): RemoteCloudBackupFile {
        val folderId = ensureGoogleDriveBackupFolder(token)
        val metadataJson = JSONObject().apply {
            put("name", vaultFile.name)
            put("parents", org.json.JSONArray().put(folderId))
        }.toString()

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(vaultFile.asRequestBody("application/zip".toMediaType()))
            .build()

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime")
            .header("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Google Drive upload failed (HTTP ${resp.code})")
            }
            val obj = JSONObject(body)
            return RemoteCloudBackupFile(
                provider = CloudProviderType.GOOGLE_DRIVE,
                remoteId = obj.getString("id"),
                name = obj.optString("name", vaultFile.name),
                sizeBytes = obj.optString("size", vaultFile.length().toString()).toLongOrNull() ?: vaultFile.length(),
                modifiedAt = parseIsoTimestamp(obj.optString("modifiedTime", ""))
            )
        }
    }

    private fun listGoogleDriveBackups(token: String): List<RemoteCloudBackupFile> {
        val folderId = ensureGoogleDriveBackupFolder(token)
        val q = Uri.encode("'$folderId' in parents and trashed=false")
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&orderBy=modifiedTime%20desc&pageSize=25&fields=files(id,name,size,modifiedTime)")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Google Drive list failed (HTTP ${resp.code})")
            }
            val arr = JSONObject(body).optJSONArray("files") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        RemoteCloudBackupFile(
                            provider = CloudProviderType.GOOGLE_DRIVE,
                            remoteId = o.getString("id"),
                            name = o.optString("name", "backup.examset.zip"),
                            sizeBytes = o.optString("size", "0").toLongOrNull() ?: 0L,
                            modifiedAt = parseIsoTimestamp(o.optString("modifiedTime", ""))
                        )
                    )
                }
            }
        }
    }

    // --- OneDrive (Microsoft Graph AppFolder) Implementation ---

    private fun uploadToOneDrive(token: String, vaultFile: File): RemoteCloudBackupFile {
        val encodedName = Uri.encode(vaultFile.name)
        val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$ONEDRIVE_FOLDER_NAME/$encodedName:/content"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .put(vaultFile.asRequestBody("application/zip".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("OneDrive upload failed (HTTP ${resp.code})")
            }
            val obj = JSONObject(body)
            return RemoteCloudBackupFile(
                provider = CloudProviderType.ONEDRIVE,
                remoteId = obj.getString("id"),
                name = obj.optString("name", vaultFile.name),
                sizeBytes = obj.optLong("size", vaultFile.length()),
                modifiedAt = parseIsoTimestamp(obj.optString("lastModifiedDateTime", ""))
            )
        }
    }

    private fun listOneDriveBackups(token: String): List<RemoteCloudBackupFile> {
        val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$ONEDRIVE_FOLDER_NAME:/children?\$orderby=lastModifiedDateTime%20desc&\$top=25"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 404) return emptyList()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("OneDrive list failed (HTTP ${resp.code})")
            }
            val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.has("folder")) continue
                    add(
                        RemoteCloudBackupFile(
                            provider = CloudProviderType.ONEDRIVE,
                            remoteId = o.getString("id"),
                            name = o.optString("name", "backup.examset.zip"),
                            sizeBytes = o.optLong("size", 0L),
                            modifiedAt = parseIsoTimestamp(o.optString("lastModifiedDateTime", ""))
                        )
                    )
                }
            }
        }
    }

    private fun fetchProfileInfo(provider: CloudProviderType, accessToken: String): Pair<String, String> {
        return try {
            val url = when (provider) {
                CloudProviderType.GOOGLE_DRIVE -> "https://www.googleapis.com/oauth2/v2/userinfo"
                CloudProviderType.ONEDRIVE -> "https://graph.microsoft.com/v1.0/me"
            }
            val req = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return "" to ""
                val obj = JSONObject(resp.body?.string().orEmpty())
                when (provider) {
                    CloudProviderType.GOOGLE_DRIVE ->
                        obj.optString("email", "") to obj.optString("name", "")
                    CloudProviderType.ONEDRIVE -> {
                        val mail = obj.optString("mail", "").ifBlank { obj.optString("userPrincipalName", "") }
                        mail to obj.optString("displayName", "")
                    }
                }
            }
        } catch (_: Exception) {
            "" to ""
        }
    }

    private fun generateRandomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256UrlSafe(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun extractOAuthError(body: String, fallback: String): String {
        return try {
            val obj = JSONObject(body)
            obj.optString("error_description", "").ifBlank {
                obj.optString("error", fallback)
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseIsoTimestamp(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(raw)?.time?.let { return it }
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
