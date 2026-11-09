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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uniffi.ferrostar.Route
import uniffi.ferrostar.TripState
import java.util.UUID

data class RouteOption(val route: Route, val durationSec: Double, val distanceM: Double)

sealed interface TripUi {
    data object Idle : TripUi
    data class Preview(
        val destination: Place,
        val loading: Boolean = true,
        val routes: List<RouteOption> = emptyList(),
        val selected: Int = 0,
        val error: String? = null,
        /** Computed on the phone from a downloaded region (no connection or server unreachable). */
        val offline: Boolean = false,
    ) : TripUi
    data class Navigating(val destination: Place) : TripUi
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
                _speedLimit.value = nav.speedLimit(s)
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

    private fun requestRoutes() {
        val p = _trip.value as? TripUi.Preview ?: return
        routeJob?.cancel()
        _trip.value = p.copy(loading = true, error = null, routes = emptyList(), selected = 0)
        routeJob = viewModelScope.launch {
            val from = z.gnss.location.value
            if (from == null) {
                update { it.copy(loading = false, error = "Waiting for a GPS fix. Go outside or near a window and retry.") }
                return@launch
            }
            try {
                val routes = nav.routes(from, p.destination.lat, p.destination.lon).map {
                    RouteOption(it, it.steps.sumOf { s -> s.duration }, it.distance)
                }
                update {
                    if (routes.isEmpty()) it.copy(loading = false, error = "No route found.")
                    else it.copy(loading = false, routes = routes, selected = 0,
                        offline = nav.routeProvider.lastSource.value == com.zoomi.app.nav.RouteSource.OFFLINE)
                }
            } catch (e: Exception) {
                Log.w("Zoomi", "route failed", e)
                val msg = if (e is com.zoomi.app.nav.RoutingException) e.message else "Couldn't get a route: ${e.message ?: e::class.simpleName}"
                update { it.copy(loading = false, error = msg) }
            }
        }
    }

    private inline fun update(f: (TripUi.Preview) -> TripUi.Preview) {
        val cur = _trip.value
        if (cur is TripUi.Preview) _trip.value = f(cur)
    }

    fun selectRoute(index: Int) = update { if (index in it.routes.indices) it.copy(selected = index) else it }

    fun startNavigation() {
        val p = _trip.value as? TripUi.Preview ?: return
        val option = p.routes.getOrNull(p.selected) ?: return
        nav.start(option.route)
        _trip.value = TripUi.Navigating(p.destination)
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
