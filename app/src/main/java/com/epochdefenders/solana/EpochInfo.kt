package com.epochdefenders.solana

data class EpochInfo(
    val epoch: Long,
    val slotIndex: Long,
    val slotsInEpoch: Long,
    val absoluteSlot: Long,
    val blockHeight: Long? = null,
    val transactionCount: Long? = null
)
