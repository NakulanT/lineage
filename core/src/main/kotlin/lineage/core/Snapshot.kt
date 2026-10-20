package lineage.core

/** Details of the creature the viewer has tapped. */
class SelectedInfo(
    val uid: Int,
    val species: Int,
    val female: Boolean,
    val alive: Boolean,
    val stateName: String,
    val x: Float,
    val y: Float,
    val heading: Float,
    val hp: Float,
    val maxHp: Float,
    val energy: Float,
    val maxEnergy: Float,
    val ageDays: Float,
    val lifespanDays: Float,
    val kills: Int,
    val meals: Int,
    val offspring: Int,
    val matings: Int,
    /** Days since it last mated, or -1 if it never has. */
    val lastMateDaysAgo: Float,
    val distance: Float,
    val generation: Int,
    val vision: Float,
    val visionHalf: Float,
    val hear: Float,
    val smell: Float,
    /** Footprints, newest first, as x,y pairs. */
    val trail: FloatArray,
    val trailN: Int,
    val label: String,
) {
    /** Its brain family (fix 15) and how many families the species has. */
    var family: Int = 0
    var families: Int = 1
    /** Its brain right now: senses, neurons, choice chances and the reasons for its choice. */
    var brain: lineage.core.rl.BrainView? = null
}

const val FX_SLASH = 1
const val FX_BIRTH = 2
const val FX_DEATH = 3

const val F_MOVE = 1
const val F_EAT = 2
const val F_ATTACK = 4
const val F_HURT = 8
const val F_REST = 16

/**
 * A copy of what the viewer needs to draw one frame. The simulation keeps running while the
 * viewer draws from this copy, so the two never fight over the same data.
 */
class RenderSnapshot(
    val tick: Long,
    val n: Int,
    val uid: IntArray,
    val sp: ByteArray,
    val female: BooleanArray,
    val x: FloatArray,
    val y: FloatArray,
    val px: FloatArray,
    val py: FloatArray,
    val h: FloatArray,
    val ph: FloatArray,
    val scale: FloatArray,
    val walk: FloatArray,
    val flags: ByteArray,
    val plantG: FloatArray,
    val meatX: FloatArray,
    val meatY: FloatArray,
    val meatAmt: FloatArray,
    val meatN: Int,
    val counts: IntArray,
    val maxGeneration: Int,
    val hist: Array<IntArray>,
    val histN: Int,
    val fxType: IntArray,
    val fxX: FloatArray,
    val fxY: FloatArray,
    val fxN: Int,
    val selected: SelectedInfo?,
) {
    /** Set by the engine just before publishing. */
    var stepsPerSecond: Float = 0f
    var speed: Int = 1
    var wallNanos: Long = 0L
    /** What drives the creatures right now: instincts, copying instincts, or learning. */
    var brainLabel: String = ""
    var rescues: Int = 0
    var topUps: Int = 0
    var brainResets: Int = 0
    /** Reward and entropy over time per species, and whether each is improving. Null without learning brains. */
    var curves: Array<LearnCurve>? = null
    var trends: Array<Trend>? = null
    /** Exam scores per species (frozen brains in the same test world each time) and their verdict. */
    var exams: Array<LearnCurve>? = null
    var examTrends: Array<Trend>? = null
    var entropyCoef: FloatArray? = null
    /** Chance a female accepts an average healthy male (learned mate choice). */
    var acceptRate: FloatArray? = null
    /** Share of babies that grow up, per species (from learned mate choice). */
    var babiesGrowUp: FloatArray? = null
    /** Learning updates per species, and creatures per brain family. */
    var updates: IntArray? = null
    var familyMembers: Array<IntArray>? = null
    var familyCopies: Int = 0
    /** Recent mix of choices per species (index = action), deaths by cause, population over the whole run. */
    var actions: Array<FloatArray>? = null
    var deathTotals: Array<LongArray>? = null
    var popCurve: Array<LearnCurve>? = null
    /** Deaths per cause over the run ([species][cause], value = deaths per 600 steps), and when each species was wiped out. */
    var deathCurves: Array<Array<LearnCurve>>? = null
    var wipeouts: Array<LongArray>? = null
    /** Species-wide feature attribution and neuron behaviour. */
    var brainStats: lineage.core.rl.BrainStats? = null
    /** Where the shelters are now (they move). */
    var shelterX: FloatArray = FloatArray(0)
    var shelterY: FloatArray = FloatArray(0)
}
