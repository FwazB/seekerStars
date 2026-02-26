package com.epochdefenders.solana

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Base58Test {

    @Test
    fun `encode empty bytes returns empty string`() {
        assertEquals("", Base58.encode(ByteArray(0)))
    }

    @Test
    fun `decode empty string returns empty bytes`() {
        assertArrayEquals(ByteArray(0), Base58.decode(""))
    }

    @Test
    fun `encode single zero byte is 1`() {
        assertEquals("1", Base58.encode(byteArrayOf(0)))
    }

    @Test
    fun `decode 1 is single zero byte`() {
        assertArrayEquals(byteArrayOf(0), Base58.decode("1"))
    }

    @Test
    fun `encode leading zeros preserved`() {
        // 3 leading zero bytes → "111" prefix
        val input = byteArrayOf(0, 0, 0, 1)
        val encoded = Base58.encode(input)
        assertEquals("1112", encoded)
    }

    @Test
    fun `decode leading 1s preserved as zero bytes`() {
        val decoded = Base58.decode("1112")
        assertArrayEquals(byteArrayOf(0, 0, 0, 1), decoded)
    }

    @Test
    fun `roundtrip known Solana address`() {
        // Memo program ID
        val address = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
        val bytes = Base58.decode(address)
        assertEquals(32, bytes.size) // Solana public key is 32 bytes
        assertEquals(address, Base58.encode(bytes))
    }

    @Test
    fun `roundtrip arbitrary bytes`() {
        val original = byteArrayOf(
            0x01, 0x02, 0x03, 0x7F, 0x00, 0xFF.toByte(), 0xFE.toByte(), 0x80.toByte()
        )
        val encoded = Base58.encode(original)
        val decoded = Base58.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `encode known vector - hello world`() {
        // "Hello World!" in Base58
        val input = "Hello World!".toByteArray(Charsets.US_ASCII)
        val encoded = Base58.encode(input)
        assertEquals("2NEpo7TZRRrLZSi2U", encoded)
    }

    @Test
    fun `decode known vector - hello world`() {
        val decoded = Base58.decode("2NEpo7TZRRrLZSi2U")
        assertEquals("Hello World!", String(decoded, Charsets.US_ASCII))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects invalid character 0`() {
        Base58.decode("0InvalidBase58")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects invalid character I`() {
        Base58.decode("IAmInvalid")
    }

    @Test
    fun `roundtrip 32 zero bytes`() {
        val zeros = ByteArray(32)
        val encoded = Base58.encode(zeros)
        // 32 leading zeros → 32 '1' chars
        assertEquals(32, encoded.length)
        assertEquals("1".repeat(32), encoded)
        assertArrayEquals(zeros, Base58.decode(encoded))
    }
}
