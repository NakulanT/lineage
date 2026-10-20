package lineage.core

const val P_INT = 0
const val P_FLOAT = 1
const val P_BOOL = 2

/**
 * One setting the new-version screen can change. [key] is the settings.txt key; species settings use "sp.<name>".
 * [perSpecies] settings have three values (Alpha, Beta, Gamma).
 */
class ParamDef(
    val key: String,
    val label: String,
    val help: String,
    val kind: Int,
    val min: Float,
    val max: Float,
    val step: Float,
    val perSpecies: Boolean = false,
)

class ParamGroup(val name: String, val params: List<ParamDef>)

/** Every setting a version can change, in groups. The screen and the docs use this one list. */
object Params {
    private fun i(k: String, l: String, h: String, lo: Number, hi: Number, st: Number, ps: Boolean = false) =
        ParamDef(k, l, h, P_INT, lo.toFloat(), hi.toFloat(), st.toFloat(), ps)
    private fun f(k: String, l: String, h: String, lo: Number, hi: Number, st: Number, ps: Boolean = false) =
        ParamDef(k, l, h, P_FLOAT, lo.toFloat(), hi.toFloat(), st.toFloat(), ps)
    private fun b(k: String, l: String, h: String) = ParamDef(k, l, h, P_BOOL, 0f, 1f, 1f)

    val groups: List<ParamGroup> = listOf(
        ParamGroup("World", listOf(
            i("startCounts", "Creatures at the start", "How many of each species the world starts with.", 0, 5000, 5, true),
            i("hardCap", "Population cap (all)", "No more babies once this many creatures are alive in total.", 50, 5000, 50),
            i("speciesCap", "Population cap per species", "Most of each species. 0 = only the total cap.", 0, 5000, 25, true),
            i("width", "Map width (cells)", "", 32, 512, 16),
            i("height", "Map height (cells)", "", 32, 512, 16),
            i("plants", "Plants", "Number of plants on the map (one kind).", 10, 10000, 50),
            i("plantRegrowTicks", "Plant regrowth (steps)", "Steps for an eaten plant to be ripe again.", 50, 20000, 100),
            f("plantEnergy", "Energy in one plant", "", 1, 500, 5),
            i("shelters", "Shelters", "How many shelters each species has.", 0, 100, 1, true),
            f("mutation", "Gene mutation", "Size of the random change when a gene is passed to a baby.", 0, 0.5, 0.01),
            f("meatShare", "Meat from a body (share of its energy)", "Only killed or injured bodies leave meat, as much as the energy they had left.", 0, 2, 0.05),
            f("satietyLevel", "Can eat again below (share of energy)", "After eating to full, it waits until its energy is below this.", 0.05, 1, 0.05),
            b("allCanHunt", "Every species can attack the others", "Off: only Alpha hunts."),
            b("rescueEnabled", "Rescue a species that dies out", "Adds new creatures from its best genes."),
            i("rescueCount", "Creatures added by a rescue", "", 1, 100, 1),
            i("minPopulation", "Minimum population", "Topped up from the best genes when below this. 0 = off.", 0, 500, 1, true),
        )),
        ParamGroup("Reward", listOf(
            f("foodRewardScale", "Food reward x", "Reward for eating = energy gained / max energy, times this.", 0, 10, 0.1),
            f("matingReward", "Mating reward", "To each parent when a mating gives babies.", 0, 50, 0.5),
            f("deathPenalty", "Death penalty", "Lost when it dies (not old age after having babies).", 0, 50, 0.5),
            f("hurtPenaltyScale", "Hurt penalty x", "Penalty = health lost / max health, times this.", 0, 10, 0.1),
            f("hungryLevel", "Very hungry below (share of energy)", "", 0, 1, 0.05),
            f("hungerPenalty", "Hunger penalty per decision", "While very hungry, outside its shelter.", 0, 1, 0.005),
            f("shelterHungerPenalty", "Hunger penalty in shelter", "While very hungry, inside its own shelter (fix 4).", 0, 1, 0.005),
            f("exploreReward", "Exploring reward", "For reaching a part of the map it never visited (fix 6).", 0, 2, 0.01),
            f("hungryRestPenalty", "Resting while hungry penalty", "Per decision of resting below the energy level below.", 0, 1, 0.005),
            f("restPenaltyBelow", "...when energy is below (share)", "", 0, 1, 0.05),
        )),
        ParamGroup("Shelters", listOf(
            f("sp.shelterR", "Shelter radius (cells)", "", 0.5, 20, 0.2, true),
            i("shelterCapacity", "Shelter room", "Creatures one shelter holds. 0 = no limit (fix 3).", 0, 100, 1, true),
            i("shelterMaxStay", "Longest stay (steps)", "Then it must leave. 0 = no limit (fix 2).", 0, 100000, 50),
            i("shelterCooldown", "Must stay out (steps)", "After being sent out (fix 2).", 0, 100000, 50),
            f("shelterHeal", "Healing in shelter (share per step)", "", 0, 0.05, 0.0005),
            b("shelterHealsStarving", "Heals even at 0 energy", "On = the v1 trap (fix 1 off)."),
            f("foreignShelterDamage", "Damage in a stranger's shelter", "Share of max health lost per step (0.003 = about 30 s to die).", 0, 1, 0.001),
            f("shelterSpeed", "Shelters move (cells/s)", "They drift around the map and never overlap. 0 = they stay put.", 0, 5, 0.01),
            f("shelterSignal", "Shelter signal range (cells)", "How far its own species can find it from.", 2, 200, 2),
            b("matingInShelter", "Mating allowed inside shelters", "Off: shelters are only for protection."),
        )),
        ParamGroup("Body", listOf(
            f("sp.speed", "Speed (cells/s)", "", 0.2, 10, 0.1, true),
            f("sp.health", "Health", "", 5, 2000, 5, true),
            i("sp.lifespanDays", "Lifespan (days)", "", 5, 2000, 5, true),
            f("sp.maxEnergy", "Max energy", "", 10, 2000, 10, true),
            f("sp.hunger", "Hunger (energy per step)", "", 0.001, 1, 0.005, true),
            f("starveDamage", "Starving damage per step", "Health lost per step at 0 energy.", 0, 10, 0.05),
            f("sp.strength", "Bite damage", "", 0, 200, 1, true),
            f("sp.attackRange", "Bite reach (cells)", "", 0.2, 10, 0.1, true),
            i("sp.attackCooldown", "Steps between bites", "", 1, 200, 1, true),
            f("sp.plantEfficiency", "Plant food value x", "", 0, 5, 0.05, true),
            f("sp.meatEfficiency", "Meat food value x", "", 0, 5, 0.05, true),
        )),
        ParamGroup("Senses", listOf(
            f("sp.vision", "Sight range (cells)", "", 0, 60, 0.5, true),
            f("sp.visionAngle", "Sight cone (degrees)", "", 10, 360, 5, true),
            f("sp.hear", "Hearing (cells)", "", 0, 60, 0.5, true),
            f("sp.smell", "Smell (cells)", "", 0, 60, 0.5, true),
            f("smellScale", "Plant smell x", "Plants are smelled this many times farther (fix 5).", 0.1, 5, 0.1),
            f("forageRange", "Forage range (cells)", "The \"forage\" choice walks toward food sensed this far away.", 2, 200, 1),
            f("sp.call", "Mating call (cells)", "", 0, 200, 1, true),
        )),
        ParamGroup("Breeding", listOf(
            i("sp.maturityDays", "Adult after (days)", "", 1, 1000, 1, true),
            i("sp.femaleCooldownDays", "Female waits after birth (days)", "", 0, 1000, 1, true),
            i("sp.litterMin", "Babies per mating, fewest", "", 1, 200, 1, true),
            i("sp.litterMax", "Babies per mating, most", "", 1, 200, 1, true),
            i("sp.infantDays", "Baby for (days)", "", 0, 200, 1, true),
            f("mateEnergyNeeded", "Energy a female needs to mate", "Share of max energy.", 0, 1, 0.05),
            f("litterCost", "Litter cost, mother", "Share of her max energy.", 0, 1, 0.05),
            f("fatherCost", "Litter cost, father", "Share of his max energy.", 0, 1, 0.05),
            f("babyEnergy", "Newborn energy", "Share of max energy (fix 13).", 0.05, 1, 0.05),
            f("motherFeed", "Food a mother gives", "Energy per feeding (fix 13).", 0, 200, 1),
            b("learnedMateChoice", "Females learn mate choice", "Off = fixed rule (fix 14)."),
        )),
        ParamGroup("Learning", listOf(
            b("brain", "Learning brains", "Off = fixed instincts, no learning."),
            i("brainSize", "Brain size (units per layer)", "More units = more weights; slower.", 4, 512, 8),
            i("brainLayers", "Brain layers", "Hidden layers per network (1 to 4).", 1, 4, 1),
            i("decideEvery", "Steps between decisions", "", 1, 100, 1),
            f("learningRate", "Learning rate", "", 0.00001, 0.01, 0.00005),
            f("gamma", "Look-ahead (gamma)", "0.995 is about 200 decisions.", 0.5, 0.9999, 0.001),
            f("gaeLambda", "GAE lambda", "", 0, 1, 0.01),
            f("clip", "PPO clip", "", 0.01, 1, 0.01),
            i("epochs", "Passes per update", "", 1, 20, 1),
            i("minibatch", "Minibatch size", "", 8, 4096, 32),
            i("learnBatch", "Decisions per update", "Small species can learn more often (fix 9).", 64, 20000, 128, true),
            f("entropyBonus", "Curiosity bonus (entropy)", "", 0, 1, 0.005),
            b("adaptiveEntropy", "Raise curiosity when locked in", "Fix 7."),
            f("entropyLow", "Locked in below (entropy)", "", 0, 2.2, 0.05),
            f("entropyHigh", "Curious again above (entropy)", "", 0, 2.2, 0.05),
            f("entropyMax", "Highest curiosity bonus", "", 0, 2, 0.01),
            i("pretrainTicks", "Copy instincts first (steps)", "0 = start from random brains (fix 8).", 0, 1000000, 1000),
            i("brainFamilies", "Brain families", "1 = one brain per species (fix 15).", 1, 8, 1, true),
            f("brainMutation", "Brain copy change", "Random change when a family is copied.", 0, 1, 0.005),
            i("familyCheckEvery", "Family check every (steps)", "", 100, 100000, 500),
            i("familyCopyMinMembers", "Copy only families with at least", "", 2, 1000, 1),
            i("brainResetRescues", "Reset brain after rescues", "0 = never (fix 10).", 0, 1000, 1),
            i("brainResetWindow", "...within (steps)", "", 1000, 1000000, 5000),
            i("examEvery", "Exam every (steps)", "0 = no exams.", 0, 1000000, 1000),
            i("examSteps", "Exam length (steps)", "", 100, 100000, 500),
        )),
    )

    val all: List<ParamDef> get() = groups.flatMap { it.params }

    /** Current value(s) of a setting as numbers (one per species for per-species settings). */
    fun values(cfg: SimConfig, p: ParamDef): FloatArray {
        val raw = cfg.toProps()[p.key] ?: return FloatArray(if (p.perSpecies) NSP else 1)
        return raw.split(",").map { v -> when (v.trim()) { "true" -> 1f; "false" -> 0f; else -> v.trim().toFloatOrNull() ?: 0f } }.toFloatArray()
    }

    /** A copy of [cfg] with value number [index] of setting [p] set to [v] (kept inside its range). */
    fun set(cfg: SimConfig, p: ParamDef, index: Int, v: Float): SimConfig {
        val vals = values(cfg, p)
        val c = v.coerceIn(p.min, p.max)
        vals[index.coerceIn(0, vals.size - 1)] = if (p.kind == P_INT) Math.round(c).toFloat() else c
        val text = when (p.kind) {
            P_BOOL -> if (vals[0] > 0.5f) "true" else "false"
            P_INT -> vals.joinToString(",") { Math.round(it).toString() }
            else -> vals.joinToString(",") { SimConfig.fmt(it) }
        }
        return cfg.with(mapOf(p.key to text))
    }

    fun format(p: ParamDef, v: Float): String = when (p.kind) {
        P_BOOL -> if (v > 0.5f) "on" else "off"
        P_INT -> Math.round(v).toString()
        else -> {
            val a = kotlin.math.abs(v)
            when {
                a == 0f -> "0"
                a < 0.001f -> "%.5f".format(v).trimEnd('0')
                a < 0.1f -> "%.4f".format(v).trimEnd('0').trimEnd('.')
                a < 10f -> "%.3f".format(v).trimEnd('0').trimEnd('.')
                else -> "%.1f".format(v).trimEnd('0').trimEnd('.')
            }
        }
    }

    /** Settings that differ from the defaults, as "label: value" lines (for a version's summary). */
    fun changes(cfg: SimConfig): List<String> {
        val d = SimConfig()
        val out = ArrayList<String>()
        for (p in all) {
            if (p.key == "seed") continue
            val a = values(cfg, p); val b = values(d, p)
            if (a.size != b.size || a.indices.any { kotlin.math.abs(a[it] - b[it]) > 1e-7f }) {
                out.add("${p.label}: " + if (p.perSpecies) a.joinToString(" / ") { format(p, it) } else format(p, a[0]))
            }
        }
        return out
    }
}
