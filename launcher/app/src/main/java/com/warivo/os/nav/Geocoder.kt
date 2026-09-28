package com.warivo.os.nav

import org.json.JSONArray
import java.net.URLEncoder

/**
 * Place search via Nominatim (OpenStreetMap). Free and keyless, which keeps it off Play
 * Services — but the public server allows only light, attributed use, so point [BASE] at
 * your own instance before this runs on more than one scooter.
 */
class Geocoder {

    suspend fun search(query: String, near: GeoPoint? = null): List<Place> {
        if (query.isBlank()) return emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        // Bias results toward the rider without hard-limiting them to the box.
        val bias = near?.let {
            "&viewbox=${it.lon - 0.6},${it.lat + 0.6},${it.lon + 0.6},${it.lat - 0.6}"
        } ?: ""
        val text = httpGet("$BASE/search?format=jsonv2&limit=6&q=$q$bias") ?: return emptyList()
        return runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val display = o.optString("display_name")
                if (display.isBlank()) return@mapNotNull null
                Place(
                    name = display.substringBefore(",").trim(),
                    detail = display.substringAfter(",").trim(),
                    lat = o.getString("lat").toDouble(),
                    lon = o.getString("lon").toDouble(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val BASE = "https://nominatim.openstreetmap.org"
    }
}
