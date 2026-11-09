package com.zoomi.app.nav

import android.app.Notification
import android.content.Context
import com.stadiamaps.ferrostar.core.service.ForegroundNotificationBuilder
import com.zoomi.app.R
import com.zoomi.core.nav.Formatting
import uniffi.ferrostar.TripState

/** The ongoing notification shown while navigating, so guidance continues with the screen off. */
class ZoomiNotificationBuilder(context: Context) : ForegroundNotificationBuilder(context) {
    override fun build(tripState: TripState?): Notification {
        val nav = tripState as? TripState.Navigating
        val title = nav?.visualInstruction?.primaryContent?.text ?: "Zoomi navigation"
        val text = nav?.let {
            "In ${Formatting.distance(it.progress.distanceToNextManeuver)} · " +
                "${Formatting.distance(it.progress.distanceRemaining)} left · " +
                "arrive ${Formatting.arrivalClock(System.currentTimeMillis(), it.progress.durationRemaining)}"
        } ?: ""
        return Notification.Builder(context, channelId ?: "ferrostar_navigation")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .addAction(Notification.Action.Builder(null, "End", stopPendingIntent).build())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .build()
    }
}
