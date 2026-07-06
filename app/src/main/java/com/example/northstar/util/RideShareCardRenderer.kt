package com.example.northstar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.example.northstar.data.Ride
import com.example.northstar.data.Units
import com.example.northstar.dash.nav.PolylineCodec
import com.example.northstar.ui.theme.Gold
import com.example.northstar.ui.theme.MapBase
import com.example.northstar.ui.theme.TextHi
import com.example.northstar.ui.theme.TextLo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders a shareable ride-summary card (name/date + route thumbnail + stats) as a PNG. */
object RideShareCardRenderer {
    private const val W = 1080
    private const val H = 1350
    private val dayFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    fun write(context: Context, ride: Ride, miles: Boolean): File {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(AColor.parseColor("#0D0F11"))   // Bg1

        val pad = 64f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TextHi.toArgb(); textSize = 56f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TextLo.toArgb(); textSize = 32f }
        val name = ride.name.ifBlank { "Ride" }
        canvas.drawText(name, pad, pad + 56f, titlePaint)
        canvas.drawText(dayFmt.format(Date(ride.startMs)), pad, pad + 100f, subPaint)

        // ── Route thumbnail ──
        val mapTop = pad + 140f
        val mapBottom = H - 380f
        val mapPaint = Paint().apply { color = MapBase.toArgb() }
        canvas.drawRoundRect(pad, mapTop, W - pad, mapBottom, 32f, 32f, mapPaint)

        val track = if (ride.trackPolyline.isBlank()) emptyList() else PolylineCodec.decode(ride.trackPolyline)
        if (track.size >= 2) {
            val inset = 56f
            val minLat = track.minOf { it.lat }; val maxLat = track.maxOf { it.lat }
            val minLng = track.minOf { it.lng }; val maxLng = track.maxOf { it.lng }
            val span = maxOf(maxLat - minLat, maxLng - minLng).coerceAtLeast(1e-6)
            val mapW = (W - 2 * pad - 2 * inset); val mapH = (mapBottom - mapTop - 2 * inset)
            fun px(lng: Double) = (pad + inset + (lng - minLng) / span * mapW).toFloat()
            fun py(lat: Double) = (mapBottom - inset - (lat - minLat) / span * mapH).toFloat()

            val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AColor.parseColor("#0E1416"); strokeWidth = 18f
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val route = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Gold.toArgb(); strokeWidth = 10f
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            track.forEachIndexed { i, p ->
                val x = px(p.lng); val y = py(p.lat)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, casing)
            canvas.drawPath(path, route)
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            dotPaint.color = TextHi.toArgb()
            canvas.drawCircle(px(track.first().lng), py(track.first().lat), 14f, dotPaint)
            dotPaint.color = Gold.toArgb()
            canvas.drawCircle(px(track.last().lng), py(track.last().lat), 14f, dotPaint)
        } else {
            canvas.drawText("No track recorded", pad + 32f, (mapTop + mapBottom) / 2, subPaint)
        }

        // ── Stats ──
        val stats = buildList {
            add("Distance" to Units.distance(ride.distanceKm, miles))
            add("Duration" to fmtDur(ride.durationSec))
            add("Avg speed" to Units.speed(ride.avgSpeedKmh, miles))
            if (ride.elevationGainM > 0.0) add("Elevation" to Units.elevation(ride.elevationGainM, miles))
            if (ride.maxLeanDeg > 0.0) add("Max lean" to "%.0f°".format(ride.maxLeanDeg))
        }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TextLo.toArgb(); textSize = 26f }
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TextHi.toArgb(); textSize = 42f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cols = 3
        val colW = (W - 2 * pad) / cols
        stats.forEachIndexed { i, (label, value) ->
            val col = i % cols; val row = i / cols
            val x = pad + col * colW
            val y = mapBottom + 90f + row * 130f
            canvas.drawText(label, x, y, statLabelPaint)
            canvas.drawText(value, x, y + 48f, statValuePaint)
        }

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${ride.sid.ifBlank { "ride" }}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun fmtDur(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
