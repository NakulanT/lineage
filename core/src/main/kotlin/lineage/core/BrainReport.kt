package lineage.core

import lineage.core.rl.ACTION_NAMES
import lineage.core.rl.BrainStats
import lineage.core.rl.NA
import lineage.core.rl.OBS
import lineage.core.rl.OBS_NAMES
import java.io.File

/**
 * Writes what the brains have learned, as two CSV files for a spreadsheet:
 *  - brain_report.csv: for each species, choice and sense, how much that sense pushes the choice (feature attribution).
 *  - brain_neurons.csv: for each hidden neuron, how often it is off or maxed out, and the senses it reacts to most.
 */
object BrainReport {
    fun write(stats: BrainStats, dir: File) {
        dir.mkdirs()
        File(dir, "brain_report.csv").bufferedWriter().use { w ->
            w.write("species,choice,times_sampled,sense,mean_push,mean_strength\n")
            for (s in 0 until NSP) for (a in 0 until NA) {
                val n = stats.samples[s][a]
                if (n < 1f) continue
                for (i in 0 until OBS) {
                    w.write("${Species.all[s].name},${ACTION_NAMES[a]},${"%.0f".format(n)},${OBS_NAMES[i]}," +
                        "${"%.5f".format(stats.sumAttr[s][a][i] / n)},${"%.5f".format(stats.absAttr[s][a][i] / n)}\n")
                }
            }
        }
        File(dir, "brain_neurons.csv").bufferedWriter().use { w ->
            w.write("species,layer,neuron,share_off,share_maxed,mean_activity,reacts_most_to,reacts_second_to\n")
            for (s in 0 until NSP) {
                val n = stats.neuronSamples[s]
                if (n < 1f) continue
                for (l in stats.off[s].indices) for (k in stats.off[s][l].indices) {
                    val top = topInputs(stats, s, l, k)
                    w.write("${Species.all[s].name},${l + 1},${k + 1},${"%.3f".format(stats.off[s][l][k] / n)}," +
                        "${"%.3f".format(stats.maxed[s][l][k] / n)},${"%.3f".format(stats.meanAbs[s][l][k] / n)},${top.getOrElse(0) { "" }},${top.getOrElse(1) { "" }}\n")
                }
            }
        }
    }

    /** The senses a neuron reacts to most, as "name (+)" or "name (-)" (fires with or against that sense). */
    fun topInputs(stats: BrainStats, s: Int, l: Int, k: Int, n: Int = 2): List<String> {
        val lk = stats.link[s][l][k]
        return (0 until OBS).sortedByDescending { kotlin.math.abs(lk[it]) }.take(n)
            .map { "${OBS_NAMES[it]} (${if (lk[it] >= 0f) "+" else "-"})" }
    }

    /** Neuron health of one layer: how many are mostly off, mostly maxed, and working. */
    fun layerHealth(stats: BrainStats, s: Int, l: Int): IntArray {
        val n = stats.neuronSamples[s]
        if (n < 1f) return intArrayOf(0, 0, 0)
        var off = 0; var maxed = 0; var ok = 0
        for (k in stats.off[s][l].indices) {
            when {
                stats.off[s][l][k] / n > 0.9f -> off++
                stats.maxed[s][l][k] / n > 0.9f -> maxed++
                else -> ok++
            }
        }
        return intArrayOf(ok, off, maxed)
    }
}
