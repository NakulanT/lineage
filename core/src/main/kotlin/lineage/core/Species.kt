package lineage.core

import kotlin.math.cos

const val ALPHA = 0
const val BETA = 1
const val GAMMA = 2
const val NSP = 3

/** One simulation step is 0.1 s at normal speed. A day is 100 steps (10 s). */
const val TPS = 10
const val DAY_TICKS = 100

/** Evolvable trait multipliers carried by every creature. 1.0 is the species average. */
const val NG = 6
const val G_SPEED = 0
const val G_STR = 1
const val G_VIS = 2
const val G_HP = 3
const val G_LIFE = 4
const val G_FERT = 5

/**
 * Starting values for one species. Distances are in grid cells, speeds in cells per second,
 * times in days (1 day = 100 steps), energy in arbitrary units.
 */
class SpeciesDef(
    val id: Int,
    val name: String,
    val creature: String,
    val color: Int,
    val speed: Float,
    val health: Float,
    val lifespanDays: Int,
    val maxEnergy: Float,
    val hunger: Float,
    val strength: Float,
    val attackRange: Float,
    val attackCooldown: Int,
    val vision: Float,
    val visionHalfAngle: Float,
    val hear: Float,
    val smell: Float,
    /** How far a female's mating call carries. Rarer species call farther so mates can still find each other. */
    val call: Float,
    val maturityDays: Int,
    val femaleCooldownDays: Int,
    val litterMin: Int,
    val litterMax: Int,
    val infantDays: Int,
    /** Shelter radius in cells. */
    val shelterR: Float,
    val carnivore: Boolean,
    val plantEfficiency: Float,
    val meatEfficiency: Float,
    val bodyEnergy: Float,
) {
    val cosHalf: Float = cos(visionHalfAngle)
    val lifespanTicks: Int get() = lifespanDays * DAY_TICKS
    val maturityTicks: Int get() = maturityDays * DAY_TICKS
    val infantTicks: Int get() = infantDays * DAY_TICKS
}

object Species {
    private const val D = (Math.PI / 180.0).toFloat()

    val all: Array<SpeciesDef> = arrayOf(
        SpeciesDef(
            id = ALPHA, name = "Alpha", creature = "Hammerhorn", color = 0xFFE0A030.toInt(),
            speed = 1.8f, health = 200f, lifespanDays = 120, maxEnergy = 200f, hunger = 0.06f,
            strength = 24f, attackRange = 1.8f, attackCooldown = 12,
            vision = 10f, visionHalfAngle = 35f * D, hear = 5f, smell = 6f, call = 40f,
            maturityDays = 40, femaleCooldownDays = 40, litterMin = 1, litterMax = 1, infantDays = 8,
            shelterR = 4f, carnivore = true, plantEfficiency = 0.8f, meatEfficiency = 1f, bodyEnergy = 220f,
        ),
        SpeciesDef(
            id = BETA, name = "Beta", creature = "Trunkling", color = 0xFF9A82F2.toInt(),
            speed = 2.5f, health = 100f, lifespanDays = 80, maxEnergy = 120f, hunger = 0.05f,
            strength = 8f, attackRange = 1.2f, attackCooldown = 12,
            vision = 8f, visionHalfAngle = 55f * D, hear = 6f, smell = 6f, call = 26f,
            maturityDays = 18, femaleCooldownDays = 16, litterMin = 1, litterMax = 2, infantDays = 6,
            shelterR = 3.2f, carnivore = false, plantEfficiency = 1.15f, meatEfficiency = 0f, bodyEnergy = 120f,
        ),
        SpeciesDef(
            id = GAMMA, name = "Gamma", creature = "Puffkin", color = 0xFF4FA8F0.toInt(),
            speed = 3.3f, health = 50f, lifespanDays = 50, maxEnergy = 100f, hunger = 0.04f,
            strength = 3f, attackRange = 1f, attackCooldown = 15,
            vision = 6f, visionHalfAngle = 95f * D, hear = 8f, smell = 6f, call = 16f,
            maturityDays = 8, femaleCooldownDays = 8, litterMin = 2, litterMax = 4, infantDays = 3,
            shelterR = 2.2f, carnivore = false, plantEfficiency = 1f, meatEfficiency = 0f, bodyEnergy = 50f,
        ),
    )
}
