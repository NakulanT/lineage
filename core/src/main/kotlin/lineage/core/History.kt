package lineage.core

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

const val C_KILLED = 0
const val C_STARVED = 1
const val C_OLD = 2
const val C_INJURY = 3
const val NCAUSE = 4

val CAUSE_NAMES = arrayOf("killed", "starved", "old_age", "injury")

/** One row of the per-species population table. */
class PopRow(
    val tick: Long,
    val species: Int,
    val count: Int,
    val males: Int,
    val females: Int,
    val babies: Int,
    val avgAgeDays: Float,
    val births: Int,
    val matings: Int,
    val deaths: IntArray,
    val avgGenes: FloatArray,
    val ripePlants: Int,
    val meatItems: Int,
    val inShelter: Int,
)

/** Life record of one creature, written when it dies (or when a snapshot is taken). */
class LifeRec(
    val uid: Int,
    val species: Int,
    val female: Boolean,
    val motherUid: Int,
    val fatherUid: Int,
    val generation: Int,
    val birthTick: Long,
    val endTick: Long,
    val cause: String,
    val kills: Int,
    val meals: Int,
    val offspring: Int,
    val matings: Int,
    val genes: FloatArray,
)

/** Append-only record of everything that happened. Never edited, only added to. */
interface HistoryLog {
    fun runInfo(text: String)
    fun population(row: PopRow)
    fun event(tick: Long, type: String, species: Int, a: Int, b: Int, x: Float, y: Float, note: String)
    fun life(rec: LifeRec)
    fun flush()
    fun close()
}

object NullHistory : HistoryLog {
    override fun runInfo(text: String) {}
    override fun population(row: PopRow) {}
    override fun event(tick: Long, type: String, species: Int, a: Int, b: Int, x: Float, y: Float, note: String) {}
    override fun life(rec: LifeRec) {}
    override fun flush() {}
    override fun close() {}
}

/** Writes plain CSV files into one folder. Works the same on laptop and phone. */
class CsvHistory(val dir: File) : HistoryLog {
    private val pop: BufferedWriter
    private val events: BufferedWriter
    private val lives: BufferedWriter

    init {
        dir.mkdirs()
        pop = open("population.csv",
            "tick,day,species,count,males,females,babies,avg_age_days,births,matings," +
                "deaths_killed,deaths_starved,deaths_old_age,deaths_injury," +
                "g_speed,g_strength,g_vision,g_health,g_lifespan,g_fertility,ripe_plants,meat_items,in_shelter")
        events = open("events.csv", "tick,type,species,a,b,x,y,note")
        lives = open("creatures.csv",
            "uid,species,gender,mother,father,generation,birth_tick,end_tick,cause,kills,meals,offspring,matings," +
                "g_speed,g_strength,g_vision,g_health,g_lifespan,g_fertility")
    }

    private fun open(name: String, header: String): BufferedWriter {
        val f = File(dir, name)
        val fresh = !f.exists() || f.length() == 0L
        val w = BufferedWriter(FileWriter(f, true), 1 shl 16)
        if (fresh) {
            w.write(header)
            w.newLine()
        }
        return w
    }

    @Synchronized
    override fun runInfo(text: String) {
        File(dir, "run.txt").appendText(text + "\n")
    }

    @Synchronized
    override fun population(row: PopRow) {
        val sb = StringBuilder(160)
        sb.append(row.tick).append(',').append(row.tick / DAY_TICKS).append(',')
            .append(Species.all[row.species].name).append(',')
            .append(row.count).append(',').append(row.males).append(',').append(row.females).append(',')
            .append(row.babies).append(',').append("%.1f".format(row.avgAgeDays)).append(',')
            .append(row.births).append(',').append(row.matings)
        for (d in row.deaths) sb.append(',').append(d)
        for (g in row.avgGenes) sb.append(',').append("%.3f".format(g))
        sb.append(',').append(row.ripePlants).append(',').append(row.meatItems).append(',').append(row.inShelter)
        pop.write(sb.toString())
        pop.newLine()
    }

    @Synchronized
    override fun event(tick: Long, type: String, species: Int, a: Int, b: Int, x: Float, y: Float, note: String) {
        events.write("$tick,$type,${Species.all[species].name},$a,$b,${"%.1f".format(x)},${"%.1f".format(y)},$note")
        events.newLine()
    }

    @Synchronized
    override fun life(rec: LifeRec) {
        val sb = StringBuilder(128)
        sb.append(rec.uid).append(',').append(Species.all[rec.species].name).append(',')
            .append(if (rec.female) "F" else "M").append(',')
            .append(rec.motherUid).append(',').append(rec.fatherUid).append(',').append(rec.generation).append(',')
            .append(rec.birthTick).append(',').append(rec.endTick).append(',').append(rec.cause).append(',')
            .append(rec.kills).append(',').append(rec.meals).append(',').append(rec.offspring).append(',')
            .append(rec.matings)
        for (g in rec.genes) sb.append(',').append("%.3f".format(g))
        lives.write(sb.toString())
        lives.newLine()
    }

    @Synchronized
    override fun flush() {
        pop.flush(); events.flush(); lives.flush()
    }

    @Synchronized
    override fun close() {
        flush()
        pop.close(); events.close(); lives.close()
    }
}
