package com.zoomi.app.nav

import android.location.Location
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import com.zoomi.app.location.GnssLocationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/** Feeds Ferrostar from Zoomi's GPS-only source (no Google Play Services, no network location). */
class GnssNavigationLocationProvider(private val gnss: GnssLocationSource) : NavigationLocationProviding {
    override suspend fun lastLocation(): Location? = gnss.location.value
    override fun locationUpdates(intervalMillis: Long): Flow<Location> = gnss.location.filterNotNull()
}
