package com.example.quickstash.domain

import com.example.quickstash.data.nas.NasFile

data class ManualLayout(
    val orderIndex: Map<String, Int>,
    val groupId: Map<String, Long>,
) {
    val isEmpty: Boolean get() = orderIndex.isEmpty()
}

object MediaSort {

    fun similarOrder(
        media: List<NasFile>,
        signatures: Map<String, ImageSignature>,
    ): List<NasFile> {
        val withSig = media.filter { signatures.containsKey(it.path) }
        val withoutSig = media.filterNot { signatures.containsKey(it.path) }
        if (withSig.size <= 1) return media
        val remaining = withSig.toMutableList()
        val result = ArrayList<NasFile>(withSig.size)
        var current = remaining.removeAt(0)
        result.add(current)
        while (remaining.isNotEmpty()) {
            val curSig = signatures.getValue(current.path)
            var bestIndex = 0
            var bestDistance = Float.MAX_VALUE
            for (index in remaining.indices) {
                val candidate = remaining[index]
                val distance = ImageSignatures.distanceUpTo(
                    curSig,
                    signatures.getValue(candidate.path),
                    bestDistance,
                )
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            val next = remaining.removeAt(bestIndex)
            result.add(next)
            current = next
        }
        return result + withoutSig
    }

    fun clumpGroups(ordered: List<NasFile>, layout: ManualLayout): List<NasFile> {
        if (layout.groupId.isEmpty()) return ordered
        val membersByGroup = ordered
            .filter { layout.groupId.containsKey(it.path) }
            .groupBy { layout.groupId.getValue(it.path) }
        val emitted = mutableSetOf<Long>()
        val result = ArrayList<NasFile>(ordered.size)
        for (file in ordered) {
            val gid = layout.groupId[file.path]
            if (gid == null) {
                result.add(file)
            } else if (emitted.add(gid)) {
                result.addAll(
                    membersByGroup.getValue(gid)
                        .sortedBy { layout.orderIndex[it.path] ?: Int.MAX_VALUE },
                )
            }
        }
        return result
    }
}
