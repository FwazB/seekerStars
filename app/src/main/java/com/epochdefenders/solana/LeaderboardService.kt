package com.epochdefenders.solana

import com.epochdefenders.BuildConfig
import com.epochdefenders.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-chain leaderboard entry.
 *
 * @param rank          Position on the leaderboard (1-based).
 * @param walletAddress Solana wallet public key (base-58 encoded).
 * @param score         Total score for the run.
 * @param waveReached   Highest wave number survived.
 * @param timestamp     Unix epoch seconds when the score was submitted.
 */
data class LeaderboardEntry(
    val rank: Int,
    val walletAddress: String,
    val score: Int,
    val waveReached: Int,
    val timestamp: Long
)

/**
 * On-chain leaderboard using Solana RPC.
 * Follows the same singleton + cache + IO-dispatcher pattern as [EpochService].
 *
 * Score submission flow:
 *   1. Build a Memo program transaction with a JSON payload.
 *   2. Sign & send via [WalletManager] (Mobile Wallet Adapter).
 *   3. Signature is returned on success.
 *
 * Score reading flow (hackathon MVP):
 *   - Queries recent memo transactions and parses JSON payloads.
 *   - TODO: Replace with PDA-based on-chain program for production.
 *
 * Caching:
 *   - Leaderboard entries are cached for 60 seconds.
 *   - Force-refresh available via [getLeaderboard(forceRefresh = true)].
 */
class LeaderboardService(
    private val rpcUrl: String = BuildConfig.SOLANA_RPC_URL
) {
    companion object {
        private const val TAG = "LeaderboardService"
        private const val TIMEOUT_MS = 10_000
        private const val CACHE_TTL_MS = 60_000L
        private const val MAX_ENTRIES = 20

        // Memo program ID for storing scores as transaction memos
        private const val MEMO_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"

        @Volatile private var instance: LeaderboardService? = null

        fun getInstance(rpcUrl: String = BuildConfig.SOLANA_RPC_URL): LeaderboardService {
            return instance ?: synchronized(this) {
                instance ?: LeaderboardService(rpcUrl).also { instance = it }
            }
        }
    }

    @Volatile private var cachedEntries: List<LeaderboardEntry>? = null
    @Volatile private var lastFetchMs: Long = 0L

    /**
     * Fetch leaderboard entries. Cached for 60s.
     * All network calls on IO dispatcher.
     *
     * @param forceRefresh Bypass cache and fetch fresh data from chain.
     * @return Sorted list of [LeaderboardEntry], or empty list on failure.
     */
    suspend fun getLeaderboard(forceRefresh: Boolean = false): List<LeaderboardEntry> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = cachedEntries
            if (!forceRefresh && cached != null && (now - lastFetchMs) < CACHE_TTL_MS) {
                return@withContext cached
            }

            try {
                val entries = fetchScoresFromChain()
                cachedEntries = entries
                lastFetchMs = System.currentTimeMillis()
                entries
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to fetch leaderboard", e)
                cached ?: emptyList()
            }
        }

    /**
     * Submit score on-chain as a memo transaction.
     * Returns transaction signature on success, null on failure.
     *
     * @param walletManager  Connected [WalletManager] instance.
     * @param score          Total score for the run.
     * @param waveReached    Highest wave number survived.
     * @param goldEarned     Gold earned during the run.
     * @param towersPlaced   Number of towers placed.
     * @param timeSurvivedSec  Total time survived in seconds.
     * @param bossesDefeated Number of bosses defeated.
     * @return Transaction signature string, or null on failure.
     */
    suspend fun submitScore(
        walletManager: WalletManager,
        score: Int,
        waveReached: Int,
        goldEarned: Int,
        towersPlaced: Int,
        timeSurvivedSec: Float,
        bossesDefeated: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val walletAddr = walletManager.walletAddress.value
                ?: throw IllegalStateException("Wallet not connected")

            // Build memo payload
            val payload = JSONObject().apply {
                put("game", "epoch-defenders")
                put("version", 1)
                put("player", walletAddr)
                put("score", score)
                put("wave", waveReached)
                put("gold", goldEarned)
                put("towers", towersPlaced)
                put("time", timeSurvivedSec)
                put("bosses", bossesDefeated)
                put("ts", System.currentTimeMillis() / 1000)
            }

            // Build transaction with memo instruction
            val transaction = buildMemoTransaction(walletAddr, payload.toString())

            // Sign and send via wallet adapter
            walletManager.signAndSend(transaction)
        } catch (e: Exception) {
            AppLog.e(TAG, "Score submission failed", e)
            null
        }
    }

    /**
     * Build a Solana transaction with a Memo program instruction.
     * Returns a fully serialized unsigned transaction in Solana wire format.
     */
    private suspend fun buildMemoTransaction(payer: String, memo: String): ByteArray {
        val blockhash = getRecentBlockhash()
        AppLog.d(TAG, "Using blockhash: $blockhash")

        val payerKey = Base58.decode(payer)       // 32 bytes
        val programKey = Base58.decode(MEMO_PROGRAM_ID) // 32 bytes
        val blockhashBytes = Base58.decode(blockhash)    // 32 bytes
        val memoData = memo.toByteArray(Charsets.UTF_8)

        // Build the message portion
        val message = buildSolanaMessage(payerKey, programKey, blockhashBytes, memoData)

        // Full transaction: signatures section + message
        val tx = mutableListOf<Byte>()

        // Compact-u16 signature count (1) + 64 zero bytes (placeholder for wallet to fill)
        tx.addAll(compactU16(1))
        tx.addAll(ByteArray(64).toList())

        // Message
        tx.addAll(message.toList())

        return tx.toByteArray()
    }

    /**
     * Build the Solana transaction message (header + accounts + blockhash + instructions).
     */
    private fun buildSolanaMessage(
        payerKey: ByteArray,
        programKey: ByteArray,
        blockhashBytes: ByteArray,
        memoData: ByteArray
    ): ByteArray {
        val msg = mutableListOf<Byte>()

        // Message header: [numRequiredSigs, numReadonlySigned, numReadonlyUnsigned]
        msg.add(1)  // 1 required signature (payer)
        msg.add(0)  // 0 readonly signed accounts
        msg.add(1)  // 1 readonly unsigned account (memo program)

        // Compact-u16 account count (2): payer + memo program
        msg.addAll(compactU16(2))

        // Account keys: payer first, then memo program
        msg.addAll(payerKey.toList())
        msg.addAll(programKey.toList())

        // Recent blockhash (32 bytes)
        msg.addAll(blockhashBytes.toList())

        // Compact-u16 instruction count (1)
        msg.addAll(compactU16(1))

        // Instruction: memo program
        msg.add(1)  // programIdIndex = 1 (memo program is second account)

        // Accounts referenced by this instruction (compact array)
        msg.addAll(compactU16(1))  // 1 account
        msg.add(0)                  // account index 0 (payer)

        // Instruction data (compact array of memo bytes)
        msg.addAll(compactU16(memoData.size))
        msg.addAll(memoData.toList())

        return msg.toByteArray()
    }

    /** Encode an integer as Solana compact-u16 (1-3 bytes, little-endian). */
    private fun compactU16(value: Int): List<Byte> {
        if (value < 0x80) return listOf(value.toByte())
        if (value < 0x4000) return listOf(
            (value and 0x7F or 0x80).toByte(),
            (value shr 7).toByte()
        )
        return listOf(
            (value and 0x7F or 0x80).toByte(),
            (value shr 7 and 0x7F or 0x80).toByte(),
            (value shr 14).toByte()
        )
    }

    /**
     * Fetch the latest blockhash from Solana RPC.
     * Required for building any transaction.
     */
    private suspend fun getRecentBlockhash(): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getLatestBlockhash")
            put("params", JSONArray().put(JSONObject().put("commitment", "finalized")))
        }

        val response = rpcCall(body)
        response.getJSONObject("result")
            .getJSONObject("value")
            .getString("blockhash")
    }

    /**
     * Fetch scores from chain by reading memo transactions.
     * Queries getSignaturesForAddress on MEMO_PROGRAM_ID, then getTransaction
     * for each to extract epoch-defenders JSON payloads.
     *
     * TODO: Replace with PDA-based on-chain program for production.
     */
    private suspend fun fetchScoresFromChain(): List<LeaderboardEntry> {
        // 1. Get recent signatures for the Memo program
        val sigBody = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getSignaturesForAddress")
            put("params", JSONArray().apply {
                put(MEMO_PROGRAM_ID)
                put(JSONObject().put("limit", 50))
            })
        }
        val sigResponse = rpcCall(sigBody)
        val signatures = sigResponse.getJSONArray("result")

        val entries = mutableListOf<LeaderboardEntry>()

        // 2. Fetch each transaction and extract memo payloads
        for (i in 0 until signatures.length()) {
            val sigInfo = signatures.getJSONObject(i)
            if (!sigInfo.isNull("err")) continue

            try {
                val txBody = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "getTransaction")
                    put("params", JSONArray().apply {
                        put(sigInfo.getString("signature"))
                        put(JSONObject().apply {
                            put("encoding", "jsonParsed")
                            put("maxSupportedTransactionVersion", 0)
                        })
                    })
                }
                val txResponse = rpcCall(txBody)
                val result = txResponse.optJSONObject("result") ?: continue

                // Check top-level instructions for memo data
                val instructions = result
                    .optJSONObject("transaction")
                    ?.optJSONObject("message")
                    ?.optJSONArray("instructions")

                if (instructions != null) {
                    extractMemoEntries(instructions, entries)
                }

                // Also check innerInstructions for memo data (CPI case)
                val innerInstructions = result
                    .optJSONObject("meta")
                    ?.optJSONArray("innerInstructions")
                if (innerInstructions != null) {
                    for (j in 0 until innerInstructions.length()) {
                        val innerInstrs = innerInstructions.getJSONObject(j)
                            .optJSONArray("instructions") ?: continue
                        extractMemoEntries(innerInstrs, entries)
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Skipping transaction: ${e.message}")
            }
        }

        // 3. Sort by score descending, assign ranks, cap at MAX_ENTRIES
        return entries
            .sortedByDescending { it.score }
            .take(MAX_ENTRIES)
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
    }

    /** Extract epoch-defenders memo entries from a JSONArray of instructions. */
    private fun extractMemoEntries(
        instructions: JSONArray,
        out: MutableList<LeaderboardEntry>
    ) {
        for (j in 0 until instructions.length()) {
            val instr = instructions.getJSONObject(j)
            if (instr.optString("programId") != MEMO_PROGRAM_ID) continue

            val memoText = instr.optString("parsed", "")
            if (!memoText.contains("epoch-defenders")) continue

            val entry = parseMemoPayload(memoText)
            if (entry != null) out.add(entry)
        }
    }

    /**
     * Parse a memo JSON payload into a [LeaderboardEntry].
     * Returns null if the payload is not a valid epoch-defenders score.
     */
    private fun parseMemoPayload(memoText: String): LeaderboardEntry? {
        return try {
            val json = JSONObject(memoText)
            if (json.optString("game") != "epoch-defenders") return null
            LeaderboardEntry(
                rank = 0, // assigned after sorting
                walletAddress = json.getString("player"),
                score = json.getInt("score"),
                waveReached = json.getInt("wave"),
                timestamp = json.getLong("ts")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Low-level Solana JSON-RPC POST call.
     * Mirrors the pattern in [EpochService.fetchFromRpc].
     */
    private suspend fun rpcCall(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = URL(rpcUrl)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS

            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(response)

            if (json.has("error")) {
                val error = json.getJSONObject("error")
                throw Exception("RPC error ${error.optInt("code")}: ${error.optString("message")}")
            }

            json
        } finally {
            conn.disconnect()
        }
    }
}
