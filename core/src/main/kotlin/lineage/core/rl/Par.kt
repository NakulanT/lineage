package lineage.core.rl

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spreads brain training over the machine's cores. The work is always cut into the same [PIECES] pieces and
 * their results are added in the same order, so a run gives the same numbers on a phone and on a laptop.
 */
object Par {
    const val PIECES = 16

    /** Threads that help the calling thread (none on a single-core machine). */
    private val helpers = minOf(PIECES, Runtime.getRuntime().availableProcessors()) - 1

    private val pool: ExecutorService? = if (helpers <= 0) null else Executors.newFixedThreadPool(helpers) { r ->
        Thread(r, "lineage-learn").also { it.isDaemon = true }
    }

    /** Runs [body] for pieces 0 until [n] and returns when all are done. The calling thread helps too. */
    fun run(n: Int, body: (Int) -> Unit) {
        val p = pool
        if (p == null || n <= 1) { for (k in 0 until n) body(k); return }
        // each thread takes the next piece not yet started, so a slow thread never holds the others up
        val next = AtomicInteger(0)
        val work = Runnable { while (true) { val k = next.getAndIncrement(); if (k >= n) break; body(k) } }
        val rest = ArrayList<Future<*>>(helpers)
        repeat(minOf(helpers, n - 1)) { rest.add(p.submit(work)) }
        var error: Throwable? = null
        try { work.run() } catch (e: Throwable) { error = e }
        for (f in rest) {
            try { f.get() } catch (e: ExecutionException) { if (error == null) error = e.cause ?: e }
        }
        error?.let { throw it }
    }
}
