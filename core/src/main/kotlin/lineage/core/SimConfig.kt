package lineage.core

/**
 * Everything about a run that can be changed without touching the code.
 * Defaults are version 2: 5 Alpha, 20 Beta, 100 Gamma on a 128 x 128 map, learning brains, and all the fixes on.
 * Every value can be saved to and read from a small text file (settings.txt in each version folder).
 */
class SimConfig(
    val seed: Long = 1L,
    val width: Int = 128,
    val height: Int = 128,
    val startCounts: IntArray = intArrayOf(5, 20, 100),
    /** Fix 12: lower cap (was 1500) so food can keep up with breeding. */
    val hardCap: Int = 900,
    /** Most creatures of each species (0 = only the total cap). Stops one species taking all the room. */
    val speciesCap: IntArray = intArrayOf(100, 300, 500),
    val plants: Int = 400,
    /** Fix 11: plants grow back faster (was 1700 steps), more food without more plants on the map. */
    val plantRegrowTicks: Int = 1000,
    val plantEnergy: Float = 50f,
    val shelters: IntArray = intArrayOf(1, 5, 20),
    /** Spread of the random change when a gene is copied to a baby. */
    val mutation: Float = 0.03f,
    /** Share of max health a creature loses every step while inside another species' shelter. */
    val foreignShelterDamage: Float = 0.003f,
    val meatFromAllDeaths: Boolean = true,
    /** Adds a few creatures when a species falls to zero. Every use is logged. */
    val rescueEnabled: Boolean = true,
    val rescueCount: Int = 6,

    // ---- fixes for the shelter trap ----
    /** Fix 1: false = a shelter does not heal a creature with 0 energy, so starving always hurts. */
    val shelterHealsStarving: Boolean = false,
    /** Fix 2: longest stay in a shelter in one go (steps), then it must leave. 0 = no limit. */
    val shelterMaxStay: Int = 300,
    /** Fix 2: steps it must stay out after being sent out. */
    val shelterCooldown: Int = 200,
    /** Fix 3: how many creatures fit in one shelter, per species. 0 = no limit. */
    val shelterCapacity: IntArray = intArrayOf(4, 6, 8),
    /** Fix 4: hunger penalty per decision when very hungry inside its own shelter (outside it is 0.02). */
    val shelterHungerPenalty: Float = 0.04f,

    // ---- fixes that help the brains find food ----
    /** Fix 5: plants are smelled this many times farther than the species' base smell. */
    val smellScale: Float = 1.5f,
    /** Fix 6: reward for entering a part of the map (one of 8 x 8 regions) it has never been in. */
    val exploreReward: Float = 0.05f,

    // ---- fixes for learning ----
    /** Fix 7: raise the curiosity (entropy bonus) when a brain locks into one choice. */
    val adaptiveEntropy: Boolean = true,
    /** Fix 8: steps at the start in which the brains copy the instincts. 0 = start from random brains. */
    val pretrainTicks: Int = 0,
    /** Fix 9: a species below this size is topped up from its best genes, so its brain keeps learning. */
    val minPopulation: IntArray = intArrayOf(3, 6, 12),
    /** Fix 9: decisions collected before each learning update, per species (small species learn more often). */
    val learnBatch: IntArray = intArrayOf(512, 1024, 2048),
    /** Fix 10: this many rescues or top-ups within [brainResetWindow] steps reset that species' brains. 0 = never. */
    val brainResetRescues: Int = 10,
    val brainResetWindow: Int = 30000,

    // ---- world balance ----
    /** Fix 13: energy a newborn starts with, as a share of max energy (was 0.3). */
    val babyEnergy: Float = 0.5f,
    /** Fix 13: energy a mother gives her young each time she feeds it (was 12). */
    val motherFeed: Float = 16f,

    // ---- bigger features ----
    /** Fix 14: females learn which males to accept. false = the fixed rule (health and strength). */
    val learnedMateChoice: Boolean = true,
    /** Fix 15: brain families per species. Babies get their mother's family; empty families are refilled with a changed copy of the best one. */
    val brainFamilies: IntArray = intArrayOf(2, 2, 3),
    /** Fix 15: size of the random change when a brain family is copied. */
    val brainMutation: Float = 0.02f,

    /** Every this many steps, a frozen copy of the brains plays a fixed test world (the exam). 0 = never. */
    val examEvery: Int = 10000,
    /** Steps one exam lasts. */
    val examSteps: Int = 3000,
    /** true: the exam runs inside the simulation step (same result every time); false: on its own thread. */
    val examInline: Boolean = false,

    // ---- history ----
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
    /** Hidden units per brain layer ("brain capacity"). */
    val brainSize: Int = 64,
    /** Hidden layers per brain network. */
    val brainLayers: Int = 2,
    /** One decision in this many is examined for the brain-explain charts (0 = off). */
    val explainEvery: Int = 20,
    /** How far a creature senses food for the "forage" choice (cells). */
    val forageRange: Float = 25f,
    /** Penalty per decision for resting while energy is below [restPenaltyBelow] (search for food instead). */
    val hungryRestPenalty: Float = 0.02f,
    val restPenaltyBelow: Float = 0.5f,

    // ---- reward: what the brains try to get more of ----
    /** Food reward = energy gained / max energy, times this. */
    val foodRewardScale: Float = 1f,
    /** Reward to each parent for a mating that gives babies. */
    val matingReward: Float = 5f,
    /** Reward lost when a creature dies (not for old age after having babies). */
    val deathPenalty: Float = 3f,
    /** Hurt penalty = health lost / max health, times this. */
    val hurtPenaltyScale: Float = 1f,
    /** "Very hungry" means energy below this share of max energy. */
    val hungryLevel: Float = 0.25f,
    /** Penalty per decision while very hungry (outside its shelter). */
    val hungerPenalty: Float = 0.02f,

    // ---- body thresholds ----
    /** Health lost per step at 0 energy. */
    val starveDamage: Float = 0.3f,
    /** Share of max health healed per step resting in its own shelter. */
    val shelterHeal: Float = 0.004f,
    /** A female needs at least this share of max energy to mate. */
    val mateEnergyNeeded: Float = 0.6f,
    /** Share of max energy one litter costs the mother, and the father. */
    val litterCost: Float = 0.5f,
    val fatherCost: Float = 0.1f,

    // ---- learning (PPO) ----
    /** Steps between two decisions of a creature. */
    val decideEvery: Int = 6,
    val learningRate: Float = 3e-4f,
    /** How far ahead the brain looks (0.995 is about 200 decisions). */
    val gamma: Float = 0.995f,
    val gaeLambda: Float = 0.95f,
    val clip: Float = 0.2f,
    /** Curiosity bonus (entropy) and, for fix 7, when it is raised and lowered again. */
    val entropyBonus: Float = 0.03f,
    val entropyLow: Float = 0.4f,
    val entropyHigh: Float = 0.9f,
    val entropyMax: Float = 0.25f,
    val epochs: Int = 4,
    val minibatch: Int = 256,
    /** Fix 15: how often empty brain families are refilled, and how big the best family must be to be copied. */
    val familyCheckEvery: Int = 3000,
    val familyCopyMinMembers: Int = 4,

    // ---- world rules (set by the user) ----
    /** After eating to full, a creature cannot eat again until its energy falls below this share. */
    val satietyLevel: Float = 0.7f,
    /** Every species may attack the other species (false: only Alpha hunts). */
    val allCanHunt: Boolean = true,
    /** Share of the energy left in a killed or injured creature that becomes meat (starving and old age leave none). */
    val meatShare: Float = 1f,
    /** Shelters drift around the map at this speed (cells per second). 0 = they stay put. */
    val shelterSpeed: Float = 0.05f,
    /** How far a shelter's signal reaches: its own species can find it from this far (cells). */
    val shelterSignal: Float = 30f,
    /** false: nobody can mate inside a shelter (shelters are only for protection). */
    val matingInShelter: Boolean = false,

    /** Per-species numbers (body, senses, breeding): key -> value for Alpha, Beta, Gamma. See [SpeciesParams]. */
    val species: Map<String, FloatArray> = SpeciesParams.defaults(),
) {
    /** The species definitions this version uses. */
    val defs: Array<SpeciesDef> by lazy { SpeciesParams.build(species) }

    fun rlParams() = lineage.core.rl.RlParams(
        hidden = brainSize, layers = brainLayers, epochs = epochs, minibatch = minibatch, lr = learningRate, gamma = gamma, lambda = gaeLambda,
        clip = clip, entropy = entropyBonus, adaptiveEntropy = adaptiveEntropy, entropyLow = entropyLow,
        entropyHigh = entropyHigh, entropyMax = entropyMax,
    )

    fun describe(): String =
        "seed=$seed map=${width}x$height start=${startCounts.joinToString("/")} cap=$hardCap plants=$plants " +
            "regrow=$plantRegrowTicks shelters=${shelters.joinToString("/")} mutation=$mutation " +
            "brain=${if (brain) "learning" else "instincts"} rescue=$rescueEnabled"

    /** All settings as key=value pairs (written to settings.txt). */
    fun toProps(): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        m["seed"] = "$seed"; m["width"] = "$width"; m["height"] = "$height"
        m["startCounts"] = startCounts.joinToString(","); m["hardCap"] = "$hardCap"; m["speciesCap"] = speciesCap.joinToString(",")
        m["plants"] = "$plants"; m["plantRegrowTicks"] = "$plantRegrowTicks"; m["plantEnergy"] = "$plantEnergy"
        m["shelters"] = shelters.joinToString(","); m["mutation"] = "$mutation"
        m["foreignShelterDamage"] = "$foreignShelterDamage"; m["meatFromAllDeaths"] = "$meatFromAllDeaths"
        m["rescueEnabled"] = "$rescueEnabled"; m["rescueCount"] = "$rescueCount"
        m["shelterHealsStarving"] = "$shelterHealsStarving"; m["shelterMaxStay"] = "$shelterMaxStay"
        m["shelterCooldown"] = "$shelterCooldown"; m["shelterCapacity"] = shelterCapacity.joinToString(",")
        m["shelterHungerPenalty"] = "$shelterHungerPenalty"; m["smellScale"] = "$smellScale"
        m["exploreReward"] = "$exploreReward"; m["adaptiveEntropy"] = "$adaptiveEntropy"
        m["pretrainTicks"] = "$pretrainTicks"; m["minPopulation"] = minPopulation.joinToString(",")
        m["learnBatch"] = learnBatch.joinToString(","); m["brainResetRescues"] = "$brainResetRescues"
        m["brainResetWindow"] = "$brainResetWindow"; m["babyEnergy"] = "$babyEnergy"; m["motherFeed"] = "$motherFeed"
        m["learnedMateChoice"] = "$learnedMateChoice"; m["brainFamilies"] = brainFamilies.joinToString(",")
        m["brainMutation"] = "$brainMutation"; m["brain"] = "$brain"; m["brainSize"] = "$brainSize"; m["brainLayers"] = "$brainLayers"; m["explainEvery"] = "$explainEvery"
        m["forageRange"] = "$forageRange"; m["hungryRestPenalty"] = "$hungryRestPenalty"; m["restPenaltyBelow"] = "$restPenaltyBelow"
        m["examEvery"] = "$examEvery"; m["examSteps"] = "$examSteps"; m["examInline"] = "$examInline"
        m["foodRewardScale"] = "$foodRewardScale"; m["matingReward"] = "$matingReward"; m["deathPenalty"] = "$deathPenalty"
        m["hurtPenaltyScale"] = "$hurtPenaltyScale"; m["hungryLevel"] = "$hungryLevel"; m["hungerPenalty"] = "$hungerPenalty"
        m["starveDamage"] = "$starveDamage"; m["shelterHeal"] = "$shelterHeal"; m["mateEnergyNeeded"] = "$mateEnergyNeeded"
        m["litterCost"] = "$litterCost"; m["fatherCost"] = "$fatherCost"; m["decideEvery"] = "$decideEvery"
        m["learningRate"] = "$learningRate"; m["gamma"] = "$gamma"; m["gaeLambda"] = "$gaeLambda"; m["clip"] = "$clip"
        m["entropyBonus"] = "$entropyBonus"; m["entropyLow"] = "$entropyLow"; m["entropyHigh"] = "$entropyHigh"
        m["entropyMax"] = "$entropyMax"; m["epochs"] = "$epochs"; m["minibatch"] = "$minibatch"
        m["satietyLevel"] = "$satietyLevel"; m["allCanHunt"] = "$allCanHunt"; m["meatShare"] = "$meatShare"
        m["shelterSpeed"] = "$shelterSpeed"; m["shelterSignal"] = "$shelterSignal"; m["matingInShelter"] = "$matingInShelter"
        m["familyCheckEvery"] = "$familyCheckEvery"; m["familyCopyMinMembers"] = "$familyCopyMinMembers"
        for (k in SpeciesParams.keys) m["sp.$k"] = (species[k] ?: FloatArray(NSP) { SpeciesParams.get(Species.all[it], k) }).joinToString(",") { fmt(it) }
        return m
    }

    fun write(file: java.io.File) {
        file.absoluteFile.parentFile?.mkdirs()
        file.writeText(toProps().entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
    }

    /** A copy with some values changed (keys as in [toProps]). */
    fun with(changes: Map<String, String>): SimConfig = fromProps(toProps().also { it.putAll(changes) })

    companion object {
        /** Short number text: no trailing ".0" for whole numbers. */
        fun fmt(v: Float): String = if (v == Math.round(v).toFloat() && kotlin.math.abs(v) < 1e7f) Math.round(v).toString() else v.toString()

        /** Settings exactly as version 1 had them (all fixes off), for comparing. */
        val VERSION_1: Map<String, String> = mapOf(
            "hardCap" to "1500", "plantRegrowTicks" to "1700", "shelterHealsStarving" to "true", "shelterMaxStay" to "0",
            "shelterCapacity" to "0,0,0", "shelterHungerPenalty" to "0.02", "smellScale" to "1.0", "exploreReward" to "0.0",
            "adaptiveEntropy" to "false", "minPopulation" to "0,0,0", "learnBatch" to "2048,2048,2048",
            "brainResetRescues" to "0", "babyEnergy" to "0.3", "motherFeed" to "12", "learnedMateChoice" to "false",
            "brainFamilies" to "1,1,1", "speciesCap" to "0,0,0", "sp.litterMin" to "5,15,30", "sp.litterMax" to "10,20,50",
        )

        fun fromProps(p: Map<String, String>): SimConfig {
            val d = SimConfig()
            fun ints(k: String, def: IntArray) = p[k]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.size == NSP }?.toIntArray() ?: def
            fun i(k: String, def: Int) = p[k]?.trim()?.toIntOrNull() ?: def
            fun f(k: String, def: Float) = p[k]?.trim()?.toFloatOrNull() ?: def
            fun b(k: String, def: Boolean) = p[k]?.trim()?.toBooleanStrictOrNull() ?: def
            return SimConfig(
                seed = p["seed"]?.trim()?.toLongOrNull() ?: d.seed,
                width = i("width", d.width), height = i("height", d.height),
                startCounts = ints("startCounts", d.startCounts), hardCap = i("hardCap", d.hardCap),
                // a settings file from before per-species caps existed keeps having none
                speciesCap = ints("speciesCap", if (p.containsKey("hardCap")) IntArray(NSP) else d.speciesCap),
                plants = i("plants", d.plants), plantRegrowTicks = i("plantRegrowTicks", d.plantRegrowTicks),
                plantEnergy = f("plantEnergy", d.plantEnergy), shelters = ints("shelters", d.shelters),
                mutation = f("mutation", d.mutation), foreignShelterDamage = f("foreignShelterDamage", d.foreignShelterDamage),
                meatFromAllDeaths = b("meatFromAllDeaths", d.meatFromAllDeaths),
                rescueEnabled = b("rescueEnabled", d.rescueEnabled), rescueCount = i("rescueCount", d.rescueCount),
                shelterHealsStarving = b("shelterHealsStarving", d.shelterHealsStarving),
                shelterMaxStay = i("shelterMaxStay", d.shelterMaxStay), shelterCooldown = i("shelterCooldown", d.shelterCooldown),
                shelterCapacity = ints("shelterCapacity", d.shelterCapacity),
                shelterHungerPenalty = f("shelterHungerPenalty", d.shelterHungerPenalty),
                smellScale = f("smellScale", d.smellScale), exploreReward = f("exploreReward", d.exploreReward),
                adaptiveEntropy = b("adaptiveEntropy", d.adaptiveEntropy), pretrainTicks = i("pretrainTicks", d.pretrainTicks),
                minPopulation = ints("minPopulation", d.minPopulation), learnBatch = ints("learnBatch", d.learnBatch),
                brainResetRescues = i("brainResetRescues", d.brainResetRescues),
                brainResetWindow = i("brainResetWindow", d.brainResetWindow),
                babyEnergy = f("babyEnergy", d.babyEnergy), motherFeed = f("motherFeed", d.motherFeed),
                learnedMateChoice = b("learnedMateChoice", d.learnedMateChoice),
                brainFamilies = ints("brainFamilies", d.brainFamilies).map { it.coerceIn(1, 8) }.toIntArray(),
                brainMutation = f("brainMutation", d.brainMutation),
                brain = b("brain", d.brain), brainSize = i("brainSize", d.brainSize),
                brainLayers = i("brainLayers", d.brainLayers).coerceIn(1, 4),
                explainEvery = i("explainEvery", d.explainEvery).coerceAtLeast(0),
                forageRange = f("forageRange", d.forageRange), hungryRestPenalty = f("hungryRestPenalty", d.hungryRestPenalty),
                restPenaltyBelow = f("restPenaltyBelow", d.restPenaltyBelow),
                examEvery = i("examEvery", d.examEvery), examSteps = i("examSteps", d.examSteps),
                examInline = b("examInline", d.examInline),
                foodRewardScale = f("foodRewardScale", d.foodRewardScale), matingReward = f("matingReward", d.matingReward),
                deathPenalty = f("deathPenalty", d.deathPenalty), hurtPenaltyScale = f("hurtPenaltyScale", d.hurtPenaltyScale),
                hungryLevel = f("hungryLevel", d.hungryLevel), hungerPenalty = f("hungerPenalty", d.hungerPenalty),
                starveDamage = f("starveDamage", d.starveDamage), shelterHeal = f("shelterHeal", d.shelterHeal),
                mateEnergyNeeded = f("mateEnergyNeeded", d.mateEnergyNeeded), litterCost = f("litterCost", d.litterCost),
                fatherCost = f("fatherCost", d.fatherCost), decideEvery = i("decideEvery", d.decideEvery).coerceAtLeast(1),
                learningRate = f("learningRate", d.learningRate), gamma = f("gamma", d.gamma), gaeLambda = f("gaeLambda", d.gaeLambda),
                clip = f("clip", d.clip), entropyBonus = f("entropyBonus", d.entropyBonus), entropyLow = f("entropyLow", d.entropyLow),
                entropyHigh = f("entropyHigh", d.entropyHigh), entropyMax = f("entropyMax", d.entropyMax),
                epochs = i("epochs", d.epochs).coerceAtLeast(1), minibatch = i("minibatch", d.minibatch).coerceAtLeast(8),
                familyCheckEvery = i("familyCheckEvery", d.familyCheckEvery).coerceAtLeast(100),
                familyCopyMinMembers = i("familyCopyMinMembers", d.familyCopyMinMembers),
                satietyLevel = f("satietyLevel", d.satietyLevel), allCanHunt = b("allCanHunt", d.allCanHunt),
                meatShare = f("meatShare", d.meatShare), shelterSpeed = f("shelterSpeed", d.shelterSpeed),
                shelterSignal = f("shelterSignal", d.shelterSignal), matingInShelter = b("matingInShelter", d.matingInShelter),
                species = SpeciesParams.keys.associateWith { k ->
                    val def = d.species[k]!!
                    p["sp.$k"]?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == NSP }?.toFloatArray() ?: def.copyOf()
                },
            )
        }

        fun read(file: java.io.File): SimConfig? {
            if (!file.exists()) return null
            val m = HashMap<String, String>()
            for (line in file.readLines()) {
                val k = line.indexOf('=')
                if (k > 0 && !line.startsWith("#")) m[line.substring(0, k).trim()] = line.substring(k + 1)
            }
            return fromProps(m)
        }
    }
}
