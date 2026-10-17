package lineage.core

import lineage.core.rl.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// What a creature is doing
const val S_IDLE = 0
const val S_WANDER = 1
const val S_TOPLANT = 2
const val S_EAT = 3
const val S_HUNT = 4
const val S_ATTACK = 5
const val S_TOMEAT = 6
const val S_EATMEAT = 7
const val S_FLEE = 8
const val S_SHELTER = 9
const val S_MATE = 10
const val S_FOLLOW = 11

val STATE_NAMES = arrayOf(
    "resting", "wandering", "heading to food", "eating", "hunting", "attacking",
    "going to meat", "eating meat", "fleeing", "in shelter", "looking for a mate", "following mother",
)

private const val CELL = 8f
private const val TRAIL_LEN = 40
private const val MAX_MEAT = 512
private const val HIST = 720
private const val THINK_EVERY = 3
private const val EAT_TICKS = 20
private const val MEAT_ROT_TICKS = 3000
private const val MALE_COOLDOWN = 60
private const val TURN_RATE = 0.35f
private const val FX_MAX = 256
private const val BABY_ENERGY = 0.3f
private const val DECIDE_EVERY = 6

private const val MODE_THREAT = 0
private const val MODE_PREY = 1
private const val MODE_MATE = 2

/**
 * The whole world. All creatures live in flat arrays (slot number = index), which keeps thousands
 * of them cheap enough for a phone. Nothing here knows about screens.
 */
class World(val cfg: SimConfig, val history: HistoryLog = NullHistory) {
    val width = cfg.width.toFloat()
    val height = cfg.height.toFloat()
    val cap = cfg.hardCap + 64
    val rng = Rng(cfg.seed)
    var tick = 0L
    var nextUid = 1

    // ---- creatures ----
    val alive = BooleanArray(cap)
    val uid = IntArray(cap)
    val sp = ByteArray(cap)
    val female = BooleanArray(cap)
    val x = FloatArray(cap)
    val y = FloatArray(cap)
    val h = FloatArray(cap)
    val px = FloatArray(cap)
    val py = FloatArray(cap)
    val ph = FloatArray(cap)
    val age = IntArray(cap)
    val hp = FloatArray(cap)
    val en = FloatArray(cap)
    val st = ByteArray(cap)
    val tgt = IntArray(cap)
    val tgtUid = IntArray(cap)
    val timer = IntArray(cap)
    val atkCd = IntArray(cap)
    val huntCd = IntArray(cap)
    val mateCd = IntArray(cap)
    val hurtT = IntArray(cap)
    val motherSlot = IntArray(cap)
    val motherUid = IntArray(cap)
    val fatherUid = IntArray(cap)
    val partnerUid = IntArray(cap)
    val gen = IntArray(cap)
    val genes = FloatArray(cap * NG)
    val kills = IntArray(cap)
    val meals = IntArray(cap)
    val offspring = IntArray(cap)
    val matings = IntArray(cap)
    val lastMate = LongArray(cap)
    val birthTick = LongArray(cap)
    val dist = FloatArray(cap)
    val flags = ByteArray(cap)
    val trail = FloatArray(cap * TRAIL_LEN * 2)
    val trailN = IntArray(cap)
    val trailHead = IntArray(cap)
    val trailAcc = FloatArray(cap)

    val order = IntArray(cap)
    var nOrder = 0
    private val freeSlots = IntArray(cap)
    private var freeTop = 0
    val count = IntArray(NSP)
    var aliveCount = 0
    var maxGeneration = 0

    // ---- spatial grid (creatures are re-filed every step) ----
    private val gw = (cfg.width + 7) / 8
    private val gh = (cfg.height + 7) / 8
    private val gHead = IntArray(gw * gh)
    private val gNext = IntArray(cap)

    // ---- plants ----
    val plantX = FloatArray(cfg.plants)
    val plantY = FloatArray(cfg.plants)
    val plantG = FloatArray(cfg.plants)
    val plantClaim = IntArray(cfg.plants)
    private val pHead = IntArray(gw * gh)
    private val pNext = IntArray(cfg.plants)

    // ---- meat ----
    val meatX = FloatArray(MAX_MEAT)
    val meatY = FloatArray(MAX_MEAT)
    val meatAmt = FloatArray(MAX_MEAT)
    val meatAge = IntArray(MAX_MEAT)
    private var meatPtr = 0

    // ---- shelters ----
    var nSh = 0
    /** Shelters are circles: centre (shX, shY) and radius shR. */
    val shX = FloatArray(cfg.shelters.sum())
    val shY = FloatArray(cfg.shelters.sum())
    val shR = FloatArray(cfg.shelters.sum())
    val shSp = IntArray(cfg.shelters.sum())

    // ---- effects the viewer can show ----
    private val fxType = IntArray(FX_MAX)
    private val fxX = FloatArray(FX_MAX)
    private val fxY = FloatArray(FX_MAX)
    private var fxN = 0

    // ---- counters for the log ----
    private val births = IntArray(NSP)
    private val matingCount = IntArray(NSP)
    private val deaths = Array(NSP) { IntArray(NCAUSE) }
    private val extinct = BooleanArray(NSP)
    var rescues = 0
    var totalKills = 0L
    var totalBirths = 0L
    var totalDeaths = 0L

    // ---- history for the in-app graph ----
    private val histCounts = Array(NSP) { IntArray(HIST) }
    private var histN = 0
    private var histHead = 0

    // ---- learning brains ----
    var brain: RlBrain? = if (cfg.brain) RlBrain(cap, RlParams(hidden = cfg.brainSize), cfg.seed) else null
    private val rlBonus = FloatArray(cap)
    private val prevEn = FloatArray(cap)
    private val prevHp = FloatArray(cap)
    private val obs = FloatArray(OBS)
    private val amask = BooleanArray(NA)
    private var sPlant = -1
    private var sMeat = -1
    private var sThreat = -1
    private var sPrey = -1
    private var sMate = -1
    private var sShelter = -1
    /** What each creature last decided (shown in the stats panel). */
    val lastAction = ByteArray(cap)

    private var moveFactor = 0f
    private var senseD2 = 0f
    private var lastMeat = -1

    init {
        for (i in cap - 1 downTo 0) freeSlots[freeTop++] = i
        placeShelters()
        placePlants()
        spawnInitial()
        history.runInfo("version=1 ${cfg.describe()}")
    }

    // =====================================================================
    // setup
    // =====================================================================

    private fun placeShelters() {
        for (s in 0 until NSP) {
            val d = Species.all[s]
            repeat(cfg.shelters[s]) {
                var tries = 0
                while (tries++ < 400) {
                    val r = d.shelterR
                    val sx = rng.range(r + 2f, width - r - 2f)
                    val sy = rng.range(r + 2f, height - r - 2f)
                    var ok = true
                    for (k in 0 until nSh) {
                        val dx = sx - shX[k]; val dy = sy - shY[k]
                        val minD = r + shR[k] + 3f
                        if (dx * dx + dy * dy < minD * minD) { ok = false; break }
                    }
                    if (ok) {
                        shX[nSh] = sx; shY[nSh] = sy; shR[nSh] = r
                        shSp[nSh] = s
                        nSh++
                        break
                    }
                }
            }
        }
    }

    private fun inAnyShelter(px: Float, py: Float, margin: Float): Boolean {
        for (k in 0 until nSh) {
            val dx = px - shX[k]; val dy = py - shY[k]; val r = shR[k] + margin
            if (dx * dx + dy * dy < r * r) return true
        }
        return false
    }

    /** Index of a shelter of another species that contains the point, or -1. */
    fun foreignShelterAt(s: Int, px: Float, py: Float): Int {
        for (k in 0 until nSh) {
            if (shSp[k] == s) continue
            val dx = px - shX[k]; val dy = py - shY[k]
            if (dx * dx + dy * dy < shR[k] * shR[k]) return k
        }
        return -1
    }

    private fun placePlants() {
        java.util.Arrays.fill(pHead, -1)
        for (p in 0 until cfg.plants) {
            var tries = 0
            var xx: Float
            var yy: Float
            do {
                xx = rng.range(1f, width - 1f); yy = rng.range(1f, height - 1f)
            } while (inAnyShelter(xx, yy, 1f) && tries++ < 30)
            plantX[p] = xx; plantY[p] = yy
            plantG[p] = rng.range(0.4f, 1f)
            plantClaim[p] = -1
            val c = cellOf(xx, yy)
            pNext[p] = pHead[c]; pHead[c] = p
        }
    }

    private fun spawnInitial() {
        for (s in 0 until NSP) {
            val d = Species.all[s]
            for (n in 0 until cfg.startCounts[s]) {
                var xx = rng.range(1f, width - 1f)
                var yy = rng.range(1f, height - 1f)
                var tries = 0
                while (foreignShelterAt(s, xx, yy) >= 0 && tries++ < 30) {
                    xx = rng.range(1f, width - 1f); yy = rng.range(1f, height - 1f)
                }
                val i = allocSlot()
                val ageTicks = rng.range(d.maturityTicks * 0.8f, d.lifespanTicks * 0.35f).toInt()
                initCreature(i, s, rng.chance(0.5f), xx, yy, ageTicks)
                for (k in 0 until NG) genes[i * NG + k] = (1f + rng.gauss() * 0.05f).coerceIn(0.6f, 1.5f)
                en[i] = d.maxEnergy * rng.range(0.6f, 1f)
                hp[i] = maxHp(i)
                gen[i] = 1
                motherSlot[i] = -1; motherUid[i] = 0; fatherUid[i] = 0
            }
        }
    }

    private fun allocSlot(): Int = freeSlots[--freeTop]

    private fun initCreature(i: Int, s: Int, fem: Boolean, xx: Float, yy: Float, ageTicks: Int) {
        alive[i] = true
        uid[i] = nextUid++
        sp[i] = s.toByte()
        female[i] = fem
        x[i] = xx; y[i] = yy; px[i] = xx; py[i] = yy
        h[i] = rng.range(0f, (2 * PI).toFloat()); ph[i] = h[i]
        age[i] = ageTicks
        st[i] = S_WANDER.toByte()
        tgt[i] = -1; tgtUid[i] = 0; timer[i] = 0
        atkCd[i] = 0; huntCd[i] = 0; mateCd[i] = 0; hurtT[i] = 0
        partnerUid[i] = 0
        kills[i] = 0; meals[i] = 0; offspring[i] = 0; matings[i] = 0
        lastMate[i] = -1L
        birthTick[i] = tick - ageTicks
        dist[i] = 0f; flags[i] = 0
        trailN[i] = 0; trailHead[i] = 0; trailAcc[i] = 0f
        rlBonus[i] = 0f; prevEn[i] = 0f; prevHp[i] = 0f; lastAction[i] = 0
        count[s]++
        aliveCount++
    }

    // =====================================================================
    // helpers
    // =====================================================================

    fun def(i: Int): SpeciesDef = Species.all[sp[i].toInt()]
    private fun gene(i: Int, k: Int) = genes[i * NG + k]

    fun growth(i: Int): Float {
        val mt = def(i).maturityTicks
        return 0.5f + 0.5f * min(1f, age[i].toFloat() / mt)
    }

    fun maxHp(i: Int) = def(i).health * gene(i, G_HP) * growth(i)
    fun isMature(i: Int) = age[i] >= def(i).maturityTicks
    fun isInfant(i: Int) = age[i] < def(i).infantTicks

    /** Good traits cost energy: a creature with every trait high eats noticeably more. */
    private fun metabolism(i: Int): Float =
        0.5f + 0.15f * gene(i, G_SPEED) + 0.1f * gene(i, G_STR) + 0.12f * gene(i, G_HP) +
            0.08f * gene(i, G_VIS) + 0.05f * gene(i, G_LIFE)

    private fun cellOf(xx: Float, yy: Float): Int {
        val cx = (xx / CELL).toInt().coerceIn(0, gw - 1)
        val cy = (yy / CELL).toInt().coerceIn(0, gh - 1)
        return cy * gw + cx
    }


    private fun wrapAngle(a: Float): Float {
        var v = a
        while (v > PI) v -= (2 * PI).toFloat()
        while (v < -PI) v += (2 * PI).toFloat()
        return v
    }

    private fun fx(type: Int, xx: Float, yy: Float) {
        if (fxN < FX_MAX) { fxType[fxN] = type; fxX[fxN] = xx; fxY[fxN] = yy; fxN++ }
    }

    // =====================================================================
    // one step of the world
    // =====================================================================

    fun step() {
        tick++
        brain?.let {
            it.currentTick = tick
            if (!it.learning && tick >= cfg.pretrainTicks) it.learning = true
        }

        // plants grow back where they stood
        val grow = 1f / cfg.plantRegrowTicks
        for (p in 0 until cfg.plants) {
            val g = plantG[p]
            if (g < 1f) plantG[p] = min(1f, g + grow)
        }
        // meat rots
        for (m in 0 until MAX_MEAT) {
            if (meatAmt[m] > 0f) {
                if (++meatAge[m] > MEAT_ROT_TICKS) meatAmt[m] = 0f
            }
        }

        // file everyone into the grid
        java.util.Arrays.fill(gHead, -1)
        nOrder = 0
        for (i in 0 until cap) {
            if (!alive[i]) continue
            order[nOrder++] = i
            px[i] = x[i]; py[i] = y[i]; ph[i] = h[i]
            val c = cellOf(x[i], y[i])
            gNext[i] = gHead[c]; gHead[c] = i
        }

        for (n in 0 until nOrder) {
            val i = order[n]
            if (alive[i]) stepCreature(i)
        }

        if (cfg.rescueEnabled) rescueCheck()

        if (tick % cfg.sampleEveryTicks == 0L) recordHistorySample()
        if (tick % cfg.logEveryTicks == 0L) writePopulationRows()
    }

    // =====================================================================
    // one creature
    // =====================================================================

    private fun stepCreature(i: Int) {
        val d = def(i)
        flags[i] = 0
        moveFactor = 0f
        age[i]++
        if (atkCd[i] > 0) atkCd[i]--
        if (huntCd[i] > 0) huntCd[i]--
        if (mateCd[i] > 0) mateCd[i]--
        if (hurtT[i] > 0) { hurtT[i]--; flags[i] = (flags[i].toInt() or F_HURT).toByte() }

        if (age[i] > (d.lifespanTicks * gene(i, G_LIFE)).toInt()) { die(i, C_OLD, -1); return }
        if (en[i] <= 0f) {
            hp[i] -= 0.3f
            if (hp[i] <= 0f) { die(i, C_STARVED, -1); return }
        }

        val b = brain
        val phase = (i + tick.toInt())
        if (b != null && b.learning) {
            if (phase % DECIDE_EVERY == 0) brainThink(i, b)
        } else if (phase % THINK_EVERY == 0) {
            if (b != null && phase % DECIDE_EVERY == 0) {
                // the brain watches the instinct and learns to copy it
                observe(i)
                think(i)
                val label = stateToAction(st[i].toInt())
                if (label >= 0 && amask[label]) b.imitate(sp[i].toInt(), obs, amask, label)
                lastAction[i] = label.coerceAtLeast(0).toByte()
            } else think(i)
        }
        act(i)
        if (!alive[i]) return

        // inside another species' shelter: health drops fast
        if (foreignShelterAt(sp[i].toInt(), x[i], y[i]) >= 0) {
            hp[i] -= maxHp(i) * cfg.foreignShelterDamage
            hurtT[i] = 4
            if (hp[i] <= 0f) { die(i, C_INJURY, -1); return }
        }

        // energy upkeep and healing
        val upkeep = d.hunger * metabolism(i) * (0.4f + 0.6f * growth(i)) * (1f + 0.6f * moveFactor)
        en[i] = max(0f, en[i] - upkeep)
        val mh = maxHp(i)
        if (st[i].toInt() == S_SHELTER && (flags[i].toInt() and F_REST) != 0) {
            hp[i] = min(mh, hp[i] + mh * 0.004f)
        } else if (en[i] > d.maxEnergy * 0.5f) {
            hp[i] = min(mh, hp[i] + mh * 0.0003f)
        }
        if (hp[i] > mh) hp[i] = mh
    }

    // ---- deciding what to do ----

    private fun think(i: Int) {
        val d = def(i)
        val state = st[i].toInt()
        val hungryLevel = d.maxEnergy * (if (d.carnivore) 0.85f else 0.6f)

        // resting in a shelter: stay until rested and safe, unless starving
        if (state == S_SHELTER) {
            if (en[i] < d.maxEnergy * 0.25f) { releaseTarget(i); st[i] = S_WANDER.toByte() }
            return
        }

        // babies stay near their mother
        if (isInfant(i)) {
            val m = motherSlot[i]
            if (m >= 0 && alive[m] && uid[m] == motherUid[i]) {
                if (en[i] < d.maxEnergy * 0.3f) {
                    val p = nearestPlant(i)
                    if (p >= 0) { claim(i, p); return }
                }
                setState(i, S_FOLLOW, m)
                return
            }
        }

        // plant eaters run from predators
        if (!d.carnivore) {
            val thr = senseNearest(i, MODE_THREAT)
            if (thr >= 0 && en[i] > d.maxEnergy * 0.2f) {
                val sh = nearestOwnShelter(i, 25f)
                if (sh >= 0) { releaseTarget(i); st[i] = S_SHELTER.toByte(); tgt[i] = sh; timer[i] = 80; return }
                setState(i, S_FLEE, thr)
                return
            }
            if (state == S_FLEE) { releaseTarget(i); st[i] = S_WANDER.toByte() }
        }

        // keep going with a meal or a fight already under way
        if (state == S_EAT || state == S_EATMEAT || state == S_ATTACK) return

        // hurt: go and heal
        if (hp[i] < maxHp(i) * 0.5f) {
            val sh = nearestOwnShelter(i, 40f)
            if (sh >= 0) { releaseTarget(i); st[i] = S_SHELTER.toByte(); tgt[i] = sh; timer[i] = 200; return }
        }

        // hungry
        if (en[i] < hungryLevel) {
            if (d.carnivore) {
                val m = nearestMeat(i)
                if (m >= 0) { setState(i, S_TOMEAT, m); tgtUid[i] = 0; return }
                if (huntCd[i] == 0) {
                    val prey = senseNearest(i, MODE_PREY)
                    if (prey >= 0) { setState(i, S_HUNT, prey); timer[i] = 0; return }
                }
                // nothing to hunt nearby: graze, so a predator does not starve between kills
                val p = nearestPlant(i)
                if (p >= 0) { claim(i, p); return }
            } else {
                if (state == S_TOPLANT) return
                val p = nearestPlant(i)
                if (p >= 0) { claim(i, p); return }
            }
        } else if (state == S_TOPLANT || state == S_HUNT || state == S_TOMEAT) {
            releaseTarget(i); st[i] = S_WANDER.toByte()
        }

        if (state == S_HUNT || state == S_TOPLANT || state == S_TOMEAT || state == S_MATE) return

        // males look for a mate
        if (!female[i] && mateCd[i] == 0 && isMature(i) && en[i] > d.maxEnergy * 0.5f && hp[i] > maxHp(i) * 0.6f) {
            val f = senseNearest(i, MODE_MATE)
            if (f >= 0) { setState(i, S_MATE, f); return }
        }

        if (state != S_IDLE && state != S_WANDER) { releaseTarget(i); st[i] = S_WANDER.toByte() }
        else if (state == S_WANDER && rng.chance(0.02f)) { st[i] = S_IDLE.toByte(); timer[i] = 20 + rng.nextInt(40) }
    }

    private fun setState(i: Int, s: Int, target: Int) {
        if (st[i].toInt() == S_TOPLANT && tgt[i] >= 0 && tgt[i] != target) releasePlant(i)
        st[i] = s.toByte()
        tgt[i] = target
        tgtUid[i] = if (s == S_TOPLANT || s == S_TOMEAT) 0 else uid[target]
    }

    private fun claim(i: Int, p: Int) {
        if (st[i].toInt() == S_TOPLANT && tgt[i] == p) return
        releaseTarget(i)
        plantClaim[p] = i
        st[i] = S_TOPLANT.toByte(); tgt[i] = p
    }

    private fun releasePlant(i: Int) {
        val p = tgt[i]
        if (p in 0 until cfg.plants && plantClaim[p] == i) plantClaim[p] = -1
    }

    private fun releaseTarget(i: Int) {
        if (st[i].toInt() == S_TOPLANT || st[i].toInt() == S_EAT) releasePlant(i)
        tgt[i] = -1
    }

    // ---- senses ----

    private fun senseNearest(i: Int, mode: Int): Int {
        val d = def(i)
        val vr = d.vision * gene(i, G_VIS)
        val hr = if (mode == MODE_MATE) d.call else d.hear
        val maxR = if (mode == MODE_MATE) d.call else max(vr, hr)
        val xi = x[i]; val yi = y[i]
        val ch = cos(h[i]); val sh = sin(h[i])
        val vr2 = vr * vr; val hr2 = hr * hr; val max2 = maxR * maxR
        val x0 = max(0, ((xi - maxR) / CELL).toInt()); val x1 = min(gw - 1, ((xi + maxR) / CELL).toInt())
        val y0 = max(0, ((yi - maxR) / CELL).toInt()); val y1 = min(gh - 1, ((yi + maxR) / CELL).toInt())
        val mySp = sp[i]
        var best = -1
        var bd = Float.MAX_VALUE
        for (cy in y0..y1) for (cx in x0..x1) {
            var j = gHead[cy * gw + cx]
            while (j >= 0) {
                if (j != i && alive[j]) {
                    val ok = when (mode) {
                        MODE_THREAT -> sp[j].toInt() == ALPHA
                        MODE_PREY -> sp[j] != mySp && !Species.all[sp[j].toInt()].carnivore
                        else -> sp[j] == mySp && female[j] != female[i] && isMature(j) && mateCd[j] == 0 && !isInfant(j)
                    }
                    if (ok) {
                        val dx = x[j] - xi; val dy = y[j] - yi
                        val d2 = dx * dx + dy * dy
                        if (d2 < bd && d2 <= max2) {
                            val seen = d2 <= vr2 && dx * ch + dy * sh >= d.cosHalf * sqrt(d2)
                            if (seen || d2 <= hr2) { best = j; bd = d2 }
                        }
                    }
                }
                j = gNext[j]
            }
        }
        senseD2 = bd
        return best
    }

    private fun nearestPlant(i: Int): Int {
        val d = def(i)
        val r = d.smell
        val xi = x[i]; val yi = y[i]
        val r2 = r * r
        val x0 = max(0, ((xi - r) / CELL).toInt()); val x1 = min(gw - 1, ((xi + r) / CELL).toInt())
        val y0 = max(0, ((yi - r) / CELL).toInt()); val y1 = min(gh - 1, ((yi + r) / CELL).toInt())
        var best = -1
        var bd = Float.MAX_VALUE
        for (cy in y0..y1) for (cx in x0..x1) {
            var p = pHead[cy * gw + cx]
            while (p >= 0) {
                if (plantG[p] >= 1f && plantClaim[p] < 0) {
                    val dx = plantX[p] - xi; val dy = plantY[p] - yi
                    val d2 = dx * dx + dy * dy
                    if (d2 < bd && d2 <= r2) { best = p; bd = d2 }
                }
                p = pNext[p]
            }
        }
        return best
    }

    private fun nearestMeat(i: Int): Int {
        val r = def(i).smell * 1.5f
        val r2 = r * r
        var best = -1
        var bd = Float.MAX_VALUE
        for (m in 0 until MAX_MEAT) {
            if (meatAmt[m] <= 0f) continue
            val dx = meatX[m] - x[i]; val dy = meatY[m] - y[i]
            val d2 = dx * dx + dy * dy
            if (d2 < bd && d2 <= r2) { best = m; bd = d2 }
        }
        return best
    }

    private fun nearestOwnShelter(i: Int, maxDist: Float): Int {
        val s = sp[i].toInt()
        var best = -1
        var bd = maxDist * maxDist
        for (k in 0 until nSh) {
            if (shSp[k] != s) continue
            val dx = shX[k] - x[i]; val dy = shY[k] - y[i]
            val d2 = dx * dx + dy * dy
            if (d2 < bd) { best = k; bd = d2 }
        }
        return best
    }

    // ---- moving ----

    private fun moveForward(i: Int, factor: Float, limit: Float) {
        val d = def(i)
        var s = d.speed / TPS * gene(i, G_SPEED) * factor
        if (s > limit) s = limit
        if (s <= 0f) return
        x[i] = (x[i] + cos(h[i]) * s).coerceIn(0.5f, width - 0.5f)
        y[i] = (y[i] + sin(h[i]) * s).coerceIn(0.5f, height - 0.5f)
        if (brain == null) pushOut(i)
        dist[i] += s
        flags[i] = (flags[i].toInt() or F_MOVE).toByte()
        moveFactor = max(moveFactor, factor)
        trailAcc[i] += s
        if (trailAcc[i] >= 1.2f) {
            trailAcc[i] = 0f
            val head = trailHead[i]
            val base = (i * TRAIL_LEN + head) * 2
            trail[base] = x[i]; trail[base + 1] = y[i]
            trailHead[i] = (head + 1) % TRAIL_LEN
            if (trailN[i] < TRAIL_LEN) trailN[i]++
        }
    }

    /** Turn toward a point and walk. Returns the distance that was left before moving. */
    private fun steer(i: Int, tx: Float, ty: Float, factor: Float, stopAt: Float = 0f): Float {
        val dx = tx - x[i]; val dy = ty - y[i]
        val dist = sqrt(dx * dx + dy * dy)
        val diff = wrapAngle(atan2(dy, dx) - h[i])
        h[i] += diff.coerceIn(-TURN_RATE, TURN_RATE)
        if (dist > stopAt) moveForward(i, if (abs(diff) > 1.2f) factor * 0.3f else factor, dist - stopAt)
        return dist
    }

    /** Instinct only (no learning brain): step back out of another species' shelter. */
    private fun pushOut(i: Int) {
        val k = foreignShelterAt(sp[i].toInt(), x[i], y[i])
        if (k < 0) return
        val dx = x[i] - shX[k]; val dy = y[i] - shY[k]
        val d = max(0.001f, sqrt(dx * dx + dy * dy))
        val r = shR[k] + 0.3f
        x[i] = shX[k] + dx / d * r; y[i] = shY[k] + dy / d * r
    }

    // ---- acting ----

    private fun act(i: Int) {
        val d = def(i)
        when (st[i].toInt()) {
            S_IDLE -> {
                if (--timer[i] <= 0) st[i] = S_WANDER.toByte()
            }
            S_WANDER -> wander(i)
            S_TOPLANT -> {
                val p = tgt[i]
                if (p < 0 || plantG[p] < 1f || plantClaim[p] != i) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dd = steer(i, plantX[p], plantY[p], 1f, 0.6f)
                if (dd < 0.9f) { st[i] = S_EAT.toByte(); timer[i] = EAT_TICKS }
            }
            S_EAT -> {
                flags[i] = (flags[i].toInt() or F_EAT).toByte()
                if (--timer[i] <= 0) {
                    val p = tgt[i]
                    if (p >= 0 && plantClaim[p] == i) {
                        en[i] = min(d.maxEnergy, en[i] + cfg.plantEnergy * d.plantEfficiency)
                        plantG[p] = 0f; plantClaim[p] = -1
                        meals[i]++
                    }
                    tgt[i] = -1; st[i] = S_WANDER.toByte()
                }
            }
            S_HUNT -> {
                val j = tgt[i]
                if (j < 0 || !alive[j] || uid[j] != tgtUid[i]) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                timer[i]++
                if (timer[i] > 140) { st[i] = S_WANDER.toByte(); tgt[i] = -1; huntCd[i] = 120; return }
                val dd = steer(i, x[j], y[j], 2.0f)
                if (dd <= d.attackRange) st[i] = S_ATTACK.toByte()
            }
            S_ATTACK -> {
                val j = tgt[i]
                if (j < 0 || !alive[j] || uid[j] != tgtUid[i]) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dx = x[j] - x[i]; val dy = y[j] - y[i]
                val dd = sqrt(dx * dx + dy * dy)
                h[i] += wrapAngle(atan2(dy, dx) - h[i]).coerceIn(-0.6f, 0.6f)
                if (dd > d.attackRange * 1.5f) { st[i] = S_HUNT.toByte(); return }
                if (atkCd[i] == 0) {
                    atkCd[i] = d.attackCooldown
                    attack(i, j)
                }
                flags[i] = (flags[i].toInt() or F_ATTACK).toByte()
            }
            S_TOMEAT -> {
                val m = tgt[i]
                if (m < 0 || meatAmt[m] <= 0f) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dd = steer(i, meatX[m], meatY[m], 1.2f, 0.6f)
                if (dd < 1.2f) st[i] = S_EATMEAT.toByte()
            }
            S_EATMEAT -> {
                val m = tgt[i]
                flags[i] = (flags[i].toInt() or F_EAT).toByte()
                if (m < 0 || meatAmt[m] <= 0f) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val take = min(meatAmt[m], 1.2f)
                meatAmt[m] -= take
                en[i] = min(d.maxEnergy, en[i] + take * d.meatEfficiency)
                if (meatAmt[m] <= 0f || en[i] >= d.maxEnergy * 0.98f) {
                    meals[i]++; tgt[i] = -1; st[i] = S_WANDER.toByte()
                }
            }
            S_FLEE -> {
                val j = tgt[i]
                if (j < 0 || !alive[j] || uid[j] != tgtUid[i]) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dx = x[i] - x[j]; val dy = y[i] - y[j]
                val dd = sqrt(dx * dx + dy * dy)
                if (dd > 12f) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                var tx = x[i] + dx; var ty = y[i] + dy
                // do not run into a wall: slide along it
                if (tx < 3f || tx > width - 3f) tx = x[i]
                if (ty < 3f || ty > height - 3f) ty = y[i]
                if (tx == x[i] && ty == y[i]) { tx = width / 2f; ty = height / 2f }
                steer(i, tx, ty, 1.1f)
            }
            S_SHELTER -> actShelter(i)
            S_MATE -> {
                val f = tgt[i]
                if (f < 0 || !alive[f] || uid[f] != tgtUid[i] || mateCd[f] != 0 || mateCd[i] != 0) {
                    st[i] = S_WANDER.toByte(); tgt[i] = -1; return
                }
                val dd = steer(i, x[f], y[f], 1f, 0.8f)
                if (dd < 1.6f) {
                    tryMate(i, f)
                    mateCd[i] = MALE_COOLDOWN
                    st[i] = S_WANDER.toByte(); tgt[i] = -1
                }
            }
            S_FOLLOW -> {
                val m = tgt[i]
                if (m < 0 || !alive[m] || uid[m] != tgtUid[i]) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dd = steer(i, x[m], y[m], 1f, 1.8f)
                if (dd < 3f && mateCd[i] == 0 && en[i] < def(i).maxEnergy * 0.6f && en[m] > def(m).maxEnergy * 0.55f) {
                    // the mother shares food: what she gives is exactly what the baby gets
                    val amount = 12f
                    en[m] -= amount; en[i] += amount
                    mateCd[i] = 40
                }
            }
        }
    }

    private fun wander(i: Int) {
        if (rng.chance(0.04f)) h[i] += rng.gauss() * 0.7f
        else h[i] += rng.gauss() * 0.05f
        // turn away from the edge of the world
        val m = 4f
        if (x[i] < m || x[i] > width - m || y[i] < m || y[i] > height - m) {
            val diff = wrapAngle(atan2(height / 2f - y[i], width / 2f - x[i]) - h[i])
            h[i] += diff.coerceIn(-0.2f, 0.2f)
        }
        moveForward(i, 0.55f, 99f)
    }

    private fun actShelter(i: Int) {
        val k = tgt[i]
        if (k < 0 || k >= nSh) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
        val frac = ((uid[i] * 2654435761L ushr 8) and 0xFF).toFloat() / 255f
        val frac2 = ((uid[i] * 40503L ushr 4) and 0xFF).toFloat() / 255f
        val ang = frac * 6.2832f; val rr = shR[k] * 0.6f * frac2
        val tx = shX[k] + cos(ang) * rr
        val ty = shY[k] + sin(ang) * rr
        val ddx = x[i] - shX[k]; val ddy = y[i] - shY[k]
        val inside = ddx * ddx + ddy * ddy < shR[k] * shR[k]
        if (!inside) {
            steer(i, tx, ty, 1f, 0.3f)
            return
        }
        flags[i] = (flags[i].toInt() or F_REST).toByte()
        val d = def(i)
        // stay while a predator is close, otherwise rest the set time and leave
        if (--timer[i] <= 0) {
            if (!d.carnivore && senseNearest(i, MODE_THREAT) >= 0 && en[i] > d.maxEnergy * 0.25f) timer[i] = 30
            else if (hp[i] < maxHp(i) * 0.9f && en[i] > d.maxEnergy * 0.3f) timer[i] = 30
            else { st[i] = S_WANDER.toByte(); tgt[i] = -1 }
        }
    }

    // ---- fighting ----

    private fun attack(a: Int, b: Int) {
        val d = def(a)
        val dmg = d.strength * gene(a, G_STR) * rng.range(0.8f, 1.2f)
        hp[b] -= dmg
        hurtT[b] = 4
        fx(FX_SLASH, x[b], y[b])
        if (hp[b] <= 0f) {
            kills[a]++
            totalKills++
            if (cfg.logKills) history.event(tick, "kill", sp[a].toInt(), uid[a], uid[b], x[b], y[b], Species.all[sp[b].toInt()].name)
            lastMeat = -1
            die(b, C_KILLED, a)
            if (lastMeat >= 0) { st[a] = S_TOMEAT.toByte(); tgt[a] = lastMeat; tgtUid[a] = 0 }
            else { st[a] = S_WANDER.toByte(); tgt[a] = -1 }
        }
    }

    // ---- reproduction ----

    private fun tryMate(m: Int, f: Int) {
        val d = def(m)
        if (en[f] < d.maxEnergy * 0.6f || !isMature(f) || isInfant(f)) return
        // the female chooses: health and strength make a male attractive, with a little chance
        val score = 0.5f * (hp[m] / maxHp(m)) + 0.5f * ((gene(m, G_STR) - 0.6f) / 1f).coerceIn(0f, 1f)
        val pickiness = 0.3f + 0.25f * (((uid[f] * 2654435761L) ushr 10) and 0xFF).toFloat() / 255f
        if (score + rng.range(-0.12f, 0.12f) <= pickiness) return

        var n = d.litterMin + rng.nextInt(d.litterMax - d.litterMin + 1)
        val fert = (gene(m, G_FERT) + gene(f, G_FERT)) / 2f
        n = max(1, (n * fert + 0.5f).toInt())
        // a mother can only afford as many babies as her food reserve allows: each baby is paid for
        // from her energy, so breeding is limited by how much there is to eat
        val babyEnergy = d.maxEnergy * BABY_ENERGY
        val affordable = ((en[f] - d.maxEnergy * 0.3f) / (babyEnergy * 1.1f)).toInt()
        n = min(n, affordable)
        if (aliveCount + n > cfg.hardCap) n = cfg.hardCap - aliveCount
        if (n <= 0 || freeTop < n) return

        en[f] = max(0f, en[f] - babyEnergy * 1.1f * n)
        en[m] = max(0f, en[m] - d.maxEnergy * 0.04f)
        mateCd[f] = d.femaleCooldownDays * DAY_TICKS
        lastMate[f] = tick; lastMate[m] = tick
        matings[f]++; matings[m]++
        partnerUid[f] = uid[m]; partnerUid[m] = uid[f]
        matingCount[sp[f].toInt()]++

        for (b in 0 until n) spawnBaby(f, m)
    }

    private fun spawnBaby(f: Int, m: Int) {
        val s = sp[f].toInt()
        val d = Species.all[s]
        val i = allocSlot()
        initCreature(i, s, rng.chance(0.5f), x[f] + rng.range(-0.8f, 0.8f), y[f] + rng.range(-0.8f, 0.8f), 0)
        for (k in 0 until NG) {
            val from = if (rng.chance(0.5f)) gene(f, k) else gene(m, k)
            genes[i * NG + k] = (from + rng.gauss() * cfg.mutation).coerceIn(0.5f, 1.8f)
        }
        if (brain == null) pushOut(i)
        px[i] = x[i]; py[i] = y[i]
        hp[i] = maxHp(i)
        en[i] = d.maxEnergy * BABY_ENERGY
        gen[i] = max(gen[f], gen[m]) + 1
        if (gen[i] > maxGeneration) maxGeneration = gen[i]
        motherSlot[i] = f; motherUid[i] = uid[f]; fatherUid[i] = uid[m]
        st[i] = S_FOLLOW.toByte(); tgt[i] = f; tgtUid[i] = uid[f]
        offspring[f]++; offspring[m]++
        rlBonus[f] += 2f; rlBonus[m] += 1f
        births[s]++
        totalBirths++
        fx(FX_BIRTH, x[i], y[i])
        if (cfg.logBirths) history.event(tick, "birth", s, uid[i], uid[f], x[i], y[i], "father=${uid[m]}")
    }

    // ---- death ----

    private fun die(i: Int, cause: Int, killer: Int) {
        val s = sp[i].toInt()
        val d = Species.all[s]
        if (st[i].toInt() == S_TOPLANT || st[i].toInt() == S_EAT) releasePlant(i)
        if (cause == C_KILLED || cfg.meatFromAllDeaths) {
            val amount = d.bodyEnergy * growth(i) * (if (cause == C_KILLED) 1f else 0.7f)
            if (amount > 5f) lastMeat = addMeat(x[i], y[i], amount)
        }
        brain?.let { b ->
            if (b.learning) b.death(i, uid[i], if (cause == C_OLD) 0f else -3f)
        }
        fx(FX_DEATH, x[i], y[i])
        deaths[s][cause]++
        totalDeaths++
        if (cfg.logDeaths) history.event(tick, "death", s, uid[i], killer, x[i], y[i], CAUSE_NAMES[cause])
        if (cfg.notableMin <= 0 || offspring[i] >= cfg.notableMin || kills[i] >= cfg.notableMin ||
            age[i] >= d.lifespanTicks * 0.9f
        ) {
            history.life(
                LifeRec(
                    uid[i], s, female[i], motherUid[i], fatherUid[i], gen[i], birthTick[i], tick, CAUSE_NAMES[cause],
                    kills[i], meals[i], offspring[i], matings[i], genes.copyOfRange(i * NG, i * NG + NG),
                ),
            )
        }
        alive[i] = false
        count[s]--
        aliveCount--
        freeSlots[freeTop++] = i
        if (count[s] == 0 && !extinct[s]) {
            extinct[s] = true
            history.event(tick, "extinction", s, 0, 0, x[i], y[i], "last one died of ${CAUSE_NAMES[cause]}")
        }
    }

    private fun addMeat(xx: Float, yy: Float, amount: Float): Int {
        var tries = 0
        while (meatAmt[meatPtr] > 0f && tries++ < MAX_MEAT) meatPtr = (meatPtr + 1) % MAX_MEAT
        val m = meatPtr
        meatPtr = (meatPtr + 1) % MAX_MEAT
        meatX[m] = xx; meatY[m] = yy; meatAmt[m] = amount; meatAge[m] = 0
        return m
    }

    // ---- rescue (off by default) ----

    private fun rescueCheck() {
        for (s in 0 until NSP) {
            if (count[s] == 0 && cfg.startCounts[s] > 0) {
                val d = Species.all[s]
                repeat(cfg.rescueCount) { n ->
                    if (freeTop > 0 && aliveCount < cfg.hardCap) {
                        val i = allocSlot()
                        initCreature(i, s, n % 2 == 0, rng.range(2f, width - 2f), rng.range(2f, height - 2f), d.maturityTicks)
                        for (k in 0 until NG) genes[i * NG + k] = 1f
                        en[i] = d.maxEnergy; hp[i] = maxHp(i); gen[i] = 1
                        motherSlot[i] = -1; motherUid[i] = 0; fatherUid[i] = 0
                    }
                }
                rescues++
                extinct[s] = false
                history.event(tick, "rescue", s, 0, 0, 0f, 0f, "added ${cfg.rescueCount}")
            }
        }
    }

    // =====================================================================
    // numbers for the log and the graph
    // =====================================================================

    private fun recordHistorySample() {
        for (s in 0 until NSP) histCounts[s][histHead] = count[s]
        histHead = (histHead + 1) % HIST
        if (histN < HIST) histN++
    }

    private fun writePopulationRows() {
        val males = IntArray(NSP); val females = IntArray(NSP); val babies = IntArray(NSP)
        val ageSum = FloatArray(NSP); val inShelter = IntArray(NSP)
        val geneSum = Array(NSP) { FloatArray(NG) }
        for (n in 0 until nOrder) {
            val i = order[n]
            if (!alive[i]) continue
            val s = sp[i].toInt()
            if (female[i]) females[s]++ else males[s]++
            if (isInfant(i)) babies[s]++
            ageSum[s] += age[i].toFloat() / DAY_TICKS
            if ((flags[i].toInt() and F_REST) != 0) inShelter[s]++
            for (k in 0 until NG) geneSum[s][k] += gene(i, k)
        }
        var ripe = 0
        for (p in 0 until cfg.plants) if (plantG[p] >= 1f) ripe++
        var meat = 0
        for (m in 0 until MAX_MEAT) if (meatAmt[m] > 0f) meat++
        for (s in 0 until NSP) {
            val c = max(1, males[s] + females[s])
            history.population(
                PopRow(
                    tick, s, count[s], males[s], females[s], babies[s], ageSum[s] / c,
                    births[s], matingCount[s], deaths[s].copyOf(),
                    FloatArray(NG) { geneSum[s][it] / c }, ripe, meat, inShelter[s],
                ),
            )
            births[s] = 0; matingCount[s] = 0
            java.util.Arrays.fill(deaths[s], 0)
        }
        history.flush()
    }

    // =====================================================================
    // learning brain: senses in, choice out
    // =====================================================================

    private fun brainThink(i: Int, b: RlBrain) {
        val d = def(i)
        observe(i)
        val mh = max(1f, maxHp(i))
        val reward = 0.01f + (en[i] - prevEn[i]) / d.maxEnergy + (hp[i] - prevHp[i]) / mh + rlBonus[i]
        rlBonus[i] = 0f; prevEn[i] = en[i]; prevHp[i] = hp[i]
        val a = b.act(i, uid[i], sp[i].toInt(), obs, amask, reward)
        lastAction[i] = a.toByte()
        applyAction(i, a)
    }

    private fun inOwnShelter(i: Int): Boolean {
        val s = sp[i].toInt()
        for (k in 0 until nSh) {
            if (shSp[k] != s) continue
            val dx = x[i] - shX[k]; val dy = y[i] - shY[k]
            if (dx * dx + dy * dy < shR[k] * shR[k]) return true
        }
        return false
    }

    /** 0 = no foreign shelter within 10 cells, 1 = at the edge of one. */
    private fun foreignShelterNear(i: Int): Float {
        val s = sp[i].toInt()
        var best = 0f
        for (k in 0 until nSh) {
            if (shSp[k] == s) continue
            val dx = x[i] - shX[k]; val dy = y[i] - shY[k]
            val edge = sqrt(dx * dx + dy * dy) - shR[k]
            val c = (1f - edge / 10f).coerceIn(0f, 1f)
            if (c > best) best = c
        }
        return best
    }

    private fun closeness(dx: Float, dy: Float, range: Float) = (1f - sqrt(dx * dx + dy * dy) / range).coerceIn(0f, 1f)

    /** Fills [obs] (what the creature senses) and [amask] (which choices make sense right now). */
    private fun observe(i: Int) {
        val d = def(i)
        val o = obs
        java.util.Arrays.fill(o, 0f)
        val state = st[i].toInt()
        o[0] = en[i] / d.maxEnergy
        o[1] = hp[i] / max(1f, maxHp(i))
        o[2] = age[i].toFloat() / (d.lifespanTicks * gene(i, G_LIFE))
        o[3] = if (isMature(i)) 1f else 0f
        o[4] = if (female[i]) 1f else 0f
        o[5] = if (isInfant(i)) 1f else 0f
        o[6] = if (mateCd[i] > 0) 1f else 0f
        o[7] = if (inOwnShelter(i)) 1f else 0f
        o[8] = if (hurtT[i] > 0) 1f else 0f

        // food plant (keep the one it is already going for)
        sPlant = if ((state == S_TOPLANT || state == S_EAT) && tgt[i] >= 0 && plantClaim[tgt[i]] == i) tgt[i]
            else if (d.plantEfficiency > 0f) nearestPlant(i) else -1
        if (sPlant >= 0) { o[9] = 1f; o[10] = closeness(plantX[sPlant] - x[i], plantY[sPlant] - y[i], d.smell) }
        // meat
        sMeat = if ((state == S_TOMEAT || state == S_EATMEAT) && tgt[i] >= 0 && meatAmt[tgt[i]] > 0f) tgt[i]
            else if (d.carnivore) nearestMeat(i) else -1
        if (sMeat >= 0) { o[11] = 1f; o[12] = closeness(meatX[sMeat] - x[i], meatY[sMeat] - y[i], d.smell * 1.5f) }
        // predator
        sThreat = if (!d.carnivore) senseNearest(i, MODE_THREAT) else -1
        if (sThreat >= 0) {
            o[13] = 1f; o[14] = closeness(x[sThreat] - x[i], y[sThreat] - y[i], max(d.vision, d.hear))
            o[15] = if (hurtT[i] > 0) 1f else 0f
        }
        // prey
        sPrey = if ((state == S_HUNT || state == S_ATTACK) && tgt[i] >= 0 && alive[tgt[i]] && uid[tgt[i]] == tgtUid[i]) tgt[i]
            else if (d.carnivore && huntCd[i] == 0) senseNearest(i, MODE_PREY) else -1
        if (sPrey >= 0) {
            o[16] = 1f; o[17] = closeness(x[sPrey] - x[i], y[sPrey] - y[i], d.vision)
            o[18] = if (sp[sPrey].toInt() == BETA) 1f else 0f
        }
        // mate (males look, females choose when asked)
        sMate = if (state == S_MATE && tgt[i] >= 0 && alive[tgt[i]] && uid[tgt[i]] == tgtUid[i]) tgt[i]
            else if (!female[i] && isMature(i) && mateCd[i] == 0) senseNearest(i, MODE_MATE) else -1
        if (sMate >= 0) { o[19] = 1f; o[20] = closeness(x[sMate] - x[i], y[sMate] - y[i], d.call) }
        // shelter
        sShelter = if (state == S_SHELTER && tgt[i] >= 0) tgt[i] else nearestOwnShelter(i, 40f)
        if (sShelter >= 0) {
            o[21] = 1f
            o[22] = closeness(shX[sShelter] - x[i], shY[sShelter] - y[i], 40f)
        }
        // mother
        val m = motherSlot[i]
        val hasMother = !isMature(i) && m >= 0 && alive[m] && uid[m] == motherUid[i]
        if (hasMother) o[23] = closeness(x[m] - x[i], y[m] - y[i], 20f)
        o[24] = if (foreignShelterAt(sp[i].toInt(), x[i], y[i]) >= 0) 1f else 0f
        o[25] = foreignShelterNear(i)

        amask[A_EXPLORE] = true
        amask[A_EAT_PLANT] = sPlant >= 0
        amask[A_EAT_MEAT] = sMeat >= 0
        amask[A_HUNT] = sPrey >= 0
        amask[A_FLEE] = sThreat >= 0
        amask[A_SHELTER] = sShelter >= 0
        amask[A_MATE] = sMate >= 0
        amask[A_FOLLOW] = hasMother
        amask[A_REST] = true
    }

    private fun stateToAction(s: Int): Int = when (s) {
        S_WANDER -> A_EXPLORE
        S_TOPLANT, S_EAT -> A_EAT_PLANT
        S_TOMEAT, S_EATMEAT -> A_EAT_MEAT
        S_HUNT, S_ATTACK -> A_HUNT
        S_FLEE -> A_FLEE
        S_SHELTER -> A_SHELTER
        S_MATE -> A_MATE
        S_FOLLOW -> A_FOLLOW
        S_IDLE -> A_REST
        else -> -1
    }

    /** Carries out a choice with the body's own movement code. Keeps going if it is already doing it. */
    private fun applyAction(i: Int, a: Int) {
        val state = st[i].toInt()
        when (a) {
            A_EXPLORE -> if (state != S_WANDER) { releaseTarget(i); st[i] = S_WANDER.toByte() }
            A_EAT_PLANT -> if (!((state == S_TOPLANT || state == S_EAT) && tgt[i] == sPlant)) claim(i, sPlant)
            A_EAT_MEAT -> if (!((state == S_TOMEAT || state == S_EATMEAT) && tgt[i] == sMeat)) { setState(i, S_TOMEAT, sMeat); tgtUid[i] = 0 }
            A_HUNT -> if (!((state == S_HUNT || state == S_ATTACK) && tgt[i] == sPrey)) { setState(i, S_HUNT, sPrey); timer[i] = 0 }
            A_FLEE -> setState(i, S_FLEE, sThreat)
            A_SHELTER -> if (!(state == S_SHELTER && tgt[i] == sShelter)) {
                releaseTarget(i); st[i] = S_SHELTER.toByte(); tgt[i] = sShelter; timer[i] = 100000
            }
            A_MATE -> if (!(state == S_MATE && tgt[i] == sMate)) setState(i, S_MATE, sMate)
            A_FOLLOW -> setState(i, S_FOLLOW, motherSlot[i])
            A_REST -> { releaseTarget(i); st[i] = S_IDLE.toByte(); timer[i] = DECIDE_EVERY }
        }
    }

    // =====================================================================
    // loading a saved world (used by Persist)
    // =====================================================================

    fun clearCreatures() {
        java.util.Arrays.fill(alive, false)
        java.util.Arrays.fill(plantClaim, -1)
    }

    fun restoreSlot(i: Int) {
        alive[i] = true
        st[i] = S_WANDER.toByte(); tgt[i] = -1; tgtUid[i] = 0; timer[i] = 0
        atkCd[i] = 0; huntCd[i] = 0; hurtT[i] = 0; flags[i] = 0
        trailN[i] = 0; trailHead[i] = 0; trailAcc[i] = 0f
    }

    /** Rebuilds the free-slot list and the counts after a load. */
    fun finishRestore() {
        freeTop = 0
        aliveCount = 0
        java.util.Arrays.fill(count, 0)
        for (i in cap - 1 downTo 0) {
            if (alive[i]) { aliveCount++; count[sp[i].toInt()]++ } else freeSlots[freeTop++] = i
        }
    }

    // =====================================================================
    // snapshot for the viewer
    // =====================================================================

    fun makeSnapshot(selectedUid: Int): RenderSnapshot {
        val n = aliveCount
        val suid = IntArray(n); val ssp = ByteArray(n); val sfem = BooleanArray(n)
        val sx = FloatArray(n); val sy = FloatArray(n); val spx = FloatArray(n); val spy = FloatArray(n)
        val sh = FloatArray(n); val sph = FloatArray(n); val sscale = FloatArray(n); val swalk = FloatArray(n)
        val sflags = ByteArray(n)
        var k = 0
        var selSlot = -1
        for (i in 0 until cap) {
            if (!alive[i]) continue
            if (k >= n) break
            suid[k] = uid[i]; ssp[k] = sp[i]; sfem[k] = female[i]
            sx[k] = x[i]; sy[k] = y[i]; spx[k] = px[i]; spy[k] = py[i]
            sh[k] = h[i]; sph[k] = ph[i]
            sscale[k] = (0.55f + 0.45f * min(1f, age[i].toFloat() / def(i).maturityTicks)) * (if (female[i]) 0.94f else 1.06f)
            swalk[k] = dist[i]
            sflags[k] = flags[i]
            if (uid[i] == selectedUid) selSlot = i
            k++
        }
        val mx = FloatArray(MAX_MEAT); val my = FloatArray(MAX_MEAT); val ma = FloatArray(MAX_MEAT)
        var mn = 0
        for (m in 0 until MAX_MEAT) if (meatAmt[m] > 0f) { mx[mn] = meatX[m]; my[mn] = meatY[m]; ma[mn] = meatAmt[m]; mn++ }

        val hist = Array(NSP) { s ->
            IntArray(histN) { idx -> histCounts[s][(histHead - histN + idx + HIST) % HIST] }
        }
        val fxt = fxType.copyOf(fxN); val fxx = fxX.copyOf(fxN); val fxy = fxY.copyOf(fxN)
        val nfx = fxN
        fxN = 0

        return RenderSnapshot(
            tick, k, suid, ssp, sfem, sx, sy, spx, spy, sh, sph, sscale, swalk, sflags,
            plantG.copyOf(), mx, my, ma, mn, count.copyOf(), maxGeneration, hist, histN,
            fxt, fxx, fxy, nfx, if (selSlot >= 0) selectedInfo(selSlot) else null,
        ).also { it.brainLabel = brain?.label() ?: "instincts" }
    }

    private fun selectedInfo(i: Int): SelectedInfo {
        val d = def(i)
        val tn = trailN[i]
        val tr = FloatArray(tn * 2)
        for (j in 0 until tn) {
            val idx = (trailHead[i] - 1 - j + TRAIL_LEN * 2) % TRAIL_LEN
            tr[j * 2] = trail[(i * TRAIL_LEN + idx) * 2]
            tr[j * 2 + 1] = trail[(i * TRAIL_LEN + idx) * 2 + 1]
        }
        val label = d.name.first() + "-" + (uid[i] + 1000).toString().padStart(4, '0')
        val doing = if (brain?.learning == true) "chose " + ACTION_NAMES[lastAction[i].toInt()] else STATE_NAMES[st[i].toInt()]
        return SelectedInfo(
            uid[i], sp[i].toInt(), female[i], alive[i], doing,
            x[i], y[i], h[i], hp[i], maxHp(i), en[i], d.maxEnergy,
            age[i].toFloat() / DAY_TICKS, d.lifespanDays * gene(i, G_LIFE),
            kills[i], meals[i], offspring[i], matings[i],
            if (lastMate[i] < 0) -1f else (tick - lastMate[i]).toFloat() / DAY_TICKS,
            dist[i], gen[i],
            d.vision * gene(i, G_VIS), d.visionHalfAngle, d.hear, d.smell, tr, tn, label,
        )
    }
}
