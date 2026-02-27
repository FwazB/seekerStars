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
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult

class WalletManager(private val activity: ComponentActivity) {
    companion object {
        private const val TAG = "WalletManager"
    }

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _walletError = MutableStateFlow<String?>(null)
    val walletError: StateFlow<String?> = _walletError.asStateFlow()

    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://epochdefenders.com"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Epoch Defenders"
        )
    ).apply {
        blockchain = Solana.Devnet
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        _walletError.value = null
        _isConnecting.value = true
        try {
            val sender = ActivityResultSender(activity)
            val result = adapter.transact(sender) { authResult ->
                authResult.accounts.firstOrNull()?.let { account ->
                    Base58.encode(account.publicKey)
                }
            }
            when (result) {
                is TransactionResult.Success -> _walletAddress.value = result.payload
                is TransactionResult.Failure -> {
                    _walletError.value = result.message
                    throw Exception(result.message)
                }
                is TransactionResult.NoWalletFound -> {
                    _walletError.value = "No compatible Solana wallet found. Please ensure the Seeker wallet is enabled, or install Solflare."
                    throw Exception(result.message)
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Wallet connect failed", e)
            _walletAddress.value = null
        } finally {
            _isConnecting.value = false
        }
    }

    suspend fun signAndSend(transaction: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val sender = ActivityResultSender(activity)
            val result = adapter.transact(sender) { authResult ->
                val txResult = signAndSendTransactions(arrayOf(transaction))
                txResult.signatures.firstOrNull()?.let { sig ->
                    Base58.encode(sig)
                }
            }
            when (result) {
                is TransactionResult.Success -> result.payload
                is TransactionResult.Failure -> throw Exception(result.message)
                is TransactionResult.NoWalletFound -> throw Exception(result.message)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Sign and send failed", e)
            null
        }
    }

    fun disconnect() {
        _walletAddress.value = null
    }

    fun isConnected(): Boolean = _walletAddress.value != null

    fun truncatedAddress(): String? {
        val addr = _walletAddress.value ?: return null
        if (addr.length <= 8) return addr
        return "${addr.take(4)}...${addr.takeLast(4)}"
    }
}
