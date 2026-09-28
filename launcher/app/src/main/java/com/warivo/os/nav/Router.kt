package com.warivo.os.nav

import org.json.JSONObject

/**
 * Driving routes via OSRM. The public demo server is fine for one personal scooter but is
 * rate-limited and not for production — point [BASE] at your own OSRM before scaling.
 */
class Router {

    suspend fun route(from: GeoPoint, to: GeoPoint): Route? {
        val coords = "${from.lon},${from.lat};${to.lon},${to.lat}"
        val text = httpGet(
            "$BASE/route/v1/driving/$coords?overview=full&geometries=geojson&steps=true"
        ) ?: return null
        return runCatching {
            val root = JSONObject(text)
            if (root.optString("code") != "Ok") return@runCatching null
            val route = root.getJSONArray("routes").getJSONObject(0)
            val geometry = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = (0 until geometry.length()).map { i ->
                val c = geometry.getJSONArray(i)
                GeoPoint(c.getDouble(1), c.getDouble(0))   // GeoJSON is [lon, lat]
            }
            val steps = mutableListOf<Maneuver>()
            val legs = route.getJSONArray("legs")
            for (l in 0 until legs.length()) {
                val arr = legs.getJSONObject(l).getJSONArray("steps")
                for (s in 0 until arr.length()) {
                    val step = arr.getJSONObject(s)
                    val man = step.getJSONObject("maneuver")
                    val loc = man.getJSONArray("location")
                    steps += Maneuver(
                        instruction = instruction(man, step.optString("name")),
                        distanceM = step.getDouble("distance"),
                        lat = loc.getDouble(1),
                        lon = loc.getDouble(0),
                    )
                }
            }
            Route(
                points = points,
                distanceM = route.getDouble("distance"),
                durationS = route.getDouble("duration"),
                steps = steps,
            )
        }.getOrNull()
    }

    /** Turn an OSRM maneuver object into a plain-language instruction. */
    private fun instruction(man: JSONObject, road: String): String {
        val onto = if (road.isNotBlank()) " onto $road" else ""
        val dir = when (man.optString("modifier")) {
            "left" -> "left"
            "right" -> "right"
            "slight left" -> "slightly left"
            "slight right" -> "slightly right"
            "sharp left" -> "sharply left"
            "sharp right" -> "sharply right"
            "uturn" -> "around"
            else -> ""
        }
        return when (man.optString("type")) {
            "depart" -> "Head off$onto"
            "arrive" -> "Arrive at your destination"
            "turn", "end of road" -> "Turn ${dir.ifBlank { "ahead" }}$onto"
            "continue", "new name" -> "Continue$onto"
            "merge" -> "Merge ${dir}".trim() + onto
            "on ramp" -> "Take the ramp$onto"
            "off ramp" -> "Take the exit$onto"
            "fork" -> "Keep ${dir.ifBlank { "ahead" }}$onto"
            "roundabout", "rotary" -> "Take the roundabout$onto"
            else -> if (dir.isNotBlank()) "Turn $dir$onto" else "Continue$onto"
        }
    }

    private companion object {
        const val BASE = "https://router.project-osrm.org"
    }
}
