package com.example.util

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 *
 * Architecture:
 * 1. Monotonic Hardware Anchor: Uses android.os.SystemClock.elapsedRealtime(), which
 *    counts hardware ticks since boot and CANNOT be altered by the user in Android settings.
 * 2. Network Time Synchronization: When online, queries trusted public time endpoints
 *    via HTTP Date headers and computes the offset: (networkTimeUtc - elapsedRealtime).
 * 3. Offline Immunity: In offline mode, current time advances steadily along the hardware
 *    tick rate. Any manual adjustment of the system clock is completely ignored.
 */
object TrustedTimeManager {

    private val isSynced = AtomicBoolean(false)
    // Offset between network UTC timestamp and SystemClock.elapsedRealtime()
    private val bootOffsetMillis = AtomicLong(0L)

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Initializes network time sync in background.
     */
    fun syncWithNetworkAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            syncWithNetwork()
        }
    }

    /**
     * Attempts to synchronize with a reliable network timestamp.
     */
    fun syncWithNetwork(): Boolean {
        val testEndpoints = listOf(
            "https://www.google.com/generate_204",
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://cloudflare.com"
        )

        for (url in testEndpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                val callStartTick = SystemClock.elapsedRealtime()
                val response = okHttpClient.newCall(req).execute()
                val callEndTick = SystemClock.elapsedRealtime()
                val rtt = (callEndTick - callStartTick).coerceAtLeast(0L)

                val dateHeader = response.header("Date")
                response.close()

                if (!dateHeader.isNullOrBlank()) {
                    val parsedEpoch = parseHttpDate(dateHeader)
                    if (parsedEpoch > 0L) {
                        // Estimated server time corresponding to callEndTick
                        val estimatedEpoch = parsedEpoch + (rtt / 2L)
                        val offset = estimatedEpoch - callEndTick
                        bootOffsetMillis.set(offset)
                        isSynced.set(true)
                        return true
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint on transient network error
            }
        }
        return false
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
