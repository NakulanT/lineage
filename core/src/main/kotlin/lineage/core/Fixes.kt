package lineage.core

/**
 * The fixes, each as a switch: the settings it uses when on, and the version 1 settings when off.
 * The new-version screen and the command line (--fixes-off 1,4) both use this list.
 */
class Fix(val num: Int, val title: String, val on: Map<String, String>, val off: Map<String, String>) {
    /** On when the settings are not the version 1 values (so a version with older or tuned numbers still counts as on). */
    fun isOn(cfg: SimConfig): Boolean {
        val p = cfg.toProps()
        return off.any { (k, v) -> !same(p[k], v) }
    }

    private fun same(a: String?, b: String): Boolean {
        if (a == null) return false
        if (a == b) return true
        val x = a.toFloatOrNull(); val y = b.toFloatOrNull()
        return x != null && y != null && x == y
    }

    fun apply(cfg: SimConfig, enabled: Boolean): SimConfig = cfg.with(if (enabled) on else off)
}

object Fixes {
    private val d = SimConfig()
    private fun v(k: String) = d.toProps()[k]!!

    val all: List<Fix> = listOf(
        Fix(1, "Shelter does not heal a starving creature", mapOf("shelterHealsStarving" to "false"), mapOf("shelterHealsStarving" to "true")),
        Fix(2, "Shelter stays have a time limit", mapOf("shelterMaxStay" to v("shelterMaxStay")), mapOf("shelterMaxStay" to "0")),
        Fix(3, "Shelters have limited room", mapOf("shelterCapacity" to v("shelterCapacity")), mapOf("shelterCapacity" to "0,0,0")),
        Fix(4, "Hunger costs more inside a shelter", mapOf("shelterHungerPenalty" to v("shelterHungerPenalty")), mapOf("shelterHungerPenalty" to "0.02")),
        Fix(5, "Plants are smelled farther", mapOf("smellScale" to v("smellScale")), mapOf("smellScale" to "1.0")),
        Fix(6, "Reward for exploring new places", mapOf("exploreReward" to v("exploreReward")), mapOf("exploreReward" to "0.0")),
        Fix(7, "More curiosity when a brain locks in", mapOf("adaptiveEntropy" to "true"), mapOf("adaptiveEntropy" to "false")),
        Fix(8, "Copy instincts first (not random)", mapOf("pretrainTicks" to "3000"), mapOf("pretrainTicks" to "0")),
        Fix(9, "Minimum population, small species learn faster",
            mapOf("minPopulation" to v("minPopulation"), "learnBatch" to v("learnBatch")),
            mapOf("minPopulation" to "0,0,0", "learnBatch" to "2048,2048,2048")),
        Fix(10, "Reset a brain that keeps dying out", mapOf("brainResetRescues" to v("brainResetRescues")), mapOf("brainResetRescues" to "0")),
        Fix(11, "More food: more plants, faster regrowth", mapOf("plants" to v("plants"), "plantRegrowTicks" to v("plantRegrowTicks")),
            mapOf("plants" to "400", "plantRegrowTicks" to "1700")),
        Fix(12, "Lower cap, and room for each species", mapOf("hardCap" to v("hardCap"), "speciesCap" to v("speciesCap")),
            mapOf("hardCap" to "1500", "speciesCap" to "0,0,0")),
        Fix(13, "Healthier babies", mapOf("babyEnergy" to v("babyEnergy"), "motherFeed" to v("motherFeed")),
            mapOf("babyEnergy" to "0.3", "motherFeed" to "12.0")),
        Fix(14, "Females learn mate choice", mapOf("learnedMateChoice" to "true"), mapOf("learnedMateChoice" to "false")),
        Fix(15, "Brain families that evolve", mapOf("brainFamilies" to v("brainFamilies")), mapOf("brainFamilies" to "1,1,1")),
        Fix(16, "Reward babies that grow up, not the mating", mapOf("matingReward" to v("matingReward"), "grownUpReward" to v("grownUpReward")),
            mapOf("matingReward" to "5.0", "grownUpReward" to "0.0")),
        Fix(17, "Bigger litters cost more, mothers keep a reserve",
            mapOf("litterCostBySize" to "true", "litterReserve" to v("litterReserve"), "mateEnergyNeeded" to v("mateEnergyNeeded")),
            mapOf("litterCostBySize" to "false", "litterReserve" to "0.0", "mateEnergyNeeded" to "0.6")),
        Fix(18, "Immigrants instead of rescues and brain resets", mapOf("immigration" to "true"), mapOf("immigration" to "false")),
        Fix(19, "Brains evolve: size, learning, motivations",
            mapOf("evolveBrains" to "true", "familyReplaceShare" to v("familyReplaceShare"), "brainEnergyCost" to v("brainEnergyCost")),
            mapOf("evolveBrains" to "false", "familyReplaceShare" to "0.0", "brainEnergyCost" to "0.0")),
    )

    /** Short text of what is on, e.g. "fixes 1-7, 9-15". */
    fun summary(cfg: SimConfig): String {
        val on = all.filter { it.isOn(cfg) }.map { it.num }
        if (on.isEmpty()) return "no fixes (like v1)"
        val parts = ArrayList<String>()
        var i = 0
        while (i < on.size) {
            var j = i
            while (j + 1 < on.size && on[j + 1] == on[j] + 1) j++
            parts.add(if (j > i) "${on[i]}-${on[j]}" else "${on[i]}")
            i = j + 1
        }
        return "fixes " + parts.joinToString(", ")
    }

    /** Settings with the listed fixes switched off. */
    fun without(cfg: SimConfig, nums: Collection<Int>): SimConfig {
        var c = cfg
        for (f in all) if (f.num in nums) c = f.apply(c, false)
        return c
    }
}
