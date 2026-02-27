package com.epochdefenders.solana

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LeaderboardEntry(
    val rank: Int,
    val walletAddress: String,
    val score: Int,
    val waveReached: Int,
    val timestamp: Long
)

class LeaderboardService private constructor() {
    companion object {
        private const val PREFS_NAME = "epoch_defenders_scores"
        private const val KEY_SCORES = "leaderboard_scores"
        private const val MAX_ENTRIES = 10

        @Volatile private var instance: LeaderboardService? = null

        fun getInstance(): LeaderboardService {
            return instance ?: synchronized(this) {
                instance ?: LeaderboardService().also { instance = it }
            }
        }
    }

    fun saveScore(context: Context, playerName: String, score: Int, waveReached: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = readScores(prefs.getString(KEY_SCORES, null))

        val entry = LeaderboardEntry(
            rank = 0,
            walletAddress = playerName,
            score = score,
            waveReached = waveReached,
            timestamp = System.currentTimeMillis() / 1000
        )

        val updated = (existing + entry)
            .sortedByDescending { it.score }
            .take(MAX_ENTRIES)
            .mapIndexed { index, e -> e.copy(rank = index + 1) }

        val json = JSONArray()
        for (e in updated) {
            json.put(JSONObject().apply {
                put("playerName", e.walletAddress)
                put("score", e.score)
                put("waveReached", e.waveReached)
                put("timestamp", e.timestamp)
            })
        }

        prefs.edit().putString(KEY_SCORES, json.toString()).apply()
    }

    fun getLeaderboard(context: Context): List<LeaderboardEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readScores(prefs.getString(KEY_SCORES, null))
    }

    private fun readScores(jsonStr: String?): List<LeaderboardEntry> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LeaderboardEntry(
                    rank = i + 1,
                    walletAddress = obj.getString("playerName"),
                    score = obj.getInt("score"),
                    waveReached = obj.getInt("waveReached"),
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
