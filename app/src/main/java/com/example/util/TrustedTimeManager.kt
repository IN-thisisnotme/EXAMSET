package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Trusted Time Manager (Anti-Cheating & Clock Tampering Protection).
 *
 * Provides monotonically advancing real-world epoch time anchored by network time
 * whenever network connectivity is present, linked with Android's monotonic boot clock
 * (`SystemClock.elapsedRealtime()`) and persisted per-boot so:
 * - Altering Android system date/time cannot falsify UTC dates, streaks, or timers.
 * - Force-closing and reopening the app offline during the same device boot preserves
 *   the verified network-to-boot offset.
 * - Even after an offline reboot, a persisted monotonic UTC high-water mark prevents
 *   backward clock tampering.
 */
object TrustedTimeManager {

    private const val PREFS_NAME = "trusted_time_prefs_v1"
    private const val PREF_HAS_BOOT_OFFSET = "has_boot_offset"
    private const val PREF_BOOT_OFFSET_MILLIS = "boot_offset_millis"
    private const val PREF_LAST_ELAPSED_MILLIS = "last_elapsed_millis"
    private const val PREF_BOOT_COUNT = "boot_count"
    private const val PREF_MAX_TRUSTED_UTC_MILLIS = "max_trusted_utc_millis"

    private val isSynced = AtomicBoolean(false)
    private val isSyncInFlight = AtomicBoolean(false)
    private val isObserverRegistered = AtomicBoolean(false)
    private val isPersistenceRestored = AtomicBoolean(false)

    // Offset between network UTC timestamp and SystemClock.elapsedRealtime()
    private val bootOffsetMillis = AtomicLong(0L)
    private val lastSyncElapsedMillis = AtomicLong(0L)
    private val maxKnownTrustedUtcMillis = AtomicLong(0L)
    private val lastFloorPersistElapsedMillis = AtomicLong(0L)
    private val appContextRef = AtomicReference<Context?>(null)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isNetworkTimeSynced = MutableStateFlow(false)
    val isNetworkTimeSynced: StateFlow<Boolean> = _isNetworkTimeSynced.asStateFlow()

    private const val MIN_RESYNC_INTERVAL_MS = 60_000L
    private const val FLOOR_PERSIST_INTERVAL_MS = 60_000L

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Restores persisted same-boot network offset and monotonic UTC floor once per process.
     */
    fun initPersistence(context: Context) {
        val appContext = context.applicationContext ?: context
        appContextRef.compareAndSet(null, appContext)
        if (!isPersistenceRestored.compareAndSet(false, true)) return
        restoreFromPreferences(appContext)
    }

    /**
     * Registers a system network callback (idempotent) so any transition to an active
     * internet connection automatically locks/refreshes the trusted network time offset.
     */
    fun registerNetworkObserver(context: Context) {
        initPersistence(context)
        if (!isObserverRegistered.compareAndSet(false, true)) return
        try {
            val appContext = context.applicationContext ?: context
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return

            val activeNetwork = cm.activeNetwork
            val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                syncIfDueAsync()
            }

            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    syncIfDueAsync()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        syncIfDueAsync()
                    }
                }
            })
        } catch (_: Exception) {
            // Safe fallback in unit test or restricted environments
        }
    }

    /**
     * Initializes network time sync in background.
     */
    fun syncWithNetworkAsync(scope: CoroutineScope = backgroundScope) {
        scope.launch(Dispatchers.IO) {
            syncWithNetwork()
        }
    }

    private fun syncIfDueAsync() {
        val nowElapsed = safeElapsedRealtime()
        val lastElapsed = lastSyncElapsedMillis.get()
        if (!isSynced.get() || (nowElapsed - lastElapsed) >= MIN_RESYNC_INTERVAL_MS) {
            syncWithNetworkAsync(backgroundScope)
        }
    }

    /**
     * Attempts to synchronize with a reliable network timestamp via HTTP Date headers.
     */
    fun syncWithNetwork(): Boolean {
        if (!isSyncInFlight.compareAndSet(false, true)) {
            return isSynced.get()
        }
        try {
            val testEndpoints = listOf(
                "https://www.google.com/generate_204",
                "https://connectivitycheck.gstatic.com/generate_204",
                "https://1.1.1.1/cdn-cgi/trace",
                "https://cloudflare.com"
            )

            for (url in testEndpoints) {
                for (useHead in listOf(true, false)) {
                    try {
                        val reqBuilder = Request.Builder().url(url)
                        val req = if (useHead) reqBuilder.head().build() else reqBuilder.get().build()

                        val callStartTick = safeElapsedRealtime()
                        okHttpClient.newCall(req).execute().use { response ->
                            val callEndTick = safeElapsedRealtime()
                            val rtt = (callEndTick - callStartTick).coerceAtLeast(0L)
                            val dateHeader = response.header("Date")
                            if (!dateHeader.isNullOrBlank()) {
                                val parsedEpoch = parseHttpDate(dateHeader)
                                if (parsedEpoch > 0L) {
                                    val estimatedEpoch = parsedEpoch + (rtt / 2L)
                                    recordVerifiedNetworkEpoch(
                                        estimatedEpochMillis = estimatedEpoch,
                                        elapsedAtSyncMillis = callEndTick
                                    )
                                    return true
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Try GET fallback or next endpoint
                    }
                }
            }
            return isSynced.get()
        } finally {
            isSyncInFlight.set(false)
        }
    }

    /**
     * Returns the current trusted Unix epoch timestamp in milliseconds.
     * Uses network-anchored hardware elapsedRealtime if synced (or restored from the same
     * device boot); otherwise clamps System.currentTimeMillis() against the persisted
     * monotonic UTC floor so offline clock rollback cannot regress time.
     */
    fun getTrustedTimeMillis(): Long {
        val nowElapsed = safeElapsedRealtime()
        val floor = maxKnownTrustedUtcMillis.get()
        return if (isSynced.get()) {
            val computed = maxOf(bootOffsetMillis.get() + nowElapsed, floor)
            updateMonotonicFloorIfNeeded(computed, nowElapsed)
            computed
        } else {
            maxOf(System.currentTimeMillis(), floor)
        }
    }

    /**
     * Returns true if trusted network time has been locked for this device boot.
     */
    fun isNetworkTimeLocked(): Boolean = isSynced.get()

    internal fun recordVerifiedNetworkEpoch(
        estimatedEpochMillis: Long,
        elapsedAtSyncMillis: Long = safeElapsedRealtime(),
        bootCountOverride: Int? = null
    ) {
        val offset = estimatedEpochMillis - elapsedAtSyncMillis
        bootOffsetMillis.set(offset)
        lastSyncElapsedMillis.set(elapsedAtSyncMillis)
        val newFloor = maxOf(maxKnownTrustedUtcMillis.get(), estimatedEpochMillis)
        maxKnownTrustedUtcMillis.set(newFloor)
        lastFloorPersistElapsedMillis.set(elapsedAtSyncMillis)
        isSynced.set(true)
        _isNetworkTimeSynced.value = true

        val ctx = appContextRef.get() ?: return
        val bootCount = bootCountOverride ?: readBootCount(ctx)
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_HAS_BOOT_OFFSET, true)
            .putLong(PREF_BOOT_OFFSET_MILLIS, offset)
            .putLong(PREF_LAST_ELAPSED_MILLIS, elapsedAtSyncMillis)
            .putInt(PREF_BOOT_COUNT, bootCount)
            .putLong(PREF_MAX_TRUSTED_UTC_MILLIS, newFloor)
            .apply()
    }

    internal fun restoreFromPreferences(
        context: Context,
        elapsedNowOverride: Long? = null,
        bootCountOverride: Int? = null
    ) {
        val appContext = context.applicationContext ?: context
        appContextRef.compareAndSet(null, appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFloor = prefs.getLong(PREF_MAX_TRUSTED_UTC_MILLIS, 0L)
        if (savedFloor > maxKnownTrustedUtcMillis.get()) {
            maxKnownTrustedUtcMillis.set(savedFloor)
        }

        val hasOffset = prefs.getBoolean(PREF_HAS_BOOT_OFFSET, false)
        val savedBootCount = prefs.getInt(PREF_BOOT_COUNT, -1)
        val savedLastElapsed = prefs.getLong(PREF_LAST_ELAPSED_MILLIS, -1L)
        val savedOffset = prefs.getLong(PREF_BOOT_OFFSET_MILLIS, 0L)
        val currentBootCount = bootCountOverride ?: readBootCount(appContext)
        val currentElapsed = elapsedNowOverride ?: safeElapsedRealtime()

        // Only reuse the persisted boot offset when verified within the exact same device boot
        if (hasOffset &&
            savedBootCount > 0 &&
            savedBootCount == currentBootCount &&
            savedLastElapsed >= 0L &&
            currentElapsed >= savedLastElapsed
        ) {
            bootOffsetMillis.set(savedOffset)
            lastSyncElapsedMillis.set(savedLastElapsed)
            lastFloorPersistElapsedMillis.set(currentElapsed)
            val restoredNow = maxOf(savedOffset + currentElapsed, savedFloor)
            maxKnownTrustedUtcMillis.set(restoredNow)
            isSynced.set(true)
            _isNetworkTimeSynced.value = true
            if (restoredNow > savedFloor || currentElapsed > savedLastElapsed) {
                prefs.edit()
                    .putLong(PREF_MAX_TRUSTED_UTC_MILLIS, restoredNow)
                    .putLong(PREF_LAST_ELAPSED_MILLIS, currentElapsed)
                    .apply()
            }
        }
    }

    internal fun resetForTesting(clearPrefsContext: Context? = null) {
        isSynced.set(false)
        isSyncInFlight.set(false)
        isObserverRegistered.set(false)
        isPersistenceRestored.set(false)
        bootOffsetMillis.set(0L)
        lastSyncElapsedMillis.set(0L)
        maxKnownTrustedUtcMillis.set(0L)
        lastFloorPersistElapsedMillis.set(0L)
        _isNetworkTimeSynced.value = false
        clearPrefsContext?.let { ctx ->
            appContextRef.set(ctx.applicationContext ?: ctx)
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        } ?: appContextRef.set(null)
    }

    private fun updateMonotonicFloorIfNeeded(computedUtcMillis: Long, nowElapsed: Long) {
        val prevFloor = maxKnownTrustedUtcMillis.get()
        if (computedUtcMillis > prevFloor) {
            maxKnownTrustedUtcMillis.compareAndSet(prevFloor, computedUtcMillis)
        }
        val lastPersistedElapsed = lastFloorPersistElapsedMillis.get()
        if (nowElapsed - lastPersistedElapsed >= FLOOR_PERSIST_INTERVAL_MS &&
            lastFloorPersistElapsedMillis.compareAndSet(lastPersistedElapsed, nowElapsed)
        ) {
            val ctx = appContextRef.get() ?: return
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_MAX_TRUSTED_UTC_MILLIS, maxKnownTrustedUtcMillis.get())
                .putLong(PREF_LAST_ELAPSED_MILLIS, nowElapsed)
                .apply()
        }
    }

    private fun safeElapsedRealtime(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: Exception) {
            0L
        }
    }

    private fun readBootCount(context: Context): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
    }

    private fun parseHttpDate(dateString: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd-MMM-yy HH:mm:ss z",
            "EEE MMM dd HH:mm:ss yyyy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("GMT")
                val date = sdf.parse(dateString)
                if (date != null) return date.time
            } catch (_: Exception) {
                // Ignore and try alternative RFC-1123 format
            }
        }
        return 0L
    }
}
