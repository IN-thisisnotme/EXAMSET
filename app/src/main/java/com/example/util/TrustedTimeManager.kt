package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
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

/**
 * Trusted Time Manager (Anti-Cheating & Clock Tampering Protection).
 *
 * Provides monotonically advancing real-world epoch time anchored by network time
 * whenever network connectivity is present, eliminating vulnerabilities where examinees
 * alter Android system date/time to falsify test submissions, streak dates, or timers.
 */
object TrustedTimeManager {

    private val isSynced = AtomicBoolean(false)
    private val isSyncInFlight = AtomicBoolean(false)
    private val isObserverRegistered = AtomicBoolean(false)
    // Offset between network UTC timestamp and SystemClock.elapsedRealtime()
    private val bootOffsetMillis = AtomicLong(0L)
    private val lastSyncElapsedMillis = AtomicLong(0L)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isNetworkTimeSynced = MutableStateFlow(false)
    val isNetworkTimeSynced: StateFlow<Boolean> = _isNetworkTimeSynced.asStateFlow()

    private const val MIN_RESYNC_INTERVAL_MS = 60_000L

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Registers a system network callback (idempotent) so any transition to an active
     * internet connection automatically locks/refreshes the trusted network time offset.
     */
    fun registerNetworkObserver(context: Context) {
        if (!isObserverRegistered.compareAndSet(false, true)) return
        try {
            val appContext = context.applicationContext
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
        val nowElapsed = try {
            SystemClock.elapsedRealtime()
        } catch (_: Exception) {
            0L
        }
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

                        val callStartTick = SystemClock.elapsedRealtime()
                        okHttpClient.newCall(req).execute().use { response ->
                            val callEndTick = SystemClock.elapsedRealtime()
                            val rtt = (callEndTick - callStartTick).coerceAtLeast(0L)
                            val dateHeader = response.header("Date")
                            if (!dateHeader.isNullOrBlank()) {
                                val parsedEpoch = parseHttpDate(dateHeader)
                                if (parsedEpoch > 0L) {
                                    val estimatedEpoch = parsedEpoch + (rtt / 2L)
                                    val offset = estimatedEpoch - callEndTick
                                    bootOffsetMillis.set(offset)
                                    lastSyncElapsedMillis.set(callEndTick)
                                    isSynced.set(true)
                                    _isNetworkTimeSynced.value = true
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
     * Uses network-anchored hardware elapsedRealtime if synced; falls back
     * to System.currentTimeMillis() only if network was never contacted since boot.
     */
    fun getTrustedTimeMillis(): Long {
        return if (isSynced.get()) {
            bootOffsetMillis.get() + SystemClock.elapsedRealtime()
        } else {
            System.currentTimeMillis()
        }
    }

    /**
     * Returns true if trusted network time has been locked since boot.
     */
    fun isNetworkTimeLocked(): Boolean = isSynced.get()

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
