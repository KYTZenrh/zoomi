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
    val navigation by lazy { NavigationController(this, gnss) }

    @Volatile var photonUrl: String = SettingsRepository.DEFAULT_PHOTON
    val photon by lazy { PhotonClient { photonUrl } }
}

val android.content.Context.zoomi: ZoomiApplication get() = applicationContext as ZoomiApplication
