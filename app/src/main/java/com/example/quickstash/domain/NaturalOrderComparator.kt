package com.example.quickstash.domain

object NaturalOrderComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        var zeroTiebreak = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val runA = a.substring(startA, i)
                val runB = b.substring(startB, j)
                val numA = runA.trimStart('0')
                val numB = runB.trimStart('0')
                val numCompare = when {
                    numA.length != numB.length -> numA.length - numB.length
                    else -> numA.compareTo(numB)
                }
                if (numCompare != 0) return numCompare
                if (zeroTiebreak == 0) zeroTiebreak = runA.length - runB.length
            } else {
                val la = ca.lowercaseChar()
                val lb = cb.lowercaseChar()
                if (la != lb) return la.compareTo(lb)
                i++
                j++
            }
        }
        val remaining = (a.length - i) - (b.length - j)
        if (remaining != 0) return remaining
        if (zeroTiebreak != 0) return zeroTiebreak
        return a.compareTo(b)
    }
}
