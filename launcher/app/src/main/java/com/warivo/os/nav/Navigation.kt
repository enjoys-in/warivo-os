package com.warivo.os.nav

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** A plain lat/lon, kept independent of MapLibre so the nav layer has no UI dependency. */
data class GeoPoint(val lat: Double, val lon: Double)

/** A geocoded search hit. [detail] is the rest of the address after the name. */
data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** One turn on the route. [lat]/[lon] is where the manoeuvre happens. */
data class Maneuver(val instruction: String, val distanceM: Double, val lat: Double, val lon: Double)

/** A driving route: the line to draw, its totals, and the turns along it. */
data class Route(
    val points: List<GeoPoint>,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<Maneuver>,
)

/** Live progress while navigating, recomputed from each GPS fix. */
data class NavProgress(
    val remainingM: Double,
    val etaMinutes: Int,
    val nextInstruction: String,
    val nextDistanceM: Double,
    val arrived: Boolean,
)

/** Great-circle distance in metres. */
internal fun haversine(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val s = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s))
}

/**
 * Bare HTTP GET for the OSM services below. Nominatim's usage policy REQUIRES a
 * User-Agent that identifies the app, so it is always sent.
 */
internal suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "WarivoOS/0.1 (warivo head unit)")
            setRequestProperty("Accept", "application/json")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            Log.d("WarivoNav", "GET $code for $url")
            return@withContext null
        }
        connection.inputStream.bufferedReader().use(BufferedReader::readText)
    } catch (e: Exception) {
        Log.d("WarivoNav", "GET failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    } finally {
        runCatching { connection?.disconnect() }
    }
}
