package lineage.core

import lineage.core.rl.MC_IN
import lineage.core.rl.MateChoice
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FixesTest {
    private fun small(extra: Map<String, String> = emptyMap()) = SimConfig().with(mapOf(
        "seed" to "9", "width" to "96", "height" to "96", "startCounts" to "6,40,120", "hardCap" to "500",
        "plants" to "500", "shelters" to "1,3,6",
    ) + extra)

    private fun firstOf(w: World, s: Int) = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == s && w.isMature(it) }

    @Test
    fun settingsSurviveAWriteAndRead() {
        val c = small(mapOf("shelterCapacity" to "2,3,4", "learnedMateChoice" to "false"))
        val f = File.createTempFile("settings", ".txt")
        c.write(f)
        val r = SimConfig.read(f)!!
        assertEquals(c.toProps(), r.toProps())
        f.delete()
    }

    @Test
    fun fixSwitchesTurnOnAndOff() {
        val d = SimConfig()
        for (fx in Fixes.all) {
            if (fx.num == 8) assertFalse(fx.isOn(d), "fix 8 (copy instincts) must be off by default") else assertTrue(fx.isOn(d), "fix ${fx.num} should be on by default")
            assertFalse(fx.isOn(fx.apply(d, false)))
            assertTrue(fx.isOn(fx.apply(fx.apply(d, false), true)))
        }
        val v1 = d.with(SimConfig.VERSION_1)
        assertTrue(Fixes.all.none { it.isOn(v1) }, Fixes.summary(v1))
        assertEquals("fixes 1-7, 9-20", Fixes.summary(d))
    }

    /** Fix 1: a starving creature resting in its own shelter loses health instead of healing. */
    @Test
    fun shelterDoesNotHealAStarvingCreature() {
        val end = FloatArray(2)
        var start = 0f
        for ((n, heals) in listOf(false, true).withIndex()) {
            val w = World(small(mapOf("shelterHealsStarving" to "$heals", "brain" to "false", "shelterMaxStay" to "0")))
            val i = firstOf(w, BETA)
            val k = (0 until w.nSh).first { w.shSp[it] == BETA }
            w.hp[i] = w.maxHp(i) * 0.5f
            start = w.hp[i]
            repeat(50) {
                w.x[i] = w.shX[k]; w.y[i] = w.shY[k]
                w.st[i] = S_SHELTER.toByte(); w.tgt[i] = k; w.timer[i] = 100000; w.en[i] = 0f
                w.step()
            }
            end[n] = if (w.alive[i]) w.hp[i] else 0f
        }
        assertTrue(end[0] < start, "starving in a shelter must hurt (hp ${end[0]} from $start)")
        assertTrue(end[0] < end[1], "the v1 rule heals more (${end[1]}) than the fix (${end[0]})")
    }

    /** Fix 2: after the longest allowed stay it must leave, and may not come back for a while. */
    @Test
    fun shelterStayHasATimeLimit() {
        val w = World(small(mapOf("brain" to "false", "shelterMaxStay" to "40", "shelterCooldown" to "100")))
        val i = firstOf(w, BETA)
        val k = (0 until w.nSh).first { w.shSp[it] == BETA }
        w.x[i] = w.shX[k]; w.y[i] = w.shY[k]
        w.st[i] = S_SHELTER.toByte(); w.tgt[i] = k; w.timer[i] = 100000
        w.en[i] = Species.all[BETA].maxEnergy
        var leftAt = -1
        for (t in 0 until 120) {
            w.step()
            if (!w.alive[i]) return
            if (w.st[i].toInt() != S_SHELTER) { leftAt = t; break }
        }
        assertTrue(leftAt in 35..60, "left at $leftAt")
        assertTrue(w.shelterCd[i] > 0)
    }

    /** Fix 3: no shelter ever holds more creatures resting inside than it has room for. */
    @Test
    fun sheltersHaveLimitedRoom() {
        val w = World(small(mapOf("shelterCapacity" to "2,3,4")))
        repeat(2500) {
            w.step()
            val inside = IntArray(w.nSh)
            for (n in 0 until w.nOrder) {
                val i = w.order[n]
                if (!w.alive[i] || w.st[i].toInt() != S_SHELTER || (w.flags[i].toInt() and F_REST) == 0) continue
                inside[w.tgt[i]]++
            }
            for (k in 0 until w.nSh) assertTrue(inside[k] <= w.cfg.shelterCapacity[w.shSp[k]], "shelter $k holds ${inside[k]}")
        }
    }

    /** Fix 14: when strong males give babies that grow up more often, females learn to prefer strong males. */
    @Test
    fun femalesLearnToPreferMalesWhoseBabiesSurvive() {
        val mc = MateChoice(Rng(5))
        val rng = Rng(6)
        val x = FloatArray(MC_IN)
        repeat(20000) {
            for (k in 0 until MC_IN - 1) x[k] = rng.range(-0.5f, 0.5f)
            x[MC_IN - 1] = 1f
            val yes = mc.decide(GAMMA, x)
            if (!yes) mc.learn(GAMMA, x, 0, false, 0f)
            else {
                val survived = (0.3f + 0.6f * x[1]).coerceIn(0f, 1f)
                mc.learn(GAMMA, x, 0, true, mc.litterReward(GAMMA, survived))
            }
        }
        assertTrue(mc.w[GAMMA][1] > 0.5f, "weight on strength ${mc.w[GAMMA][1]}")
        val strong = FloatArray(MC_IN).also { it[1] = 0.5f; it[MC_IN - 1] = 1f }
        val weak = FloatArray(MC_IN).also { it[1] = -0.5f; it[MC_IN - 1] = 1f }
        assertTrue(mc.prob(GAMMA, strong) > mc.prob(GAMMA, weak))
    }

    @Test
    fun learningCurveVerdicts() {
        val up = LearnCurve(); val flat = LearnCurve(); val down = LearnCurve()
        val rng = Rng(1)
        for (t in 0 until 300) {
            up.add(t.toLong(), -0.03f + t * 0.0001f + rng.gauss() * 0.002f, 1f)
            flat.add(t.toLong(), -0.02f + rng.gauss() * 0.004f, 1f)
            down.add(t.toLong(), -0.01f - t * 0.0001f + rng.gauss() * 0.002f, 1f)
        }
        assertEquals(Trend.UP, Trend.of(up).kind)
        assertEquals(Trend.FLAT, Trend.of(flat).kind)
        assertEquals(Trend.DOWN, Trend.of(down).kind)
        assertEquals(Trend.EARLY, Trend.of(LearnCurve()).kind)
        // a long curve is merged but keeps covering the whole run
        val long = LearnCurve(100)
        for (t in 0 until 1000) long.add(t.toLong(), 1f, 1f)
        assertTrue(long.n <= 100 && long.tick[long.n - 1] == 999f)
    }

    @Test
    fun versionsGetTheirOwnFoldersAndSurviveAReopen() {
        val root = kotlin.io.path.createTempDirectory("lineage").toFile()
        val store = RunStore(root)
        val a = store.open(store.create(small(), "first"))
        assertEquals("v1", a.name)
        repeat(1500) { a.world.step() }
        val fam = a.world.cfg.brainFamilies
        a.close()
        val b = store.open(store.create(small(mapOf("plants" to "300")), ""))
        assertEquals("v2", b.name)
        assertEquals(300, b.world.cfg.plants)
        b.close()
        val list = store.list()
        assertEquals(listOf("v1", "v2"), list.map { it.name })
        assertEquals("v2", store.current())
        val again = store.open("v1")
        assertEquals(1500L, again.world.tick)
        for (i in 0 until again.world.cap) if (again.world.alive[i]) {
            assertTrue(again.world.family[i] < fam[again.world.sp[i].toInt()])
        }
        assertNotNull(again.world.brain)
        again.close()
        root.deleteRecursively()
    }

    /** Fix 15: every creature uses one of its species' brain families, and babies use their mother's. */
    @Test
    fun babiesInheritTheirMothersBrainFamily() {
        val w = World(small())
        repeat(4000) { w.step() }
        var checked = 0
        for (i in 0 until w.cap) {
            if (!w.alive[i]) continue
            assertTrue(w.family[i] < w.cfg.brainFamilies[w.sp[i].toInt()])
            val m = w.motherSlot[i]
            if (m >= 0 && w.alive[m] && w.uid[m] == w.motherUid[i] && w.isInfant(i)) {
                assertEquals(w.family[m], w.family[i]); checked++
            }
        }
        assertTrue(w.brain!!.learners.size == w.cfg.brainFamilies.sum())
    }

    /** rescueGrowth: every time a species is wiped out it comes back with more creatures than the time before. */
    @Test
    fun eachComebackIsBigger() {
        val w = World(small(mapOf("immigration" to "false", "rescueCount" to "6", "rescueGrowth" to "10", "brainResetRescues" to "0")))
        val sizes = ArrayList<Int>()
        repeat(3) {
            for (i in 0 until w.cap) if (w.alive[i] && w.sp[i].toInt() == ALPHA) { w.hp[i] = -1f; w.en[i] = 0f }
            w.step()
            sizes.add(w.count[ALPHA])
        }
        assertEquals(listOf(6, 16, 26), sizes)
        assertEquals(3, w.wipeouts[ALPHA].size)
    }

    /** Fix 9: a species below its minimum is topped up. */
    @Test
    fun smallSpeciesAreToppedUp() {
        val w = World(small(mapOf("minPopulation" to "4,6,10")))
        // remove all but one Alpha
        var left = 1
        for (i in 0 until w.cap) if (w.alive[i] && w.sp[i].toInt() == ALPHA) { if (left-- <= 0) { w.hp[i] = -1f; w.en[i] = 0f } }
        repeat(1300) { w.step() }
        assertTrue(w.topUps > 0 || w.rescues > 0, "no top-up happened")
    }
}
