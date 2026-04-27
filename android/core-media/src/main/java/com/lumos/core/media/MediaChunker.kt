package com.lumos.core.media

object MediaChunker {
    fun chunk(bytes: ByteArray, chunkSize: Int): List<ByteArray> {
        val cs = chunkSize.coerceIn(512, 64*1024)
        val out = ArrayList<ByteArray>((bytes.size + cs - 1) / cs)
        var i = 0
        while (i < bytes.size) {
            val end = minOf(bytes.size, i + cs)
            out.add(bytes.copyOfRange(i, end))
            i = end
        }
        return out
    }
}
