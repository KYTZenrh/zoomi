package com.zoomi.app.nav

import android.location.Location
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import com.zoomi.app.location.PositionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Feeds Ferrostar from Zoomi's position engine: GPS-only fixes (no Google Play Services, no
 * network location), or dead-reckoned positions along the route during GPS dropouts.
 */
class GnssNavigationLocationProvider(private val engine: PositionEngine) : NavigationLocationProviding {
    override suspend fun lastLocation(): Location? = engine.position.value
    override fun locationUpdates(intervalMillis: Long): Flow<Location> = engine.position.filterNotNull()
}
