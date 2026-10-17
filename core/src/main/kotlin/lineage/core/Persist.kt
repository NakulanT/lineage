package lineage.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Saves and loads the whole world. The file is written to a temp name first, so a crash cannot ruin it. */
object Persist {
    private const val MAGIC = 0x4C494E31 // "LIN1"

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
            }
            for (p in 0 until w.cfg.plants) o.writeFloat(w.plantG[p])
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        w.brain?.save(File(file.absoluteFile.parentFile, "brains.bin"))
    }

    /** Returns false if there is no usable save (wrong size or missing). */
    fun load(w: World, file: File): Boolean {
        if (!file.exists()) return false
        DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { i ->
            if (i.readInt() != MAGIC) return false
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
                w.px[s] = w.x[s]; w.py[s] = w.y[s]; w.ph[s] = w.h[s]
            }
            for (p in 0 until plants) w.plantG[p] = i.readFloat()
            w.finishRestore()
        }
        w.brain?.load(File(file.absoluteFile.parentFile, "brains.bin"))
        return true
    }
}
