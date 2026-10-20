package lineage.core

import lineage.core.rl.RlBrain

/**
 * The exam: the honest test of whether a brain got better. A frozen copy of the current brains plays the same
 * fixed test world (same map, same creatures, same seed every time) for a few minutes of world time, with no
 * learning. Only the brains differ between exams, so a higher score means better brains, not just more food or
 * fewer creatures in the live world.
 *
 * Score per species: average reward per decision. Also recorded: meals per creature per day.
 */
object Exam {
    const val SEED = 777L

    @Volatile private var running = false

    /** Starts an exam of the world's current brains (on its own thread unless the settings say inline). */
    fun start(w: World, live: RlBrain) {
        if (running) return
        val cfg = w.cfg.with(mapOf("seed" to "$SEED", "examEvery" to "0", "pretrainTicks" to "0", "explainEvery" to "0"))
        val tick = w.tick
        // copy the weights now, on the simulation thread
        val test = World(cfg, NullHistory)
        val b = test.brain ?: return
        b.copyWeightsFrom(live)
        b.frozen = true
        val job = Runnable {
            try {
                val (score, meals) = run(test, w.cfg.examSteps)
                live.addExam(tick, score, meals)
            } catch (_: Throwable) {
            } finally { running = false }
        }
        running = true
        if (w.cfg.examInline) job.run() else Thread(job, "lineage-exam").also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY }.start()
    }

    /** Runs the test world and returns (reward per decision, meals per creature-day) per species. NaN = no data. */
    fun run(test: World, steps: Int): Pair<FloatArray, FloatArray> {
        repeat(steps) { test.step() }
        val score = FloatArray(NSP) { s ->
            if (test.decisionCount[s] < 50) Float.NaN else (test.rewardSum[s] / test.decisionCount[s]).toFloat()
        }
        val meals = FloatArray(NSP) { s ->
            val days = test.aliveSteps[s].toDouble() / DAY_TICKS
            if (days < 1.0) Float.NaN else (test.mealCount[s] / days).toFloat()
        }
        return score to meals
    }
}
