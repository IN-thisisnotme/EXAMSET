package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.CloudAuthStore
import com.example.data.OAuthTokenBundle
import com.example.data.PendingOAuthFlowState
import com.example.model.CloudProviderType
import com.example.model.RemoteCloudBackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private class CloudHttpStatusException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)

object CloudBackupClients {

    val REDIRECT_URI: String
        get() = "${BuildConfig.APPLICATION_ID}:/oauth2redirect"

    private const val DRIVE_FOLDER_NAME = "ExamSet Backups"
    private const val ONEDRIVE_FOLDER_NAME = "ExamSetBackups"
    private const val ONEDRIVE_SIMPLE_UPLOAD_LIMIT_BYTES = 4L * 1024L * 1024L
    // Microsoft Graph requires upload chunk sizes to be multiples of 320 KiB
    private const val ONEDRIVE_CHUNK_SIZE_BYTES = 320 * 1024 * 10 // 3.2 MiB

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun isDeviceOnline(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun buildAuthorizeIntent(
        provider: CloudProviderType,
        authStore: CloudAuthStore,
        callerContext: Context? = null
    ): Result<Intent> {
        if (callerContext != null && !isDeviceOnline(callerContext)) {
            return Result.failure(
                IllegalStateException("No active internet connection. Connect to Wi-Fi or mobile data and try again.")
            )
        }

        val clientId = authStore.getClientId(provider).trim()
        if (clientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Set your ${provider.displayName} OAuth Client ID first, or use Quick Cloud File Sync above with zero setup."
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
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (callerContext !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return Result.success(intent)
    }

    suspend fun handleOAuthRedirect(
        uri: Uri,
        authStore: CloudAuthStore
    ): Result<CloudProviderType> = withContext(Dispatchers.IO) {
        val pending = authStore.consumePendingOAuthFlow()
            ?: return@withContext Result.failure(IllegalStateException("No active OAuth sign-in flow found. Please tap Connect again."))

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
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = extractOAuthError(bodyStr, "Token exchange failed (HTTP ${resp.code})")
                    authStore.setLastError(pending.provider, msg)
                    throw IllegalStateException(msg)
                }
                val obj = JSONObject(bodyStr)
                val accessToken = obj.optString("access_token", "")
                val refreshToken = obj.optString("refresh_token", "")
                val expiresIn = obj.optLong("expires_in", 3600L)
                if (accessToken.isBlank()) {
                    throw IllegalStateException("Empty access token received from ${pending.provider.displayName}.")
                }

                val (email, name) = fetchProfileInfo(pending.provider, accessToken)
                val bundle = OAuthTokenBundle(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMillis = TrustedTimeManager.getTrustedTimeMillis() + (expiresIn * 1000L),
                    email = email,
                    displayName = name
                )
                try {
                    authStore.saveTokenBundle(pending.provider, bundle)
                } catch (secErr: Exception) {
                    val msg = secErr.localizedMessage ?: "Failed to securely store OAuth credentials."
                    authStore.setLastError(pending.provider, msg)
                    throw IllegalStateException(msg, secErr)
                }
                authStore.setLastError(pending.provider, null)
                pending.provider
            }
        }
    }

    suspend fun ensureValidAccessToken(
        provider: CloudProviderType,
        authStore: CloudAuthStore,
        forceRefresh: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val current = authStore.getTokenBundle(provider)
            ?: return@withContext Result.failure(IllegalStateException("${provider.displayName} is not connected."))
        if (!forceRefresh && current.isAccessTokenValid()) {
            return@withContext Result.success(current.accessToken)
        }
        val clientId = authStore.getClientId(provider)
        if (current.refreshToken.isBlank() || clientId.isBlank()) {
            val msg = "${provider.displayName} session expired. Please reconnect your account."
            authStore.setLastError(provider, msg)
            return@withContext Result.failure(IllegalStateException(msg))
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
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = extractOAuthError(bodyStr, "Token refresh failed (HTTP ${resp.code}). Please reconnect.")
                    authStore.setLastError(provider, msg)
                    throw IllegalStateException(msg)
                }
                val obj = JSONObject(bodyStr)
                val newAccess = obj.optString("access_token", "")
                val newRefresh = obj.optString("refresh_token", "").ifBlank { current.refreshToken }
                val expiresIn = obj.optLong("expires_in", 3600L)
                if (newAccess.isBlank()) {
                    throw IllegalStateException("Empty refreshed access token.")
                }
                val updated = current.copy(
                    accessToken = newAccess,
                    refreshToken = newRefresh,
                    expiresAtMillis = TrustedTimeManager.getTrustedTimeMillis() + (expiresIn * 1000L)
                )
                try {
                    authStore.saveTokenBundle(provider, updated)
                } catch (secErr: Exception) {
                    val msg = secErr.localizedMessage ?: "Failed to securely store refreshed OAuth credentials."
                    authStore.setLastError(provider, msg)
                    throw IllegalStateException(msg, secErr)
                }
                authStore.setLastError(provider, null)
                newAccess
            }
        }
    }

    private suspend fun <T> executeWithTokenRetry(
        provider: CloudProviderType,
        authStore: CloudAuthStore,
        block: (accessToken: String) -> T
    ): Result<T> {
        val initialToken = ensureValidAccessToken(provider, authStore, forceRefresh = false)
            .getOrElse { return Result.failure(it) }

        return try {
            Result.success(block(initialToken))
        } catch (httpErr: CloudHttpStatusException) {
            if (httpErr.statusCode == 401) {
                val refreshedToken = ensureValidAccessToken(provider, authStore, forceRefresh = true)
                    .getOrElse { return Result.failure(it) }
                runCatching { block(refreshedToken) }
            } else {
                Result.failure(httpErr)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBackupFile(
        provider: CloudProviderType,
        authStore: CloudAuthStore,
        vaultFile: File,
        context: Context? = null
    ): Result<RemoteCloudBackupFile> = withContext(Dispatchers.IO) {
        if (context != null && !isDeviceOnline(context)) {
            val msg = "No active internet connection. Connect to Wi-Fi or mobile data and retry."
            authStore.setLastError(provider, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }
        executeWithTokenRetry(provider, authStore) { token ->
            when (provider) {
                CloudProviderType.GOOGLE_DRIVE -> uploadToGoogleDriveResumable(token, vaultFile)
                CloudProviderType.ONEDRIVE -> uploadToOneDrive(token, vaultFile)
            }
        }.onSuccess {
            authStore.setLastSyncedAt(provider, TrustedTimeManager.getTrustedTimeMillis())
        }.onFailure { err ->
            authStore.setLastError(provider, err.localizedMessage ?: "Upload failed")
        }
    }

    suspend fun listRemoteBackups(
        provider: CloudProviderType,
        authStore: CloudAuthStore,
        context: Context? = null
    ): Result<List<RemoteCloudBackupFile>> = withContext(Dispatchers.IO) {
        if (context != null && !isDeviceOnline(context)) {
            val msg = "No active internet connection. Connect to Wi-Fi or mobile data to browse cloud backups."
            authStore.setLastError(provider, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }
        executeWithTokenRetry(provider, authStore) { token ->
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
        if (!isDeviceOnline(context)) {
            return@withContext Result.failure(
                IllegalStateException("No active internet connection. Connect to Wi-Fi or mobile data to download backup.")
            )
        }
        executeWithTokenRetry(remoteFile.provider, authStore) { token ->
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
                    val body = resp.body?.string().orEmpty()
                    throw CloudHttpStatusException(
                        resp.code,
                        extractApiError(body, "Download failed (HTTP ${resp.code})")
                    )
                }
                val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty download body")
                BufferedOutputStream(FileOutputStream(dest)).use { out ->
                    stream.copyTo(out, bufferSize = 16 * 1024)
                }
            }
            dest
        }
    }

    // --- Google Drive v3 Implementation (Resumable Upload for All Sizes) ---

    private fun ensureGoogleDriveBackupFolder(token: String): String {
        val query = Uri.encode("name='$DRIVE_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
        val searchReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)&spaces=drive")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(searchReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "Google Drive folder lookup failed (HTTP ${resp.code})")
                )
            }
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
            if (!resp.isSuccessful) {
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "Unable to create Google Drive backup folder (HTTP ${resp.code})")
                )
            }
            return JSONObject(body).getString("id")
        }
    }

    private fun uploadToGoogleDriveResumable(token: String, vaultFile: File): RemoteCloudBackupFile {
        val folderId = ensureGoogleDriveBackupFolder(token)
        val fileLength = vaultFile.length()
        val metadataJson = JSONObject().apply {
            put("name", vaultFile.name)
            put("mimeType", "application/zip")
            put("parents", JSONArray().put(folderId))
        }.toString()

        // Step 1: Initiate resumable upload session
        val initReq = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,size,modifiedTime")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", "application/zip")
            .header("X-Upload-Content-Length", fileLength.toString())
            .post(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val uploadSessionUri = httpClient.newCall(initReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "Google Drive upload session failed (HTTP ${resp.code})")
                )
            }
            resp.header("Location")
                ?: throw IllegalStateException("Google Drive did not return a resumable upload URL.")
        }

        // Step 2: Stream ZIP payload directly to the resumable session URI
        val uploadReq = Request.Builder()
            .url(uploadSessionUri)
            .put(vaultFile.asRequestBody("application/zip".toMediaType()))
            .build()

        httpClient.newCall(uploadReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "Google Drive upload failed (HTTP ${resp.code})")
                )
            }
            val obj = JSONObject(body)
            return RemoteCloudBackupFile(
                provider = CloudProviderType.GOOGLE_DRIVE,
                remoteId = obj.getString("id"),
                name = obj.optString("name", vaultFile.name),
                sizeBytes = obj.optString("size", fileLength.toString()).toLongOrNull() ?: fileLength,
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
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "Google Drive list failed (HTTP ${resp.code})")
                )
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

    // --- OneDrive (Microsoft Graph AppFolder — Simple <=4MB, Chunked Session >4MB) ---

    private fun uploadToOneDrive(token: String, vaultFile: File): RemoteCloudBackupFile {
        val fileLength = vaultFile.length()
        return if (fileLength <= ONEDRIVE_SIMPLE_UPLOAD_LIMIT_BYTES) {
            uploadToOneDriveSimple(token, vaultFile)
        } else {
            uploadToOneDriveResumable(token, vaultFile)
        }
    }

    private fun uploadToOneDriveSimple(token: String, vaultFile: File): RemoteCloudBackupFile {
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
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "OneDrive upload failed (HTTP ${resp.code})")
                )
            }
            return parseOneDriveItem(JSONObject(body), vaultFile)
        }
    }

    private fun uploadToOneDriveResumable(token: String, vaultFile: File): RemoteCloudBackupFile {
        val encodedName = Uri.encode(vaultFile.name)
        val sessionUrlEndpoint =
            "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$ONEDRIVE_FOLDER_NAME/$encodedName:/createUploadSession"
        val sessionPayload = JSONObject().apply {
            put(
                "item",
                JSONObject().apply {
                    put("@microsoft.graph.conflictBehavior", "replace")
                    put("name", vaultFile.name)
                }
            )
        }.toString()

        val initReq = Request.Builder()
            .url(sessionUrlEndpoint)
            .header("Authorization", "Bearer $token")
            .post(sessionPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val uploadUrl = httpClient.newCall(initReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "OneDrive upload session failed (HTTP ${resp.code})")
                )
            }
            JSONObject(body).optString("uploadUrl", "").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("OneDrive did not return an upload session URL.")
        }

        val totalSize = vaultFile.length()
        val buffer = ByteArray(ONEDRIVE_CHUNK_SIZE_BYTES)
        var offset = 0L
        var finalItemJson: JSONObject? = null

        FileInputStream(vaultFile).use { input ->
            while (offset < totalSize) {
                val toRead = minOf(ONEDRIVE_CHUNK_SIZE_BYTES.toLong(), totalSize - offset).toInt()
                var bytesReadTotal = 0
                while (bytesReadTotal < toRead) {
                    val read = input.read(buffer, bytesReadTotal, toRead - bytesReadTotal)
                    if (read == -1) break
                    bytesReadTotal += read
                }
                if (bytesReadTotal <= 0) break

                val rangeEnd = offset + bytesReadTotal - 1L
                val chunkBody = buffer.toRequestBody(
                    contentType = "application/octet-stream".toMediaType(),
                    offset = 0,
                    byteCount = bytesReadTotal
                )
                val chunkReq = Request.Builder()
                    .url(uploadUrl)
                    .header("Content-Range", "bytes $offset-$rangeEnd/$totalSize")
                    .put(chunkBody)
                    .build()

                httpClient.newCall(chunkReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.code == 200 || resp.code == 201) {
                        finalItemJson = JSONObject(body)
                    } else if (resp.code != 202) {
                        throw CloudHttpStatusException(
                            resp.code,
                            extractApiError(body, "OneDrive chunk upload failed (HTTP ${resp.code})")
                        )
                    }
                }
                offset += bytesReadTotal
            }
        }

        val completed = finalItemJson
            ?: throw IllegalStateException("OneDrive upload completed without item metadata.")
        return parseOneDriveItem(completed, vaultFile)
    }

    private fun parseOneDriveItem(obj: JSONObject, vaultFile: File): RemoteCloudBackupFile {
        return RemoteCloudBackupFile(
            provider = CloudProviderType.ONEDRIVE,
            remoteId = obj.getString("id"),
            name = obj.optString("name", vaultFile.name),
            sizeBytes = obj.optLong("size", vaultFile.length()),
            modifiedAt = parseIsoTimestamp(obj.optString("lastModifiedDateTime", ""))
        )
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
                throw CloudHttpStatusException(
                    resp.code,
                    extractApiError(body, "OneDrive list failed (HTTP ${resp.code})")
                )
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

    private fun extractApiError(body: String, fallback: String): String {
        return try {
            val obj = JSONObject(body)
            val errObj = obj.optJSONObject("error")
            val msg = errObj?.optString("message", "")?.trim().orEmpty()
            val code = errObj?.optString("code", "")?.trim().orEmpty()
            when {
                msg.isNotBlank() && code.isNotBlank() -> "$msg ($code)"
                msg.isNotBlank() -> msg
                else -> extractOAuthError(body, fallback)
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseIsoTimestamp(raw: String): Long {
        if (raw.isBlank()) return TrustedTimeManager.getTrustedTimeMillis()
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
        return TrustedTimeManager.getTrustedTimeMillis()
    }
}
