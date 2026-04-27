package com.lumos.core.remoteconfig

import android.content.Context
import java.io.File

/**
 * Stores Last Known Good (LKG) config bytes atomically:
 * - write temp
 * - fsync (best effort)
 * - rename
 */
class LastKnownGoodStore(private val ctx: Context) {
    private val dir: File = File(ctx.filesDir, "remote_config").apply { mkdirs() }
    private val lkg = File(dir, "lkg.bin")
    private val prev = File(dir, "prev.bin")

    fun readLkg(): ByteArray? = if (lkg.exists()) lkg.readBytes() else null

    fun writeLkg(bytes: ByteArray) {
        // rotate
        if (lkg.exists()) {
            lkg.copyTo(prev, overwrite = true)
        }
        val tmp = File(dir, "lkg.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(lkg)
    }

    fun rollbackToPrevious(): Boolean {
        if (!prev.exists()) return false
        prev.copyTo(lkg, overwrite = true)
        return true
    }
}
