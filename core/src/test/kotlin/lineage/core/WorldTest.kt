package lineage.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldTest {
    private fun small(seed: Long = 7L) = SimConfig(
        seed = seed, width = 96, height = 96, startCounts = intArrayOf(10, 50, 200),
        hardCap = 600, plants = 700, shelters = intArrayOf(1, 3, 6),
    )

    @Test
    fun sameSeedGivesSameWorld() {
        val a = World(small()); val b = World(small())
        repeat(1500) { a.step(); b.step() }
        assertEquals(a.aliveCount, b.aliveCount)
        assertEquals(a.totalBirths, b.totalBirths)
        assertEquals(a.totalKills, b.totalKills)
        for (i in 0 until a.cap) {
            assertEquals(a.alive[i], b.alive[i])
            if (a.alive[i]) { assertEquals(a.x[i], b.x[i]); assertEquals(a.y[i], b.y[i]) }
        }
    }

    @Test
    fun populationNeverPassesTheCap() {
        val w = World(SimConfig(seed = 3, width = 64, height = 64, startCounts = intArrayOf(5, 30, 120), hardCap = 200, plants = 900, shelters = intArrayOf(1, 2, 3)))
        repeat(3000) { w.step(); assertTrue(w.aliveCount <= 200, "alive ${w.aliveCount} at ${w.tick}") }
    }

    @Test
    fun instinctsKeepStrangersOutOfShelters() {
        val cfg = small()
        val w = World(SimConfig(seed = cfg.seed, width = 96, height = 96, startCounts = intArrayOf(10, 50, 200),
            hardCap = 600, plants = 700, shelters = intArrayOf(1, 3, 6), brain = false))
        repeat(1200) {
            w.step()
            for (n in 0 until w.nOrder) {
                val i = w.order[n]
                if (!w.alive[i]) continue
                val k = w.foreignShelterAt(w.sp[i].toInt(), w.x[i], w.y[i])
                if (k >= 0) {
                    val dx = w.x[i] - w.shX[k]; val dy = w.y[i] - w.shY[k]
                    // babies may be born right at the edge; nobody may be deep inside
                    assertTrue(dx * dx + dy * dy > (w.shR[k] - 0.5f) * (w.shR[k] - 0.5f), "creature ${w.uid[i]} deep inside a foreign shelter")
                }
            }
        }
    }

    @Test
    fun strangersInsideAShelterLoseHealthFast() {
        val w = World(small())
        val k = (0 until w.nSh).first { w.shSp[it] == BETA }
        val i = (0 until w.cap).first { w.alive[it] && w.sp[it].toInt() == GAMMA }
        w.x[i] = w.shX[k]; w.y[i] = w.shY[k]
        val before = w.hp[i]
        val maxBefore = w.maxHp(i)
        w.step()
        if (w.alive[i]) assertTrue(before - w.hp[i] >= maxBefore * 0.02f, "health only fell from $before to ${w.hp[i]}")
    }

    @Test
    fun plantsOnlyGetEatenWhenRipe() {
        val w = World(small())
        var before = FloatArray(w.cfg.plants)
        repeat(600) {
            System.arraycopy(w.plantG, 0, before, 0, before.size)
            w.step()
            for (p in 0 until w.cfg.plants) {
                // a plant that dropped must have been fully grown the step before
                if (w.plantG[p] < before[p]) assertTrue(before[p] >= 1f, "plant $p eaten while growing")
            }
        }
    }

    @Test
    fun saveAndLoadKeepsTheWorld() {
        val a = World(small())
        repeat(800) { a.step() }
        val f = File.createTempFile("lineage", ".save")
        try {
            Persist.save(a, f)
            val b = World(small())
            assertTrue(Persist.load(b, f))
            assertEquals(a.aliveCount, b.aliveCount)
            assertEquals(a.tick, b.tick)
            for (i in 0 until a.cap) {
                assertEquals(a.alive[i], b.alive[i])
                if (a.alive[i]) assertEquals(a.hp[i], b.hp[i])
            }
        } finally { f.delete() }
    }

    @Test
    fun historyFilesAreWritten() {
        val dir = File.createTempFile("lineage", "dir").also { it.delete() }
        val h = CsvHistory(dir)
        val w = World(small(), h)
        repeat(1300) { w.step() }
        h.close()
        val lines = File(dir, "population.csv").readLines()
        assertTrue(lines.size > 3, "population rows missing")
        assertTrue(lines[0].startsWith("tick,day,species"))
        dir.deleteRecursively()
    }
}
