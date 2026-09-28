package com.warivo.os.nav

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Destination search and turn-by-turn state for the Map panel: geocode a query, route to
 * a pick, then recompute the remaining distance, ETA and next turn from every GPS fix.
 *
 * Deliberately service-backed (Nominatim + OSRM) rather than an on-device engine — a real
 * router and its map data are gigabytes the head unit does not carry. It degrades to the
 * plain map when offline; nothing here is on the safety path.
 */
class NavigationController(
    private val geocoder: Geocoder,
    private val router: Router,
    private val scope: CoroutineScope,
) {
    private val _results = MutableStateFlow<List<Place>>(emptyList())
    val results: StateFlow<List<Place>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _routing = MutableStateFlow(false)
    val routing: StateFlow<Boolean> = _routing.asStateFlow()

    private val _destination = MutableStateFlow<Place?>(null)
    val destination: StateFlow<Place?> = _destination.asStateFlow()

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _progress = MutableStateFlow<NavProgress?>(null)
    val progress: StateFlow<NavProgress?> = _progress.asStateFlow()

    private var lastFix: GeoPoint? = null
    private var searchJob: Job? = null

    /** Fed from the GPS service so navigation follows the rider. */
    fun onLocation(lat: Double, lon: Double) {
        lastFix = GeoPoint(lat, lon)
        _route.value?.let { recompute(GeoPoint(lat, lon), it) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = scope.launch {
            _searching.value = true
            delay(350)   // debounce keystrokes; Nominatim asks for <= 1 req/s
            _results.value = geocoder.search(query, lastFix)
            _searching.value = false
        }
    }

    fun clearResults() {
        searchJob?.cancel()
        _results.value = emptyList()
        _searching.value = false
    }

    fun navigateTo(place: Place) {
        _destination.value = place
        _results.value = emptyList()
        val from = lastFix ?: return   // no fix yet: keep the destination, route on next fix
        scope.launch {
            _routing.value = true
            val r = router.route(from, GeoPoint(place.lat, place.lon))
            _routing.value = false
            _route.value = r
            if (r != null) recompute(from, r) else _progress.value = null
        }
    }

    fun retry() {
        _destination.value?.let { navigateTo(it) }
    }

    fun stop() {
        _destination.value = null
        _route.value = null
        _progress.value = null
        _results.value = emptyList()
    }

    private fun recompute(loc: GeoPoint, route: Route) {
        val pts = route.points
        if (pts.isEmpty()) return

        // Nearest route vertex to the current fix.
        var nearest = 0
        var best = Double.MAX_VALUE
        for (i in pts.indices) {
            val d = haversine(loc.lat, loc.lon, pts[i].lat, pts[i].lon)
            if (d < best) {
                best = d
                nearest = i
            }
        }
        // Remaining = fix -> nearest vertex, then along the rest of the line.
        var remaining = best
        for (i in nearest until pts.size - 1) {
            remaining += haversine(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon)
        }
        val speed = if (route.durationS > 0) route.distanceM / route.durationS else 8.0  // m/s
        val etaMin = Math.ceil(remaining / speed.coerceAtLeast(2.0) / 60.0).toInt()

        // Next manoeuvre: the nearest turn still meaningfully ahead of us.
        val next = route.steps
            .filter { it.instruction.isNotBlank() && !it.instruction.startsWith("Head off") }
            .minByOrNull { haversine(loc.lat, loc.lon, it.lat, it.lon) }
        val nextDist = next?.let { haversine(loc.lat, loc.lon, it.lat, it.lon) } ?: remaining

        _progress.value = NavProgress(
            remainingM = remaining,
            etaMinutes = etaMin,
            nextInstruction = next?.instruction ?: "Continue",
            nextDistanceM = nextDist,
            arrived = remaining < 30.0,
        )
    }
}
