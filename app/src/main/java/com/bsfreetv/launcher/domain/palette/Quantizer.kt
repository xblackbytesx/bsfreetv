package com.bsfreetv.launcher.domain.palette

/** One quantised colour and how many sample pixels it stands for. */
data class Swatch(val color: Rgb, val population: Int)

/**
 * Median-cut quantiser. Stands in for Impasto's `magick -colors 24 -unique-colors`
 * on a 120x120 sample: the caller downsizes, this reduces to at most [maxColors]
 * averaged colours. Transparent pixels are ignored.
 */
object Quantizer {
    fun quantize(pixels: IntArray, maxColors: Int = 24): List<Swatch> {
        require(maxColors >= 1)
        // Pack to 5-5-5 bits first: plenty for an accent pick and keeps boxes small.
        val counts = HashMap<Int, Int>()
        for (p in pixels) {
            if ((p ushr 24) < 0x80) continue
            val key = (((p shr 19) and 0x1F) shl 10) or (((p shr 11) and 0x1F) shl 5) or ((p shr 3) and 0x1F)
            counts[key] = (counts[key] ?: 0) + 1
        }
        if (counts.isEmpty()) return emptyList()
        val colors = counts.keys.toIntArray()
        val boxes = ArrayList<Box>().apply { add(Box(colors, counts)) }
        while (boxes.size < maxColors) {
            val target = boxes.filter { it.size > 1 }.maxByOrNull { it.longestSide() * it.population.toLong() } ?: break
            boxes.remove(target)
            boxes.addAll(target.split())
        }
        return boxes.map { it.average() }.sortedByDescending { it.population }
    }

    private class Box(val keys: IntArray, val counts: Map<Int, Int>) {
        val size get() = keys.size
        val population: Int = keys.sumOf { counts.getValue(it) }

        private fun ch(key: Int, c: Int) = (key shr (10 - 5 * c)) and 0x1F

        fun range(c: Int): Int {
            var lo = 31
            var hi = 0
            for (k in keys) {
                val v = ch(k, c)
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            return hi - lo
        }

        fun longestSide() = maxOf(range(0), range(1), range(2))

        fun split(): List<Box> {
            val c = (0..2).maxBy { range(it) }
            val sorted = keys.sortedBy { ch(it, c) }
            val half = population / 2
            var acc = 0
            var cut = 1
            for ((i, k) in sorted.withIndex()) {
                acc += counts.getValue(k)
                if (acc >= half) {
                    cut = (i + 1).coerceIn(1, sorted.size - 1)
                    break
                }
            }
            return listOf(
                Box(sorted.subList(0, cut).toIntArray(), counts),
                Box(sorted.subList(cut, sorted.size).toIntArray(), counts),
            )
        }

        fun average(): Swatch {
            var r = 0L
            var g = 0L
            var b = 0L
            for (k in keys) {
                val n = counts.getValue(k)
                // Expand 5 bits back to 8 (x * 255 / 31).
                r += ch(k, 0) * 255L / 31 * n
                g += ch(k, 1) * 255L / 31 * n
                b += ch(k, 2) * 255L / 31 * n
            }
            val p = population.toLong()
            return Swatch(Rgb((r / p).toInt(), (g / p).toInt(), (b / p).toInt()), population)
        }
    }
}
