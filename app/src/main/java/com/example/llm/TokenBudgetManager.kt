package com.example.llm

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Thread-safe sliding-window token budget governor for LLM requests.
 * Tracks usage over a 60-second sliding window per provider + model,
 * enforcing that input + max_tokens <= 0.85 * TPM.
 */
object TokenBudgetManager {
    private const val TAG = "TokenBudgetManager"
    private const val WINDOW_MS = 60_000L

    // History entry: (timestamp, tokensUsed)
    private data class TokenUsageEntry(val timestampMs: Long, val tokens: Int)

    private val usageHistories = ConcurrentHashMap<String, MutableList<TokenUsageEntry>>()
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    private fun cacheKey(provider: String, model: String) =
        "${provider.lowercase().trim()}:::${model.lowercase().trim()}"

    private fun getMutex(key: String): Mutex =
        mutexes.computeIfAbsent(key) { Mutex() }

    private fun getHistory(key: String): MutableList<TokenUsageEntry> =
        usageHistories.computeIfAbsent(key) { mutableListOf() }

    /**
     * Estimates token count:
     * - chars / 3.2 for code
     * - chars / 4.0 for prose
     * Adds 10% safety margin.
     */
    fun estimateTokens(text: String, isCode: Boolean = false): Int {
        if (text.isEmpty()) return 0
        val divisor = if (isCode) 3.2 else 4.0
        val raw = text.length / divisor
        return ceil(raw * 1.10).toInt().coerceAtLeast(1)
    }

    /**
     * Calculates the safe maximum tokens allowed for a single task given the model's TPM.
     * Rule: input + max_tokens <= 0.85 * TPM.
     */
    fun calculateBudgetCeiling(provider: String, model: String): Int {
        val limits = ModelLimitsStore.get(provider, model)
        val tpm = limits.tokensPerMinute ?: when {
            provider.equals("Groq", true) -> if (model.contains("70b", true)) 6000 else 8000
            else -> 60_000
        }
        return (tpm * 0.85).toInt()
    }

    /**
     * Reserves token budget for a task, suspending if the sliding 60-second window is crowded.
     * Emits countdown callbacks via [onWaiting].
     */
    suspend fun reserve(
        provider: String,
        model: String,
        inputTokens: Int,
        maxOutputTokens: Int,
        onWaiting: (suspend (reason: String, secondsRemaining: Int) -> Unit)? = null
    ): Int {
        val key = cacheKey(provider, model)
        val mutex = getMutex(key)
        val neededTotal = inputTokens + maxOutputTokens
        val ceiling = calculateBudgetCeiling(provider, model)

        // Safety clamp: task tokens cannot exceed 0.85 * TPM alone
        val clampedOutput = if (neededTotal > ceiling) {
            val adjusted = (ceiling - inputTokens).coerceAtLeast(256)
            Log.w(TAG, "[$key] Total tokens ($neededTotal) exceeds ceiling ($ceiling). Clamping maxOutput: $maxOutputTokens -> $adjusted")
            adjusted
        } else {
            maxOutputTokens
        }
        val actualNeeded = inputTokens + clampedOutput

        mutex.withLock {
            val history = getHistory(key)
            while (true) {
                val now = System.currentTimeMillis()
                // Evict entries older than 60 seconds
                val cutoff = now - WINDOW_MS
                history.removeAll { it.timestampMs < cutoff }

                val currentlyUsed = history.sumOf { it.tokens }
                val availableInWindow = ceiling - currentlyUsed

                if (availableInWindow >= actualNeeded) {
                    // Record reservation
                    history.add(TokenUsageEntry(now, actualNeeded))
                    break
                }

                // Window is crowded: find earliest entry that will expire
                val oldest = history.firstOrNull()
                val waitMs = if (oldest != null) {
                    val remaining = (oldest.timestampMs + WINDOW_MS) - now
                    remaining.coerceIn(500L, WINDOW_MS)
                } else {
                    1000L
                }

                val waitSec = ceil(waitMs / 1000.0).toInt().coerceAtLeast(1)
                Log.i(TAG, "[$key] Rate-limit window crowded (used=$currentlyUsed/$ceiling, needed=$actualNeeded). Waiting ${waitSec}s...")
                onWaiting?.invoke("Rate-limit window cooling down ($waitSec s)", waitSec)
                delay(waitMs)
            }
        }

        return clampedOutput
    }

    /**
     * Reports actual token usage after call completion to calibrate history.
     */
    fun recordActualUsage(
        provider: String,
        model: String,
        promptTokens: Int,
        completionTokens: Int
    ) {
        val key = cacheKey(provider, model)
        val total = promptTokens + completionTokens
        if (total <= 0) return
        val history = getHistory(key)
        val now = System.currentTimeMillis()
        synchronized(history) {
            // Replace latest estimated reservation with actual usage
            if (history.isNotEmpty()) {
                val lastIdx = history.size - 1
                history[lastIdx] = TokenUsageEntry(now, total)
            } else {
                history.add(TokenUsageEntry(now, total))
            }
        }
    }

    /**
     * Clears tracked history (for tests or reset).
     */
    fun reset() {
        usageHistories.clear()
        mutexes.clear()
    }
}
