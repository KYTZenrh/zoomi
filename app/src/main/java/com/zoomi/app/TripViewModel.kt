package com.zoomi.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.core.NavigationState
import com.zoomi.app.nav.SpeedLimit
import com.zoomi.core.nav.EtaShare
import com.zoomi.core.places.PlaceKind
import com.zoomi.core.places.SavedPlace
import com.zoomi.core.places.SavedPlaces
import com.zoomi.core.search.Place
import com.zoomi.core.search.SearchQueryPolicy
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uniffi.ferrostar.Route
import uniffi.ferrostar.TripState
import java.util.UUID

data class RouteOption(val route: Route, val durationSec: Double, val distanceM: Double)

/** The destination can't be reached with this mode (island, sea, no road/path). */
data class Unreachable(
    val reason: com.zoomi.core.nav.RouteFailure?,
    /** How far the best route ends from the destination (null when no route at all). */
    val gapKm: Double?,
    val airportsNearYou: List<Place> = emptyList(),
    val airportsNearDestination: List<Place> = emptyList(),
)

sealed interface TripUi {
    data object Idle : TripUi
    data class Preview(
        val destination: Place,
        val mode: com.zoomi.core.nav.TravelMode = com.zoomi.core.nav.TravelMode.CAR,
        val loading: Boolean = true,
        val routes: List<RouteOption> = emptyList(),
        val selected: Int = 0,
        val error: String? = null,
        /** Computed on the phone from a downloaded region (no connection or server unreachable). */
        val offline: Boolean = false,
        /** Ferries / long bridges / long tunnels on the selected route (null = not checked yet). */
        val crossings: List<com.zoomi.core.nav.Crossing>? = null,
        val unreachable: Unreachable? = null,
        val itineraries: List<com.zoomi.core.transit.Itinerary> = emptyList(),
    ) : TripUi
    data class Navigating(val destination: Place, val mode: com.zoomi.core.nav.TravelMode = com.zoomi.core.nav.TravelMode.CAR) : TripUi
    data class Arrived(val destination: Place) : TripUi
}

data class SearchUi(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class TripViewModel(app: Application) : AndroidViewModel(app) {
    private val z = app.zoomi
    private val nav = z.navigation

    private val _trip = MutableStateFlow<TripUi>(TripUi.Idle)
    val trip: StateFlow<TripUi> = _trip.asStateFlow()

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search.asStateFlow()
    private val queryFlow = MutableStateFlow("")

    val savedPlaces: StateFlow<List<SavedPlace>> =
        z.settings.savedPlaces.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val voiceMuted: StateFlow<Boolean> =
        z.settings.voiceMuted.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val navState: StateFlow<NavigationState> =
        nav.state.stateIn(viewModelScope, SharingStarted.Eagerly, NavigationState())

    private val _speedLimit = MutableStateFlow<SpeedLimit?>(null)
    val speedLimit: StateFlow<SpeedLimit?> = _speedLimit.asStateFlow()

    private var routeJob: Job? = null

    init {
        viewModelScope.launch { z.settings.photonUrl.collect { z.photonUrl = it } }
        viewModelScope.launch { z.settings.valhallaUrl.collect { nav.valhallaUrl = it } }
        viewModelScope.launch { z.regions.refresh() }
        viewModelScope.launch { voiceMuted.collect { nav.setMuted(it) } }
        viewModelScope.launch { runSearch() }
        // Give the dead reckoner the active route (or none).
        viewModelScope.launch {
            navState
                .map { s -> if (s.routeGeometry.size >= 2) s.routeGeometry else null }
                .distinctUntilChanged() // only when a new route arrives (start / reroute)
                .collect { g -> z.position.setRoute(g?.map { com.zoomi.core.location.LatLon(it.lat, it.lng) }) }
        }
        viewModelScope.launch {
            navState.collect { s ->
                _speedLimit.value = if ((_trip.value as? TripUi.Navigating)?.mode == com.zoomi.core.nav.TravelMode.CAR) nav.speedLimit(s) else null
                val current = _trip.value
                if (current is TripUi.Navigating && s.tripState is TripState.Complete) {
                    _trip.value = TripUi.Arrived(current.destination)
                    nav.stop()
                }
            }
        }
    }

    // ---- Search ----

    fun onQueryChange(q: String) {
        _search.value = _search.value.copy(query = q)
        queryFlow.value = q
    }

    @OptIn(FlowPreview::class)
    private suspend fun runSearch() {
        queryFlow.debounce(SearchQueryPolicy.DEBOUNCE_MS).distinctUntilChanged().collectLatest { raw ->
            val q = SearchQueryPolicy.normalize(raw)
            if (q == null) {
                _search.value = _search.value.copy(results = emptyList(), loading = false, error = null)
                return@collectLatest
            }
            _search.value = _search.value.copy(loading = true, error = null)
            val loc = z.gnss.location.value
            _search.value = try {
                val r = z.photon.search(q, loc?.latitude, loc?.longitude)
                _search.value.copy(results = r, loading = false, error = if (r.isEmpty()) "No results" else null)
            } catch (e: Exception) {
                Log.w("Zoomi", "search failed", e)
                _search.value.copy(loading = false, error = "Search failed: ${e.message ?: "network error"}")
            }
        }
    }

    fun clearSearch() = onQueryChange("")

    // ---- Destination & routes ----

    fun chooseDestination(place: Place) {
        clearSearch()
        _trip.value = TripUi.Preview(place)
        requestRoutes()
    }

    /** Long-press on the map: route there, label it via reverse geocoding when possible. */
    fun chooseMapPoint(lat: Double, lon: Double) {
        val placeholder = Place("Dropped pin", String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon), lat, lon)
        chooseDestination(placeholder)
        viewModelScope.launch {
            val named = runCatching { z.photon.reverse(lat, lon) }.getOrNull() ?: return@launch
            val cur = _trip.value
            if (cur is TripUi.Preview && cur.destination === placeholder) {
                _trip.value = cur.copy(destination = named.copy(lat = lat, lon = lon))
            }
        }
    }

    fun retryRoutes() = requestRoutes()

    fun setMode(mode: com.zoomi.core.nav.TravelMode) {
        val p = _trip.value as? TripUi.Preview ?: return
        if (p.mode == mode) return
        _trip.value = p.copy(mode = mode)
        requestRoutes()
    }

    private fun requestRoutes() {
        val p = _trip.value as? TripUi.Preview ?: return
        routeJob?.cancel()
        _trip.value = p.copy(loading = true, error = null, routes = emptyList(), selected = 0,
            crossings = null, unreachable = null, itineraries = emptyList())
        routeJob = viewModelScope.launch {
            val from = z.gnss.location.value
            if (from == null) {
                update { it.copy(loading = false, error = "Waiting for a GPS fix. Go outside or near a window and retry.") }
                return@launch
            }
            if (p.mode == com.zoomi.core.nav.TravelMode.TRANSIT) { requestTransit(from.latitude, from.longitude, p); return@launch }
            nav.setCosting(p.mode.valhallaCosting!!)
            val dest = com.zoomi.core.location.LatLon(p.destination.lat, p.destination.lon)
            try {
                val routes = nav.routes(from, dest.lat, dest.lon).map {
                    RouteOption(it, it.steps.sumOf { s -> s.duration }, it.distance)
                }
                if (routes.isEmpty()) { update { it.copy(loading = false, error = "No route found.") }; return@launch }
                val end = routes[0].route.geometry.last().let { com.zoomi.core.location.LatLon(it.lat, it.lng) }
                val reachable = com.zoomi.core.nav.Reachability.reachable(p.mode, dest, end)
                update {
                    it.copy(loading = false, routes = routes, selected = 0,
                        offline = nav.routeProvider.lastSource.value == com.zoomi.app.nav.RouteSource.OFFLINE,
                        unreachable = if (reachable) null else Unreachable(null, com.zoomi.core.nav.Reachability.gapMeters(dest, end) / 1000))
                }
                if (!reachable) loadAirports(from.latitude, from.longitude, dest)
                loadCrossings()
            } catch (e: Exception) {
                Log.w("Zoomi", "route failed", e)
                val re = e as? com.zoomi.app.nav.RoutingException
                val failure = re?.failure
                if (failure == com.zoomi.core.nav.RouteFailure.NO_ROUTE || failure == com.zoomi.core.nav.RouteFailure.NO_ROAD_NEAR_DESTINATION) {
                    update { it.copy(loading = false, unreachable = Unreachable(failure, null)) }
                    loadAirports(from.latitude, from.longitude, dest)
                } else {
                    val msg = when (failure) {
                        com.zoomi.core.nav.RouteFailure.TOO_FAR_FOR_SERVER ->
                            "This trip is longer than the routing server allows. Try a closer destination or public transport."
                        else -> re?.message ?: "Couldn't get a route: ${e.message ?: e::class.simpleName}"
                    }
                    update { it.copy(loading = false, error = msg) }
                }
            }
        }
    }

    private suspend fun requestTransit(fromLat: Double, fromLon: Double, p: TripUi.Preview) {
        if (!z.network.online.value) {
            update { it.copy(loading = false, error = "Public transport search needs an internet connection.") }
            return
        }
        try {
            val plan = z.transit.plan(fromLat, fromLon, p.destination.lat, p.destination.lon)
            val its = plan.itineraries
            val walk = plan.direct.firstOrNull { d -> d.legs.all { l -> l.mode == "WALK" } }
            update {
                when {
                    its.isNotEmpty() -> it.copy(loading = false, itineraries = its, selected = 0)
                    walk != null -> it.copy(loading = false, error = "Walking (${com.zoomi.core.nav.Formatting.duration(walk.durationSec.toDouble())}) " +
                        "is quicker than any public transport for this trip.")
                    else -> it.copy(loading = false, error = "No public transport connection found. Coverage depends on " +
                        "which operators publish open timetables (see transitous.org/sources).")
                }
            }
        } catch (e: Exception) {
            Log.w("Zoomi", "transit failed", e)
            update { it.copy(loading = false, error = "Public transport search failed: ${e.message}") }
        }
    }

    private fun loadCrossings() {
        val p = _trip.value as? TripUi.Preview ?: return
        val route = p.routes.getOrNull(p.selected)?.route ?: return
        viewModelScope.launch {
            val c = runCatching { nav.crossings(route) }.onFailure { Log.w("Zoomi", "crossings failed", it) }.getOrNull()
            update { if (it.routes.getOrNull(it.selected)?.route === route) it.copy(crossings = c ?: emptyList()) else it }
        }
    }

    private fun loadAirports(fromLat: Double, fromLon: Double, dest: com.zoomi.core.location.LatLon) {
        if (!z.network.online.value) return
        viewModelScope.launch {
            val near = runCatching { z.photon.airportsNear(fromLat, fromLon) }.getOrDefault(emptyList())
            val far = runCatching { z.photon.airportsNear(dest.lat, dest.lon) }.getOrDefault(emptyList())
            update { it.copy(unreachable = it.unreachable?.copy(airportsNearYou = near, airportsNearDestination = far)) }
        }
    }

    private inline fun update(f: (TripUi.Preview) -> TripUi.Preview) {
        val cur = _trip.value
        if (cur is TripUi.Preview) _trip.value = f(cur)
    }

    fun selectRoute(index: Int) {
        val p = _trip.value as? TripUi.Preview ?: return
        val count = if (p.mode == com.zoomi.core.nav.TravelMode.TRANSIT) p.itineraries.size else p.routes.size
        if (index !in 0 until count || index == p.selected) return
        _trip.value = p.copy(selected = index, crossings = null)
        if (p.mode != com.zoomi.core.nav.TravelMode.TRANSIT) loadCrossings()
    }

    fun startNavigation() {
        val p = _trip.value as? TripUi.Preview ?: return
        val option = p.routes.getOrNull(p.selected) ?: return
        if (p.mode == com.zoomi.core.nav.TravelMode.TRANSIT) return // itineraries are shown, not navigated
        nav.start(option.route)
        _trip.value = TripUi.Navigating(p.destination, p.mode)
    }

    fun endTrip() {
        routeJob?.cancel()
        z.position.setRoute(null)
        if (_trip.value is TripUi.Navigating) nav.stop()
        _trip.value = TripUi.Idle
    }

    fun setVoiceMuted(muted: Boolean) = viewModelScope.launch { z.settings.setVoiceMuted(muted) }

    fun etaShareText(): String? {
        val dest = (_trip.value as? TripUi.Navigating)?.destination ?: return null
        val ts = navState.value.tripState as? TripState.Navigating ?: return null
        return EtaShare.text(dest.title, dest.lat, dest.lon, System.currentTimeMillis(), ts.progress.durationRemaining)
    }

    // ---- Saved places ----

    fun savePlace(place: Place, kind: PlaceKind, name: String) = viewModelScope.launch {
        z.settings.updatePlaces {
            SavedPlaces.upsert(it, SavedPlace(UUID.randomUUID().toString(), kind, name.ifBlank { place.title }, place.subtitle, place.lat, place.lon))
        }
    }

    fun deletePlace(id: String) = viewModelScope.launch { z.settings.updatePlaces { SavedPlaces.remove(it, id) } }

    fun goTo(saved: SavedPlace) = chooseDestination(Place(saved.name, saved.address, saved.lat, saved.lon))
}
