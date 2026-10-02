package com.openmapsrouter.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.openmapsrouter.app.data.CoordinateResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object OrganicMapsLauncher {

    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=app.organicmaps"
    const val FDROID_URL = "https://f-droid.org/packages/app.organicmaps/"
    const val WEBSITE_URL = "https://organicmaps.app"

    fun buildOrganicMapsUrl(lat: Double, lon: Double, name: String?): String {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lonStr = String.format(Locale.US, "%.6f", lon)
        val nameParam = if (!name.isNullOrBlank()) {
            "&n=" + URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        } else ""
        return "om://map?v=1&ll=$latStr,$lonStr$nameParam"
    }

    fun buildGeoUrl(lat: Double, lon: Double, name: String?): String {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lonStr = String.format(Locale.US, "%.6f", lon)
        val label = if (!name.isNullOrBlank()) {
            "(" + URLEncoder.encode(name, StandardCharsets.UTF_8.name()) + ")"
        } else ""
        return "geo:$latStr,$lonStr?q=$latStr,$lonStr$label"
    }

    fun buildOsmWebUrl(lat: Double, lon: Double): String {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lonStr = String.format(Locale.US, "%.6f", lon)
        return "https://www.openstreetmap.org/?mlat=$latStr&mlon=$lonStr#map=16/$latStr/$lonStr"
    }

    fun launchOrganicMaps(
        context: Context,
        result: CoordinateResult,
        preferredScheme: String = "om",
        onNotInstalled: (() -> Unit)? = null
    ): Boolean {
        val primaryUriStr = if (preferredScheme == "om") {
            buildOrganicMapsUrl(result.latitude, result.longitude, result.name)
        } else {
            buildGeoUrl(result.latitude, result.longitude, result.name)
        }

        val secondaryUriStr = if (preferredScheme == "om") {
            buildGeoUrl(result.latitude, result.longitude, result.name)
        } else {
            buildOrganicMapsUrl(result.latitude, result.longitude, result.name)
        }

        // 1. Try primary URI
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(primaryUriStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Fall through to secondary
        }

        // 2. Try secondary URI
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(secondaryUriStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Neither could be opened
            if (onNotInstalled != null) {
                onNotInstalled()
            } else {
                Toast.makeText(context, "Organic Maps app not found. Please install it.", Toast.LENGTH_LONG).show()
                openUrl(context, PLAY_STORE_URL)
            }
            return false
        }
    }

    fun openInSystemMap(context: Context, result: CoordinateResult) {
        val geoUri = buildGeoUrl(result.latitude, result.longitude, result.name)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No map application found.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInOsmWeb(context: Context, result: CoordinateResult) {
        openUrl(context, buildOsmWebUrl(result.latitude, result.longitude))
    }

    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link in browser.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyCoordinatesToClipboard(context: Context, result: CoordinateResult) {
        val text = String.format(Locale.US, "%.6f, %.6f", result.latitude, result.longitude)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Coordinates", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Coordinates copied: $text", Toast.LENGTH_SHORT).show()
    }

    fun copyOmLinkToClipboard(context: Context, result: CoordinateResult) {
        val text = buildOrganicMapsUrl(result.latitude, result.longitude, result.name)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Organic Maps Link", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Organic Maps link copied!", Toast.LENGTH_SHORT).show()
    }

    fun shareCoordinates(context: Context, result: CoordinateResult) {
        val label = if (!result.name.isNullOrBlank()) "${result.name}: " else ""
        val omUrl = buildOrganicMapsUrl(result.latitude, result.longitude, result.name)
        val text = String.format(
            Locale.US,
            "%s%.6f, %.6f\nOrganic Maps: %s",
            label, result.latitude, result.longitude, omUrl
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, result.name ?: "Map Location")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Location").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun openDefaultAppsSettings(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to app details
            }
        }

        try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open system settings.", Toast.LENGTH_SHORT).show()
        }
    }
}
