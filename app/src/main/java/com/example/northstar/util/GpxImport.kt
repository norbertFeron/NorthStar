package com.example.northstar.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.northstar.dash.nav.GeoPoint
import com.example.northstar.dash.nav.Maneuver
import com.example.northstar.dash.nav.ManeuverType
import com.example.northstar.dash.nav.Route
import org.xmlpull.v1.XmlPullParser

/**
 * Parses a GPX file into a [Route] for silent path-follow navigation — the imported track IS
 * the route geometry, with only DEPART/ARRIVE maneuvers (no synthesized turn banners; the
 * point of importing a curated route, e.g. from Kurviger/Komoot, is following a known road,
 * not needing turn-by-turn cues for it).
 */
object GpxImport {
    /**
     * Assumed pace for routes with no real duration data (a Directions API gives one; a GPX
     * track doesn't). Just enough to drive the route-profile ETA math (Router.kt's
     * cumulativeSeconds) with a flat line — not a claim about how fast the road actually goes.
     */
    private const val ASSUMED_SPEED_MPS = 12.0   // ~43 km/h

    /** Returns (route name, Route), preferring the GPX's own <trk>/<name> over [fallbackName]. */
    fun parse(context: Context, uri: Uri, fallbackName: String): Pair<String, Route>? {
        val points = ArrayList<GeoPoint>()
        var trackName: String? = null
        var inName = false

        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "trkpt", "rtept" -> {
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (lat != null && lng != null) points.add(GeoPoint(lat, lng))
                        }
                        "name" -> if (trackName == null) inName = true
                    }
                    XmlPullParser.TEXT -> if (inName && trackName == null) {
                        val t = parser.text?.trim()
                        if (!t.isNullOrBlank()) trackName = t
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "name") inName = false
                }
                event = parser.next()
            }
        }

        if (points.size < 2) return null

        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) cum[i] = cum[i - 1] + GeoPoint.distMeters(points[i - 1], points[i])
        val totalMeters = cum.last()
        val totalSeconds = totalMeters / ASSUMED_SPEED_MPS
        val cumSeconds = DoubleArray(points.size) { i -> cum[i] / ASSUMED_SPEED_MPS }

        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Head out on the imported route", points.first(), 0.0),
            Maneuver(ManeuverType.ARRIVE, "Arrive at destination", points.last(), totalMeters),
        )
        val route = Route(
            geometry = points, maneuvers = maneuvers,
            totalMeters = totalMeters, totalSeconds = totalSeconds,
            cumulative = cum, cumulativeSeconds = cumSeconds,
        )
        return (trackName ?: fallbackName) to route
    }
}
