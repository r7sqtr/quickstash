package com.example.quickstash.domain

import com.example.quickstash.data.nas.NasFile
import org.junit.Assert.assertEquals
import org.junit.Test

class SortSpecTest {

    private fun file(
        name: String,
        size: Long = 0,
        ctime: Long = 0,
    ) = NasFile(path = name, name = name, size = size, mtime = 0, ctime = ctime, isDirectory = false)

    @Test
    fun `デフォルトは名前の自然順昇順`() {
        val files = listOf(file("a-11.jpg"), file("a-2.jpg"))
        val sorted = files.sortedWith(SortSpec.DEFAULT.comparator())
        assertEquals(listOf("a-2.jpg", "a-11.jpg"), sorted.map { it.name })
    }

    @Test
    fun `容量降順`() {
        val files = listOf(file("a.jpg", size = 10), file("b.jpg", size = 30), file("c.jpg", size = 20))
        val spec = SortSpec(listOf(SortEntry(SortKey.SIZE, ascending = false)))
        assertEquals(
            listOf("b.jpg", "c.jpg", "a.jpg"),
            files.sortedWith(spec.comparator()).map { it.name },
        )
    }

    @Test
    fun `複合ソート タイプ優先で同タイプ内は名前順`() {
        val files = listOf(
            file("b.mp4"),
            file("z.jpg"),
            file("a.mp4"),
            file("c.jpg"),
        )
        val spec = SortSpec(
            listOf(SortEntry(SortKey.TYPE, true), SortEntry(SortKey.NAME, true)),
        )
        assertEquals(
            listOf("c.jpg", "z.jpg", "a.mp4", "b.mp4"),
            files.sortedWith(spec.comparator()).map { it.name },
        )
    }

    @Test
    fun `追加日時昇順`() {
        val files = listOf(file("new.jpg", ctime = 200), file("old.jpg", ctime = 100))
        val spec = SortSpec(listOf(SortEntry(SortKey.CREATED, true)))
        assertEquals(
            listOf("old.jpg", "new.jpg"),
            files.sortedWith(spec.comparator()).map { it.name },
        )
    }

    @Test
    fun `シリアライズ往復`() {
        val spec = SortSpec(
            listOf(SortEntry(SortKey.TYPE, true), SortEntry(SortKey.SIZE, false)),
        )
        assertEquals(spec, SortSpec.parse(spec.serialize()))
    }

    @Test
    fun `不正な文字列はデフォルトにフォールバック`() {
        assertEquals(SortSpec.DEFAULT, SortSpec.parse("garbage"))
        assertEquals(SortSpec.DEFAULT, SortSpec.parse(null))
        assertEquals(SortSpec.DEFAULT, SortSpec.parse(""))
    }

    @Test
    fun `toggled は 追加 → 降順 → 解除 の順に遷移する`() {
        val empty = SortSpec(emptyList())
        val added = empty.toggled(SortKey.SIZE)
        assertEquals(listOf(SortEntry(SortKey.SIZE, true)), added.entries)
        val descending = added.toggled(SortKey.SIZE)
        assertEquals(listOf(SortEntry(SortKey.SIZE, false)), descending.entries)
        val removed = descending.toggled(SortKey.SIZE)
        assertEquals(emptyList<SortEntry>(), removed.entries)
    }

    @Test
    fun `タップしたキーが最優先に追加される`() {
        val fromDefault = SortSpec.DEFAULT.toggled(SortKey.SIZE)
        assertEquals(
            listOf(SortEntry(SortKey.SIZE, true), SortEntry(SortKey.NAME, true)),
            fromDefault.entries,
        )
    }

    @Test
    fun `選択済みの下位キーをタップすると最優先に昇格する`() {
        val spec = SortSpec(
            listOf(SortEntry(SortKey.NAME, true), SortEntry(SortKey.SIZE, false)),
        )
        val promoted = spec.toggled(SortKey.SIZE)
        assertEquals(
            listOf(SortEntry(SortKey.SIZE, false), SortEntry(SortKey.NAME, true)),
            promoted.entries,
        )
    }

    @Test
    fun `類似画像は 有効 → 解除 の2状態のみ`() {
        val empty = SortSpec(emptyList())
        val added = empty.toggled(SortKey.SIMILAR)
        assertEquals(listOf(SortEntry(SortKey.SIMILAR, true)), added.entries)
        val removed = added.toggled(SortKey.SIMILAR)
        assertEquals(emptyList<SortEntry>(), removed.entries)
    }

    @Test
    fun `グループ優先＋類似画像 グループ外は類似順に並ぶ`() {
        val files = listOf(file("x.jpg"), file("y.jpg"), file("g1b.jpg"), file("z.jpg"), file("g1a.jpg"))
        val context = SortContext(
            similarRank = mapOf("z.jpg" to 0, "x.jpg" to 1, "y.jpg" to 2),
            groupIdOf = mapOf("g1a.jpg" to 1L, "g1b.jpg" to 1L),
            orderIndexOf = mapOf(
                "g1a.jpg" to 0,
                "g1b.jpg" to 1,
                "x.jpg" to 2,
                "y.jpg" to 3,
                "z.jpg" to 4,
            ),
        )
        val spec = SortSpec(
            listOf(SortEntry(SortKey.GROUP, true), SortEntry(SortKey.SIMILAR, true)),
        )
        assertEquals(
            listOf("g1a.jpg", "g1b.jpg", "z.jpg", "x.jpg", "y.jpg"),
            files.sortedWith(spec.comparator(context)).map { it.name },
        )
    }
}
