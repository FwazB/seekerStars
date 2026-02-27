package com.epochdefenders.solana

/**
 * Base58 encoding/decoding using the standard Bitcoin/Solana alphabet.
 * Pure Kotlin — no external dependencies.
 */
object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }.also { arr ->
        ALPHABET.forEachIndexed { i, c -> arr[c.code] = i }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        // Work on a copy — divmod mutates the array in place
        val number = input.copyOf()

        // Count leading zeros
        var leadingZeros = 0
        while (leadingZeros < number.size && number[leadingZeros] == 0.toByte()) {
            leadingZeros++
        }

        // Convert base-256 to base-58
        val encoded = CharArray(number.size * 2) // upper bound
        var outputStart = encoded.size
        var inputStart = leadingZeros

        while (inputStart < number.size) {
            val remainder = divmod(number, inputStart, 256, 58)
            if (number[inputStart] == 0.toByte()) inputStart++
            encoded[--outputStart] = ALPHABET[remainder]
        }

        // Preserve leading zeros as '1' characters
        while (outputStart < encoded.size && encoded[outputStart] == ALPHABET[0]) {
            outputStart++
        }
        repeat(leadingZeros) {
            encoded[--outputStart] = ALPHABET[0]
        }

        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        // Convert base-58 string to base-256 byte array
        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid Base58 character '$c' at position $i" }
            input58[i] = digit.toByte()
        }

        // Count leading zeros (Base58 '1' = 0x00)
        var leadingZeros = 0
        while (leadingZeros < input58.size && input58[leadingZeros] == 0.toByte()) {
            leadingZeros++
        }

        // Convert base-58 digits to base-256
        val decoded = ByteArray(input.length) // upper bound
        var outputStart = decoded.size

        var inputStart = leadingZeros
        while (inputStart < input58.size) {
            val remainder = divmod(input58, inputStart, 58, 256)
            if (input58[inputStart] == 0.toByte()) inputStart++
            decoded[--outputStart] = remainder.toByte()
        }

        // Skip extra leading zeros from conversion
        while (outputStart < decoded.size && decoded[outputStart] == 0.toByte()) {
            outputStart++
        }

        // Restore original leading zeros
        val result = ByteArray(leadingZeros + (decoded.size - outputStart))
        System.arraycopy(decoded, outputStart, result, leadingZeros, decoded.size - outputStart)
        return result
    }

    /**
     * Divides a number represented as a byte array in the given base,
     * returning the remainder in the target base.
     */
    private fun divmod(number: ByteArray, firstNonZero: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstNonZero until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder
    }
}
