package com.zoomi.app

import android.app.Application
import com.zoomi.app.data.SettingsRepository
import com.zoomi.app.location.GnssLocationSource
import com.zoomi.app.nav.NavigationController
import com.zoomi.app.search.PhotonClient

/** Process-wide singletons (one GPS source shared by the map, theme and navigation). */
class ZoomiApplication : Application() {
    val settings by lazy { SettingsRepository(this) }
    val gnss by lazy { GnssLocationSource(this) }
    val position by lazy { com.zoomi.app.location.PositionEngine(gnss) }
    val regions by lazy { com.zoomi.app.regions.RegionStore(this) }
    val network by lazy { com.zoomi.app.net.NetworkMonitor(this) }
    val navigation by lazy { NavigationController(this, position, regions, network) }

    @Volatile var photonUrl: String = SettingsRepository.DEFAULT_PHOTON
    val photon by lazy { PhotonClient { photonUrl } }
}

val android.content.Context.zoomi: ZoomiApplication get() = applicationContext as ZoomiApplication
