package com.example.quickstash.domain

import com.example.quickstash.data.nas.NasFile

enum class SortKey(val id: String, val label: String) {
    NAME("name", "名前"),
    SIZE("size", "サイズ"),
    TYPE("type", "タイプ"),
    CREATED("created", "追加日時"),
    SIMILAR("similar", "類似画像"),
    GROUP("group", "グループ"),
    ;

    companion object {
        fun fromId(id: String): SortKey? = entries.firstOrNull { it.id == id }
    }
}

data class SortEntry(val key: SortKey, val ascending: Boolean)

data class SortContext(
    val similarRank: Map<String, Int> = emptyMap(),
    val groupIdOf: Map<String, Long> = emptyMap(),
    val orderIndexOf: Map<String, Int> = emptyMap(),
)

data class SortSpec(val entries: List<SortEntry>) {

    fun needs(key: SortKey): Boolean = entries.any { it.key == key }

    fun comparator(context: SortContext = SortContext()): Comparator<NasFile> {
        val effective = entries.ifEmpty { DEFAULT.entries }
        var comparator: Comparator<NasFile>? = null
        for (entry in effective) {
            val base = comparatorFor(entry.key, context)
            val directed = if (entry.ascending) base else base.reversed()
            comparator = comparator?.then(directed) ?: directed
        }
        return comparator!!.then(compareBy(NaturalOrderComparator) { it.name })
    }

    fun serialize(): String =
        entries.joinToString(",") { "${it.key.id}:${if (it.ascending) "asc" else "desc"}" }

    fun toggled(key: SortKey): SortSpec {
        val index = entries.indexOfFirst { it.key == key }
        val existing = entries.getOrNull(index)
        return when {
            existing == null -> SortSpec(listOf(SortEntry(key, true)) + entries)
            index > 0 -> SortSpec(listOf(existing) + entries.filterNot { it.key == key })
            key == SortKey.SIMILAR || !existing.ascending ->
                SortSpec(entries.filterNot { it.key == key })
            else -> SortSpec(
                entries.map { if (it.key == key) it.copy(ascending = false) else it },
            )
        }
    }

    companion object {
        val DEFAULT = SortSpec(listOf(SortEntry(SortKey.NAME, true)))

        fun parse(raw: String?): SortSpec {
            when (raw) {
                null, "", "manual" -> return DEFAULT
                "similar" -> return SortSpec(listOf(SortEntry(SortKey.SIMILAR, true)))
                "groups" -> return SortSpec(listOf(SortEntry(SortKey.GROUP, true)))
                "tier" -> return DEFAULT
            }
            val parsed = raw.orEmpty().split(",").mapNotNull { token ->
                val parts = token.split(":")
                val key = SortKey.fromId(parts.getOrNull(0).orEmpty()) ?: return@mapNotNull null
                SortEntry(key, parts.getOrNull(1) != "desc")
            }
            return if (parsed.isEmpty()) DEFAULT else SortSpec(parsed)
        }

        private fun comparatorFor(key: SortKey, context: SortContext): Comparator<NasFile> =
            when (key) {
                SortKey.NAME -> compareBy(NaturalOrderComparator) { it.name }
                SortKey.SIZE -> compareBy { it.size }
                SortKey.CREATED -> compareBy { it.ctime }
                SortKey.TYPE -> compareBy<NasFile> { typeGroup(it) }.thenBy { it.extension }
                SortKey.SIMILAR -> compareBy {
                    context.similarRank[it.path] ?: Int.MAX_VALUE
                }
                SortKey.GROUP -> compareBy<NasFile> {
                    context.groupIdOf[it.path] ?: Long.MAX_VALUE
                }.thenBy {
                    if (context.groupIdOf.containsKey(it.path)) {
                        context.orderIndexOf[it.path] ?: Int.MAX_VALUE
                    } else {
                        0
                    }
                }
            }

        private fun typeGroup(file: NasFile): Int = when {
            file.isImage -> 0
            file.isVideo -> 1
            file.isPdf -> 2
            else -> 3
        }
    }
}
