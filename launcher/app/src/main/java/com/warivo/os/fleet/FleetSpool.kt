package com.warivo.os.fleet

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The offline spool: samples, events and completed rides waiting to be uploaded.
 *
 * A scooter rides through basements, lifts and dead cells. Without a spool, a ride through
 * one of those is simply missing from the owner's map afterwards — so nothing is dropped
 * because of coverage, only because of age.
 *
 * Bounded and oldest-first: [MAX_SAMPLES] at a 10 s interval is about 5½ hours of
 * continuous offline riding. Beyond that the oldest samples go, because a full disk is a
 * worse failure than a gap in last week's track.
 *
 * Stored as newline-delimited JSON. Appending a line is the cheapest durable write there
 * is, and a truncated final line from a power cut costs exactly one sample.
 */
class FleetSpool(context: Context) {

    private val dir = File(context.filesDir, "fleet").apply { mkdirs() }
    private val samplesFile = File(dir, "samples.ndjson")
    private val eventsFile = File(dir, "events.ndjson")
    private val ridesFile = File(dir, "rides.ndjson")

    fun addSample(sample: JSONObject) = append(samplesFile, sample, MAX_SAMPLES)

    fun addEvent(event: JSONObject) = append(eventsFile, event, MAX_EVENTS)

    fun addRide(ride: JSONObject) = append(ridesFile, ride, MAX_RIDES)

    fun samples(limit: Int): List<String> = read(samplesFile, limit)

    fun events(limit: Int): List<String> = read(eventsFile, limit)

    fun rides(limit: Int): List<String> = read(ridesFile, limit)

    val pendingSamples: Int get() = countLines(samplesFile)

    /**
     * Drops the first [n] lines of each file — the ones just uploaded.
     *
     * Dropping by count rather than clearing the file matters: samples keep arriving while
     * an upload is in flight, and clearing would discard whatever landed in between.
     */
    fun drop(samples: Int, events: Int, rides: Int) {
        dropFirst(samplesFile, samples)
        dropFirst(eventsFile, events)
        dropFirst(ridesFile, rides)
    }

    private fun append(file: File, json: JSONObject, max: Int) {
        runCatching {
            file.appendText(json.toString().replace("\n", " ") + "\n")
            if (countLines(file) > max) dropFirst(file, countLines(file) - max)
        }.onFailure { Log.w(TAG, "spool append failed: ${it.message}") }
    }

    private fun read(file: File, limit: Int): List<String> = runCatching {
        if (!file.exists()) emptyList()
        else file.useLines { lines -> lines.filter { it.isNotBlank() }.take(limit).toList() }
    }.getOrDefault(emptyList())

    private fun countLines(file: File): Int = runCatching {
        if (!file.exists()) 0 else file.useLines { it.count { line -> line.isNotBlank() } }
    }.getOrDefault(0)

    private fun dropFirst(file: File, n: Int) {
        if (n <= 0 || !file.exists()) return
        runCatching {
            val kept = file.useLines { lines ->
                lines.filter { it.isNotBlank() }.drop(n).toList()
            }
            file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n") + "\n")
        }.onFailure { Log.w(TAG, "spool trim failed: ${it.message}") }
    }

    /** Wraps the spooled lines back into arrays for the request body. */
    fun toArray(lines: List<String>): JSONArray {
        val array = JSONArray()
        lines.forEach { line ->
            runCatching { array.put(JSONObject(line)) }
        }
        return array
    }

    private companion object {
        const val TAG = "WarivoFleetSpool"
        const val MAX_SAMPLES = 2_000
        const val MAX_EVENTS = 500
        const val MAX_RIDES = 200
    }
}
