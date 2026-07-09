package com.example.quickstash.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderComparatorTest {

    private fun sorted(vararg names: String): List<String> =
        names.toList().sortedWith(NaturalOrderComparator)

    @Test
    fun `数字部分は数値として比較される`() {
        val input = (1..11).map { "a-$it.jpg" }.shuffled()
        val expected = (1..11).map { "a-$it.jpg" }
        assertEquals(expected, sorted(*input.toTypedArray()))
    }

    @Test
    fun `2と11では2が先`() {
        assertEquals(listOf("a-2.jpg", "a-11.jpg"), sorted("a-11.jpg", "a-2.jpg"))
    }

    @Test
    fun `複数の数字ブロックを持つ名前`() {
        assertEquals(
            listOf("img2-3.png", "img2-10.png", "img10-1.png"),
            sorted("img10-1.png", "img2-10.png", "img2-3.png"),
        )
    }

    @Test
    fun `先頭ゼロは数値が同じならゼロ埋めが少ない方が先`() {
        assertEquals(listOf("a-1.jpg", "a-01.jpg"), sorted("a-01.jpg", "a-1.jpg"))
        assertEquals(listOf("a-01.jpg", "a-2.jpg"), sorted("a-2.jpg", "a-01.jpg"))
    }

    @Test
    fun `大文字小文字は無視される`() {
        assertEquals(listOf("Apple.jpg", "banana.jpg"), sorted("banana.jpg", "Apple.jpg"))
    }

    @Test
    fun `数字なしは通常の辞書順`() {
        assertEquals(listOf("abc", "abd"), sorted("abd", "abc"))
    }

    @Test
    fun `前方一致の場合は短い方が先`() {
        assertEquals(listOf("a", "ab"), sorted("ab", "a"))
    }

    @Test
    fun `数字と文字の混在`() {
        assertEquals(
            listOf("1.jpg", "2.jpg", "10.jpg", "a.jpg"),
            sorted("a.jpg", "10.jpg", "1.jpg", "2.jpg"),
        )
    }

    @Test
    fun `巨大な数字でもオーバーフローしない`() {
        assertEquals(
            listOf("f-99999999999999999998", "f-99999999999999999999"),
            sorted("f-99999999999999999999", "f-99999999999999999998"),
        )
    }

    @Test
    fun `同一文字列は等しい`() {
        assertEquals(0, NaturalOrderComparator.compare("a-1.jpg", "a-1.jpg"))
    }

    @Test
    fun `日本語ファイル名も安定して並ぶ`() {
        assertEquals(
            listOf("旅行1.jpg", "旅行2.jpg", "旅行10.jpg"),
            sorted("旅行10.jpg", "旅行1.jpg", "旅行2.jpg"),
        )
    }
}
