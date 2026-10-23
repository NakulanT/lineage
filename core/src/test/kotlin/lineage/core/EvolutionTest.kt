package lineage.core

import lineage.core.rl.BrainGenes
import lineage.core.rl.M_DEATH
import lineage.core.rl.Mlp
import lineage.core.rl.NM
import lineage.core.rl.OBS
import lineage.core.rl.RlBrain
import lineage.core.rl.RlParams
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvolutionTest {
    private fun small(extra: Map<String, String> = emptyMap()) = SimConfig().with(mapOf(
        "seed" to "11", "width" to "96", "height" to "96", "startCounts" to "6,40,120", "hardCap" to "500",
        "plants" to "500", "shelters" to "1,3,6", "brainSize" to "16", "examEvery" to "0",
    ) + extra)

    private fun firstOf(w: World, s: Int, fem: Boolean) =
        (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == s && w.isMature(it) && w.female[it] == fem }

    /** Records events so a test can look at them. */
    private class Events : HistoryLog {
        val list = ArrayList<Pair<String, String>>()
        override fun runInfo(text: String) {}
        override fun population(row: PopRow) {}
        override fun event(tick: Long, type: String, species: Int, a: Int, b: Int, x: Float, y: Float, note: String) { list.add(type to note) }
        override fun life(rec: LifeRec) {}
        override fun flush() {}
        override fun close() {}
    }

    /** Plants whose claim belongs to nobody going for them or eating them: nobody else can ever eat them again. */
    private fun staleClaims(w: World): Int = (0 until w.cfg.plants).count { p ->
        val c = w.plantClaim[p]
        c >= 0 && !(w.alive[c] && (w.st[c].toInt() == S_TOPLANT || w.st[c].toInt() == S_EAT) && w.tgt[c] == p)
    }

    /** A1: a creature that stops eating halfway (the brain chose something else) lets go of the plant. */
    @Test
    fun plantsAreNeverLockedForever() {
        val w = World(small())
        repeat(8000) { w.step() }
        assertEquals(0, staleClaims(w), "plants claimed by nobody eating them")
    }

    /** Fix 16: when a baby grows up, both parents get their share of the reward. */
    @Test
    fun parentsAreRewardedWhenBabiesGrowUp() {
        val w = World(small())
        val f = firstOf(w, BETA, true); val m = firstOf(w, BETA, false)
        val baby = w.spawnBaby(f, m)
        w.grewUp(baby)
        val d = w.defs[BETA]
        val share = w.cfg.grownUpReward / ((d.litterMin + d.litterMax) / 2f)
        assertEquals(share, w.rwKids[f], 1e-5f)
        assertEquals(share, w.rwKids[m], 1e-5f)
        // off: nothing
        val off = World(small(mapOf("grownUpReward" to "0")))
        val f2 = firstOf(off, BETA, true); val m2 = firstOf(off, BETA, false)
        off.grewUp(off.spawnBaby(f2, m2))
        assertEquals(0f, off.rwKids[f2])
    }

    /** Fix 17: each baby costs its share of an average litter, and a mother never goes below her reserve. */
    @Test
    fun litterCostGrowsWithLitterSizeAndKeepsAReserve() {
        val w = World(small(mapOf("brain" to "false")))
        val d = w.defs[ALPHA]
        val f = firstOf(w, ALPHA, true); val m = firstOf(w, ALPHA, false)
        for (k in 0 until NG) { w.genes[f * NG + k] = 1f; w.genes[m * NG + k] = 1f }
        w.genes[m * NG + G_STR] = 1.6f
        w.hp[m] = w.maxHp(m)
        w.en[f] = d.maxEnergy * 0.8f
        val before = w.count[ALPHA]
        w.tryMate(m, f)
        val babies = w.count[ALPHA] - before
        assertTrue(babies > 0, "no litter")
        val perBaby = d.maxEnergy * w.cfg.litterCost / ((d.litterMin + d.litterMax) / 2f)
        assertEquals(d.maxEnergy * 0.8f - perBaby * babies, w.en[f], 0.01f)
        assertTrue(w.en[f] >= d.maxEnergy * w.cfg.litterReserve - 0.01f, "mother went below her reserve: ${w.en[f] / d.maxEnergy}")
    }

    /** Fix 18: a wiped-out species gets immigrants at the map edge, only after the wait, and its brain is never reset. */
    @Test
    fun immigrantsArriveAtTheEdgeAfterTheWait() {
        val ev = Events()
        val w = World(small(mapOf("immigrationEvery" to "500", "brainResetRescues" to "1")), ev)
        repeat(10) { w.step() }
        for (i in 0 until w.cap) if (w.alive[i] && w.sp[i].toInt() == ALPHA) { w.hp[i] = -1f; w.en[i] = 0f }
        var firstBack = -1L
        repeat(1200) {
            for (i in 0 until w.cap) if (w.alive[i] && w.sp[i].toInt() == ALPHA && w.age[i] > w.defs[ALPHA].maturityTicks + 5) { w.hp[i] = -1f; w.en[i] = 0f }
            w.step()
            if (firstBack < 0 && ev.list.any { it.first == "rescue" }) {
                firstBack = w.tick
                val edge = (0 until w.cap).filter { w.alive[it] && w.sp[it].toInt() == ALPHA }
                assertTrue(edge.isNotEmpty())
                for (i in edge) assertTrue(w.x[i] < 4f || w.y[i] < 4f || w.x[i] > w.width - 4f || w.y[i] > w.height - 4f, "not at the edge: ${w.x[i]},${w.y[i]}")
            }
        }
        assertTrue(firstBack > 0, "immigrants never came")
        val rescues = ev.list.count { it.first == "rescue" }
        assertTrue(rescues <= 1200 / 500 + 1, "too many immigrant groups: $rescues")
        assertEquals(0, ev.list.count { it.first == "brain_reset" })
    }

    /** E1: a brain that grows behaves exactly as before; one that shrinks or changes layers stays close. */
    @Test
    fun brainsGrowAndShrinkKeepingWhatTheyLearned() {
        val rng = Rng(5)
        val a = Mlp(intArrayOf(OBS, 16, 16, 10), rng, 1f)
        val x = FloatArray(OBS) { rng.nextFloat() }
        val before = a.forward(x).copyOf()
        val grown = a.resized(intArrayOf(OBS, 24, 24, 10), rng, 1f)
        val out = grown.forward(x)
        for (k in 0 until 10) assertEquals(before[k], out[k], 1e-5f)
        // a pass-through layer added: nearly the same
        val deeper = a.resized(intArrayOf(OBS, 16, 16, 16, 10), rng, 1f)
        val o2 = deeper.forward(x)
        assertTrue((0 until 10).maxOf { abs(o2[it] - before[it]) } < 1.0f)
        // shrinking keeps the shape right and works
        val small = a.resized(intArrayOf(OBS, 8, 10), rng, 1f)
        assertEquals(listOf(OBS, 8, 10), small.sizes.toList())
        assertTrue(small.forward(x).all { it.isFinite() })
    }

    /** E3/E4: genes stay inside their limits however often they change. */
    @Test
    fun genesStayInsideTheirLimits() {
        val rng = Rng(3)
        var g = BrainGenes.of(RlParams(hidden = 16, layers = 2))
        repeat(500) {
            g = g.mutate(rng, 0.5f, 0.5f, 8, 64)
            assertTrue(g.hidden in 8..64 && g.layers in 1..4)
            assertTrue(g.lr in 1e-5f..3e-3f && g.entropy in 1e-3f..0.3f && g.gamma in 0.89f..0.9996f)
            for (k in 0 until NM) assertTrue(g.motive[k] in 0.1f..10f)
        }
    }

    /** Fix 19: weak families are replaced by changed copies, sizes can differ, and it all survives a save and the exam copy. */
    @Test
    fun familiesEvolveAndDifferentSizesSaveAndLoad() {
        val ev = Events()
        val w = World(small(mapOf("familyCheckEvery" to "300", "brainGrowChance" to "1.0", "familyReplaceShare" to "0.9",
            "familyCopyMinMembers" to "2")), ev)
        repeat(6000) { w.step() }
        val b = w.brain!!
        assertTrue(ev.list.any { it.first == "brain_copy" }, "no family was ever copied")
        val sizes = b.learners.map { it.genes.hidden to it.genes.layers }
        assertTrue(sizes.any { it != (16 to 2) }, "no brain changed size: $sizes")
        // the creatures still decide with brains of other sizes (brainUpkeep and rewards use the family genes)
        assertTrue(w.aliveCount > 0)
        // save and load keeps every family's size and weights
        val f = File.createTempFile("brains", ".bin")
        b.save(f)
        val c = RlBrain(w.cap, w.cfg.rlParams(), 99L, w.cfg.brainFamilies, w.cfg.learnBatch)
        assertTrue(c.load(f))
        for (k in b.learners.indices) {
            assertEquals(b.learners[k].genes.hidden, c.learners[k].genes.hidden)
            assertEquals(b.learners[k].genes.motive[M_DEATH], c.learners[k].genes.motive[M_DEATH])
            assertTrue(b.learners[k].pi.sizes.contentEquals(c.learners[k].pi.sizes))
            assertEquals(b.learners[k].pi.w[0][3], c.learners[k].pi.w[0][3])
        }
        f.delete()
        // the exam copies brains of any size
        val exam = RlBrain(w.cap, w.cfg.rlParams(), 7L, w.cfg.brainFamilies, w.cfg.learnBatch)
        exam.copyWeightsFrom(b)
        for (k in b.learners.indices) assertTrue(b.learners[k].vf.sizes.contentEquals(exam.learners[k].vf.sizes))
        // the genes log has a row per family per check
        assertTrue(w.geneCurves[BETA][GC_HIDDEN].n > 0)
    }

    /** An older version's settings (no fix 16-19 keys) keep running exactly as before. */
    @Test
    fun oldVersionsKeepTheirBehaviour() {
        val f = File.createTempFile("settings", ".txt")
        f.writeText(SimConfig().toProps().filterKeys { k ->
            k !in setOf("grownUpReward", "litterCostBySize", "litterReserve", "immigration", "immigrationEvery", "evolveBrains",
                "familyReplaceShare", "brainEnergyCost", "brainGeneMutation", "brainGrowChance", "brainMinSize", "brainMaxSize")
        }.entries.joinToString("\n") { "${it.key}=${it.value}" })
        val c = SimConfig.read(f)!!
        f.delete()
        assertEquals(0f, c.grownUpReward)
        assertFalse(c.litterCostBySize)
        assertFalse(c.immigration)
        assertFalse(c.evolveBrains)
        assertEquals(0f, c.brainEnergyCost)
        // and a new version has them on
        val d = SimConfig()
        assertTrue(d.evolveBrains && d.immigration && d.litterCostBySize && d.grownUpReward > 0f)
    }
}
