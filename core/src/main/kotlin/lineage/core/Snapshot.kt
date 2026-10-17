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
)

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
}
