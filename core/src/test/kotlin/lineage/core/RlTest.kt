package lineage.core

import lineage.core.rl.Mlp
import lineage.core.rl.NA
import lineage.core.rl.OBS
import lineage.core.rl.RlBrain
import lineage.core.rl.RlParams
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class RlTest {
    /** The hand-written backpropagation must match a numerical gradient. */
    @Test
    fun mlpGradientMatchesNumeric() {
        val rng = Rng(3)
        val net = Mlp(intArrayOf(5, 7, 6, 3), rng)
        val x = FloatArray(5) { rng.range(-1f, 1f) }
        val target = floatArrayOf(0.3f, -0.2f, 0.9f)
        fun loss(): Double {
            val y = net.forward(x)
            var l = 0.0
            for (k in 0 until 3) l += 0.5 * (y[k] - target[k]) * (y[k] - target[k])
            return l
        }
        net.zeroGrad()
        val y = net.forward(x)
        net.backward(FloatArray(3) { y[it] - target[it] })
        val eps = 1e-3f
        for (l in 0 until net.nl) {
            for (k in net.w[l].indices step 3) {
                val old = net.w[l][k]
                net.w[l][k] = old + eps; val lp = loss()
                net.w[l][k] = old - eps; val lm = loss()
                net.w[l][k] = old
                val num = (lp - lm) / (2 * eps)
                assertTrue(abs(num - net.gw[l][k]) < 2e-3, "layer $l weight $k: numeric $num vs ${net.gw[l][k]}")
            }
        }
    }

    /** A one-step task where action 2 always pays: PPO must learn to prefer it. */
    @Test
    fun ppoLearnsTheRewardingChoice() {
        val brain = RlBrain(cap = 8, p = RlParams(batch = 256, minibatch = 64, lr = 3e-3f), seed = 11)
        brain.learning = true
        val o = FloatArray(OBS) { 0.5f }
        val m = BooleanArray(NA) { true }
        var reward = 0f
        var goodLate = 0
        val steps = 6000
        for (t in 0 until steps) {
            val a = brain.act(0, 1, 0, 0, o, m, reward)
            reward = if (a == 2) 1f else 0f
            if (t >= steps - 500 && a == 2) goodLate++
        }
        assertTrue(goodLate > 300, "picked the paying action only $goodLate of 500 times at the end")
    }

    @Test
    fun learningWorldRunsAndStaysInsideTheCap() {
        val cfg = SimConfig(seed = 4, width = 64, height = 64, startCounts = intArrayOf(4, 40, 20), hardCap = 300,
            plants = 420, shelters = intArrayOf(1, 2, 3), pretrainTicks = 600)
        val w = World(cfg)
        repeat(3000) { w.step(); assertTrue(w.aliveCount <= 300) }
        assertTrue(w.brain!!.learning)
    }

    @Test
    fun restartGivesANewWorldAndNewRandomBrains() {
        val cfg = SimConfig(seed = 5, width = 64, height = 64, startCounts = intArrayOf(3, 10, 40), hardCap = 300,
            plants = 200, shelters = intArrayOf(1, 2, 3))
        val w = World(cfg)
        repeat(2000) { w.step() }
        val oldWeight = w.brain!!.learners[0].pi.w[0][0]
        w.restart(99L)
        assertTrue(w.tick == 0L)
        assertTrue(w.count[0] == 3 && w.count[1] == 10 && w.count[2] == 40, "counts ${w.count.toList()}")
        assertTrue(w.brain!!.learners[0].pi.w[0][0] != oldWeight)
        assertTrue(w.brain!!.learners.all { it.updates == 0 })
        repeat(500) { w.step() }
    }

    /** Integrated gradients add up to the change in log-chance between "senses nothing" and the real input. */
    @Test
    fun attributionsAddUpToTheChangeInChance() {
        val brain = RlBrain(cap = 4, p = RlParams(hidden = 16), seed = 3)
        val L = brain.learners[0]
        // make the policy less flat so the test means something
        val rng = Rng(9)
        for (l in 0 until L.pi.nl) for (k in L.pi.w[l].indices) L.pi.w[l][k] = rng.gauss() * 0.5f
        val o = FloatArray(OBS) { rng.range(0f, 1f) }
        val m = BooleanArray(NA) { true }
        val p = FloatArray(NA)
        L.probsFor(o, 0, m, 0, p)
        val a = (0 until NA).maxByOrNull { p[it] }!!
        val p0 = FloatArray(NA)
        L.probsFor(FloatArray(OBS), 0, m, 0, p0)
        val attr = lineage.core.rl.BrainProbe.attribution(L, o, m, a, steps = 200)
        val expected = kotlin.math.ln(p[a]) - kotlin.math.ln(p0[a])
        assertTrue(abs(attr.sum() - expected) < 0.02f + 0.02f * abs(expected), "sum ${attr.sum()} vs $expected")
        // probing must not leave gradients behind
        assertTrue(L.pi.gw.all { g -> g.all { it == 0f } })
    }

    /** The species-wide brain charts collect samples while the world runs. */
    @Test
    fun brainStatsCollectSamples() {
        val w = World(SimConfig().with(mapOf("seed" to "5", "width" to "64", "height" to "64", "shelters" to "1,2,3",
            "brainSize" to "16", "explainEvery" to "5", "examEvery" to "0")))
        repeat(1200) { w.step() }
        assertTrue(w.brainStats.samples.sumOf { it.sum().toDouble() } > 10.0)
        val dir = kotlin.io.path.createTempDirectory("brain").toFile()
        BrainReport.write(w.brainStats, dir)
        assertTrue(java.io.File(dir, "brain_report.csv").readLines().size > 10)
        assertTrue(java.io.File(dir, "brain_neurons.csv").readLines().size > 10)
        dir.deleteRecursively()
    }
}
