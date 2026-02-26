package com.epochdefenders.solana

import androidx.activity.ComponentActivity
import android.net.Uri

import com.epochdefenders.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter

/**
 * Solana Mobile Wallet Adapter wrapper for Seeker device.
 *
 * - Manages wallet connection lifecycle (connect / sign / disconnect)
 * - Exposes wallet state via StateFlows for Compose observation
 * - All network/wallet calls run on IO dispatcher
 *
 * TODO: Verify MWA clientlib-ktx API surface against v2.0.3 release.
 *       The transact {} DSL, authorize(), and signAndSendTransactions()
 *       signatures may differ slightly from the snippet below.
 */
class WalletManager(private val activity: ComponentActivity) {
    companion object {
        private const val TAG = "WalletManager"
    }

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private var authToken: String? = null

    /**
     * Connect wallet via Mobile Wallet Adapter.
     *
     * Opens the user's installed Solana wallet app (e.g. Phantom, Solflare)
     * and requests authorization for Epoch Defenders.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        _isConnecting.value = true
        try {
            val sender = ActivityResultSender(activity)
            val adapter = MobileWalletAdapter()
            adapter.transact(sender) {
                val result = authorize(
                    identityUri = Uri.parse("https://epochdefenders.com"),
                    iconUri = Uri.parse("favicon.ico"),
                    identityName = "Epoch Defenders",
                    chain = "solana:devnet"
                )
                authToken = result.authToken
                _walletAddress.value = result.accounts.firstOrNull()?.let { account ->
                    Base58.encode(account.publicKey)
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Wallet connect failed", e)
            _walletAddress.value = null
        } finally {
            _isConnecting.value = false
        }
    }

    /**
     * Sign and send a serialized Solana transaction via the connected wallet.
     *
     * @param transaction  Serialized transaction bytes (unsigned message).
     * @return Transaction signature as a string, or null on failure.
     */
    suspend fun signAndSend(transaction: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val sender = ActivityResultSender(activity)
            val adapter = MobileWalletAdapter()
            var signature: String? = null
            adapter.transact(sender) {
                val result = signAndSendTransactions(arrayOf(transaction))
                signature = result.signatures.firstOrNull()?.let { sig ->
                    Base58.encode(sig)
                }
            }
            signature
        } catch (e: Exception) {
            AppLog.e(TAG, "Sign and send failed", e)
            null
        }
    }

    /** Disconnect wallet and clear cached auth state. */
    fun disconnect() {
        _walletAddress.value = null
        authToken = null
    }

    /** Whether a wallet is currently connected. */
    fun isConnected(): Boolean = _walletAddress.value != null

    /**
     * Truncate address for UI display: "Ab12...Xy89".
     * Returns null if no wallet is connected.
     */
    fun truncatedAddress(): String? {
        val addr = _walletAddress.value ?: return null
        if (addr.length <= 8) return addr
        return "${addr.take(4)}...${addr.takeLast(4)}"
    }
}
