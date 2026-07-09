package com.example.quickstash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSignatureTest {

    private fun gradient(w: Int = 32, h: Int = 32): IntArray {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val v = (x * 255 / w)
            px[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return px
    }

    private fun coloredScene(hueShift: Int, w: Int = 32, h: Int = 32): IntArray {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val r = if (hueShift == 0) 200 else 40
            val g = 80
            val b = if (hueShift == 0) 40 else 200
            px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return px
    }

    @Test
    fun `同一画像は距離0`() {
        val g = gradient()
        val sig = ImageSignatures.compute(g, 32, 32)
        assertEquals(0f, ImageSignatures.distance(sig, sig), 0.0001f)
    }

    @Test
    fun `リサイズ版は距離が小さい`() {
        val a = ImageSignatures.compute(gradient(64, 64), 64, 64)
        val b = ImageSignatures.compute(gradient(32, 32), 32, 32)
        val distance = ImageSignatures.distance(a, b)
        assertTrue("distance=$distance", distance < 0.1f)
    }

    @Test
    fun `モノクロ同士は構造の違いで大きく離れる`() {
        val whiteToBlack = gradient()
        val blackToWhite = IntArray(32 * 32) { i ->
            val x = i % 32
            val v = 255 - (x * 255 / 32)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val a = ImageSignatures.compute(whiteToBlack, 32, 32)
        val b = ImageSignatures.compute(blackToWhite, 32, 32)
        val distance = ImageSignatures.distance(a, b)
        assertTrue("distance=$distance", distance > 0.35f)
    }

    @Test
    fun `カラー画像同士は色の違いが距離に効く`() {
        val red = ImageSignatures.compute(coloredScene(0), 32, 32)
        val blue = ImageSignatures.compute(coloredScene(1), 32, 32)
        val distance = ImageSignatures.distance(red, blue)
        assertTrue("distance=$distance", distance > 0.3f)
    }
}
