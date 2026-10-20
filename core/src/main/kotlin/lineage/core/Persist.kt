package lineage.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Saves and loads the whole world. The file is written to a temp name first, so a crash cannot ruin it. */
object Persist {
    private const val MAGIC_V1 = 0x4C494E31 // "LIN1"
    private const val MAGIC_V2 = 0x4C494E32 // "LIN2": adds brain family and shelter timers per creature
    private const val MAGIC = 0x4C494E33 // "LIN3": adds moving shelters (position and velocity)

    fun save(w: World, file: File) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(w.cap); o.writeInt(w.cfg.plants)
            o.writeLong(w.tick); o.writeInt(w.nextUid); o.writeInt(w.maxGeneration)
            o.writeLong(w.rng.state)
            for (i in 0 until w.cap) {
                o.writeBoolean(w.alive[i])
                if (!w.alive[i]) continue
                o.writeInt(w.uid[i]); o.writeByte(w.sp[i].toInt()); o.writeBoolean(w.female[i])
                o.writeFloat(w.x[i]); o.writeFloat(w.y[i]); o.writeFloat(w.h[i])
                o.writeInt(w.age[i]); o.writeFloat(w.hp[i]); o.writeFloat(w.en[i])
                o.writeInt(w.mateCd[i]); o.writeInt(w.gen[i])
                o.writeInt(w.motherSlot[i]); o.writeInt(w.motherUid[i]); o.writeInt(w.fatherUid[i]); o.writeInt(w.partnerUid[i])
                o.writeInt(w.kills[i]); o.writeInt(w.meals[i]); o.writeInt(w.offspring[i]); o.writeInt(w.matings[i])
                o.writeLong(w.lastMate[i]); o.writeLong(w.birthTick[i]); o.writeFloat(w.dist[i])
                for (k in 0 until NG) o.writeFloat(w.genes[i * NG + k])
                o.writeByte(w.family[i].toInt()); o.writeInt(w.shelterCd[i]); o.writeLong(w.visited[i])
            }
            for (p in 0 until w.cfg.plants) o.writeFloat(w.plantG[p])
            o.writeInt(w.nSh)
            for (k in 0 until w.nSh) { o.writeFloat(w.shX[k]); o.writeFloat(w.shY[k]); o.writeFloat(w.shVx[k]); o.writeFloat(w.shVy[k]) }
            // best creatures of each species
            for (s in 0 until NSP) {
                o.writeInt(w.hofCount[s])
                for (j in 0 until w.hofCount[s]) {
                    o.writeInt(w.hofDays[s][j]); o.writeInt(w.hofMatings[s][j]); o.writeInt(w.hofKills[s][j]); o.writeInt(w.hofUid[s][j])
                    for (k in 0 until NG) o.writeFloat(w.hofGenes[s][j * NG + k])
                }
            }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        w.brain?.save(File(file.absoluteFile.parentFile, "brains.bin"))
    }

    /** Returns false if there is no usable save (wrong size or missing). */
    fun load(w: World, file: File): Boolean {
        if (!file.exists()) return false
        DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { i ->
            val magic = i.readInt()
            if (magic != MAGIC && magic != MAGIC_V2 && magic != MAGIC_V1) return false
            val cap = i.readInt(); val plants = i.readInt()
            if (cap != w.cap || plants != w.cfg.plants) return false
            w.tick = i.readLong(); w.nextUid = i.readInt(); w.maxGeneration = i.readInt()
            w.rng.state = i.readLong()
            w.clearCreatures()
            for (s in 0 until w.cap) {
                if (!i.readBoolean()) continue
                w.restoreSlot(s)
                w.uid[s] = i.readInt(); w.sp[s] = i.readByte(); w.female[s] = i.readBoolean()
                w.x[s] = i.readFloat(); w.y[s] = i.readFloat(); w.h[s] = i.readFloat()
                w.age[s] = i.readInt(); w.hp[s] = i.readFloat(); w.en[s] = i.readFloat()
                w.mateCd[s] = i.readInt(); w.gen[s] = i.readInt()
                w.motherSlot[s] = i.readInt(); w.motherUid[s] = i.readInt(); w.fatherUid[s] = i.readInt(); w.partnerUid[s] = i.readInt()
                w.kills[s] = i.readInt(); w.meals[s] = i.readInt(); w.offspring[s] = i.readInt(); w.matings[s] = i.readInt()
                w.lastMate[s] = i.readLong(); w.birthTick[s] = i.readLong(); w.dist[s] = i.readFloat()
                for (k in 0 until NG) w.genes[s * NG + k] = i.readFloat()
                if (magic != MAGIC_V1) {
                    w.family[s] = i.readByte().toInt().coerceIn(0, w.cfg.brainFamilies[w.sp[s].toInt()] - 1).toByte()
                    w.shelterCd[s] = i.readInt(); w.visited[s] = i.readLong()
                }
                w.px[s] = w.x[s]; w.py[s] = w.y[s]; w.ph[s] = w.h[s]
            }
            for (p in 0 until plants) w.plantG[p] = i.readFloat()
            if (magic == MAGIC) {
                val n = i.readInt()
                for (k in 0 until n) {
                    val x = i.readFloat(); val y = i.readFloat(); val vx = i.readFloat(); val vy = i.readFloat()
                    if (k < w.nSh) { w.shX[k] = x; w.shY[k] = y; w.shVx[k] = vx; w.shVy[k] = vy }
                }
            }
            if (i.available() > 0) {
                for (s in 0 until NSP) {
                    val n = i.readInt()
                    w.hofCount[s] = n
                    for (j in 0 until n) {
                        w.hofDays[s][j] = i.readInt(); w.hofMatings[s][j] = i.readInt(); w.hofKills[s][j] = i.readInt(); w.hofUid[s][j] = i.readInt()
                        for (k in 0 until NG) w.hofGenes[s][j * NG + k] = i.readFloat()
                    }
                }
            }
            w.finishRestore()
        }
        val dir = file.absoluteFile.parentFile
        readPopulation(w, File(dir, "population.csv"))
        readEvents(w, File(dir, "events.csv"))
        w.brain?.let { b ->
            b.load(File(dir, "brains.bin"))
            // the learning graph carries on from the log
            val curves = LearnCurve.readCsv(File(dir, "training.csv"))
            for (s in 0 until NSP) b.curves[s] = curves[s]
            val exams = LearnCurve.readExams(File(dir, "training.csv"))
            for (s in 0 until NSP) b.examCurves[s] = exams[s]
        }
        return true
    }

    /** Counts rescues, top-ups, brain copies and resets from the log, so the stats page shows the whole run. */
    private fun readEvents(w: World, f: File) {
        if (!f.exists()) return
        var r = 0; var t = 0; var c = 0; var b = 0
        for (l in w.wipeouts) l.clear()
        f.forEachLine { line ->
            val p = line.split(',', limit = 4)
            if (p.size < 2) return@forEachLine
            when (p[1]) {
                "restart" -> { r = 0; t = 0; c = 0; b = 0; for (l in w.wipeouts) l.clear() }
                "extinction" -> {
                    val s = Species.all.indexOfFirst { it.name == p.getOrNull(2) }
                    val tick = p[0].toLongOrNull()
                    if (s >= 0 && tick != null) w.wipeouts[s].add(tick)
                }
                "rescue" -> r++
                "topup" -> t++
                "brain_copy" -> c++
                "brain_reset" -> b++
            }
        }
        w.rescues = r; w.topUps = t; w.familyCopies = c; w.brainResets = b
    }

    /** Rebuilds the whole-run population curve and death totals from the log (the newest run in the file). */
    private fun readPopulation(w: World, f: File) {
        if (!f.exists()) return
        var last = -1L
        f.bufferedReader().use { r ->
            r.readLine() ?: return
            while (true) {
                val line = r.readLine() ?: break
                val p = line.split(',')
                if (p.size < 14) continue
                val t = p[0].toLongOrNull() ?: continue
                if (t < last - 1000) for (s in 0 until NSP) {
                    w.popCurve[s].clear(); java.util.Arrays.fill(w.deathTotals[s], 0L); for (c in w.deathCurves[s]) c.clear()
                }
                last = t
                val s = Species.all.indexOfFirst { it.name == p[2] }
                if (s < 0) continue
                w.popCurve[s].add(t, p[3].toFloatOrNull() ?: 0f, 0f)
                for (c in 0 until NCAUSE) {
                    val n = p[10 + c].toLongOrNull() ?: 0L
                    w.deathTotals[s][c] += n
                    w.deathCurves[s][c].add(t, n.toFloat(), 0f)
                }
            }
        }
    }
}
