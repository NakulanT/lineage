package lineage.core

/**
 * Everything about a run that can be changed without touching the code.
 * Defaults are the current test: 5 Alpha, 20 Beta, 100 Gamma on a 128 x 128 map, with learning brains.
 */
class SimConfig(
    val seed: Long = 1L,
    val width: Int = 128,
    val height: Int = 128,
    val startCounts: IntArray = intArrayOf(5, 20, 100),
    val hardCap: Int = 1500,
    val plants: Int = 400,
    val plantRegrowTicks: Int = 1700,
    val plantEnergy: Float = 50f,
    val shelters: IntArray = intArrayOf(1, 5, 20),
    /** Chance of a gene changing when copied to a baby (spread of the random change). */
    val mutation: Float = 0.03f,
    /** Share of max health a creature loses every step while inside another species' shelter. */
    val foreignShelterDamage: Float = 0.03f,
    val meatFromAllDeaths: Boolean = true,
    /** Adds a few creatures when a species falls to zero. Off by default, every use is logged. */
    val rescueEnabled: Boolean = true,
    val rescueCount: Int = 6,
    // history
    val sampleEveryTicks: Int = 100,
    val logEveryTicks: Int = 600,
    val logKills: Boolean = true,
    val logBirths: Boolean = false,
    val logDeaths: Boolean = false,
    /** Save the life record of creatures with at least this many offspring or kills. 0 = save everyone. */
    val notableMin: Int = 3,
    // ---- learning (reinforcement learning) ----
    /** true: creatures are driven by learning brains (PPO). false: fixed instincts only. */
    val brain: Boolean = true,
    /** Steps at the start in which the brains copy the instincts. 0 = start from random brains. */
    val pretrainTicks: Int = 0,
    /** Hidden units per brain layer ("brain capacity"). */
    val brainSize: Int = 32,
) {
    fun describe(): String =
        "seed=$seed map=${width}x$height start=${startCounts.joinToString("/")} cap=$hardCap plants=$plants " +
            "shelters=${shelters.joinToString("/")} mutation=$mutation brain=${if (brain) "learning" else "instincts"} rescue=$rescueEnabled"
}
