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
const val S_FORAGE = 12

val STATE_NAMES = arrayOf(
    "resting", "wandering", "heading to food", "eating", "hunting", "attacking",
    "going to meat", "eating meat", "fleeing", "in shelter", "looking for a mate", "following mother", "foraging",
)

private const val CELL = 8f
private const val TRAIL_LEN = 40
private const val MAX_MEAT = 512
/** Creature slots made at the start when there is no cap (fix 20); more are added as the population grows. */
private const val START_SLOTS = 1024
private const val HIST = 720
private const val THINK_EVERY = 3
private const val EAT_TICKS = 20
private const val MEAT_ROT_TICKS = 3000
private const val MALE_COOLDOWN = 60
private const val TURN_RATE = 0.35f
private const val FX_MAX = 256
/** Share of her max energy a mother spends on one whole litter (any size). The father spends 10%. */
private const val LITTERS = 4096
private const val REGIONS = 8

/** A healthy average male, used to show how willing females are (accept chance) in the panel. */
private val AVG_MALE = floatArrayOf(1f, 0f, 0f, 0f, 0.4f, 0f, 0.8f, 0.4f, 1f)

/** Brain gene curves (fix 19): neurons per layer, layers, learning rate, curiosity, decisions planned ahead, motivations. */
const val GC_HIDDEN = 0
const val GC_LAYERS = 1
const val GC_LR = 2
const val GC_CURIOSITY = 3
const val GC_HORIZON = 4
const val GC_MOTIVE = 5
const val NGC = GC_MOTIVE + NM

private const val MODE_THREAT = 0
private const val MODE_PREY = 1
private const val MODE_MATE = 2

/**
 * The whole world. All creatures live in flat arrays (slot number = index), which keeps thousands
 * of them cheap enough for a phone. Nothing here knows about screens.
 */
class World(val cfg: SimConfig, val history: HistoryLog = NullHistory) {
    /** This version's species numbers (body, senses, breeding). */
    val defs: Array<SpeciesDef> = cfg.defs
    private val decideEvery = cfg.decideEvery
    val width = cfg.width.toFloat()
    val height = cfg.height.toFloat()
    /** Slots in the creature arrays. Fix 20: with no cap they grow (double) whenever every slot is taken. */
    var cap = (if (cfg.hardCap > 0) cfg.hardCap else max(START_SLOTS, cfg.startCounts.sum() * 2)) + 64
        private set
    val rng = Rng(cfg.seed)
    var tick = 0L
    var nextUid = 1

    // ---- creatures ----
    var alive = BooleanArray(cap); private set
    var uid = IntArray(cap); private set
    var sp = ByteArray(cap); private set
    var female = BooleanArray(cap); private set
    var x = FloatArray(cap); private set
    var y = FloatArray(cap); private set
    var h = FloatArray(cap); private set
    var px = FloatArray(cap); private set
    var py = FloatArray(cap); private set
    var ph = FloatArray(cap); private set
    var age = IntArray(cap); private set
    var hp = FloatArray(cap); private set
    var en = FloatArray(cap); private set
    var st = ByteArray(cap); private set
    var tgt = IntArray(cap); private set
    var tgtUid = IntArray(cap); private set
    var timer = IntArray(cap); private set
    var atkCd = IntArray(cap); private set
    var huntCd = IntArray(cap); private set
    var mateCd = IntArray(cap); private set
    var hurtT = IntArray(cap); private set
    var motherSlot = IntArray(cap); private set
    var motherUid = IntArray(cap); private set
    var fatherUid = IntArray(cap); private set
    var partnerUid = IntArray(cap); private set
    var gen = IntArray(cap); private set
    var genes = FloatArray(cap * NG); private set
    var kills = IntArray(cap); private set
    var meals = IntArray(cap); private set
    var offspring = IntArray(cap); private set
    var matings = IntArray(cap); private set
    var lastMate = LongArray(cap); private set
    var birthTick = LongArray(cap); private set
    var dist = FloatArray(cap); private set
    var flags = ByteArray(cap); private set
    var trail = FloatArray(cap * TRAIL_LEN * 2); private set
    var trailN = IntArray(cap); private set
    var trailHead = IntArray(cap); private set
    var trailAcc = FloatArray(cap); private set
    /** Brain family (fix 15): babies get their mother's. */
    var family = ByteArray(cap); private set
    /** Steps spent inside its own shelter in this stay, and steps it must wait before going back (fix 2). */
    var shelterT = IntArray(cap); private set
    var shelterCd = IntArray(cap); private set
    /** Parts of the map (8 x 8 regions, one bit each) it has been to (fix 6). */
    var visited = LongArray(cap); private set
    /** Which litter a young creature belongs to, for learning mate choice (fix 14). */
    private var litterOf = IntArray(cap) { -1 }
    private var litterTagOf = IntArray(cap)

    var order = IntArray(cap); private set
    var nOrder = 0
    private var freeSlots = IntArray(cap)
    private var freeTop = 0
    val count = IntArray(NSP)
    var aliveCount = 0
    var maxGeneration = 0

    // ---- spatial grid (creatures are re-filed every step) ----
    private val gw = (cfg.width + 7) / 8
    private val gh = (cfg.height + 7) / 8
    private val gHead = IntArray(gw * gh)
    private var gNext = IntArray(cap)

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
    /** Shelters drift: their velocity in cells per step. */
    val shVx = FloatArray(cfg.shelters.sum())
    val shVy = FloatArray(cfg.shelters.sum())
    /** Food lying under a shelter cannot be eaten (no food inside shelters). Updated every step. */
    private val plantCovered = BooleanArray(cfg.plants)
    private val meatCovered = BooleanArray(MAX_MEAT)
    /** Ate to full: cannot eat again until its energy drops below [SimConfig.satietyLevel]. */
    var full = BooleanArray(cap); private set
    /** Creatures inside or heading into each shelter (fix 3). */
    val shOcc = IntArray(cfg.shelters.sum())

    // ---- litters waiting to grow up (mate choice learns from them) ----
    private val litFeat = FloatArray(LITTERS * MC_IN)
    private val litSp = IntArray(LITTERS)
    private val litN = IntArray(LITTERS)
    private val litMatured = IntArray(LITTERS)
    private val litLeft = IntArray(LITTERS)
    private val litTag = IntArray(LITTERS)
    private var litPtr = 0
    private var litNextTag = 1
    private val mcIn = FloatArray(MC_IN)

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
    /** Reward, decisions, meals and creature-steps per species since the start (used by the exam). */
    val rewardSum = DoubleArray(NSP)
    val decisionCount = LongArray(NSP)
    val mealCount = LongArray(NSP)
    val aliveSteps = LongArray(NSP)
    /** What each species' brains chose lately (halved every 600 steps, so it shows the recent mix). */
    val actRecent = Array(NSP) { FloatArray(NA) }
    /** Deaths by cause over the whole run, per species. */
    val deathTotals = Array(NSP) { LongArray(NCAUSE) }
    /** Deaths per cause over the whole run (one point per log row = deaths in the last 600 steps), for the stats page. */
    val deathCurves = Array(NSP) { Array(NCAUSE) { LearnCurve(600) } }
    /** Steps at which each species was wiped out. */
    val wipeouts = Array(NSP) { ArrayList<Long>() }
    /** Population over the whole run (one point per log row), for the stats page. */
    val popCurve = Array(NSP) { LearnCurve(600) }
    var rescues = 0
    var topUps = 0
    var brainResets = 0
    var familyCopies = 0
    /** When each species last got immigrants (fix 18). */
    private val lastImmigrants = LongArray(NSP) { -1_000_000L }
    /**
     * Brain genes over the run, per species (fix 19): one point per family check, the average over its creatures.
     * Index: [GC_HIDDEN], [GC_LAYERS], [GC_LR], [GC_CURIOSITY], [GC_HORIZON], then [GC_MOTIVE] + motivation.
     */
    val geneCurves = Array(NSP) { Array(NGC) { LearnCurve(600) } }
    /** Ticks of recent rescues per species (fix 10). */
    private val rescueTicks = Array(NSP) { LongArray(64) }
    private val rescueN = IntArray(NSP)
    private val lastReset = LongArray(NSP) { -1_000_000L }
    var totalKills = 0L
    var totalBirths = 0L
    var totalDeaths = 0L

    // ---- best creatures ever, per species (used to bring a species back) ----
    val hofSize = 6
    val hofCount = IntArray(NSP)
    val hofDays = Array(NSP) { IntArray(hofSize) }
    val hofMatings = Array(NSP) { IntArray(hofSize) }
    val hofKills = Array(NSP) { IntArray(hofSize) }
    val hofUid = Array(NSP) { IntArray(hofSize) }
    val hofGenes = Array(NSP) { FloatArray(hofSize * NG) }

    // ---- history for the in-app graph ----
    private val histCounts = Array(NSP) { IntArray(HIST) }
    private var histN = 0
    private var histHead = 0

    // ---- learning brains ----
    var brain: RlBrain? = makeBrain(cfg.seed)
    /** How the brains decide, from a sample of real decisions (feature attribution and neuron behaviour). */
    var brainStats = BrainStats(cfg.brainSize, cfg.brainLayers)
    private var explainCount = 0L
    /** Reward earned since its last decision, by kind (the brain family's motivations weigh them, fix 19). */
    private var rwFood = FloatArray(cap)
    private var rwMate = FloatArray(cap)
    internal var rwKids = FloatArray(cap); private set
    private var rwExplore = FloatArray(cap)
    private var prevEn = FloatArray(cap)
    private var prevHp = FloatArray(cap)
    private val obs = FloatArray(OBS)
    private val amask = BooleanArray(NA)
    private var sPlant = -1
    private var sMeat = -1
    private var sThreat = -1
    private var sPrey = -1
    private var sMate = -1
    private var sShelter = -1
    /** What each creature last decided (shown in the stats panel). */
    var lastAction = ByteArray(cap); private set
    /** Where a foraging creature is heading (the middle of the food it senses). */
    private var goalX = FloatArray(cap)
    private var goalY = FloatArray(cap)
    private var foodNear = 0f

    private var moveFactor = 0f
    private var senseD2 = 0f
    private var lastMeat = -1

    private fun makeBrain(seed: Long): RlBrain? = if (!cfg.brain) null else RlBrain(
        cap, cfg.rlParams(), seed,
        cfg.brainFamilies, cfg.learnBatch,
    ).also { it.learning = cfg.pretrainTicks <= 0 }

    /** How far a creature smells ripe plants (fix 5 makes it farther). */
    fun plantSmell(d: SpeciesDef) = d.smell * cfg.smellScale

    init {
        for (i in cap - 1 downTo 0) freeSlots[freeTop++] = i
        placeShelters()
        placePlants()
        spawnInitial()
        history.runInfo("start ${cfg.describe()}")
    }

    // =====================================================================
    // setup
    // =====================================================================

    private fun placeShelters() {
        for (s in 0 until NSP) {
            val d = defs[s]
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
                        val a = rng.range(0f, 6.2832f)
                        shVx[nSh] = cos(a) * cfg.shelterSpeed / TPS; shVy[nSh] = sin(a) * cfg.shelterSpeed / TPS
                        nSh++
                        break
                    }
                }
            }
        }
    }

    /**
     * Shelters drift slowly in a wandering direction, bounce off the map edge, and never overlap each other.
     * Then marks the food that lies under a shelter (it cannot be eaten there).
     */
    private fun moveShelters() {
        if (cfg.shelterSpeed > 0f) {
            val sp = cfg.shelterSpeed / TPS
            for (k in 0 until nSh) {
                // wander: turn a little now and then
                if (rng.chance(0.01f)) {
                    val a = atan2(shVy[k], shVx[k]) + rng.gauss() * 0.8f
                    shVx[k] = cos(a) * sp; shVy[k] = sin(a) * sp
                }
                val r = shR[k]
                var nx = shX[k] + shVx[k]; var ny = shY[k] + shVy[k]
                if (nx < r + 1f || nx > width - r - 1f) { shVx[k] = -shVx[k]; nx = shX[k] }
                if (ny < r + 1f || ny > height - r - 1f) { shVy[k] = -shVy[k]; ny = shY[k] }
                var blocked = false
                for (j in 0 until nSh) {
                    if (j == k) continue
                    val dx = nx - shX[j]; val dy = ny - shY[j]
                    val minD = r + shR[j] + 1f
                    if (dx * dx + dy * dy < minD * minD) {
                        // turn away from the other shelter
                        val a = atan2(dy, dx) + rng.range(-0.6f, 0.6f)
                        shVx[k] = cos(a) * sp; shVy[k] = sin(a) * sp
                        blocked = true
                        break
                    }
                }
                if (!blocked) { shX[k] = nx; shY[k] = ny }
            }
        }
        java.util.Arrays.fill(plantCovered, false)
        for (k in 0 until nSh) {
            val r = shR[k]; val r2 = r * r
            val x0 = max(0, ((shX[k] - r) / CELL).toInt()); val x1 = min(gw - 1, ((shX[k] + r) / CELL).toInt())
            val y0 = max(0, ((shY[k] - r) / CELL).toInt()); val y1 = min(gh - 1, ((shY[k] + r) / CELL).toInt())
            for (cy in y0..y1) for (cx in x0..x1) {
                var p = pHead[cy * gw + cx]
                while (p >= 0) {
                    val dx = plantX[p] - shX[k]; val dy = plantY[p] - shY[k]
                    if (dx * dx + dy * dy < r2) plantCovered[p] = true
                    p = pNext[p]
                }
            }
        }
        for (m in 0 until MAX_MEAT) meatCovered[m] = meatAmt[m] > 0f && inAnyShelter(meatX[m], meatY[m], 0f)
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
            } while (inAnyShelter(xx, yy, 1f) && tries++ < 5000)   // never inside a shelter
            plantX[p] = xx; plantY[p] = yy
            plantG[p] = rng.range(0.4f, 1f)
            plantClaim[p] = -1
            val c = cellOf(xx, yy)
            pNext[p] = pHead[c]; pHead[c] = p
        }
    }

    private fun spawnInitial() {
        for (s in 0 until NSP) {
            val d = defs[s]
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
                family[i] = (n % cfg.brainFamilies[s]).toByte()
            }
        }
    }

    private fun allocSlot(): Int {
        if (freeTop == 0) growTo(cap * 2)
        return freeSlots[--freeTop]
    }

    /** Fix 20: makes sure [n] slots are free, growing the creature arrays if needed. */
    private fun ensureFree(n: Int) {
        if (freeTop < n) growTo(max(cap * 2, cap + n - freeTop + 64))
    }

    /** Grows every per-creature array to [n] slots, keeping all creatures where they are. Called by Persist too. */
    fun growTo(n: Int) {
        if (n <= cap) return
        val old = cap
        alive = alive.copyOf(n); uid = uid.copyOf(n); sp = sp.copyOf(n); female = female.copyOf(n)
        x = x.copyOf(n); y = y.copyOf(n); h = h.copyOf(n); px = px.copyOf(n); py = py.copyOf(n); ph = ph.copyOf(n)
        age = age.copyOf(n); hp = hp.copyOf(n); en = en.copyOf(n); st = st.copyOf(n)
        tgt = tgt.copyOf(n); tgtUid = tgtUid.copyOf(n); timer = timer.copyOf(n)
        atkCd = atkCd.copyOf(n); huntCd = huntCd.copyOf(n); mateCd = mateCd.copyOf(n); hurtT = hurtT.copyOf(n)
        motherSlot = motherSlot.copyOf(n); motherUid = motherUid.copyOf(n); fatherUid = fatherUid.copyOf(n); partnerUid = partnerUid.copyOf(n)
        gen = gen.copyOf(n); genes = genes.copyOf(n * NG)
        kills = kills.copyOf(n); meals = meals.copyOf(n); offspring = offspring.copyOf(n); matings = matings.copyOf(n)
        lastMate = lastMate.copyOf(n); birthTick = birthTick.copyOf(n); dist = dist.copyOf(n); flags = flags.copyOf(n)
        trail = trail.copyOf(n * TRAIL_LEN * 2); trailN = trailN.copyOf(n); trailHead = trailHead.copyOf(n); trailAcc = trailAcc.copyOf(n)
        family = family.copyOf(n); shelterT = shelterT.copyOf(n); shelterCd = shelterCd.copyOf(n); visited = visited.copyOf(n)
        litterOf = litterOf.copyOf(n).also { java.util.Arrays.fill(it, old, n, -1) }; litterTagOf = litterTagOf.copyOf(n)
        order = order.copyOf(n); gNext = gNext.copyOf(n); full = full.copyOf(n)
        rwFood = rwFood.copyOf(n); rwMate = rwMate.copyOf(n); rwKids = rwKids.copyOf(n); rwExplore = rwExplore.copyOf(n)
        prevEn = prevEn.copyOf(n); prevHp = prevHp.copyOf(n); lastAction = lastAction.copyOf(n)
        goalX = goalX.copyOf(n); goalY = goalY.copyOf(n)
        // the new slots go under the free ones, so slots are handed out exactly as in a world that started this big
        val free = IntArray(n)
        var k = 0
        for (i in n - 1 downTo old) free[k++] = i
        System.arraycopy(freeSlots, 0, free, k, freeTop)
        freeSlots = free; freeTop += k
        cap = n
        brain?.ensureSlots(n)
    }

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
        rwFood[i] = 0f; rwMate[i] = 0f; rwKids[i] = 0f; rwExplore[i] = 0f; prevEn[i] = 0f; prevHp[i] = 0f; lastAction[i] = 0
        family[i] = 0; shelterT[i] = 0; shelterCd[i] = 0; visited[i] = 0L; litterOf[i] = -1; full[i] = false
        count[s]++
        aliveCount++
    }

    // =====================================================================
    // helpers
    // =====================================================================

    fun def(i: Int): SpeciesDef = defs[sp[i].toInt()]
    private fun gene(i: Int, k: Int) = genes[i * NG + k]

    fun growth(i: Int): Float {
        val mt = def(i).maturityTicks
        return 0.5f + 0.5f * min(1f, age[i].toFloat() / mt)
    }

    fun maxHp(i: Int) = def(i).health * gene(i, G_HP) * growth(i)
    fun isMature(i: Int) = age[i] >= def(i).maturityTicks
    fun isInfant(i: Int) = age[i] < def(i).infantTicks

    /**
     * Fix 19: a bigger brain eats more. 1 at the starting brain size; a brain twice that size costs
     * [SimConfig.brainEnergyCost] more, a smaller one saves energy.
     */
    fun brainUpkeep(i: Int): Float {
        val b = brain ?: return 1f
        if (cfg.brainEnergyCost == 0f) return 1f
        val start = max(1, cfg.brainSize * cfg.brainLayers).toFloat()
        val g = b.learner(sp[i].toInt(), family[i].toInt()).genes
        return (1f + cfg.brainEnergyCost * (g.neurons / start - 1f)).coerceAtLeast(0.5f)
    }

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

        moveShelters()

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

        // who is in or heading into each shelter (fix 3)
        java.util.Arrays.fill(shOcc, 0)
        for (n in 0 until nOrder) {
            val i = order[n]
            if (st[i].toInt() == S_SHELTER && tgt[i] in 0 until nSh) shOcc[tgt[i]]++
        }

        for (n in 0 until nOrder) {
            val i = order[n]
            if (alive[i]) stepCreature(i)
        }

        for (s in 0 until NSP) aliveSteps[s] += count[s]
        if (cfg.rescueEnabled) rescueCheck()
        if (tick % cfg.familyCheckEvery == 0L) refillFamilies()
        if (cfg.examEvery > 0 && tick % cfg.examEvery == 0L) brain?.let { if (it.learning && !it.frozen) Exam.start(this, it) }

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
        if (shelterCd[i] > 0) shelterCd[i]--
        if (age[i] == d.maturityTicks) {
            if (litterOf[i] >= 0) litterMemberDone(i, true)
            grewUp(i)
        }
        if (hurtT[i] > 0) { hurtT[i]--; flags[i] = (flags[i].toInt() or F_HURT).toByte() }

        if (age[i] > (d.lifespanTicks * gene(i, G_LIFE)).toInt()) { die(i, C_OLD, -1); return }
        if (en[i] <= 0f) {
            hp[i] -= cfg.starveDamage
            if (hp[i] <= 0f) { die(i, C_STARVED, -1); return }
        }

        val b = brain
        val phase = (i + tick.toInt())
        if (b != null && b.learning) {
            if (phase % decideEvery == 0) brainThink(i, b)
        } else if (phase % THINK_EVERY == 0) {
            if (b != null && phase % decideEvery == 0) {
                // the brain watches the instinct and learns to copy it
                observe(i)
                think(i)
                val label = stateToAction(st[i].toInt())
                if (label >= 0 && amask[label]) b.imitate(sp[i].toInt(), family[i].toInt(), obs, amask, label)
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
        val upkeep = d.hunger * metabolism(i) * (0.4f + 0.6f * growth(i)) * (1f + 0.6f * moveFactor) * brainUpkeep(i)
        en[i] = max(0f, en[i] - upkeep)
        // full after eating: no more food until energy falls below the satiety level
        if (en[i] >= d.maxEnergy * 0.98f) full[i] = true
        else if (en[i] < d.maxEnergy * cfg.satietyLevel) full[i] = false
        val mh = maxHp(i)
        val resting = st[i].toInt() == S_SHELTER && (flags[i].toInt() and F_REST) != 0
        if (!resting) shelterT[i] = 0
        // fix 1: a shelter does not heal a starving creature, so hiding cannot replace eating
        if (resting && (cfg.shelterHealsStarving || en[i] > 0f)) {
            hp[i] = min(mh, hp[i] + mh * cfg.shelterHeal)
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
                if (sh >= 0) { goShelter(i, sh, 80); return }
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
            if (sh >= 0) { goShelter(i, sh, 200); return }
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
        // stopping halfway to a plant or halfway through eating it lets go of it (it used to stay locked forever)
        if (st[i].toInt() == S_TOPLANT || st[i].toInt() == S_EAT) releasePlant(i)
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

    /**
     * true if nobody else is going for plant [p] or eating it. A claim only counts while its owner is alive and
     * really on its way to that plant or eating it, so a claim left behind can never lock a plant.
     */
    private fun plantFree(p: Int): Boolean {
        val c = plantClaim[p]
        if (c < 0) return true
        if (alive[c] && (st[c].toInt() == S_TOPLANT || st[c].toInt() == S_EAT) && tgt[c] == p) return false
        plantClaim[p] = -1
        return true
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
                        // a threat: another species that is hunting or biting me, or a stronger species
                        MODE_THREAT -> sp[j] != mySp && (
                            ((st[j].toInt() == S_HUNT || st[j].toInt() == S_ATTACK) && tgt[j] == i && tgtUid[j] == uid[i]) ||
                                defs[sp[j].toInt()].strength > d.strength)
                        MODE_PREY -> sp[j] != mySp && (cfg.allCanHunt || !defs[sp[j].toInt()].carnivore)
                        else -> sp[j] == mySp && female[j] != female[i] && isMature(j) && mateCd[j] == 0 && !isInfant(j) &&
                            (cfg.matingInShelter || !inAnyShelter(x[j], y[j], 0f))
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
        val r = plantSmell(d)
        val xi = x[i]; val yi = y[i]
        val r2 = r * r
        val x0 = max(0, ((xi - r) / CELL).toInt()); val x1 = min(gw - 1, ((xi + r) / CELL).toInt())
        val y0 = max(0, ((yi - r) / CELL).toInt()); val y1 = min(gh - 1, ((yi + r) / CELL).toInt())
        var best = -1
        var bd = Float.MAX_VALUE
        for (cy in y0..y1) for (cx in x0..x1) {
            var p = pHead[cy * gw + cx]
            while (p >= 0) {
                if (plantG[p] >= 1f && !plantCovered[p] && plantFree(p)) {
                    val dx = plantX[p] - xi; val dy = plantY[p] - yi
                    val d2 = dx * dx + dy * dy
                    if (d2 < bd && d2 <= r2) { best = p; bd = d2 }
                }
                p = pNext[p]
            }
        }
        return best
    }

    /**
     * Food it can eat within the forage range: ripe free plants and meat, not under a shelter. Sets [goalX], [goalY]
     * to their middle (closer food counts more) and [foodNear] to how much there is. Returns false if there is none.
     */
    private fun forageGoal(i: Int): Boolean {
        val d = def(i)
        val r = cfg.forageRange
        val r2 = r * r
        val xi = x[i]; val yi = y[i]
        var sw = 0f; var sx = 0f; var sy = 0f; var n = 0f
        if (d.plantEfficiency > 0f) {
            val x0 = max(0, ((xi - r) / CELL).toInt()); val x1 = min(gw - 1, ((xi + r) / CELL).toInt())
            val y0 = max(0, ((yi - r) / CELL).toInt()); val y1 = min(gh - 1, ((yi + r) / CELL).toInt())
            for (cy in y0..y1) for (cx in x0..x1) {
                var p = pHead[cy * gw + cx]
                while (p >= 0) {
                    if (plantG[p] >= 1f && !plantCovered[p] && plantFree(p)) {
                        val dx = plantX[p] - xi; val dy = plantY[p] - yi
                        val dd = dx * dx + dy * dy
                        if (dd <= r2) { val wgt = d.plantEfficiency / (2f + sqrt(dd)); sw += wgt; sx += plantX[p] * wgt; sy += plantY[p] * wgt; n++ }
                    }
                    p = pNext[p]
                }
            }
        }
        if (d.meatEfficiency > 0f) {
            for (m in 0 until MAX_MEAT) {
                if (meatAmt[m] <= 0f || meatCovered[m]) continue
                val dx = meatX[m] - xi; val dy = meatY[m] - yi
                val dd = dx * dx + dy * dy
                if (dd <= r2) { val wgt = d.meatEfficiency * 2f / (2f + sqrt(dd)); sw += wgt; sx += meatX[m] * wgt; sy += meatY[m] * wgt; n += 2f }
            }
        }
        foodNear = n
        if (sw <= 0f) return false
        goalX[i] = sx / sw; goalY[i] = sy / sw
        return true
    }

    private fun nearestMeat(i: Int): Int {
        val r = def(i).smell * 1.5f
        val r2 = r * r
        var best = -1
        var bd = Float.MAX_VALUE
        for (m in 0 until MAX_MEAT) {
            if (meatAmt[m] <= 0f || meatCovered[m]) continue
            val dx = meatX[m] - x[i]; val dy = meatY[m] - y[i]
            val d2 = dx * dx + dy * dy
            if (d2 < bd && d2 <= r2) { best = m; bd = d2 }
        }
        return best
    }

    /** Nearest own shelter that still has room (fix 3) and that it may go back to (fix 2), or -1. */
    private fun nearestOwnShelter(i: Int, maxDist: Float): Int {
        val s = sp[i].toInt()
        val already = if (st[i].toInt() == S_SHELTER) tgt[i] else -1
        if (shelterCd[i] > 0 && already < 0) return -1
        val room = cfg.shelterCapacity[s]
        var best = -1
        var bd = maxDist * maxDist
        for (k in 0 until nSh) {
            if (shSp[k] != s) continue
            if (room > 0 && k != already && shOcc[k] >= room) continue
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
                if (p < 0 || plantG[p] < 1f || plantClaim[p] != i || full[i] || plantCovered[p]) { releasePlant(i); st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dd = steer(i, plantX[p], plantY[p], 1f, 0.6f)
                if (dd < 0.9f) { st[i] = S_EAT.toByte(); timer[i] = EAT_TICKS }
            }
            S_EAT -> {
                flags[i] = (flags[i].toInt() or F_EAT).toByte()
                if (--timer[i] <= 0) {
                    val p = tgt[i]
                    if (p >= 0 && plantClaim[p] == i && !plantCovered[p]) {
                        en[i] = min(d.maxEnergy, en[i] + cfg.plantEnergy * d.plantEfficiency)
                        rwFood[i] += cfg.plantEnergy * d.plantEfficiency / d.maxEnergy * cfg.foodRewardScale
                        plantG[p] = 0f; plantClaim[p] = -1
                        meals[i]++; mealCount[sp[i].toInt()]++
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
                if (m < 0 || meatAmt[m] <= 0f || full[i] || meatCovered[m]) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dd = steer(i, meatX[m], meatY[m], 1.2f, 0.6f)
                if (dd < 1.2f) st[i] = S_EATMEAT.toByte()
            }
            S_EATMEAT -> {
                val m = tgt[i]
                flags[i] = (flags[i].toInt() or F_EAT).toByte()
                if (m < 0 || meatAmt[m] <= 0f || meatCovered[m] || d.meatEfficiency <= 0f) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val take = min(meatAmt[m], 1.2f)
                meatAmt[m] -= take
                en[i] = min(d.maxEnergy, en[i] + take * d.meatEfficiency)
                rwFood[i] += take * d.meatEfficiency / d.maxEnergy * cfg.foodRewardScale
                if (meatAmt[m] <= 0f || en[i] >= d.maxEnergy * 0.98f) {
                    if (en[i] >= d.maxEnergy * 0.98f) full[i] = true
                    meals[i]++; mealCount[sp[i].toInt()]++; tgt[i] = -1; st[i] = S_WANDER.toByte()
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
            S_FORAGE -> {
                // walk toward the middle of the food it sensed; once there, smell takes over (eat plant / eat meat)
                val dd = steer(i, goalX[i], goalY[i], 1f, 0.5f)
                if (dd < 1.5f) st[i] = S_WANDER.toByte()
            }
            S_FOLLOW -> {
                val m = tgt[i]
                if (m < 0 || !alive[m] || uid[m] != tgtUid[i]) { st[i] = S_WANDER.toByte(); tgt[i] = -1; return }
                val dd = steer(i, x[m], y[m], 1f, 1.8f)
                if (dd < 3f && mateCd[i] == 0 && en[i] < def(i).maxEnergy * 0.6f && en[m] > def(m).maxEnergy * 0.55f) {
                    // the mother shares food: what she gives is exactly what the baby gets
                    val amount = cfg.motherFeed
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

    private fun goShelter(i: Int, k: Int, stay: Int) {
        val wasThere = st[i].toInt() == S_SHELTER && tgt[i] == k
        releaseTarget(i); st[i] = S_SHELTER.toByte(); tgt[i] = k; timer[i] = stay
        if (!wasThere) shOcc[k]++
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
        // fix 2: a stay has a time limit, then it must go out for a while
        if (cfg.shelterMaxStay > 0 && ++shelterT[i] > cfg.shelterMaxStay) {
            shelterT[i] = 0; shelterCd[i] = cfg.shelterCooldown
            st[i] = S_WANDER.toByte(); tgt[i] = -1
            return
        }
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

    /** What a female looks at when a male asks her (fix 14). All roughly between -1 and 1. */
    private fun mateFeatures(m: Int, f: Int, out: FloatArray) {
        val d = def(f)
        out[0] = hp[m] / max(1f, maxHp(m))
        out[1] = gene(m, G_STR) - 1f
        out[2] = gene(m, G_SPEED) - 1f
        out[3] = gene(m, G_HP) - 1f
        out[4] = age[m].toFloat() / (d.lifespanTicks * gene(m, G_LIFE))
        out[5] = min(1f, kills[m] / 5f)
        out[6] = en[f] / d.maxEnergy
        out[7] = age[f].toFloat() / (d.lifespanTicks * gene(f, G_LIFE))
        out[8] = 1f
    }

    internal fun tryMate(m: Int, f: Int) {
        val d = def(m)
        // shelters are only for protection: no mating inside one
        if (!cfg.matingInShelter && (inAnyShelter(x[m], y[m], 0f) || inAnyShelter(x[f], y[f], 0f))) return
        if (en[f] < d.maxEnergy * cfg.mateEnergyNeeded || !isMature(f) || isInfant(f)) return
        var n = d.litterMin + rng.nextInt(d.litterMax - d.litterMin + 1)
        val fert = (gene(m, G_FERT) + gene(f, G_FERT)) / 2f
        n = max(1, (n * fert + 0.5f).toInt())
        // fix 20: hardCap 0 = no cap, so a population can erupt
        if (cfg.hardCap > 0 && aliveCount + n > cfg.hardCap) n = cfg.hardCap - aliveCount
        // each species has its own room, so one species cannot fill the world and stop the others breeding
        val room = cfg.speciesCap[sp[f].toInt()] - count[sp[f].toInt()]
        if (cfg.speciesCap[sp[f].toInt()] > 0 && n > room) n = room
        // fix 17: each baby costs its share of an average litter's cost, and the mother keeps a reserve
        val perBaby = d.maxEnergy * cfg.litterCost / max(1f, (d.litterMin + d.litterMax) / 2f)
        if (cfg.litterCostBySize) n = min(n, ((en[f] - d.maxEnergy * cfg.litterReserve) / max(1e-6f, perBaby)).toInt())
        if (n <= 0) return

        val s = sp[f].toInt()
        val b = brain
        val learned = cfg.learnedMateChoice && b != null && b.learning
        if (learned) {
            // fix 14: she has learned which males give babies that grow up
            val mc = b!!.mateChoice
            mateFeatures(m, f, mcIn)
            val yes = mc.decide(s, mcIn)
            if (mc.askedSinceLog(s) >= 200) b.logChoice(s, mc.takeAcceptRate(s))
            if (!yes) { if (!b.frozen) mc.learn(s, mcIn, 0, false, 0f); return }
        } else {
            // the fixed rule: health and strength make a male attractive, with a little chance
            val score = 0.5f * (hp[m] / maxHp(m)) + 0.5f * ((gene(m, G_STR) - 0.6f) / 1f).coerceIn(0f, 1f)
            val pickiness = 0.3f + 0.25f * (((uid[f] * 2654435761L) ushr 10) and 0xFF).toFloat() / 255f
            if (score + rng.range(-0.12f, 0.12f) <= pickiness) return
        }
        var lit = -1
        if (learned) {
            lit = litPtr; litPtr = (litPtr + 1) % LITTERS
            System.arraycopy(mcIn, 0, litFeat, lit * MC_IN, MC_IN)
            litSp[lit] = s; litN[lit] = n; litMatured[lit] = 0; litLeft[lit] = n; litTag[lit] = litNextTag++
        }

        // one litter costs the mother half her max energy (fix 17: more for a big litter, less for a small one)
        en[f] = max(0f, en[f] - if (cfg.litterCostBySize) perBaby * n else d.maxEnergy * cfg.litterCost)
        en[m] = max(0f, en[m] - d.maxEnergy * cfg.fatherCost)
        rwMate[f] += cfg.matingReward; rwMate[m] += cfg.matingReward
        mateCd[f] = d.femaleCooldownDays * DAY_TICKS
        lastMate[f] = tick; lastMate[m] = tick
        matings[f]++; matings[m]++
        partnerUid[f] = uid[m]; partnerUid[m] = uid[f]
        matingCount[sp[f].toInt()]++

        ensureFree(n)
        for (k in 0 until n) {
            val baby = spawnBaby(f, m)
            if (lit >= 0) { litterOf[baby] = lit; litterTagOf[baby] = litTag[lit] }
        }
    }

    /**
     * Fix 16: a baby grew up. Each parent still alive gets its share of [SimConfig.grownUpReward] (a whole litter of
     * average size gives the full reward), so the brain learns that raising babies pays, not just mating.
     */
    internal fun grewUp(i: Int) {
        if (cfg.grownUpReward <= 0f || brain?.learning != true) return
        val d = def(i)
        val r = cfg.grownUpReward / max(1f, (d.litterMin + d.litterMax) / 2f)
        val m = motherSlot[i]
        if (m >= 0 && alive[m] && uid[m] == motherUid[i]) rwKids[m] += r
        val fu = fatherUid[i]
        if (fu != 0) for (j in 0 until cap) {
            if (alive[j] && uid[j] == fu) { rwKids[j] += r; break }
        }
    }

    /** One baby of a remembered litter grew up or died young. When the whole litter is done, mate choice learns. */
    private fun litterMemberDone(i: Int, grewUp: Boolean) {
        val lit = litterOf[i]
        litterOf[i] = -1
        if (lit < 0 || litTag[lit] != litterTagOf[i]) return
        if (grewUp) litMatured[lit]++
        if (--litLeft[lit] > 0) return
        litTag[lit] = 0
        val mc = brain?.mateChoice ?: return
        if (brain?.frozen == true) return
        val s = litSp[lit]
        mc.learn(s, litFeat, lit * MC_IN, true, mc.litterReward(s, litMatured[lit].toFloat() / max(1, litN[lit])))
    }

    internal fun spawnBaby(f: Int, m: Int): Int {
        val s = sp[f].toInt()
        val d = defs[s]
        val i = allocSlot()
        initCreature(i, s, rng.chance(0.5f), x[f] + rng.range(-1.5f, 1.5f), y[f] + rng.range(-1.5f, 1.5f), 0)
        for (k in 0 until NG) {
            val from = if (rng.chance(0.5f)) gene(f, k) else gene(m, k)
            genes[i * NG + k] = (from + rng.gauss() * cfg.mutation).coerceIn(0.5f, 1.8f)
        }
        if (brain == null) pushOut(i)
        px[i] = x[i]; py[i] = y[i]
        hp[i] = maxHp(i)
        en[i] = d.maxEnergy * cfg.babyEnergy
        gen[i] = max(gen[f], gen[m]) + 1
        if (gen[i] > maxGeneration) maxGeneration = gen[i]
        motherSlot[i] = f; motherUid[i] = uid[f]; fatherUid[i] = uid[m]
        family[i] = family[f]
        st[i] = S_FOLLOW.toByte(); tgt[i] = f; tgtUid[i] = uid[f]
        offspring[f]++; offspring[m]++
        births[s]++
        totalBirths++
        fx(FX_BIRTH, x[i], y[i])
        if (cfg.logBirths) history.event(tick, "birth", s, uid[i], uid[f], x[i], y[i], "father=${uid[m]}")
        return i
    }

    // ---- death ----

    private fun die(i: Int, cause: Int, killer: Int) {
        val s = sp[i].toInt()
        val d = defs[s]
        if (st[i].toInt() == S_TOPLANT || st[i].toInt() == S_EAT) releasePlant(i)
        if (litterOf[i] >= 0) litterMemberDone(i, false)
        // meat only from an accident (killed or injured), as much as the energy it still had; starving or old age leave none
        if (cause == C_KILLED || cause == C_INJURY) {
            val amount = en[i] * cfg.meatShare
            if (amount > 5f) lastMeat = addMeat(x[i], y[i], amount)
        }
        brain?.let { b ->
            // dying is bad, unless it is old age after having babies
            if (b.learning) {
                val r = if (cause == C_OLD && offspring[i] > 0) 0f else -cfg.deathPenalty
                b.death(i, uid[i], r * b.learner(s, family[i].toInt()).genes.motive[M_DEATH])
                rewardSum[s] += r
            }
        }
        fx(FX_DEATH, x[i], y[i])
        deaths[s][cause]++
        deathTotals[s][cause]++
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
        rememberChampion(i)
        alive[i] = false
        count[s]--
        aliveCount--
        freeSlots[freeTop++] = i
        if (count[s] == 0 && !extinct[s]) {
            extinct[s] = true
            history.event(tick, "extinction", s, 0, 0, x[i], y[i], "last one died of ${CAUSE_NAMES[cause]}")
            wipeouts[s].add(tick)
        }
    }

    /** Meat never lies inside a shelter: a body that falls inside one is left just outside its edge. */
    private fun addMeat(x0: Float, y0: Float, amount: Float): Int {
        var xx = x0; var yy = y0
        for (k in 0 until nSh) {
            val dx = xx - shX[k]; val dy = yy - shY[k]
            val r = shR[k] + 0.8f
            if (dx * dx + dy * dy < r * r) {
                val d = max(0.001f, sqrt(dx * dx + dy * dy))
                val ux = if (d < 0.01f) 1f else dx / d; val uy = if (d < 0.01f) 0f else dy / d
                xx = (shX[k] + ux * r).coerceIn(0.5f, width - 0.5f); yy = (shY[k] + uy * r).coerceIn(0.5f, height - 0.5f)
            }
        }
        var tries = 0
        while (meatAmt[meatPtr] > 0f && tries++ < MAX_MEAT) meatPtr = (meatPtr + 1) % MAX_MEAT
        val m = meatPtr
        meatPtr = (meatPtr + 1) % MAX_MEAT
        meatX[m] = xx; meatY[m] = yy; meatAmt[m] = amount; meatAge[m] = 0
        return m
    }

    // ---- rescue (off by default) ----

    /**
     * Keeps the 6 best creatures of each species ever seen. Best means, in this order:
     * lived the most days, then mated the most, then killed the most.
     */
    fun rememberChampion(i: Int) {
        val s = sp[i].toInt()
        val days = age[i] / DAY_TICKS
        val n = hofCount[s]
        fun better(d: Int, m: Int, k: Int, j: Int) =
            d > hofDays[s][j] || (d == hofDays[s][j] && (m > hofMatings[s][j] || (m == hofMatings[s][j] && k > hofKills[s][j])))
        var pos = n
        while (pos > 0 && better(days, matings[i], kills[i], pos - 1)) pos--
        if (pos >= hofSize) return
        val last = minOf(n, hofSize - 1)
        for (j in last downTo pos + 1) {
            hofDays[s][j] = hofDays[s][j - 1]; hofMatings[s][j] = hofMatings[s][j - 1]
            hofKills[s][j] = hofKills[s][j - 1]; hofUid[s][j] = hofUid[s][j - 1]
            System.arraycopy(hofGenes[s], (j - 1) * NG, hofGenes[s], j * NG, NG)
        }
        hofDays[s][pos] = days; hofMatings[s][pos] = matings[i]; hofKills[s][pos] = kills[i]; hofUid[s][pos] = uid[i]
        System.arraycopy(genes, i * NG, hofGenes[s], pos * NG, NG)
        if (n < hofSize) hofCount[s] = n + 1
    }

    private fun rescueCheck() {
        for (s in 0 until NSP) {
            if (cfg.startCounts[s] <= 0) continue
            if (cfg.immigration) {
                // fix 18: newcomers walk in at the edge, now and then; the species may stay wiped out until then
                val min = max(1, cfg.minPopulation[s])
                if (count[s] < min && tick - lastImmigrants[s] >= cfg.immigrationEvery) {
                    val wiped = count[s] == 0
                    rescueSpecies(s, if (wiped) comeback(s, max(cfg.rescueCount, min)) else min - count[s], if (wiped) "rescue" else "topup", atEdge = true)
                    lastImmigrants[s] = tick
                }
                continue
            }
            if (count[s] == 0) {
                rescueSpecies(s, comeback(s, cfg.rescueCount))
                noteRescue(s)
            } else if (count[s] < cfg.minPopulation[s] && tick % cfg.logEveryTicks == 0L) {
                // fix 9: keep enough creatures for the brain to learn from
                rescueSpecies(s, cfg.minPopulation[s] - count[s], "topup")
            }
        }
    }

    /**
     * How many come back after a wipe-out: [base], plus [SimConfig.rescueGrowth] for every earlier wipe-out of the species
     * (the one just now is already in [wipeouts]). So each comeback is bigger than the last.
     */
    private fun comeback(s: Int, base: Int): Int = base + cfg.rescueGrowth * max(0, wipeouts[s].size - 1)

    /** Fix 10: a species that keeps dying out has a broken brain, so it starts again from random weights. */
    private fun noteRescue(s: Int) {
        val b = brain ?: return
        if (cfg.brainResetRescues <= 0 || cfg.immigration || !b.learning || b.frozen) return
        val ring = rescueTicks[s]
        ring[rescueN[s] % ring.size] = tick
        rescueN[s]++
        val n = min(rescueN[s], ring.size)
        var recent = 0
        for (k in 0 until n) if (tick - ring[k] <= cfg.brainResetWindow) recent++
        if (recent >= cfg.brainResetRescues && tick - lastReset[s] > cfg.brainResetWindow) {
            b.resetSpecies(s)
            lastReset[s] = tick
            rescueN[s] = 0
            brainResets++
            history.event(tick, "brain_reset", s, recent, 0, 0f, 0f, "$recent rescues in ${cfg.brainResetWindow} steps: new random brain")
        }
    }

    /**
     * Fix 15: a brain family with no members left is replaced by a changed copy of the family with the most
     * members. Brains that keep their creatures alive and breeding spread; the others disappear.
     * Fix 19: a family with far fewer members than the biggest is replaced too, and the copy's genes change a little
     * (brain size, learning settings, motivations), so evolution chooses them. Every check logs each family's genes.
     */
    private fun refillFamilies() {
        val b = brain ?: return
        if (!b.learning || b.frozen) return
        for (s in 0 until NSP) {
            val nf = cfg.brainFamilies[s]
            val members = IntArray(nf)
            for (n in 0 until nOrder) {
                val i = order[n]
                if (alive[i] && sp[i].toInt() == s) members[family[i].toInt().coerceIn(0, nf - 1)]++
            }
            var best = 0
            for (f in 1 until nf) if (members[f] > members[best]) best = f
            if (nf > 1 && count[s] > 0 && members[best] >= max(2, cfg.familyCopyMinMembers)) {
                for (f in 0 until nf) {
                    if (f == best) continue
                    val weak = members[f] == 0 || (cfg.familyReplaceShare > 0f && members[f] < members[best] * cfg.familyReplaceShare &&
                        tick - b.learner(s, f).bornTick >= 2L * cfg.familyCheckEvery)
                    if (!weak) continue
                    val genes = if (cfg.evolveBrains) b.learner(s, best).genes.mutate(rng, cfg.brainGeneMutation, cfg.brainGrowChance,
                        cfg.brainMinSize, cfg.brainMaxSize) else null
                    val had = members[f]
                    b.copyFamily(s, best, f, cfg.brainMutation, genes)
                    familyCopies++
                    history.event(tick, "brain_copy", s, best, f, 0f, 0f,
                        (if (had == 0) "family $f died out" else "family $f had only $had members") +
                            ": copied family $best (${members[best]} members) with small changes" + (genes?.let { " (${it.describe()})" } ?: ""))
                    // give the new family a few members so it can be tried out
                    var moved = had
                    for (n in 0 until nOrder) {
                        val i = order[n]
                        if (moved >= max(1, members[best] / 4)) break
                        if (alive[i] && sp[i].toInt() == s && family[i].toInt() == best && n % 2 == 0) {
                            b.handOver(i, uid[i]); family[i] = f.toByte(); moved++; members[best]--; members[f]++
                        }
                    }
                }
            }
            b.logGenes(tick, s, members)
            recordGenes(s, members)
        }
    }

    /** Adds one point per gene to [geneCurves]: the average over the species' creatures (each family weighted by its members). */
    private fun recordGenes(s: Int, members: IntArray) {
        val b = brain ?: return
        val nf = cfg.brainFamilies[s]
        val total = members.sum()
        val v = FloatArray(NGC)
        for (f in 0 until nf) {
            val wgt = if (total > 0) members[f].toFloat() / total else 1f / nf
            val g = b.learner(s, f).genes
            v[GC_HIDDEN] += wgt * g.hidden; v[GC_LAYERS] += wgt * g.layers; v[GC_LR] += wgt * g.lr
            v[GC_CURIOSITY] += wgt * g.entropy; v[GC_HORIZON] += wgt * g.horizon
            for (k in 0 until NM) v[GC_MOTIVE + k] += wgt * g.motive[k]
        }
        for (k in 0 until NGC) geneCurves[s][k].add(tick, v[k], 0f)
        geneCurveVersion++
    }

    private fun bestFamily(s: Int): Int {
        val nf = cfg.brainFamilies[s]
        if (nf <= 1) return 0
        val members = IntArray(nf)
        for (i in 0 until cap) if (alive[i] && sp[i].toInt() == s) members[family[i].toInt().coerceIn(0, nf - 1)]++
        var best = rng.nextInt(nf)
        for (f in 0 until nf) if (members[f] > members[best]) best = f
        return best
    }

    /**
     * Brings a species back with [SimConfig.rescueCount] adults whose genes are copied from the best
     * creatures the species ever had, each with a small random change.
     */
    fun rescueSpecies(s: Int, howMany: Int = cfg.rescueCount, kind: String = "rescue", atEdge: Boolean = false): IntArray {
        val d = defs[s]
        val made = ArrayList<Int>()
        val from = ArrayList<Int>()
        val fam = bestFamily(s)
        repeat(howMany) { n ->
            if (cfg.hardCap <= 0 || aliveCount < cfg.hardCap) {
                // whichever sex is missing more (a top-up of one used to always be a female)
                var males = 0; var females = 0
                for (j in 0 until cap) if (alive[j] && sp[j].toInt() == s) { if (female[j]) females++ else males++ }
                val fem = if (males == females) rng.chance(0.5f) else females < males
                val i = allocSlot()
                var xx = rng.range(2f, width - 2f); var yy = rng.range(2f, height - 2f)
                if (atEdge) {
                    // immigrants arrive from outside: somewhere along the edge, not inside a stranger's shelter
                    var tries = 0
                    do {
                        val side = rng.nextInt(4)
                        val t = rng.nextFloat()
                        xx = when (side) { 0 -> 1.5f; 1 -> width - 1.5f; else -> 1.5f + t * (width - 3f) }
                        yy = when (side) { 2 -> 1.5f; 3 -> height - 1.5f; else -> 1.5f + t * (height - 3f) }
                    } while (foreignShelterAt(s, xx, yy) >= 0 && tries++ < 20)
                }
                initCreature(i, s, fem, xx, yy, d.maturityTicks)
                val hc = hofCount[s]
                if (hc > 0) {
                    val c = n % hc
                    from.add(hofUid[s][c])
                    for (k in 0 until NG) {
                        genes[i * NG + k] = (hofGenes[s][c * NG + k] + rng.gauss() * cfg.mutation).coerceIn(0.5f, 1.8f)
                    }
                } else for (k in 0 until NG) genes[i * NG + k] = 1f
                en[i] = d.maxEnergy; hp[i] = maxHp(i); gen[i] = 1
                motherSlot[i] = -1; motherUid[i] = 0; fatherUid[i] = 0
                family[i] = fam.toByte()
                made.add(i)
            }
        }
        if (kind == "rescue") rescues++ else topUps++
        extinct[s] = false
        val note = (if (atEdge) "immigrants at the map edge: " else "") + (if (from.isEmpty()) "added ${made.size} with average genes"
            else "added ${made.size} from the best genes of ${from.distinct().joinToString(" ")}")
        history.event(tick, kind, s, made.size, 0, 0f, 0f, note)
        return made.toIntArray()
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
            popCurve[s].add(tick, count[s].toFloat(), 0f)
            for (c in 0 until NCAUSE) deathCurves[s][c].add(tick, deaths[s][c].toFloat(), 0f)
            if (s == 0) brainStats.decay()
            for (a in 0 until NA) actRecent[s][a] *= 0.5f
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
        // reward: food eaten and matings (added as they happen), getting hurt, and being very hungry.
        // Spending energy and resting are not rewarded or punished on their own.
        val hurt = min(0f, hp[i] - prevHp[i]) / mh * cfg.hurtPenaltyScale
        // fix 4: being very hungry costs more while sitting in its own shelter
        val starving = if (en[i] >= d.maxEnergy * cfg.hungryLevel) 0f else if (obs[7] > 0f) -cfg.shelterHungerPenalty else -cfg.hungerPenalty
        // fix 6: a small reward for reaching a part of the map it has never been to
        if (cfg.exploreReward > 0f) {
            val rx = (x[i] / width * REGIONS).toInt().coerceIn(0, REGIONS - 1)
            val ry = (y[i] / height * REGIONS).toInt().coerceIn(0, REGIONS - 1)
            val bit = 1L shl (ry * REGIONS + rx)
            if (visited[i] and bit == 0L) {
                if (visited[i] != 0L) rwExplore[i] += cfg.exploreReward
                visited[i] = visited[i] or bit
            }
        }
        // resting while hungry costs a little, so searching for food pays more than standing still
        val restCost = if (lastAction[i].toInt() == A_REST && en[i] < d.maxEnergy * cfg.restPenaltyBelow) -cfg.hungryRestPenalty else 0f
        // fix 19: the brain learns from the reward as its family's motivations weigh it (all 1 without evolution);
        // the exam and the stats use the plain reward, so scores stay comparable while motivations change
        val mo = b.learner(sp[i].toInt(), family[i].toInt()).genes.motive
        val plain = hurt + starving + restCost + rwFood[i] + rwMate[i] + rwKids[i] + rwExplore[i]
        val reward = mo[M_HURT] * hurt + mo[M_HUNGER] * (starving + restCost) + mo[M_FOOD] * rwFood[i] + mo[M_MATE] * rwMate[i] +
            mo[M_KIDS] * rwKids[i] + mo[M_EXPLORE] * rwExplore[i]
        rwFood[i] = 0f; rwMate[i] = 0f; rwKids[i] = 0f; rwExplore[i] = 0f; prevEn[i] = en[i]; prevHp[i] = hp[i]
        rewardSum[sp[i].toInt()] += plain; decisionCount[sp[i].toInt()]++
        val a = b.act(i, uid[i], sp[i].toInt(), family[i].toInt(), obs, amask, reward)
        lastAction[i] = a.toByte()
        actRecent[sp[i].toInt()][a] += 1f
        if (cfg.explainEvery > 0 && ++explainCount % cfg.explainEvery == 0L) {
            brainStats.record(b.learner(sp[i].toInt(), family[i].toInt()), obs, amask, a)
        }
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

    private var alliesNear = 0
    private var othersNear = 0

    /** Counts creatures of its own species and of other species within [r] cells. */
    private fun countAround(i: Int, r: Float) {
        alliesNear = 0; othersNear = 0
        val r2 = r * r
        val x0 = max(0, ((x[i] - r) / CELL).toInt()); val x1 = min(gw - 1, ((x[i] + r) / CELL).toInt())
        val y0 = max(0, ((y[i] - r) / CELL).toInt()); val y1 = min(gh - 1, ((y[i] + r) / CELL).toInt())
        for (cy in y0..y1) for (cx in x0..x1) {
            var j = gHead[cy * gw + cx]
            while (j >= 0) {
                if (j != i && alive[j]) {
                    val dx = x[j] - x[i]; val dy = y[j] - y[i]
                    if (dx * dx + dy * dy <= r2) { if (sp[j] == sp[i]) alliesNear++ else othersNear++ }
                }
                j = gNext[j]
            }
        }
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
            else if (d.plantEfficiency > 0f && !full[i]) nearestPlant(i) else -1
        val smell = plantSmell(d)
        if (sPlant >= 0) { o[9] = 1f; o[10] = closeness(plantX[sPlant] - x[i], plantY[sPlant] - y[i], smell) }
        // meat
        sMeat = if ((state == S_TOMEAT || state == S_EATMEAT) && tgt[i] >= 0 && meatAmt[tgt[i]] > 0f) tgt[i]
            else if (d.meatEfficiency > 0f && !full[i]) nearestMeat(i) else -1
        if (sMeat >= 0) { o[11] = 1f; o[12] = closeness(meatX[sMeat] - x[i], meatY[sMeat] - y[i], d.smell * 1.5f) }
        // predator
        // every species can sense a threat and flee (Alpha too: from a group hunting it)
        sThreat = senseNearest(i, MODE_THREAT)
        if (sThreat >= 0) {
            o[13] = 1f; o[14] = closeness(x[sThreat] - x[i], y[sThreat] - y[i], max(d.vision, d.hear))
            o[15] = if (hurtT[i] > 0) 1f else 0f
        }
        // prey
        sPrey = if ((state == S_HUNT || state == S_ATTACK) && tgt[i] >= 0 && alive[tgt[i]] && uid[tgt[i]] == tgtUid[i]) tgt[i]
            else if ((d.carnivore || cfg.allCanHunt) && huntCd[i] == 0) senseNearest(i, MODE_PREY) else -1
        if (sPrey >= 0) {
            o[16] = 1f; o[17] = closeness(x[sPrey] - x[i], y[sPrey] - y[i], d.vision)
            // how strong the prey is compared with itself (1 = twice as strong or more)
            o[18] = min(1f, defs[sp[sPrey].toInt()].strength / max(0.1f, d.strength * 2f))
        }
        // mate (males look, females choose when asked)
        sMate = if (state == S_MATE && tgt[i] >= 0 && alive[tgt[i]] && uid[tgt[i]] == tgtUid[i]) tgt[i]
            else if (!female[i] && isMature(i) && mateCd[i] == 0 && (cfg.matingInShelter || !inAnyShelter(x[i], y[i], 0f))) senseNearest(i, MODE_MATE) else -1
        if (sMate >= 0) { o[19] = 1f; o[20] = closeness(x[sMate] - x[i], y[sMate] - y[i], d.call) }
        // shelter
        // shelters send a signal their own species can pick up from [SimConfig.shelterSignal] cells away
        sShelter = if (state == S_SHELTER && tgt[i] >= 0) tgt[i] else nearestOwnShelter(i, cfg.shelterSignal)
        if (sShelter >= 0) {
            o[21] = 1f
            o[22] = closeness(shX[sShelter] - x[i], shY[sShelter] - y[i], cfg.shelterSignal)
        }
        // mother
        val m = motherSlot[i]
        val hasMother = !isMature(i) && m >= 0 && alive[m] && uid[m] == motherUid[i]
        if (hasMother) o[23] = closeness(x[m] - x[i], y[m] - y[i], 20f)
        o[24] = if (foreignShelterAt(sp[i].toInt(), x[i], y[i]) >= 0) 1f else 0f
        o[25] = foreignShelterNear(i)
        // fix 5: how rich the area is in ripe plants (twice the smell range)
        // how much food it senses within the forage range, and where it is (for the "forage" choice)
        // always measured (a full creature used to read the previous creature's number here)
        val canForage = forageGoal(i) && !full[i]
        o[26] = min(1f, foodNear / 8f)
        // fix 2: how long it has been in its shelter, and whether it must stay out
        o[27] = if (cfg.shelterMaxStay > 0) min(1f, shelterT[i].toFloat() / cfg.shelterMaxStay) else 0f
        o[28] = if (shelterCd[i] > 0) 1f else 0f
        // fix 6: how much of the map it has seen
        o[29] = java.lang.Long.bitCount(visited[i]) / 64f
        // it can see how healthy the animals it senses are: a weak big one can be brought down by a group
        if (sPrey >= 0) o[30] = hp[sPrey] / max(1f, maxHp(sPrey))
        if (sThreat >= 0) o[31] = hp[sThreat] / max(1f, maxHp(sThreat))
        // how many of its own kind, and of other species, are around it (hearing range)
        countAround(i, max(d.hear, 6f))
        o[32] = min(1f, alliesNear / 8f)
        o[33] = min(1f, othersNear / 8f)

        amask[A_EXPLORE] = true
        amask[A_EAT_PLANT] = sPlant >= 0
        amask[A_EAT_MEAT] = sMeat >= 0
        amask[A_HUNT] = sPrey >= 0
        amask[A_FLEE] = sThreat >= 0
        amask[A_SHELTER] = sShelter >= 0
        amask[A_MATE] = sMate >= 0
        amask[A_FOLLOW] = hasMother
        amask[A_REST] = true
        amask[A_FORAGE] = canForage
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
        S_FORAGE -> A_FORAGE
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
            A_SHELTER -> if (!(state == S_SHELTER && tgt[i] == sShelter)) goShelter(i, sShelter, 100000)
            A_MATE -> if (!(state == S_MATE && tgt[i] == sMate)) setState(i, S_MATE, sMate)
            A_FOLLOW -> setState(i, S_FOLLOW, motherSlot[i])
            A_REST -> { releaseTarget(i); st[i] = S_IDLE.toByte(); timer[i] = decideEvery }
            A_FORAGE -> { releaseTarget(i); st[i] = S_FORAGE.toByte() }
        }
    }

    // =====================================================================
    // restart: a brand-new world and brand-new random brains
    // =====================================================================

    /** Throws everything away and starts again: new map, new creatures, new random brain weights. */
    fun restart(newSeed: Long) {
        rng.state = newSeed
        tick = 0L; nextUid = 1; maxGeneration = 0
        java.util.Arrays.fill(alive, false)
        freeTop = 0
        for (i in cap - 1 downTo 0) freeSlots[freeTop++] = i
        java.util.Arrays.fill(count, 0); aliveCount = 0
        java.util.Arrays.fill(meatAmt, 0f); meatPtr = 0
        fxN = 0
        java.util.Arrays.fill(births, 0); java.util.Arrays.fill(matingCount, 0)
        for (d in deaths) java.util.Arrays.fill(d, 0)
        java.util.Arrays.fill(extinct, false)
        rescues = 0; topUps = 0; brainResets = 0; familyCopies = 0; totalKills = 0; totalBirths = 0; totalDeaths = 0
        java.util.Arrays.fill(rescueN, 0); java.util.Arrays.fill(lastReset, -1_000_000L)
        java.util.Arrays.fill(lastImmigrants, -1_000_000L)
        for (s in 0 until NSP) for (c in geneCurves[s]) c.clear()
        geneCurveVersion++
        java.util.Arrays.fill(litTag, 0); litPtr = 0
        for (s in 0 until NSP) {
            popCurve[s].clear(); java.util.Arrays.fill(deathTotals[s], 0L); java.util.Arrays.fill(actRecent[s], 0f)
            for (c in deathCurves[s]) c.clear()
            wipeouts[s].clear()
        }
        java.util.Arrays.fill(litterOf, -1)
        java.util.Arrays.fill(hofCount, 0)
        histN = 0; histHead = 0
        nOrder = 0
        nSh = 0
        placeShelters()
        placePlants()
        val dir = brain?.logDir
        brain?.close()
        brain = makeBrain(newSeed)
        brainStats = BrainStats(cfg.brainSize, cfg.brainLayers)
        if (dir != null) brain?.logTo(dir)
        spawnInitial()
        history.runInfo("restart seed=$newSeed ${cfg.describe()}")
        history.event(0, "restart", 0, 0, 0, 0f, 0f, "new world and random brains, seed $newSeed")
        history.flush()
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
        shelterT[i] = 0; shelterCd[i] = 0; visited[i] = 0L; litterOf[i] = -1; family[i] = 0; full[i] = false
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
        val sflags = ByteArray(n); val sfam = ByteArray(n)
        var k = 0
        var selSlot = -1
        for (i in 0 until cap) {
            if (!alive[i]) continue
            if (k >= n) break
            suid[k] = uid[i]; ssp[k] = sp[i]; sfem[k] = female[i]; sfam[k] = family[i]
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
        ).also {
            it.brainLabel = brain?.label() ?: "instincts"
            it.family = sfam
            it.shelterX = shX.copyOf(nSh); it.shelterY = shY.copyOf(nSh)
            it.rescues = rescues; it.topUps = topUps; it.brainResets = brainResets; it.familyCopies = familyCopies
            it.actions = Array(NSP) { s -> actRecent[s].copyOf() }
            it.deathTotals = Array(NSP) { s -> deathTotals[s].copyOf() }
            if (popCurveSeen != tick / cfg.logEveryTicks || cachedPop == null) {
                cachedPop = Array(NSP) { s -> popCurve[s].copy() }
                cachedDeaths = Array(NSP) { s -> Array(NCAUSE) { c -> deathCurves[s][c].copy() } }
                popCurveSeen = tick / cfg.logEveryTicks
            }
            it.popCurve = cachedPop
            if (cachedStats == null || statsSeen != tick / cfg.logEveryTicks) { cachedStats = brainStats.copy(); statsSeen = tick / cfg.logEveryTicks }
            it.brainStats = cachedStats
            it.deathCurves = cachedDeaths
            it.wipeouts = Array(NSP) { s -> wipeouts[s].toLongArray() }
            if (cachedGenes == null || genesSeen != geneCurveVersion) {
                cachedGenes = Array(NSP) { s -> Array(NGC) { k -> geneCurves[s][k].copy() } }
                genesSeen = geneCurveVersion
            }
            it.geneCurves = cachedGenes
            val b = brain
            if (b != null) {
                val total = b.learners.sumOf { l -> l.updates } + 100000 * b.examCurves.sumOf { c -> c.n }
                if (total != curvesSeen || cachedCurves == null) {
                    cachedCurves = Array(NSP) { s -> b.curves[s].copy() }
                    cachedTrends = Array(NSP) { s -> Trend.of(b.curves[s]) }
                    synchronized(b) {
                        cachedExams = Array(NSP) { s -> b.examCurves[s].copy() }
                    }
                    cachedExamTrends = Array(NSP) { s -> Trend.ofExam(cachedExams!![s]) }
                    curvesSeen = total
                }
                it.curves = cachedCurves
                it.trends = cachedTrends
                it.exams = cachedExams
                it.examTrends = cachedExamTrends
                it.entropyCoef = FloatArray(NSP) { s -> b.learner(s, 0).entCoef }
                it.acceptRate = FloatArray(NSP) { s -> b.mateChoice.prob(s, AVG_MALE) }
                it.babiesGrowUp = b.mateChoice.avgSurvive.copyOf()
                it.updates = IntArray(NSP) { s -> (0 until cfg.brainFamilies[s]).sumOf { f -> b.learner(s, f).updates } }
                it.familyGenes = Array(NSP) { s -> Array(cfg.brainFamilies[s]) { f -> b.learner(s, f).genes.copy() } }
                it.familyMembers = Array(NSP) { s ->
                    val m = IntArray(cfg.brainFamilies[s])
                    for (n in 0 until nOrder) { val i = order[n]; if (alive[i] && sp[i].toInt() == s) m[family[i].toInt().coerceIn(0, m.size - 1)]++ }
                    m
                }
            }
        }
    }

    private var curvesSeen = -1
    /** Changes whenever [geneCurves] get a point (so the snapshot copies them only then). */
    var geneCurveVersion = 0
    private var genesSeen = -1
    private var cachedGenes: Array<Array<LearnCurve>>? = null
    private var popCurveSeen = -1L
    private var cachedPop: Array<LearnCurve>? = null
    private var cachedStats: BrainStats? = null
    private var statsSeen = -1L
    private var cachedDeaths: Array<Array<LearnCurve>>? = null
    private var cachedCurves: Array<LearnCurve>? = null
    private var cachedTrends: Array<Trend>? = null
    private var cachedExams: Array<LearnCurve>? = null
    private var cachedExamTrends: Array<Trend>? = null

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
            d.vision * gene(i, G_VIS), d.visionHalfAngle, d.hear, plantSmell(d), tr, tn, label,
        ).also {
            it.family = family[i].toInt(); it.families = cfg.brainFamilies[sp[i].toInt()]
            // what its brain sees and why it chose what it chose (only for the creature being looked at)
            val b = brain
            if (b != null && b.learning && alive[i]) {
                observe(i)
                it.brain = BrainProbe.view(b.learner(sp[i].toInt(), family[i].toInt()), obs, amask, lastAction[i].toInt())
            }
        }
    }
}
