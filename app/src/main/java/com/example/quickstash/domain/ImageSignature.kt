package com.example.quickstash.domain

class ImageSignature(
    val hashH: Long,
    val hashV: Long,
    val histogram: ByteArray,
) {
    val colorfulness: Float = ImageSignatures.colorfulnessOf(histogram)
}

object ImageSignatures {

    private const val GRID = 9
    private const val HUE_BINS = 8
    private const val SAT_BINS = 3
    private const val VAL_BINS = 4
    const val HISTOGRAM_SIZE = HUE_BINS * SAT_BINS * VAL_BINS

    private const val MIN_COLOR_WEIGHT = 0.12f
    private const val MAX_COLOR_WEIGHT = 0.55f
    private const val COLORFULNESS_SATURATION_POINT = 0.3f

    fun compute(pixels: IntArray, width: Int, height: Int): ImageSignature {
        val gray = downscaleToGray(pixels, width, height)
        var h = 0L
        var v = 0L
        var bit = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val cell = gray[row * GRID + col]
                if (cell > gray[row * GRID + col + 1]) h = h or (1L shl bit)
                if (cell > gray[(row + 1) * GRID + col]) v = v or (1L shl bit)
                bit++
            }
        }
        return ImageSignature(h, v, histogram(pixels))
    }

    fun distance(a: ImageSignature, b: ImageSignature): Float =
        distanceUpTo(a, b, Float.MAX_VALUE)

    fun distanceUpTo(a: ImageSignature, b: ImageSignature, bound: Float): Float {
        val structural =
            (java.lang.Long.bitCount(a.hashH xor b.hashH) +
                java.lang.Long.bitCount(a.hashV xor b.hashV)) / 128f
        val colorfulness = minOf(a.colorfulness, b.colorfulness)
        val scale = (colorfulness / COLORFULNESS_SATURATION_POINT).coerceAtMost(1f)
        val colorWeight = MIN_COLOR_WEIGHT + (MAX_COLOR_WEIGHT - MIN_COLOR_WEIGHT) * scale
        val structuralPart = (1f - colorWeight) * structural
        if (structuralPart >= bound) return structuralPart
        var histDiff = 0
        for (i in 0 until HISTOGRAM_SIZE) {
            histDiff += kotlin.math.abs(
                (a.histogram[i].toInt() and 0xFF) - (b.histogram[i].toInt() and 0xFF),
            )
        }
        val color = (histDiff / (2f * 255f)).coerceAtMost(1f)
        return structuralPart + colorWeight * color
    }

    fun colorfulnessOf(histogram: ByteArray): Float {
        var colorful = 0f
        var total = 0f
        for (i in histogram.indices) {
            val value = (histogram[i].toInt() and 0xFF).toFloat()
            total += value
            when ((i / VAL_BINS) % SAT_BINS) {
                1 -> colorful += value * 0.6f
                2 -> colorful += value
            }
        }
        if (total <= 0f) return 0f
        return (colorful / total).coerceIn(0f, 1f)
    }

    private fun histogram(pixels: IntArray): ByteArray {
        val bins = IntArray(HISTOGRAM_SIZE)
        val step = (pixels.size / 4096).coerceAtLeast(1)
        var sampled = 0
        var i = 0
        while (i < pixels.size) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val value = max
            val delta = max - min
            val saturation = if (max == 0) 0 else delta * 255 / max
            val hue = when {
                delta == 0 -> 0
                max == r -> ((g - b) * 60 / delta + 360) % 360
                max == g -> (b - r) * 60 / delta + 120
                else -> (r - g) * 60 / delta + 240
            }
            val hueBin = (hue * HUE_BINS / 360).coerceIn(0, HUE_BINS - 1)
            val satBin = (saturation * SAT_BINS / 256).coerceIn(0, SAT_BINS - 1)
            val valBin = (value * VAL_BINS / 256).coerceIn(0, VAL_BINS - 1)
            bins[(hueBin * SAT_BINS + satBin) * VAL_BINS + valBin]++
            sampled++
            i += step
        }
        val out = ByteArray(HISTOGRAM_SIZE)
        if (sampled > 0) {
            for (index in bins.indices) {
                out[index] = ((bins[index].toLong() * 255) / sampled)
                    .coerceIn(0, 255)
                    .toInt()
                    .toByte()
            }
        }
        return out
    }

    private fun downscaleToGray(pixels: IntArray, srcWidth: Int, srcHeight: Int): IntArray {
        val out = IntArray(GRID * GRID)
        for (y in 0 until GRID) {
            val y0 = (y * srcHeight / GRID).coerceIn(0, srcHeight - 1)
            val y1 = ((y + 1) * srcHeight / GRID).coerceIn(y0 + 1, srcHeight)
            for (x in 0 until GRID) {
                val x0 = (x * srcWidth / GRID).coerceIn(0, srcWidth - 1)
                val x1 = ((x + 1) * srcWidth / GRID).coerceIn(x0 + 1, srcWidth)
                var sum = 0L
                var count = 0
                for (sy in y0 until y1) {
                    for (sx in x0 until x1) {
                        val pixel = pixels[sy * srcWidth + sx]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        sum += (r * 30 + g * 59 + b * 11) / 100
                        count++
                    }
                }
                out[y * GRID + x] = if (count > 0) (sum / count).toInt() else 0
            }
        }
        return out
    }
}
