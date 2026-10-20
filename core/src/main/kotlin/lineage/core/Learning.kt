package lineage.core

import java.io.File
import kotlin.math.sqrt

/**
 * How one species' brain has done over time: average reward per decision and entropy (how undecided it is),
 * one point per learning update. When it gets long, neighbouring points are merged, so it never grows past
 * [maxPoints] and still covers the whole run.
 */
class LearnCurve(val maxPoints: Int = 1200) {
    var tick = FloatArray(maxPoints); private set
    var reward = FloatArray(maxPoints); private set
    var entropy = FloatArray(maxPoints); private set
    private var weight = IntArray(maxPoints)
    var n = 0; private set

    fun add(t: Long, r: Float, e: Float) {
        if (n == maxPoints) halve()
        tick[n] = t.toFloat(); reward[n] = r; entropy[n] = e; weight[n] = 1
        n++
    }

    private fun halve() {
        var k = 0
        var j = 0
        while (j + 1 < n) {
            val w1 = weight[j]; val w2 = weight[j + 1]; val ws = (w1 + w2).toFloat()
            tick[k] = tick[j + 1]
            reward[k] = (reward[j] * w1 + reward[j + 1] * w2) / ws
            entropy[k] = (entropy[j] * w1 + entropy[j + 1] * w2) / ws
            weight[k] = w1 + w2
            k++; j += 2
        }
        if (j < n) { tick[k] = tick[j]; reward[k] = reward[j]; entropy[k] = entropy[j]; weight[k] = weight[j]; k++ }
        n = k
    }

    fun clear() { n = 0 }

    fun copy(): LearnCurve {
        val c = LearnCurve(maxPoints)
        System.arraycopy(tick, 0, c.tick, 0, n); System.arraycopy(reward, 0, c.reward, 0, n)
        System.arraycopy(entropy, 0, c.entropy, 0, n); System.arraycopy(weight, 0, c.weight, 0, n)
        c.n = n
        return c
    }

    fun lastEntropy(): Float = if (n == 0) 0f else entropy[n - 1]

    companion object {
        /** Reads the exam rows of a training.csv: score (reward per decision) and meals per creature-day. */
        fun readExams(file: File): Array<LearnCurve> {
            val out = Array(NSP) { LearnCurve(400) }
            if (!file.exists()) return out
            file.bufferedReader().use { r ->
                val header = r.readLine() ?: return out
                if (!header.contains(",family,")) return out
                while (true) {
                    val line = r.readLine() ?: break
                    val p = line.split(',')
                    if (p.size < 7 || p[3] != "exam") continue
                    val s = Species.all.indexOfFirst { it.name == p[1] }
                    if (s < 0) continue
                    out[s].add(p[0].toLongOrNull() ?: continue, p[5].toFloatOrNull() ?: continue, p[6].toFloatOrNull() ?: 0f)
                }
            }
            return out
        }

        /** Reads the learning rows of a training.csv (old and new layout) into one curve per species. */
        fun readCsv(file: File, maxPoints: Int = 1200): Array<LearnCurve> {
            val out = Array(NSP) { LearnCurve(maxPoints) }
            if (!file.exists()) return out
            var lastTick = -1L
            file.bufferedReader().use { r ->
                val header = r.readLine() ?: return out
                val hasFamily = header.contains(",family,")
                val off = if (hasFamily) 1 else 0
                while (true) {
                    val line = r.readLine() ?: break
                    val p = line.split(',')
                    if (p.size < 9 + off) continue
                    if (p[2 + off] != "learn") continue
                    val t = p[0].toLongOrNull() ?: continue
                    // a restart inside the same file: keep only the newest run
                    if (t < lastTick - 1000) for (c in out) c.clear()
                    lastTick = t
                    val s = Species.all.indexOfFirst { it.name == p[1] }
                    if (s < 0) continue
                    val rew = p[4 + off].toFloatOrNull() ?: continue
                    val ent = p[7 + off].toFloatOrNull() ?: continue
                    out[s].add(t, rew, ent)
                }
            }
            return out
        }
    }
}

/** Is a species' brain getting better? Compares the start of the run with the end, allowing for noise. */
class Trend(val kind: Int, val early: Float, val late: Float, val points: Int) {
    val text: String get() = when (kind) {
        UP -> "improving"
        DOWN -> "getting worse"
        FLAT -> "flat"
        else -> "too early"
    }
    val arrow: String get() = when (kind) { UP -> "↑"; DOWN -> "↓"; FLAT -> "→"; else -> "…" }

    companion object {
        const val UP = 1
        const val FLAT = 0
        const val DOWN = -1
        const val EARLY = 2

        /**
         * Splits the curve into 10 equal parts, then compares the first 3 parts with the last 3.
         * The change must be larger than twice its uncertainty (from how much the parts differ) to count.
         */
        /**
         * For exam scores (few points, each from the same test world): a straight line through the scores.
         * Improving if the line rises by more than twice its uncertainty.
         */
        fun ofExam(c: LearnCurve): Trend {
            val n = c.n
            if (n < 5) return Trend(EARLY, 0f, 0f, n)
            var mx = 0.0; var my = 0.0
            for (i in 0 until n) { mx += i; my += c.reward[i] }
            mx /= n; my /= n
            var sxx = 0.0; var sxy = 0.0
            for (i in 0 until n) { sxx += (i - mx) * (i - mx); sxy += (i - mx) * (c.reward[i] - my) }
            val slope = sxy / sxx
            var sse = 0.0
            for (i in 0 until n) { val e = c.reward[i] - (my + slope * (i - mx)); sse += e * e }
            val se = sqrt(sse / (n - 2) / sxx)
            val first = (my - slope * mx).toFloat(); val last = (my + slope * (n - 1 - mx)).toFloat()
            val diff = last - first
            val kind = if (slope > 2 * se && diff > 0.001f) UP else if (slope < -2 * se && diff < -0.001f) DOWN else FLAT
            return Trend(kind, first, last, n)
        }

        fun of(c: LearnCurve): Trend {
            val n = c.n
            if (n < 20) return Trend(EARLY, 0f, 0f, n)
            val blocks = 10
            val m = FloatArray(blocks)
            for (b in 0 until blocks) {
                val s = b * n / blocks; val e = (b + 1) * n / blocks
                var sum = 0.0
                for (i in s until e) sum += c.reward[i]
                m[b] = (sum / (e - s)).toFloat()
            }
            val early = (m[0] + m[1] + m[2]) / 3f
            val late = (m[7] + m[8] + m[9]) / 3f
            fun sd(a: Int, b: Int): Double {
                val mean = (a..b).sumOf { m[it].toDouble() } / (b - a + 1)
                return sqrt((a..b).sumOf { (m[it] - mean) * (m[it] - mean) } / (b - a))
            }
            val se = sqrt((sd(0, 2) * sd(0, 2) + sd(7, 9) * sd(7, 9)) / 3.0)
            val diff = late - early
            val kind = if (diff > 2 * se && diff > 0.001f) UP else if (diff < -2 * se && diff < -0.001f) DOWN else FLAT
            return Trend(kind, early, late, n)
        }
    }
}
