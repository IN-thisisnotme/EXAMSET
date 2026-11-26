package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSpeechRetryPolicyTest {

    @Test
    fun slowEmptyErrorsResetRapidCounterAndKeepListening() {
        val decision = VoiceSpeechRetryPolicy.evaluateEmptyError(
            silenceSoFarMs = 5_000L,
            passElapsedMs = 3_500L,
            priorRapidEmptyErrors = 2,
            engineMode = VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST,
            isStandardFallbackActive = false
        )
        assertEquals(VoiceSpeechEmptyErrorAction.RETRY_SAME_ENGINE, decision.action)
        assertEquals(0, decision.nextRapidErrorCount)
    }

    @Test
    fun rapidEmptyErrorsTriggerStandardFallbackOnceInAutoOfflineFirstMode() {
        val first = VoiceSpeechRetryPolicy.evaluateEmptyError(
            silenceSoFarMs = 400L,
            passElapsedMs = 180L,
            priorRapidEmptyErrors = 0,
            engineMode = VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST,
            isStandardFallbackActive = false
        )
        assertEquals(VoiceSpeechEmptyErrorAction.RETRY_SAME_ENGINE, first.action)
        assertEquals(1, first.nextRapidErrorCount)

        val third = VoiceSpeechRetryPolicy.evaluateEmptyError(
            silenceSoFarMs = 900L,
            passElapsedMs = 150L,
            priorRapidEmptyErrors = 2,
            engineMode = VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST,
            isStandardFallbackActive = false
        )
        assertEquals(VoiceSpeechEmptyErrorAction.FALLBACK_TO_STANDARD, third.action)
        assertEquals(0, third.nextRapidErrorCount)
    }

    @Test
    fun rapidEmptyErrorsStopCleanlyWhenAlreadyOnFallbackOrStrictOffline() {
        val fallbackFailed = VoiceSpeechRetryPolicy.evaluateEmptyError(
            silenceSoFarMs = 1_500L,
            passElapsedMs = 200L,
            priorRapidEmptyErrors = 2,
            engineMode = VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST,
            isStandardFallbackActive = true
        )
        assertEquals(VoiceSpeechEmptyErrorAction.STOP_WITH_NOTICE, fallbackFailed.action)

        val strictOfflineFailed = VoiceSpeechRetryPolicy.evaluateEmptyError(
            silenceSoFarMs = 1_500L,
            passElapsedMs = 200L,
            priorRapidEmptyErrors = 2,
            engineMode = VoiceSpeechEngineMode.STRICT_OFFLINE,
            isStandardFallbackActive = false
        )
        assertEquals(VoiceSpeechEmptyErrorAction.STOP_WITH_NOTICE, strictOfflineFailed.action)
    }

    @Test
    fun silenceThresholdExceedingLimitStopsImmediately() {
        val decision = VoiceSpeechRetryPolicy.evaluateEmptyError(
            silenceSoFarMs = 30_000L,
            passElapsedMs = 2_000L,
            priorRapidEmptyErrors = 0,
            engineMode = VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST,
            isStandardFallbackActive = false
        )
        assertEquals(VoiceSpeechEmptyErrorAction.STOP_WITH_NOTICE, decision.action)
        assertEquals(0, decision.nextRapidErrorCount)
    }
}
