package com.example.northstar.data

/**
 * Compact altitude-sample storage, 1:1 with a [Ride]'s decoded [Ride.trackPolyline] points.
 * Plain comma-separated decimetres — elevation is one value per point (vs. two for lat/lng),
 * so the polyline varint codec isn't worth reusing here; a CSV of small integers is simpler
 * and still compact enough for local SQLite storage.
 */
object ElevationProfile {
    fun encode(altitudesM: List<Double>): String =
        altitudesM.joinToString(",") { Math.round(it * 10).toString() }

    fun decode(s: String): List<Double> =
        if (s.isBlank()) emptyList() else s.split(",").map { it.toDouble() / 10.0 }
}
