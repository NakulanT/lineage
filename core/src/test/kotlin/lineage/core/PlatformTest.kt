package lineage.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTest {
    /** Every setting on the new-version screen can be changed, saved, read back, and kept inside its range. */
    @Test
    fun everySettingCanBeChangedAndSaved() {
        var c = SimConfig()
        for (p in Params.all) {
            val n = if (p.perSpecies) NSP else 1
            for (k in 0 until n) {
                val before = Params.values(c, p)[k]
                val target = if (p.kind == P_BOOL) (if (before > 0.5f) 0f else 1f) else (before + p.step).coerceAtMost(p.max)
                c = Params.set(c, p, k, target)
                assertEquals(target, Params.values(c, p)[k], 1e-5f, "${p.key}[$k]")
            }
            // out of range values are kept inside the range
            assertTrue(Params.values(Params.set(c, p, 0, p.max * 10 + 10), p)[0] <= p.max + 1e-4f, p.key)
        }
        val f = File.createTempFile("settings", ".txt")
        c.write(f)
        assertEquals(c.toProps(), SimConfig.read(f)!!.toProps())
        f.delete()
        assertTrue(Params.changes(c).size >= Params.all.size - 2, "changes: ${Params.changes(c).size}")
    }

    /** Species settings reach the world. */
    @Test
    fun speciesSettingsChangeTheWorld() {
        val litter = Params.all.first { it.key == "sp.litterMax" }
        val speed = Params.all.first { it.key == "sp.speed" }
        var c = SimConfig().with(mapOf("seed" to "3", "width" to "64", "height" to "64", "shelters" to "1,2,3"))
        c = Params.set(c, speed, GAMMA, 5f)
        c = Params.set(c, litter, GAMMA, 33f)
        val w = World(c)
        assertEquals(5f, w.defs[GAMMA].speed)
        assertEquals(33, w.defs[GAMMA].litterMax)
        assertEquals(Species.all[ALPHA].speed, w.defs[ALPHA].speed)
        repeat(300) { w.step() }
    }

    /** Reward settings change what the brain is paid. */
    @Test
    fun rewardSettingsAreUsed() {
        val base = SimConfig().with(mapOf("seed" to "4", "width" to "64", "height" to "64", "shelters" to "1,2,3", "examEvery" to "0"))
        val a = World(base); val b = World(base.with(mapOf("hungerPenalty" to "0.5", "shelterHungerPenalty" to "0.5", "deathPenalty" to "20")))
        repeat(1500) { a.step(); b.step() }
        val ra = a.rewardSum.sum() / a.decisionCount.sum(); val rb = b.rewardSum.sum() / b.decisionCount.sum()
        assertTrue(rb < ra, "bigger penalties should lower the reward ($rb vs $ra)")
    }

    /** A new version can start from another version's trained brains. */
    @Test
    fun aVersionCanStartFromAnotherVersionsBrains() {
        val root = kotlin.io.path.createTempDirectory("lineage").toFile()
        val store = RunStore(root)
        val cfg = SimConfig().with(mapOf("width" to "64", "height" to "64", "shelters" to "1,2,3", "examEvery" to "0"))
        val a = store.open(store.create(cfg, "a"))
        repeat(3000) { a.world.step() }
        val w = a.world.brain!!.learners[0].pi.w[0][0]
        a.close()
        val b = store.open(store.create(cfg, "b", brainsFrom = "v1"))
        assertEquals(w, b.world.brain!!.learners[0].pi.w[0][0])
        assertEquals(0L, b.world.tick)
        b.close()
        root.deleteRecursively()
    }

    /** One species cannot take all the room: each has its own cap. */
    @Test
    fun eachSpeciesHasItsOwnRoom() {
        val c = SimConfig().with(mapOf("seed" to "6", "width" to "96", "height" to "96", "shelters" to "1,3,6",
            "speciesCap" to "40,60,80", "hardCap" to "900", "startCounts" to "5,20,50", "sp.litterMin" to "5,15,30", "sp.litterMax" to "10,20,50", "examEvery" to "0"))
        val w = World(c)
        repeat(3000) {
            w.step()
            for (s in 0 until NSP) assertTrue(w.count[s] <= c.speciesCap[s] + c.rescueCount, "${Species.all[s].name} ${w.count[s]}")
        }
    }

    /** Topping up a species adds the missing sex (it used to add only females). */
    @Test
    fun topUpsAddTheMissingSex() {
        val w = World(SimConfig().with(mapOf("seed" to "2", "width" to "64", "height" to "64", "shelters" to "1,2,3", "examEvery" to "0")))
        for (i in 0 until w.cap) if (w.alive[i] && w.sp[i].toInt() == ALPHA) w.female[i] = true
        w.rescueSpecies(ALPHA, 1, "topup")
        var males = 0
        for (i in 0 until w.cap) if (w.alive[i] && w.sp[i].toInt() == ALPHA && !w.female[i]) males++
        assertEquals(1, males)
    }

    /** Plants start outside shelters, meat is never dropped inside one, and food under a moving shelter is never eaten. */
    @Test
    fun noFoodInsideShelters() {
        val w = World(SimConfig().with(mapOf("seed" to "8", "width" to "96", "height" to "96", "shelters" to "2,8,25", "plants" to "900", "examEvery" to "0")))
        fun inside(x: Float, y: Float) = (0 until w.nSh).any { k -> val dx = x - w.shX[k]; val dy = y - w.shY[k]; dx * dx + dy * dy < w.shR[k] * w.shR[k] }
        for (p in 0 until w.cfg.plants) assertTrue(!inside(w.plantX[p], w.plantY[p]), "plant $p is inside a shelter")
        val g = FloatArray(w.cfg.plants)
        val had = BooleanArray(w.meatAmt.size)
        repeat(3000) {
            System.arraycopy(w.plantG, 0, g, 0, g.size)
            for (m in had.indices) had[m] = w.meatAmt[m] > 0f
            w.step()
            for (p in g.indices) if (w.plantG[p] < g[p]) assertTrue(!inside(w.plantX[p], w.plantY[p]), "plant $p eaten under a shelter")
            for (m in had.indices) if (!had[m] && w.meatAmt[m] > 0f) assertTrue(!inside(w.meatX[m], w.meatY[m]), "meat dropped inside a shelter")
        }
    }

    /** Shelters drift but never overlap each other. */
    @Test
    fun sheltersMoveButNeverOverlap() {
        val w = World(SimConfig().with(mapOf("seed" to "9", "width" to "96", "height" to "96", "shelters" to "2,8,25", "examEvery" to "0", "shelterSpeed" to "2")))
        val x0 = w.shX.copyOf()
        repeat(2000) {
            w.step()
            for (a in 0 until w.nSh) for (b in a + 1 until w.nSh) {
                val dx = w.shX[a] - w.shX[b]; val dy = w.shY[a] - w.shY[b]
                assertTrue(dx * dx + dy * dy >= (w.shR[a] + w.shR[b]) * (w.shR[a] + w.shR[b]), "shelters $a and $b overlap")
            }
        }
        assertTrue((0 until w.nSh).count { kotlin.math.abs(w.shX[it] - x0[it]) > 1f } > w.nSh / 2, "shelters did not move")
    }

    private fun quiet() = SimConfig().with(mapOf("seed" to "11", "width" to "64", "height" to "64", "shelters" to "1,2,3", "examEvery" to "0", "brain" to "false", "brainSize" to "16"))

    /** Meat comes only from an accident, as much as the energy left; starving and old age leave nothing. */
    @Test
    fun meatOnlyFromAccidentsAndEqualToEnergyLeft() {
        fun meatTotal(w: World) = w.meatAmt.sum()
        // learning brains: the instinct-only mode steps strangers back out of shelters
        val w = World(quiet().with(mapOf("brain" to "true", "shelterSpeed" to "0")))
        val starve = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == BETA }
        val before = meatTotal(w)
        w.en[starve] = 0f; w.hp[starve] = 0.01f
        w.step()
        assertTrue(!w.alive[starve])
        assertEquals(before, meatTotal(w), 0.01f, "starving left meat")
        val hurt = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == GAMMA }
        val k = (0 until w.nSh).first { w.shSp[it] == BETA }
        w.en[hurt] = 40f; w.hp[hurt] = 0.01f
        w.x[hurt] = w.shX[k]; w.y[hurt] = w.shY[k]
        val b2 = meatTotal(w)
        w.step()
        assertTrue(!w.alive[hurt])
        val added = meatTotal(w) - b2
        assertTrue(added > 30f && added <= 40.5f, "meat from an injury should be about its energy, got $added")
    }

    /** A creature that ate to full does not eat again until its energy falls below the satiety level. */
    @Test
    fun fullCreaturesDoNotEat() {
        val w = World(quiet())
        val i = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == GAMMA && !w.isInfant(it) }
        val max = w.defs[GAMMA].maxEnergy
        w.en[i] = max
        w.step()
        assertTrue(w.full[i])
        w.en[i] = max * 0.8f
        w.step()
        assertTrue(w.full[i], "80% is above the 70% satiety level")
        val meals = w.meals[i]
        repeat(50) { w.en[i] = max * 0.8f; w.step() }
        assertEquals(meals, w.meals[i], "ate while full")
        w.en[i] = max * 0.5f
        w.step()
        assertTrue(!w.full[i])
    }

    /** Nobody mates inside a shelter. */
    @Test
    fun noMatingInsideShelters() {
        val w = World(quiet().with(mapOf("shelterSpeed" to "0")))
        val k = (0 until w.nSh).first { w.shSp[it] == BETA }
        val f = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == BETA && w.female[it] && w.isMature(it) }
        val m = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == BETA && !w.female[it] && w.isMature(it) }
        for (j in listOf(f, m)) { w.x[j] = w.shX[k]; w.y[j] = w.shY[k] + (if (j == f) 0.3f else -0.3f); w.en[j] = w.defs[BETA].maxEnergy; w.mateCd[j] = 0 }
        val births = w.totalBirths
        val tryMate = World::class.java.getDeclaredMethod("tryMate", Int::class.java, Int::class.java).also { it.isAccessible = true }
        repeat(20) { tryMate.invoke(w, m, f) }
        assertEquals(births, w.totalBirths, "babies were made inside a shelter")
    }

    /** "Forage" walks toward the food the creature senses farther away than its smell. */
    @Test
    fun foragingWalksTowardFood() {
        val w = World(quiet().with(mapOf("brain" to "true", "shelterSpeed" to "0", "forageRange" to "40")))
        val goal = World::class.java.getDeclaredMethod("forageGoal", Int::class.java).also { it.isAccessible = true }
        val gx = World::class.java.getDeclaredField("goalX").also { it.isAccessible = true }
        val gy = World::class.java.getDeclaredField("goalY").also { it.isAccessible = true }
        repeat(700) { w.step() }   // plants start part-grown; let some ripen
        val i = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == BETA && !w.isInfant(it) && goal.invoke(w, it) as Boolean }
        val tx = (gx.get(w) as FloatArray)[i]; val ty = (gy.get(w) as FloatArray)[i]
        val d0 = kotlin.math.hypot(w.x[i] - tx, w.y[i] - ty)
        w.st[i] = S_FORAGE.toByte()
        val act = World::class.java.getDeclaredMethod("act", Int::class.java).also { it.isAccessible = true }
        repeat(30) { act.invoke(w, i) }
        assertTrue(d0 < 2f || kotlin.math.hypot(w.x[i] - tx, w.y[i] - ty) < d0 - 1f, "did not get closer to the food")
    }

    /** With the default bite strengths, a group of 6 adult Gammas brings down a healthy adult Alpha. */
    @Test
    fun aGroupOfGammasCanKillAnAlpha() {
        var wins = 0
        for (seed in 1..10) {
            val w = World(quiet().with(mapOf("seed" to "$seed")))
            val attack = World::class.java.getDeclaredMethod("attack", Int::class.java, Int::class.java).also { it.isAccessible = true }
            val a = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == ALPHA }
            val gs = (0 until w.cap).filter { w.alive[it] && w.sp[it].toInt() == GAMMA }.take(6)
            for (j in gs + a) { w.age[j] = w.def(j).maturityTicks; for (k in 0 until NG) w.genes[j * NG + k] = 1f; w.hp[j] = w.maxHp(j) }
            var t = 0
            while (w.alive[a] && gs.any { w.alive[it] } && t < 2000) {
                if (t % w.defs[ALPHA].attackCooldown == 0) gs.firstOrNull { w.alive[it] }?.let { attack.invoke(w, a, it) }
                if (t % w.defs[GAMMA].attackCooldown == 0) for (g in gs) if (w.alive[g] && w.alive[a]) attack.invoke(w, g, a)
                t++
            }
            if (!w.alive[a]) wins++
        }
        assertTrue(wins >= 8, "6 Gammas beat an Alpha only $wins times out of 10")
    }

    /** A version can be given a name; its folder (v1) stays the same. */
    @Test
    fun versionsCanBeRenamed() {
        val root = kotlin.io.path.createTempDirectory("lineage").toFile()
        val store = RunStore(root)
        val name = store.create(SimConfig().with(mapOf("width" to "64", "height" to "64", "shelters" to "1,2,3")), "x")
        store.rename(name, "  more plants  ")
        assertEquals("more plants", store.labelOf(name))
        assertEquals("v1 \u00b7 more plants", store.list().first().title)
        store.rename(name, "")
        assertEquals("v1", store.list().first().title)
        root.deleteRecursively()
    }

    /** Alpha can flee too: a Gamma hunting it counts as a threat, and "flee" becomes possible. */
    @Test
    fun alphaCanFleeFromAnimalsHuntingIt() {
        val w = World(quiet().with(mapOf("brain" to "true", "shelterSpeed" to "0")))
        val a = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == ALPHA }
        val g = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == GAMMA }
        w.step()
        w.x[g] = w.x[a] + 1f; w.y[g] = w.y[a]
        w.st[g] = S_HUNT.toByte(); w.tgt[g] = a; w.tgtUid[g] = w.uid[a]
        w.step()   // re-file everyone in the grid
        w.x[g] = w.x[a] + 1f; w.y[g] = w.y[a]; w.st[g] = S_HUNT.toByte(); w.tgt[g] = a; w.tgtUid[g] = w.uid[a]
        val observe = World::class.java.getDeclaredMethod("observe", Int::class.java).also { it.isAccessible = true }
        val mask = World::class.java.getDeclaredField("amask").also { it.isAccessible = true }
        observe.invoke(w, a)
        assertTrue((mask.get(w) as BooleanArray)[lineage.core.rl.A_FLEE], "Alpha could not flee from a Gamma hunting it")
    }
}
