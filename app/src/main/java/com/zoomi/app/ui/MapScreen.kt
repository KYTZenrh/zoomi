package com.zoomi.app.ui

import android.content.Intent
import android.location.Location
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoomi.app.R
import com.zoomi.app.map.ZoomiMap
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    dark: Boolean,
    location: Location?,
    course: Float?,
    courseUp: Boolean,
    gpsEnabled: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleCourseUp: () -> Unit,
    onTunnelRoad: (Boolean?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var follow by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        ZoomiMap(
            dark = dark,
            location = location,
            course = course,
            follow = follow,
            courseUp = courseUp,
            brandColor = MaterialTheme.colorScheme.primary.toArgb(),
            onUserPanned = { follow = false },
            onTunnelRoad = onTunnelRoad,
            modifier = Modifier.fillMaxSize(),
        )

        val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

        // Top: one status pill. Only shown when something needs attention, to keep the map clean.
        val status: String? = when {
            !hasPermission -> null
            !gpsEnabled -> "Location is off"
            location == null -> "Searching for GPS…"
            else -> null
        }
        Column(insets.align(Alignment.TopCenter).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (status != null) {
                Pill(status, onClick = if (!gpsEnabled) {
                    { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                } else null)
            }
            if (location != null && location.hasAccuracy() && location.accuracy > 30f) {
                Pill("GPS weak · ±${location.accuracy.roundToInt()} m", null)
            }
        }

        if (!hasPermission) {
            Surface(
                insets.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp, shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Zoomi needs precise location", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Your position comes from the phone's GPS chip and never leaves the device. " +
                            "Without it Zoomi can show the map but can't follow you.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRequestPermission) { Text("Allow location") }
                }
            }
        }

        // Right side: big round buttons, reachable with a thumb.
        Column(
            insets.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            RoundButton(
                icon = R.drawable.ic_compass,
                label = if (courseUp) "Heading up (tap for north up)" else "North up (tap for heading up)",
                rotation = if (courseUp) -(course ?: 0f) else 0f,
                onClick = onToggleCourseUp,
            )
            RoundButton(R.drawable.ic_settings, "Settings", onClick = onOpenSettings)
            if (!follow) {
                RoundButton(R.drawable.ic_locate, "Follow my position", highlighted = true, onClick = { follow = true })
            }
        }

        // Required OpenStreetMap attribution, always visible; tap for full credits.
        Surface(
            modifier = insets.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            shape = RoundedCornerShape(6.dp),
            onClick = onOpenAbout,
        ) {
            Text("© OpenStreetMap", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun Pill(text: String, onClick: (() -> Unit)?) {
    val mod = Modifier.padding(bottom = 6.dp)
    val content: @Composable () -> Unit = {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
    if (onClick != null) {
        Surface(modifier = mod, shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary, onClick = onClick, content = content)
    } else {
        Surface(mod, shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface, content = content)
    }
}

@Composable
private fun RoundButton(
    icon: Int,
    label: String,
    rotation: Float = 0f,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(60.dp),
        shape = CircleShape,
        shadowElevation = 6.dp,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon), contentDescription = label,
                modifier = Modifier.size(28.dp).rotate(rotation),
                tint = if (icon == R.drawable.ic_compass) Color.Unspecified else androidx.compose.material3.LocalContentColor.current,
            )
        }
    }
}
