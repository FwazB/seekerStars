package com.epochdefenders.solana

import com.epochdefenders.BuildConfig
import com.epochdefenders.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Solana RPC client for epoch data.
 * Ported from epoch-defenders/src/game/EpochService.ts.
 *
 * - Fetches epoch info via JSON-RPC POST to Solana cluster
 * - 30-second cache to avoid rate limiting
 * - Fallback mock data on network errors
 * - Boss trigger logic based on epoch progress thresholds
 */
class EpochService(
    private val rpcUrl: String = DEFAULT_RPC_URL
) {
    companion object {
        private const val TAG = "EpochService"
        val DEFAULT_RPC_URL: String = BuildConfig.SOLANA_RPC_URL
        private const val CACHE_TTL_MS = 30_000L
        private const val SLOT_TIME_SEC = 0.4f

        private val BOSS_THRESHOLDS = listOf(0.25f, 0.50f, 0.75f, 0.99f)
        private const val BOSS_THRESHOLD_TOLERANCE = 0.01f

        private val MOCK_EPOCH_INFO = EpochInfo(
            epoch = 500,
            slotIndex = 216_000,
            slotsInEpoch = 432_000,
            absoluteSlot = 216_000_000
        )

        @Volatile
        private var instance: EpochService? = null

        fun getInstance(rpcUrl: String = DEFAULT_RPC_URL): EpochService {
            return instance ?: synchronized(this) {
                instance ?: EpochService(rpcUrl).also { instance = it }
            }
        }
    }

    @Volatile private var cachedInfo: EpochInfo? = null
    @Volatile private var lastFetchTimeMs: Long = 0L

    /**
     * Fetch epoch info from Solana RPC. Returns cached data if fresh (<30s).
     * Falls back to mock data on network error.
     */
    suspend fun getEpochInfo(): EpochInfo {
        val now = System.currentTimeMillis()
        val cached = cachedInfo
        if (cached != null && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
            return cached
        }

        return try {
            val info = fetchFromRpc()
            cachedInfo = info
            lastFetchTimeMs = System.currentTimeMillis()
            info
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to fetch epoch info: ${e.message}", e)
            MOCK_EPOCH_INFO
        }
    }

    /** Epoch progress: 0.0 (start) to 1.0 (end). */
    fun getEpochProgress(info: EpochInfo): Float {
        if (info.slotsInEpoch == 0L) return 0f
        return info.slotIndex.toFloat() / info.slotsInEpoch.toFloat()
    }

    /** Estimated seconds until next epoch. */
    fun getTimeUntilNextEpoch(info: EpochInfo): Float {
        val remaining = info.slotsInEpoch - info.slotIndex
        return remaining * SLOT_TIME_SEC
    }

    /** Human-readable time remaining. */
    fun formatTimeRemaining(seconds: Float): String {
        val totalSec = seconds.toInt()
        val days = totalSec / 86400
        val hours = (totalSec % 86400) / 3600
        val minutes = (totalSec % 3600) / 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    /**
     * Check if a boss should trigger based on epoch progress or wave number.
     * Boss triggers at 25%, 50%, 75%, 99% of epoch (±1%) OR every 10 waves.
     */
    fun shouldTriggerBoss(info: EpochInfo, waveNumber: Int): Boolean {
        val progress = getEpochProgress(info)

        val hitThreshold = BOSS_THRESHOLDS.any { threshold ->
            progress >= threshold && progress < threshold + BOSS_THRESHOLD_TOLERANCE
        }

        return hitThreshold || (waveNumber > 0 && waveNumber % 10 == 0)
    }

    /**
     * Boss difficulty multiplier. Increases with epoch number.
     * Base 1.0, +0.1% per epoch (epoch 1000 = 2.0x).
     */
    fun getBossDifficultyMultiplier(epoch: Long): Float {
        return 1f + (epoch.toFloat() / 1000f)
    }

    // --- RPC ---

    private suspend fun fetchFromRpc(): EpochInfo = withContext(Dispatchers.IO) {
        val url = URL(rpcUrl)
        val conn = url.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getEpochInfo")
            }

            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream)).use {
                it.readText()
            }

            parseEpochInfoResponse(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseEpochInfoResponse(json: String): EpochInfo {
        val root = JSONObject(json)

        if (root.has("error")) {
            val error = root.getJSONObject("error")
            throw Exception("RPC error ${error.optInt("code")}: ${error.optString("message")}")
        }

        val result = root.getJSONObject("result")
        return EpochInfo(
            epoch = result.getLong("epoch"),
            slotIndex = result.getLong("slotIndex"),
            slotsInEpoch = result.getLong("slotsInEpoch"),
            absoluteSlot = result.getLong("absoluteSlot"),
            blockHeight = if (result.has("blockHeight")) result.optLong("blockHeight") else null,
            transactionCount = if (result.has("transactionCount")) result.optLong("transactionCount") else null
        )
    }
}
