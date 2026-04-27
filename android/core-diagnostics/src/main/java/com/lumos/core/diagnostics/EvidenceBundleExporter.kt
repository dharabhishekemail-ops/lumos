package com.lumos.core.diagnostics

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * Phase 9: Sanitized evidence bundle exporter.
 *
 * MUST NOT include:
 * - raw message content
 * - stable device identifiers
 * - photos/media
 *
 * Allowed:
 * - versions, hashes
 * - orchestrator state transitions (redacted ids)
 * - counters and timings
 */
class EvidenceBundleExporter(private val context: Context) {

    data class BundleInputs(
        val appVersion: String,
        val protocolVersion: String,
        val configHash: String,
        val timelineJson: JSONObject,
        val countersJson: JSONObject,
        val redactedLogs: String
    )

    fun export(inputs: BundleInputs): File {
        val outDir = File(context.cacheDir, "evidence").apply { mkdirs() }
        val outZip = File(outDir, "lumos_evidence_${System.currentTimeMillis()}.zip")
        ZipOutputStream(outZip.outputStream().buffered()).use { zos ->
            fun put(name: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            val meta = JSONObject()
                .put("appVersion", inputs.appVersion)
                .put("protocolVersion", inputs.protocolVersion)
                .put("configHash", inputs.configHash)
                .put("exportedAtMs", System.currentTimeMillis())
            put("meta.json", meta.toString(2).toByteArray())
            put("timeline.json", inputs.timelineJson.toString(2).toByteArray())
            put("counters.json", inputs.countersJson.toString(2).toByteArray())
            put("logs_redacted.txt", inputs.redactedLogs.toByteArray())
        }
        return outZip
    }
}
