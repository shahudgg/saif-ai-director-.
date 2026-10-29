package com.example.agent

import android.util.Log
import com.example.data.remote.GeminiKeyPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object RateGovernor {
    private const val TAG = "SAIF_AGENT"
    private const val DEFAULT_RPM = 12 // Free-tier friendly 12 requests per minute
    private const val REFILL_INTERVAL_MS = 60_000L / DEFAULT_RPM // ~5000ms per token

    private val mutex = Mutex()
    private var availableTokens = DEFAULT_RPM.toDouble()
    private var lastRefillTime = System.currentTimeMillis()

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRefillTime
            val tokensToAdd = elapsed.toDouble() / REFILL_INTERVAL_MS
            availableTokens = (availableTokens + tokensToAdd).coerceAtMost(DEFAULT_RPM.toDouble())
            lastRefillTime = now

            if (availableTokens < 1.0) {
                val neededMs = ((1.0 - availableTokens) * REFILL_INTERVAL_MS).toLong().coerceAtLeast(100L)
                Log.d(TAG, "RateGovernor throttling for ${neededMs}ms")
                delay(neededMs)
                availableTokens = 1.0
                lastRefillTime = System.currentTimeMillis()
            }

            availableTokens -= 1.0
        }
    }

    fun reportStatus(key: String, statusCode: Int, retryAfterHeader: String? = null) {
        val retrySec = retryAfterHeader?.toLongOrNull() ?: 0L
        GeminiKeyPool.reportError(key, statusCode, retrySec)
    }
}
